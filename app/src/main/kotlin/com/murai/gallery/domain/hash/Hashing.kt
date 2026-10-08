package com.murai.gallery.domain.hash

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

/**
 * Exact and perceptual hashing used by the duplicate finder.
 * dHash (difference hash, 9x8 grayscale, 64 bits) is computed from a small
 * decode so it is fast enough for entire libraries.
 */
object Hashing {

    suspend fun sha256(input: InputStream): String? = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun decodeSmall(stream: InputStream, target: Int = 72): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeStream(stream, null, opts)
    }.getOrNull()

    /** 64-bit difference hash of the luminance grid. */
    fun dHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(9 * 8)
        scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        var hash = 0L
        var bit = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(pixels[y * 9 + x])
                val right = luminance(pixels[y * 9 + x + 1])
                if (left > right) hash = hash or (1L shl bit.toInt())
                bit++
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return hash
    }

    fun dHashFromBytes(bytes: ByteArray): Long? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            dHash(bmp)
        } finally {
            bmp.recycle()
        }
    }.getOrNull()

    private fun luminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Hamming distance between two 64-bit hashes; <= 8 usually means "similar". */
    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
