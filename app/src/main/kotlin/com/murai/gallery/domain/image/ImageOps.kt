package com.murai.gallery.domain.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/** Adjustments available in the editor. All are pure functions on bitmaps. */
data class Adjustments(
    val brightness: Float = 0f,      // -1..1
    val contrast: Float = 0f,        // -1..1
    val saturation: Float = 0f,      // -1..1
    val rotationDeg: Int = 0,
    val filter: Filter = Filter.NONE
)

enum class Filter(val id: String) {
    NONE("none"),
    GRAYSCALE("grayscale"),
    SEPIA("sepia"),
    INVERT("invert"),
    WARM("warm"),
    COOL("cool"),
    FADE("fade"),
    DRAMATIC("dramatic");
}

object ImageOps {

    /**
     * Samples a source stream so the decoded bitmap fits within [maxDim].
     * Two passes need two streams: provider streams are not seekable, so
     * callers open the source twice (see decodeSourceSampled).
     */
    fun boundsSample(stream: InputStream): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
        return sample
    }

    fun decodeStreamSampled(stream: InputStream, sample: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeStream(stream, null, opts)
    }.getOrNull()

    /** Opens [uri] (content or file) sampled to [maxDim] via two stream passes. */
    fun decodeSourceSampled(context: android.content.Context, uri: String, maxDim: Int = 1600): Bitmap? =
        runCatching {
            val parsed = android.net.Uri.parse(uri)
            if (uri.startsWith("content:")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
                context.contentResolver.openInputStream(parsed)?.use {
                    decodeStreamSampled(it, sample)
                }
            } else {
                decodeFileSampled(uri, maxDim)
            }
        }.getOrNull()

    /** Samples a real file so the decoded bitmap fits within [maxDim]. */
    fun decodeFileSampled(path: String, maxDim: Int = 2048): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull()

    fun exifRotated(bitmap: Bitmap, path: String): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            ExifInterface.ORIENTATION_TRANSPOSE -> 45
            ExifInterface.ORIENTATION_TRANSVERSE -> 315
            else -> 0
        }
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun colorMatrixFor(adjust: Adjustments): ColorMatrix {
        val cm = ColorMatrix()
        val b = adjust.brightness * 255f
        val c = 1f + adjust.contrast
        val translate = (0.5f - 0.5f * c) * 255f
        val contrastM = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate + b,
                0f, c, 0f, 0f, translate + b,
                0f, 0f, c, 0f, translate + b,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val sat = 1f + adjust.saturation
        val saturationM = ColorMatrix().apply { setSaturation(sat.coerceIn(0f, 2f)) }
        cm.postConcat(contrastM)
        cm.postConcat(saturationM)
        when (adjust.filter) {
            Filter.GRAYSCALE -> cm.postConcat(ColorMatrix().apply { setSaturation(0f) })
            Filter.SEPIA -> cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            Filter.INVERT -> cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            Filter.WARM -> cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.08f, 0f, 0f, 0f, 8f,
                        0f, 1.02f, 0f, 0f, 0f,
                        0f, 0f, 0.92f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            Filter.COOL -> cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.92f, 0f, 0f, 0f, 0f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 1.08f, 0f, 12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            Filter.FADE -> cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.85f, 0f, 0f, 0f, 32f,
                        0f, 0.85f, 0f, 0f, 32f,
                        0f, 0f, 0.85f, 0f, 32f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            Filter.DRAMATIC -> {
                cm.postConcat(ColorMatrix().apply { setSaturation(0.7f) })
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.25f, 0f, 0f, 0f, -28f,
                            0f, 1.2f, 0f, 0f, -24f,
                            0f, 0f, 1.15f, 0f, -18f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            Filter.NONE -> Unit
        }
        return cm
    }

    /** Applies adjustments (color + rotation), returning a new bitmap. */
    fun applyAdjustments(src: Bitmap, adjust: Adjustments): Bitmap {
        val rotated = if (adjust.rotationDeg % 360 != 0) {
            val m = Matrix().apply { postRotate(adjust.rotationDeg.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        } else src
        val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrixFor(adjust))
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        if (rotated !== src && rotated !== output) rotated.recycle()
        return output
    }

    fun crop(src: Bitmap, rect: android.graphics.RectF): Bitmap {
        val l = min(max(rect.left, 0f), src.width.toFloat()).toInt()
        val t = min(max(rect.top, 0f), src.height.toFloat()).toInt()
        val w = min(max(rect.width().toInt(), 1), src.width - l)
        val h = min(max(rect.height().toInt(), 1), src.height - t)
        return Bitmap.createBitmap(src, l, t, w, h)
    }

    fun resize(src: Bitmap, targetWidth: Int): Bitmap {
        if (targetWidth >= src.width) return src
        val ratio = targetWidth.toFloat() / src.width
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetWidth, h, true)
    }

    fun compress(bmp: Bitmap, format: Bitmap.CompressFormat, quality: Int, out: OutputStream): Boolean =
        bmp.compress(format, quality.coerceIn(10, 100), out)

    fun formatFor(mime: String): Bitmap.CompressFormat = when {
        mime.contains("png") -> Bitmap.CompressFormat.PNG
        mime.contains("webp") ->
            if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }

    fun tempBitmapFile(cacheDir: File, name: String): File = File(cacheDir, name)
}
