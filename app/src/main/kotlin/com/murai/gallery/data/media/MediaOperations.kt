package com.murai.gallery.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.murai.gallery.data.db.MuraiDatabase
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.util.ErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * All writes go through MediaStore content URIs resolved from row ids, never
 * hand-built file paths, which is what keeps "Invalid URI" errors away.
 * Scoped-storage consent (RecoverableSecurityException) is surfaced to the
 * caller as a [ConsentEvent] instead of crashing.
 */
class MediaOperations(
    private val context: Context,
    private val db: MuraiDatabase
) {

    sealed interface ConsentEvent {
        data class Needed(val sender: IntentSender, val retry: () -> Unit) : ConsentEvent
    }

    data class BatchResult(val ok: Int, val failed: Int, val consent: IntentSender?)

    private fun contentUri(id: Long, isVideo: Boolean): Uri =
        ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            id
        )

    /**
     * Resolves the on-disk path from the DATA column. Only meaningful on
     * API 26-28 where the app may touch the file directly; on 29+ the value
     * is not writable and callers must use content URI streams instead.
     * Never trusts the cached relative "path" column.
     */
    private fun realDataPath(item: LibraryItemEntity): String? = runCatching {
        context.contentResolver.query(
            contentUri(item.id, item.isVideo),
            arrayOf(MediaStore.MediaColumns.DATA),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    suspend fun trash(ids: List<LibraryItemEntity>): BatchResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            trashModern(ids)
        } else {
            softDeleteLegacy(ids)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun trashModern(items: List<LibraryItemEntity>): BatchResult {
        return try {
            val uris = items.map { contentUri(it.id, it.isVideo) }.toTypedArray()
            val request = MediaStore.createTrashRequest(context.contentResolver, uris.toList(), true)
            BatchResult(0, 0, request.intentSender)
        } catch (t: Throwable) {
            ErrorLogger.write(context, "trash", t)
            BatchResult(0, items.size, null)
        }
    }

    private suspend fun softDeleteLegacy(items: List<LibraryItemEntity>): BatchResult {
        var ok = 0
        var failed = 0
        val binDir = File(context.getExternalFilesDir(null), "bin").apply { mkdirs() }
        for (item in items) {
            try {
                // Copy through the content URI stream — the cached path column
                // is RELATIVE_PATH on 29+ and is never a file path.
                val srcUri = contentUri(item.id, item.isVideo)
                val dest = File(binDir, "${item.id}_${item.name}")
                val copied = context.contentResolver.openInputStream(srcUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (copied) {
                    context.contentResolver.delete(srcUri, null, null)
                }
                db.libraryDao().setTrashed(listOf(item.id), true)
                ok++
            } catch (t: Throwable) {
                failed++
                ErrorLogger.write(context, "soft-delete", t)
            }
        }
        return BatchResult(ok, failed, null)
    }

    suspend fun restore(items: List<LibraryItemEntity>): BatchResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var consent: IntentSender? = null
            try {
                val uris = items.map { contentUri(it.id, it.isVideo) }.toTypedArray()
                val request = MediaStore.createTrashRequest(context.contentResolver, uris.toList(), false)
                consent = request.intentSender
            } catch (t: Throwable) {
                ErrorLogger.write(context, "restore", t)
            }
            if (consent != null) BatchResult(0, 0, consent)
            else BatchResult(0, items.size, null)
        } else {
            var ok = 0
            val binDir = File(context.getExternalFilesDir(null), "bin")
            for (item in items) {
                try {
                    val backup = File(binDir, "${item.id}_${item.name}")
                    if (!backup.exists()) continue
                    copyIntoMediaStore(backup, item.folder, item.name, item.isVideo, item.mime)
                    backup.delete()
                    db.libraryDao().setTrashed(listOf(item.id), false)
                    ok++
                } catch (t: Throwable) {
                    ErrorLogger.write(context, "restore-legacy", t)
                }
            }
            BatchResult(ok, items.size - ok, null)
        }
    }

    suspend fun deleteForever(items: List<LibraryItemEntity>): BatchResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val uris = items.map { contentUri(it.id, it.isVideo) }.toTypedArray()
                val request = MediaStore.createDeleteRequest(context.contentResolver, uris.toList())
                return@withContext BatchResult(0, 0, request.intentSender)
            } catch (t: Throwable) {
                ErrorLogger.write(context, "delete-forever", t)
                return@withContext BatchResult(0, items.size, null)
            }
        }
        var ok = 0
        var failed = 0
        for (item in items) {
            try {
                context.contentResolver.delete(contentUri(item.id, item.isVideo), null, null)
                db.libraryDao().deleteByIds(listOf(item.id))
                ok++
            } catch (t: Throwable) {
                failed++
                ErrorLogger.write(context, "delete-legacy", t)
            }
        }
        BatchResult(ok, failed, null)
    }

    suspend fun setFavorite(items: List<LibraryItemEntity>, fav: Boolean): BatchResult =
        withContext(Dispatchers.IO) {
            var ok = 0
            var failed = 0
            for (item in items) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_FAVORITE, if (fav) 1 else 0)
                        }
                        context.contentResolver.update(contentUri(item.id, item.isVideo), values, null, null)
                    }
                    db.libraryDao().setFavorite(item.id, fav)
                    ok++
                } catch (t: Throwable) {
                    failed++
                    ErrorLogger.write(context, "favorite", t)
                }
            }
            BatchResult(ok, failed, null)
        }

    suspend fun rename(item: LibraryItemEntity, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Q+ renames go through MediaStore; RELATIVE_PATH keeps the folder.
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                context.contentResolver.update(contentUri(item.id, item.isVideo), values, null, null) > 0
            } else {
                // Legacy: rename the real file resolved from DATA, not the cached path.
                val realPath = realDataPath(item) ?: return@withContext false
                val src = File(realPath)
                if (!src.exists()) return@withContext false
                val dest = File(src.parentFile, newName)
                val done = src.renameTo(dest)
                if (done) scanFile(dest)
                done
            }
        } catch (t: Throwable) {
            ErrorLogger.write(context, "rename", t)
            false
        }.also { success ->
            if (success) {
                val newPath = item.path.substringBeforeLast('/') + "/" + newName
                db.libraryDao().rename(item.id, newName, newPath)
            }
        }
    }

    suspend fun copyIntoGallery(item: LibraryItemEntity, targetFolder: String, newName: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val src = contentUri(item.id, item.isVideo)
                context.contentResolver.openInputStream(src)?.use { input ->
                    copyStreamIntoMediaStore(input, targetFolder, newName, item.isVideo, item.mime)
                }
            } catch (t: Throwable) {
                ErrorLogger.write(context, "copy", t)
                null
            }
        }

    suspend fun move(item: LibraryItemEntity, targetFolder: String): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetFolder)
                }
                val updated = context.contentResolver.update(contentUri(item.id, item.isVideo), values, null, null)
                if (updated > 0) contentUri(item.id, item.isVideo) else null
            } else {
                // Legacy move: resolve the real file from DATA and rename it.
                val realPath = realDataPath(item) ?: return@withContext null
                val src = File(realPath)
                if (!src.exists()) return@withContext null
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val destDir = File(base, targetFolder.substringAfter("DCIM/").ifEmpty { "Murai" }).apply { mkdirs() }
                val dest = File(destDir, item.name)
                if (src.renameTo(dest)) {
                    scanFile(dest)
                    contentUri(item.id, item.isVideo)
                } else null
            }
        } catch (t: Throwable) {
            ErrorLogger.write(context, "move", t)
            null
        }
    }

    suspend fun saveStream(
        input: java.io.InputStream,
        folder: String,
        name: String,
        isVideo: Boolean,
        mime: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            copyStreamIntoMediaStore(input, folder, name, isVideo, mime)
        } catch (t: Throwable) {
            ErrorLogger.write(context, "save-stream", t)
            null
        }
    }

    private fun copyIntoMediaStore(file: File, folder: String, name: String, isVideo: Boolean, mime: String): Uri? =
        file.inputStream().buffered().use { copyStreamIntoMediaStore(it, folder, name, isVideo, mime) }

    private fun copyStreamIntoMediaStore(
        input: java.io.InputStream,
        folder: String,
        name: String,
        isVideo: Boolean,
        mime: String
    ): Uri? {
        val collection = if (isVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun scanFile(file: File) {
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), null, null
        )
    }

    /**
     * Edits the EXIF metadata of a media item through the content URI:
     * the original bytes are copied to a private temp file, the edit runs
     * there, and the result is written back through openOutputStream. On
     * API 30+ writing to shared media may require user consent; the
     * resulting IntentSender is surfaced instead of crashing.
     * Returns WRITE_OK, or WRITE_CONSENT with the sender to launch.
     */
    suspend fun writeExif(item: LibraryItemEntity, edit: (File) -> Unit): ExifWriteResult =
        withContext(Dispatchers.IO) {
            val temp = File(context.cacheDir, "murai_exif_${System.nanoTime()}_${item.name}")
            try {
                val srcUri = contentUri(item.id, item.isVideo)
                context.contentResolver.openInputStream(srcUri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext ExifWriteResult(false, null)
                edit(temp)
                try {
                    context.contentResolver.openOutputStream(srcUri)?.use { out ->
                        temp.inputStream().use { it.copyTo(out) }
                    } ?: return@withContext ExifWriteResult(false, null)
                    ExifWriteResult(true, null)
                } catch (sec: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val request = MediaStore.createWriteRequest(
                                context.contentResolver, listOf(srcUri)
                            )
                            ExifWriteResult(false, request.intentSender)
                        } catch (t: Throwable) {
                            ErrorLogger.write(context, "exif-write-consent", t)
                            ExifWriteResult(false, null)
                        }
                    } else {
                        ErrorLogger.write(context, "exif-write", sec)
                        ExifWriteResult(false, null)
                    }
                }
            } catch (t: Throwable) {
                ErrorLogger.write(context, "exif-write", t)
                ExifWriteResult(false, null)
            } finally {
                temp.delete()
            }
        }

    data class ExifWriteResult(val ok: Boolean, val consent: IntentSender?)
}
