package com.murai.gallery.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.murai.gallery.data.db.MuraiDatabase
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.util.ErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Incremental, paged scan of the system media provider. Designed for large
 * libraries (50k+ items): the query runs on Dispatchers.IO in pages, rows are
 * mapped to entities and upserted in batches so the grid can appear while the
 * scan is still running.
 *
 * v2.0.1 hardening:
 *  - The projection is built per API level: RELATIVE_PATH exists on 29+,
 *    IS_FAVORITE / IS_TRASHED on 30+. Optional columns are resolved with
 *    getColumnIndex + a -1 check, never getColumnIndexOrThrow.
 *  - On API 30+ the query includes trashed rows via QUERY_ARG_MATCH_TRASHED =
 *    MATCH_INCLUDE so the in-app Bin can still restore them; API 26-29 keeps
 *    the app-internal legacy bin.
 *  - A background pass backfills GPS coordinates for items that have none,
 *    reading EXIF through MediaStore.setRequireOriginal (needs
 *    ACCESS_MEDIA_LOCATION, API 29+).
 *  - The whole pass is single-flight (ScanCoordinator): ScanWorker, refresh
 *    and the permission callback share one job.
 *  - deleteStale runs only after a fully completed pass with permission; a
 *    failed, partial or permission-less pass never deletes rows, and a
 *    revoked / partial grant (Android 14 selected photos) never wipes the
 *    library cache.
 */
