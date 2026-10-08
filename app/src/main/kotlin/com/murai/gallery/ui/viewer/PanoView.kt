package com.murai.gallery.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.cos
import kotlin.math.PI

/**
 * Original 360 panorama viewer: pans the equirectangular strip with
 * wrap-around and a subtle cylindrical vertical warp, so horizontal drag
 * feels like looking around. No third-party sphere engine involved.
 */
@Composable
fun PanoView(uri: String) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(uri) {
        bitmap = runCatching {
            // Two passes need two streams: provider streams are not seekable.
            if (uri.startsWith("content:")) {
                val parsed = android.net.Uri.parse(uri)
                val sample = context.contentResolver.openInputStream(parsed)?.use { boundsOnly(it) } ?: 1
                context.contentResolver.openInputStream(parsed)?.use { decodeLong(it, sample) }
            } else {
                val file = File(uri.replace("file://", ""))
                val sample = boundsOnly(file.inputStream())
                decodeLong(file.inputStream(), sample)
            }
        }.getOrNull()
    }

    val bmp = bitmap
    if (bmp == null) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bmp) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    scrollX += dragAmount.x * 1.4f / zoom
                }
            }
    ) {
        val native = drawContext.canvas.nativeCanvas
        native.drawColor(android.graphics.Color.BLACK)
        val viewW = size.width
        val viewH = size.height
        val sliceW = (bmp.width / zoom).toInt().coerceIn(64, bmp.width)
        val sliceH = bmp.height
        var offset = scrollX % sliceW
        if (offset < 0) offset += sliceW
        val srcStart = ((offset / sliceW) * (bmp.width - sliceW)).toInt()
        val slice = Bitmap.createBitmap(bmp, srcStart, 0, sliceW, sliceH)
        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        // draw in 12 vertical bands with a cylindrical vertical warp
        val bands = 12
        val bandSrcW = sliceW / bands
        val bandDstW = viewW / bands
        for (i in 0 until bands) {
            val u = (i + 0.5f) / bands
            val ang = (u - 0.5f) * PI.toFloat()
            val warp = 1.0f - 0.22f * cos(ang)
            val bandH = viewH * warp
            val top = (viewH - bandH) / 2f
            val src = android.graphics.Rect((i * bandSrcW), 0, ((i + 1) * bandSrcW), sliceH)
            val dst = RectF(i * bandDstW, top, (i + 1) * bandDstW, top + bandH)
            native.drawBitmap(slice, src, dst, paint)
        }
        if (slice !== bmp) slice.recycle()
    }
}

private fun boundsOnly(stream: java.io.InputStream): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(stream, null, bounds)
    var sample = 1
    while (bounds.outHeight / (sample * 2) >= 1024) sample *= 2
    return sample
}

private fun decodeLong(stream: java.io.InputStream, sample: Int, maxH: Int = 1024): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeStream(stream, null, opts)
}.getOrNull()
