package com.murai.gallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.WallpaperManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class MuraiToolsHandler(private val context: Context) : MethodChannel.MethodCallHandler {
    companion object {
        const val CHANNEL = "com.murai.gallery/murai_tools"
        const val TAG = "MuraiTools"
        const val NOTIFICATION_CHANNEL_ID = "murai_tools"
        const val PREFS = "murai_prefs"
        const val WORK_NAME = "murai_wallpaper_change"
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val res: Any? = when (call.method) {
                    "saveBytesToMediaStore" -> saveBytesToMediaStore(call)
                    "getVideoFrames" -> getVideoFrames(call)
                    "trimVideo" -> trimVideo(call)
                    "decodeQr" -> decodeQr(call)
                    "setWallpaperFromPath" -> setWallpaperFromPath(call)
                    "setWallpaperFromBytes" -> setWallpaperFromBytes(call)
                    "scheduleWallpaperChange" -> scheduleWallpaperChange(call)
                    "cancelWallpaperChange" -> cancelWallpaperChange()
                    "applyWallpaperNow" -> applyWallpaperNow()
                    "notifyProgress" -> notifyProgress(call)
                    "notifyFinished" -> notifyFinished(call)
                    "cancelNotification" -> cancelNotification(call)
                    "setRecentsScreenshotEnabled" -> setRecentsScreenshotEnabled(call)
                    "getFreeStorageBytes" -> getFreeStorageBytes()
                    else -> throw IllegalArgumentException("unknown method ${call.method}")
                }
                withContext(Dispatchers.Main) { result.success(res) }
            } catch (e: Exception) {
                Log.w(TAG, "method ${call.method} failed", e)
                withContext(Dispatchers.Main) { result.error(TAG, e.message, null) }
            }
        }
    }

    private fun requireArgs(call: MethodCall, vararg keys: String): List<Any> = keys.map { call.argument<Any>(it) ?: throw IllegalArgumentException("missing $it") }
    // ---------- MediaStore saving ----------

    private fun saveBytesToMediaStore(call: MethodCall): String {
        val (displayName, relativePath, mimeType, bytes) = requireArgs(call, "displayName", "relativePath", "mimeType", "bytes")
        val data = bytes as ByteArray
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName as String)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType as String)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath as String)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Murai Gallery")
                dir.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(dir, displayName as String).absolutePath)
            }
        }
        val collection = if ((mimeType as String).startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = context.contentResolver.insert(collection, values) ?: throw IllegalStateException("MediaStore insert failed")
        context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: throw IllegalStateException("cannot open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        return uri.toString()
    }

    // ---------- video frames (for GIF maker) ----------

    private fun getVideoFrames(call: MethodCall): List<ByteArray> {
        val (pathArg, timestampsSecs, maxWidth) = requireArgs(call, "path", "timestampsSecs", "maxWidth")
        val path = pathArg as String
        val stamps = (timestampsSecs as List<*>).map { (it as Number).toDouble() }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            return stamps.mapNotNull { secs ->
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime((secs * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxWidth as Int, Integer.MAX_VALUE)
                } else {
                    retriever.getFrameAtTime((secs * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                frame?.let { scaleBitmap(it, maxWidth as Int).toJpegBytes(90) }
            }
        } finally {
            retriever.release()
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun Bitmap.toJpegBytes(quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

    // ---------- video trim (MediaExtractor + MediaMuxer, MP4) ----------

    private fun trimVideo(call: MethodCall): String {
        val (path, startMs, endMs, destName) = requireArgs(call, "path", "startMs", "endMs", "destName")
        val sourcePath = path as String
        val outFile = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Murai Gallery").apply { mkdirs() }, destName as String)
        if (outFile.exists()) outFile.delete()

        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(sourcePath)
            val indexMap = mutableMapOf<Int, Int>()
            var rotation = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                extractor.selectTrack(i)
                val trackIndex = if (mime.startsWith("video/")) {
                    rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                    muxer.addTrack(format)
                } else {
                    muxer.addTrack(format)
                }
                indexMap[i] = trackIndex
            }
            if (indexMap.isEmpty()) throw IllegalStateException("no tracks found")
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val startUs = (startMs as Number).toLong() * 1000
            val endUs = (endMs as Number).toLong() * 1000
            muxer.setOrientationHint(rotation)
            muxer.start()
            for ((srcIndex, muxIndex) in indexMap) {
                extractor.selectTrack(srcIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    info.presentationTimeUs = extractor.sampleTime
                    if (info.presentationTimeUs > endUs) break
                    if (info.presentationTimeUs >= startUs && info.size > 0) {
                        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        muxer.writeSampleData(muxIndex, buffer, info)
                    }
                    extractor.advance()
                }
            }
            muxer.stop()
            return outFile.absolutePath
        } finally {
            extractor.release()
            muxer.release()
        }
    }

    // ---------- QR / barcode decoding ----------

    private fun decodeQr(call: MethodCall): Map<String, String>? {
        val (path) = requireArgs(call, "path")
        val bitmap = BitmapFactory.decodeFile(path as String) ?: run {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.frameAtTime
            } finally {
                retriever.release()
            }
        } ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val reader = MultiFormatReader()
        return try {
            val res = reader.decode(BinaryBitmap(HybridBinarizer(source)))
            mapOf("text" to res.text, "format" to res.barcodeFormat.name)
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    // ---------- wallpaper ----------

    private fun setWallpaperFromPath(call: MethodCall): Boolean {
        val (path) = requireArgs(call, "path")
        val bitmap = BitmapFactory.decodeFile(path as String) ?: return false
        return applyWallpaperBitmap(bitmap)
    }

    private fun setWallpaperFromBytes(call: MethodCall): Boolean {
        val (bytes) = requireArgs(call, "bytes")
        val bitmap = BitmapFactory.decodeByteArray(bytes as ByteArray, 0, bytes.size) ?: return false
        return applyWallpaperBitmap(bitmap)
    }

    private fun applyWallpaperBitmap(bitmap: Bitmap): Boolean {
        val manager = WallpaperManager.getInstance(context)
        // center-crop to screen
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val scale = maxOf(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(screenW)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(screenH)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val cropped = Bitmap.createBitmap(scaled, (w - screenW) / 2, (h - screenH) / 2, screenW, screenH)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.setBitmap(cropped, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            } else {
                @Suppress("DEPRECATION")
                manager.setBitmap(cropped)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "wallpaper failed", e)
            false
        } finally {
            if (cropped !== scaled) scaled.recycle()
            bitmap.recycle()
        }
    }

    private fun wallpaperPrefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun scheduleWallpaperChange(call: MethodCall): Boolean {
        val (intervalMinutes) = requireArgs(call, "intervalMinutes")
        val minutes = (intervalMinutes as Number).toLong().coerceAtLeast(15)
        val workManager = WorkManager.getInstance(context)
        val request = PeriodicWorkRequestBuilder<MuraiWallpaperWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        return true
    }

    private fun cancelWallpaperChange(): Boolean {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        return true
    }

    private fun applyWallpaperNow(): Boolean {
        val request = OneTimeWorkRequestBuilder<MuraiWallpaperWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
        return true
    }

    // ---------- notifications ----------

    private fun notificationManager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Murai Tools", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Progress of long-running Murai Gallery tools"
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notifyProgress(call: MethodCall): Boolean {
        val args = requireArgs(call, "id", "title", "text", "progress", "indeterminate", "cancellable")
        val id = args[0] as Number
        val title = args[1] as String
        val text = args[2] as String
        val progress = args[3] as Number
        val indeterminate = args[4] as Boolean
        val cancellable = args[5] as Boolean
        ensureChannel()
        val openIntent = PendingIntent.getActivity(context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title as String)
            .setContentText(text as String)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setProgress(100, (progress as Number).toInt(), indeterminate as Boolean)
        if (cancellable as Boolean) {
            val stopIntent = PendingIntent.getBroadcast(
                context, 1,
                Intent("com.murai.gallery.CANCEL_TOOL").setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, context.getString(android.R.string.cancel), stopIntent)
        }
        notificationManager().notify((id as Number).toInt(), builder.build())
        return true
    }

    private fun notifyFinished(call: MethodCall): Boolean {
        val (id, title, text) = requireArgs(call, "id", "title", "text")
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title as String)
            .setContentText(text as String)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager().notify((id as Number).toInt(), notification)
        return true
    }

    private fun cancelNotification(call: MethodCall): Boolean {
        val (id) = requireArgs(call, "id")
        notificationManager().cancel((id as Number).toInt())
        return true
    }

    // ---------- misc ----------

    private fun setRecentsScreenshotEnabled(call: MethodCall): Boolean {
        val (enabled) = requireArgs(call, "enabled")
        val activity = context as? android.app.Activity ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                activity.setRecentsScreenshotEnabled(enabled as Boolean)
            } catch (e: Exception) {
                Log.w(TAG, "setRecentsScreenshotEnabled failed", e)
            }
        }
        return true
    }

    private fun getFreeStorageBytes(): Long = Environment.getExternalStorageDirectory().usableSpace
}
