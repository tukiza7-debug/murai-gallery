package com.murai.gallery.domain.barcode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes QR codes and barcodes from existing photos using the pure-Java
 * zxing core. Includes a light safety classifier for scanned URLs.
 */
object BarcodeEngine {

    data class ScanResult(val text: String, val format: String, val looksLikeUrl: Boolean)

    suspend fun decode(context: Context, uri: android.net.Uri): ScanResult? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bmp = load(context, uri) ?: return@withContext null
            try {
                val width = bmp.width
                val height = bmp.height
                val pixels = IntArray(width * height)
                bmp.getPixels(pixels, 0, width, 0, 0, width, height)
                val source = RGBLuminanceSource(width, height, pixels)
                val reader = MultiFormatReader().apply {
                    setHints(
                        mapOf(
                            DecodeHintType.TRY_HARDER to true,
                            DecodeHintType.POSSIBLE_FORMATS to listOf(
                                BarcodeFormat.QR_CODE, BarcodeFormat.EAN_13,
                                BarcodeFormat.EAN_8, BarcodeFormat.CODE_128,
                                BarcodeFormat.CODE_39, BarcodeFormat.UPC_A,
                                BarcodeFormat.UPC_E, BarcodeFormat.DATA_MATRIX
                            )
                        )
                    )
                }
                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)))
                ScanResult(
                    text = result.text,
                    format = result.barcodeFormat.name,
                    looksLikeUrl = result.text.startsWith("http://") || result.text.startsWith("https://")
                )
            } catch (t: Throwable) {
                null
            } finally {
                bmp.recycle()
            }
        }

    /** Insecure schemes get a strong warning before any ACTION_VIEW launch. */
    fun isSafeToOpen(text: String): Boolean {
        val t = text.trim().lowercase()
        return !(t.startsWith("http://") || t.startsWith("intent:") ||
            t.startsWith("javascript:") || t.startsWith("file:"))
    }

    private fun load(context: Context, uri: android.net.Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}
