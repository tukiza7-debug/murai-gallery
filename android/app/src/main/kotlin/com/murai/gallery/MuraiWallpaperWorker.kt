package com.murai.gallery

import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Applies a wallpaper change from the user-selected albums.
 * Runs entirely on the platform side (no Flutter engine required).
 */
class MuraiWallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MuraiWallpaper"
        const val KEY_ALBUMS = "murai_wallpaper_albums"
        const val KEY_SHUFFLE = "murai_wallpaper_shuffle"
        const val KEY_INDEX = "murai_wallpaper_index"
        const val KEY_ENABLED = "murai_wallpaper_enabled"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(MuraiToolsHandler.PREFS, Context.MODE_PRIVATE)
        try {
            if (!prefs.getBoolean(KEY_ENABLED, true)) return@withContext Result.success()

            val albums = prefs.getString(KEY_ALBUMS, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            if (albums.isEmpty()) return@withContext Result.success()

            val candidates = collectImages(albums)
            if (candidates.isEmpty()) return@withContext Result.success()

            val shuffle = prefs.getBoolean(KEY_SHUFFLE, true)
            val index = prefs.getInt(KEY_INDEX, 0)
            val path = if (shuffle) candidates.random().absolutePath else candidates[index % candidates.size].absolutePath
            prefs.edit().putInt(KEY_INDEX, index + 1).apply()

            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap != null) {
                val bytes = java.io.ByteArrayOutputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    out.toByteArray()
                }
                bitmap.recycle()
                // reuse the handler pipeline for center-crop + apply
                val ok = WallpaperApplier(applicationContext).applyFromBytes(bytes)
                Log.i(TAG, "wallpaper applied=$ok from=$path")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "wallpaper change failed", e)
            Result.retry()
        }
    }

    private fun collectImages(albums: List<String>): List<File> {
        val result = mutableListOf<File>()
        for (album in albums) {
            val dir = File(album)
            if (dir.isDirectory) {
                dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic") }?.let { result.addAll(it) }
            }
        }
        if (result.isEmpty()) {
            // fall back to whole MediaStore images if configured folders are empty
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            applicationContext.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext() && result.size < 200) {
                    cursor.getString(col)?.let { result.add(File(it)) }
                }
            }
        }
        return result
    }
}

/** Extracted applier so both the handler and the worker share the same crop/apply logic. */
class WallpaperApplier(private val context: Context) {
    fun applyFromBytes(bytes: ByteArray): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val manager = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val scale = maxOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(screenW)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(screenH)
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
        val cropped = try {
            android.graphics.Bitmap.createBitmap(scaled, (w - screenW) / 2, (h - screenH) / 2, screenW, screenH)
        } catch (e: Exception) {
            scaled
        }
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                manager.setBitmap(cropped, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            } else {
                @Suppress("DEPRECATION")
                manager.setBitmap(cropped)
            }
            true
        } catch (e: Exception) {
            Log.w("MuraiWallpaper", "apply failed", e)
            false
        } finally {
            if (cropped !== scaled) scaled.recycle()
            scaled.recycle()
            bitmap.recycle()
        }
    }
}
