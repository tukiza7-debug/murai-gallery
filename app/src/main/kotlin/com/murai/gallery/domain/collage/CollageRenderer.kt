package com.murai.gallery.domain.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Template-driven collage renderer. Templates are defined as normalized
 * rectangles so any aspect ratio renders cleanly at export resolution.
 */
object CollageRenderer {

    /** Each template is a list of normalized (0..1) cells. */
    fun templates(count: Int): List<List<RectF>> = when (count) {
        2 -> listOf(
            listOf(RectF(0f, 0f, 0.5f, 1f), RectF(0.5f, 0f, 1f, 1f)),
            listOf(RectF(0f, 0f, 1f, 0.5f), RectF(0f, 0.5f, 1f, 1f))
        )
        3 -> listOf(
            listOf(
                RectF(0f, 0f, 0.5f, 0.5f), RectF(0.5f, 0f, 1f, 0.5f),
                RectF(0f, 0.5f, 1f, 1f)
            ),
            listOf(
                RectF(0f, 0f, 1f, 0.5f),
                RectF(0f, 0.5f, 0.5f, 1f), RectF(0.5f, 0.5f, 1f, 1f)
            ),
            listOf(
                RectF(0f, 0f, 0.5f, 1f),
                RectF(0.5f, 0f, 1f, 0.5f), RectF(0.5f, 0.5f, 1f, 1f)
            )
        )
        4 -> listOf(
            listOf(
                RectF(0f, 0f, 0.5f, 0.5f), RectF(0.5f, 0f, 1f, 0.5f),
                RectF(0f, 0.5f, 0.5f, 1f), RectF(0.5f, 0.5f, 1f, 1f)
            )
        )
        5, 6 -> listOf(
            listOf(
                RectF(0f, 0f, 1f / 3f, 0.5f), RectF(1f / 3f, 0f, 2f / 3f, 0.5f), RectF(2f / 3f, 0f, 1f, 0.5f),
                RectF(0f, 0.5f, 1f / 3f, 1f), RectF(1f / 3f, 0.5f, 2f / 3f, 1f), RectF(2f / 3f, 0.5f, 1f, 1f)
            ),
            if (count == 5) listOf(
                RectF(0f, 0f, 0.5f, 1f),
                RectF(0.5f, 0f, 1f, 1f / 3f),
                RectF(0.5f, 1f / 3f, 1f, 2f / 3f),
                RectF(0.5f, 2f / 3f, 1f, 1f)
            ) else listOf(
                RectF(0f, 0f, 0.5f, 1f / 3f), RectF(0.5f, 0f, 1f, 1f / 3f),
                RectF(0f, 1f / 3f, 0.5f, 2f / 3f), RectF(0.5f, 1f / 3f, 1f, 2f / 3f),
                RectF(0f, 2f / 3f, 0.5f, 1f), RectF(0.5f, 2f / 3f, 1f, 1f)
            )
        )
        else -> grid(count.coerceIn(7, 9))
    }

    private fun grid(count: Int): List<List<RectF>> {
        val cols = 3
        val rows = (count + cols - 1) / cols
        val cells = ArrayList<RectF>(count)
        var placed = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (placed >= count) break
                cells.add(
                    RectF(
                        c / 3f, r / rows.toFloat(),
                        (c + 1) / 3f, (r + 1) / rows.toFloat()
                    )
                )
                placed++
            }
        }
        return listOf(cells)
    }

    /**
     * Renders the selected images into [outWidth]x[outHeight] with center-crop
     * fit, spacing [gapPx] and a solid background color.
     */
    fun render(
        images: List<Bitmap>,
        templateIndex: Int,
        gapPx: Int,
        bgColor: Int,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val template = templates(images.size)[templateIndex.coerceIn(0, templates(images.size).size - 1)]
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(bgColor)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for ((i, cell) in template.withIndex()) {
            val bmp = images.getOrNull(i) ?: continue
            val left = cell.left * outWidth + gapPx
            val top = cell.top * outHeight + gapPx
            val right = cell.right * outWidth - gapPx
            val bottom = cell.bottom * outHeight - gapPx
            val cellW = right - left
            val cellH = bottom - top
            if (cellW <= 0 || cellH <= 0) continue
            val scale = maxOf(cellW / bmp.width, cellH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = left + (cellW - dw) / 2f
            val dy = top + (cellH - dh) / 2f
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.drawBitmap(bmp, dx, dy, paint)
            canvas.restore()
        }
        return output
    }

    fun bgColorFor(index: Int): Int = when (index % 6) {
        0 -> Color.WHITE
        1 -> Color.BLACK
        2 -> Color.parseColor("#101820")
        3 -> Color.parseColor("#F5A623")
        4 -> Color.parseColor("#ECE9E2")
        else -> Color.parseColor("#0F766E")
    }
}