class MediaScanner(
    private val context: Context,
    private val db: MuraiDatabase
) {

    sealed interface ScanEvent {
        data class Progress(val stage: String, val processed: Int, val total: Int) : ScanEvent
        data class Finished(val scanned: Int) : ScanEvent
        data class Failed(val message: String) : ScanEvent
    }

    val coordinator = ScanCoordinator()

    suspend fun scan(onEvent: suspend (ScanEvent) -> Unit) {
        coordinator.once { runScan(onEvent) }
    }

    private suspend fun runScan(onEvent: suspend (ScanEvent) -> Unit) =
        withContext(Dispatchers.IO) {
            val hasPermission = hasMediaPermission()
            if (!hasPermission) {
                // Partial grant or revocation: keep the existing cache intact.
                ErrorLogger.write(context, "scan-skipped", null, "reason=no-media-permission")
                onEvent(ScanEvent.Failed("permission"))
                return@withContext
            }
            try {
                val now = System.currentTimeMillis()
                var processed = 0
                val batch = ArrayList<LibraryItemEntity>(SCAN_BATCH)
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val sdk = Build.VERSION.SDK_INT
                val projection = projectionFor(sdk)
                val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)"
                val selectionArgs = arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
                val resolver = context.contentResolver

                var total = 0
                runCatching {
                    resolver.query(
                        collection, arrayOf(MediaStore.Files.FileColumns._ID), selection, selectionArgs, null
                    )?.use { c -> total = c.count }
                }

                val queryArgs = if (sdk >= Build.VERSION_CODES.R) {
                    Bundle().apply {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
                        putString(
                            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                            selection
                        )
                        putStringArray(
                            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                            selectionArgs
                        )
                        putString(
                            android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                        )
                    }
                } else null

                val cursor = if (queryArgs != null) {
                    resolver.query(collection, projection, queryArgs, null)
                } else {
                    resolver.query(
                        collection, projection, selection, selectionArgs,
                        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                    )
                }

                var cursorCompleted = false
                if (cursor == null) {
                    // Provider refused (rare): never delete anything.
                    ErrorLogger.write(context, "scan-aborted", null, "reason=null-cursor")
                    onEvent(ScanEvent.Failed("cursor"))
                    return@withContext
                }
                cursor.use { c ->
                    val iId = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val iName = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val iRel = if (sdk >= Build.VERSION_CODES.Q)
                        c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH) else -1
                    val iData = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val iMime = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val iType = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val iSize = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val iWidth = c.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                    val iHeight = c.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                    val iDur = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                    val iMod = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val iTaken = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
                    val iAdded = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                    val iBucket = c.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                    val iBucketName = c.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    val iFav = if (sdk >= Build.VERSION_CODES.R)
                        c.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE) else -1
                    val iTrashed = if (sdk >= Build.VERSION_CODES.R)
                        c.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED) else -1

                    while (c.moveToNext()) {
                        if (iId < 0 || iName < 0) continue
                        val id = c.getLong(iId)
                        val isVideo = iType >= 0 &&
                            c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val name = c.getString(iName) ?: continue
                        val relPath = if (iRel >= 0) c.getString(iRel) ?: "" else ""
                        val data = if (iData >= 0) c.getString(iData) ?: "" else ""
                        val bucketName = if (iBucketName >= 0) c.getString(iBucketName) else null
                        val folder = deriveFolder(relPath, data, bucketName)
                        val mime = c.getString(iMime)
                            ?: if (isVideo) "video/*" else "image/*"
                        val dateTaken = if (iTaken >= 0) c.getLong(iTaken) else 0L
                        val dateMod = if (iMod >= 0) c.getLong(iMod) else 0L
                        val dateAdded = if (iAdded >= 0) c.getLong(iAdded) else dateMod
                        val width = if (iWidth >= 0) c.getInt(iWidth) else 0
                        val height = if (iHeight >= 0) c.getInt(iHeight) else 0
                        val lower = name.lowercase()
                        val isPano = width >= height * 2 &&
                            mime.contains("image") && width > 4000
                        val trashed = if (iTrashed >= 0) c.getInt(iTrashed) == 1 else false
                        batch += LibraryItemEntity(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            path = deriveDisplayPath(relPath, data, name),
                            name = name,
                            folder = folder,
                            bucketId = if (iBucket >= 0) (c.getString(iBucket) ?: folder) else folder,
                            mime = mime,
                            isVideo = isVideo,
                            size = if (iSize >= 0) c.getLong(iSize) else 0L,
                            width = width,
                            height = height,
                            durationMs = if (isVideo && iDur >= 0) c.getLong(iDur) else 0L,
                            dateModifiedSec = dateMod,
                            dateTakenSec = if (dateTaken > 0) dateTaken / 1000 else dateMod,
                            dateAddedSec = dateAdded,
                            favorite = if (iFav >= 0) c.getInt(iFav) == 1 else false,
                            rating = 0,
                            trashed = trashed,
                            latitude = 0.0,
                            longitude = 0.0,
                            locationLabel = "",
                            exactHash = null,
                            phash = null,
                            isPano = isPano,
                            isMotion = !isVideo &&
                                (lower.contains("motion") || lower.endsWith(".mp.jpg")),
                            scannedAt = now
                        )
                        processed++
                        if (batch.size >= SCAN_BATCH) {
                            db.libraryDao().upsertAll(batch)
                            batch.clear()
                            onEvent(ScanEvent.Progress("scanning", processed, total))
                        }
                    }
                    if (batch.isNotEmpty()) db.libraryDao().upsertAll(batch)
                    cursorCompleted = true
                }

                if (ScanCoordinator.allowDeleteStale(
                        cursorCompleted = cursorCompleted,
                        hasPermission = hasPermission,
                        cursorNull = false
                    )
                ) {
                    val stale = db.libraryDao().deleteStale(now - 1)
                    ErrorLogger.write(context, "scan", null, "processed=$processed staleRemoved=$stale")
                } else {
                    ErrorLogger.write(context, "scan", null, "processed=$processed staleKept=true")
                }
                onEvent(ScanEvent.Progress("indexing", processed, processed))
                onEvent(ScanEvent.Finished(processed))
            } catch (t: Throwable) {
                // Partial pass: every cached row stays, nothing is deleted.
                ErrorLogger.write(context, "scan-failed", t)
                onEvent(ScanEvent.Failed(t.message ?: "unknown"))
            }
        }

    /**
     * Backfills GPS coordinates for items that have none. Runs in small
     * batches AFTER a quick scan so Map / location sort / GeoLabeler have
     * data on API 29+, where coordinates are redacted unless the original
     * bytes are requested via setRequireOriginal (needs ACCESS_MEDIA_LOCATION).
     */
    suspend fun backfillGps(limit: Int = GPS_BATCH_LIMIT) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
            != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            ErrorLogger.write(context, "gps-backfill-skipped", null, "reason=no-media-location")
            return@withContext
        }
        val pending = runCatching { db.libraryDao().withoutCoordinates(limit) }.getOrDefault(emptyList())
        if (pending.isEmpty()) return@withContext
        val resolver = context.contentResolver
        var updated = 0
        for (item in pending) {
            if (item.isVideo) continue
            try {
                val uri = Uri.parse(item.uri)
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        MediaStore.setRequireOriginal(uri)
                    } catch (_: Throwable) {
                        uri
                    }
                } else uri
                val latLon = resolver.openInputStream(streamUri)?.use { stream ->
                    runCatching {
                        val exif = androidx.exifinterface.media.ExifInterface(stream)
                        exif.latLong?.let { if (it.size >= 2 && (it[0] != 0.0 || it[1] != 0.0)) it else null }
                    }.getOrNull()
                } ?: continue
                db.libraryDao().setCoordinates(item.id, latLon[0], latLon[1])
                updated++
            } catch (t: Throwable) {
                ErrorLogger.write(context, "gps-backfill-item", t, "id=${item.id}")
            }
        }
        ErrorLogger.write(context, "gps-backfill", null, "candidates=${pending.size} updated=$updated")
    }

    private fun hasMediaPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    companion object {
        private const val SCAN_BATCH = 500
        private const val GPS_BATCH_LIMIT = 400

        /**
         * Pure, unit-tested projection builder. Columns that do not exist on
         * the running API are simply omitted; the row mapper checks column
         * indices for -1 before reading them.
         */
        fun projectionFor(sdk: Int): Array<String> {
            val cols = mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.DURATION,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATA
            )
            if (sdk >= Build.VERSION_CODES.Q) {
                cols.add(MediaStore.Files.FileColumns.RELATIVE_PATH)
                cols.add(MediaStore.Files.FileColumns.BUCKET_ID)
                cols.add(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            } else {
                // BUCKET_ID / BUCKET_DISPLAY_NAME are officially public on 29+;
                // on 26-28 they exist on the media tables but not reliably on
                // Files, so folder names come from DATA instead.
            }
            if (sdk >= Build.VERSION_CODES.R) {
                cols.add(MediaStore.Files.FileColumns.IS_FAVORITE)
                cols.add(MediaStore.Files.FileColumns.IS_TRASHED)
            }
            return cols.toTypedArray()
        }

        /**
         * Folder label derivation (unit tested): prefer the bucket display
         * name, fall back to the last segment of RELATIVE_PATH, then to the
         * last segment of DATA's parent directory.
         */
        fun deriveFolder(relPath: String, data: String, bucketName: String?): String {
            bucketName?.takeIf { it.isNotBlank() }?.let { return it }
            relPath.trim('/').takeIf { it.isNotBlank() }?.let { rel ->
                // RELATIVE_PATH points at the file's directory; the display
                // name is its last segment ("DCIM/Camera/" -> "Camera").
                return rel.substringAfterLast('/').ifBlank { "Storage" }
            }
            data.trim('/').takeIf { it.isNotBlank() }?.let { d ->
                val parent = d.substringBeforeLast('/')
                val seg = parent.substringAfterLast('/')
                return seg.ifBlank { "Storage" }
            }
            return "Storage"
        }

        /**
         * Display-only path stored in the DB (unit tested). On 26-28 this is
         * the real DATA path; on 29+ it is RELATIVE_PATH + name, which is NOT
         * a file path — consumers must go through the content URI.
         */
        fun deriveDisplayPath(relPath: String, data: String, name: String): String =
            if (relPath.isNotBlank()) {
                (relPath.trimEnd('/') + "/" + name).trimStart('/')
            } else if (data.isNotBlank()) data else name
    }
}
