package com.murai.gallery.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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
 */
class MediaScanner(
    private val context: Context,
    private val db: MuraiDatabase
) {

    data class ScanProgress(
        val stage: String,
        val processed: Int,
        val total: Int
    )

    sealed interface ScanEvent {
        data class Progress(val stage: String, val processed: Int, val total: Int) : ScanEvent
        data class Finished(val scanned: Int) : ScanEvent
        data class Failed(val message: String) : ScanEvent
    }

    suspend fun scan(onEvent: suspend (ScanEvent) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            var processed = 0
            val batch = ArrayList<LibraryItemEntity>(SCAN_BATCH)
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.DURATION,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.IS_FAVORITE,
                MediaStore.Files.FileColumns.IS_TRASHED
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
            val resolver = context.contentResolver
            var total = 0
            resolver.query(collection, arrayOf(MediaStore.Files.FileColumns._ID), selection, selectionArgs, null)
                ?.use { c -> total = c.count }

            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val iId = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val iName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val iPath = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val iMime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val iType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val iSize = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val iWidth = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val iHeight = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val iDur = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                val iMod = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val iTaken = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val iAdded = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val iBucket = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val iBucketName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val iFav = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_FAVORITE)
                val iTrashed = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_TRASHED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(iId)
                    val isVideo = cursor.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val relPath = cursor.getString(iPath) ?: ""
                    val name = cursor.getString(iName) ?: continue
                    val folder = (cursor.getString(iBucketName) ?: relPath.trim('/')).ifBlank { "Storage" }
                    val mime = cursor.getString(iMime)
                        ?: if (isVideo) "video/*" else "image/*"
                    val dateTaken = cursor.getLong(iTaken)
                    val dateMod = cursor.getLong(iMod)
                    val lower = name.lowercase()
                    val isPano = (cursor.getInt(iWidth) >= cursor.getInt(iHeight) * 2) &&
                        mime.contains("image") && cursor.getInt(iWidth) > 4000
                    batch += LibraryItemEntity(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        path = relPath + name,
                        name = name,
                        folder = folder,
                        bucketId = cursor.getString(iBucket) ?: folder,
                        mime = mime,
                        isVideo = isVideo,
                        size = cursor.getLong(iSize),
                        width = cursor.getInt(iWidth),
                        height = cursor.getInt(iHeight),
                        durationMs = if (isVideo) cursor.getLong(iDur) else 0L,
                        dateModifiedSec = dateMod,
                        dateTakenSec = if (dateTaken > 0) dateTaken / 1000 else dateMod,
                        dateAddedSec = cursor.getLong(iAdded),
                        favorite = cursor.getInt(iFav) == 1,
                        rating = 0,
                        trashed = cursor.getInt(iTrashed) == 1,
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
            }
            val stale = db.libraryDao().deleteStale(now - 1)
            onEvent(ScanEvent.Progress("indexing", processed, processed))
            onEvent(ScanEvent.Finished(processed))
            ErrorLogger.write(context, "scan", null, "processed=$processed staleRemoved=$stale")
        } catch (t: Throwable) {
            ErrorLogger.write(context, "scan-failed", t)
            onEvent(ScanEvent.Failed(t.message ?: "unknown"))
        }
    }

    companion object {
        private const val SCAN_BATCH = 500
    }
}
