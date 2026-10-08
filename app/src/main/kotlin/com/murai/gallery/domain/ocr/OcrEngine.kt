package com.murai.gallery.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Offline text recognition over a bundled model. */
object OcrEngine {

    suspend fun recognize(context: Context, imageUri: android.net.Uri): String =
        withContext(Dispatchers.IO) {
            val bitmap = decode(context, imageUri)
                ?: throw IllegalStateException("decode")
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { text -> if (cont.isActive) cont.resume(text.text) }
                        .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                }
            } finally {
                recognizer.close()
                bitmap.recycle()
            }
        }

    private fun decode(context: Context, uri: android.net.Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}
