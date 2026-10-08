package com.murai.gallery.ui.screens.tools.editor

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.image.Adjustments
import com.murai.gallery.domain.image.Filter
import com.murai.gallery.domain.image.ImageOps
import com.murai.gallery.ui.components.ProgressOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class TextStamp(val text: String, val x: Float, val y: Float, val sizePx: Float)
data class StrokePoint(val x: Float, val y: Float, val color: Int, val width: Float, val startNew: Boolean)

enum class EditorMode { VIEW, ADJUST, FILTER, CROP, DRAW, TEXT, STICKER }

data class EditorState(
    val source: Bitmap? = null,
    val entity: LibraryItemEntity? = null,
    val mode: EditorMode = EditorMode.VIEW,
    val adjust: Adjustments = Adjustments(),
    val cropRect: RectF? = null,
    val text: MutableList<TextStamp> = mutableListOf(),
    val strokes: MutableList<StrokePoint> = mutableListOf(),
    val brushColor: Int = 0xFFF5A623.toInt(),
    val brushWidth: Float = 8f,
    val working: Boolean = false,
    val saved: Boolean = false,
    val history: List<EditorSnapshot> = emptyList()
)

data class EditorSnapshot(
    val adjust: Adjustments,
    val crop: RectF?,
    val text: List<TextStamp>,
    val strokes: List<StrokePoint>
)

class EditorViewModel(private val container: AppContainer) : ViewModel() {

    val state = MutableStateFlow(EditorState())

    fun load(uri: String) {
        viewModelScope.launch {
            if (state.value.source != null) return@launch
            state.value = state.value.copy(working = true)
            val entity = resolveEntity(uri)
            val bmp = entity?.let {
                ImageOps.decodeFileSampled(it.path, 1600)
            }
            state.value = state.value.copy(source = bmp, entity = entity, working = false)
        }
    }

    private suspend fun resolveEntity(uri: String): LibraryItemEntity? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uri.startsWith("content:")) {
                    val id = android.content.ContentUris.parseId(android.net.Uri.parse(uri))
                    container.db.libraryDao().byId(id)
                } else null
            }.getOrNull()
        }

    fun setMode(mode: EditorMode) {
        val s = state.value
        state.value = s.copy(
            mode = mode,
            cropRect = if (mode == EditorMode.CROP) s.cropRect ?: RectF(0.1f, 0.1f, 0.9f, 0.9f) else null
        )
    }

    fun updateAdjustments(block: (Adjustments) -> Adjustments) {
        state.value = state.value.copy(adjust = block(state.value.adjust))
    }

    fun setFilter(filter: Filter) = updateAdjustments { it.copy(filter = filter) }

    fun addText(text: String) {
        if (text.isBlank()) return
        state.value.text.add(TextStamp(text, 0.5f, 0.3f, 0.08f))
    }

    fun addSticker(emoji: String) {
        state.value.text.add(TextStamp(emoji, 0.5f, 0.5f, 0.18f))
    }

    fun moveStamp(index: Int, x: Float, y: Float) {
        val stamps = state.value.text
        if (index !in stamps.indices) return
        stamps[index] = stamps[index].copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
    }

    fun addStrokePoint(x: Float, y: Float, startNew: Boolean) {
        state.value.strokes.add(
            StrokePoint(x, y, state.value.brushColor, state.value.brushWidth, startNew)
        )
    }

    fun setCropRect(rect: RectF) {
        state.value = state.value.copy(cropRect = rect)
    }

    fun pushHistory() {
        val s = state.value
        state.value = s.copy(
            history = s.history.takeLast(20) + EditorSnapshot(s.adjust, s.cropRect, s.text.toList(), s.strokes.toList())
        )
    }

    fun undo() {
        val s = state.value
        val last = s.history.lastOrNull() ?: return
        state.value = s.copy(
            adjust = last.adjust,
            cropRect = last.crop,
            text = last.text.toMutableList(),
            strokes = last.strokes.toMutableList(),
            history = s.history.dropLast(1)
        )
    }

    fun save(context: android.content.Context) {
        val s = state.value
        val source = s.source ?: return
        val entity = s.entity ?: return
        viewModelScope.launch {
            state.value = s.copy(working = true)
            val out = withContext(Dispatchers.Default) {
                render(source, s)
            }
            val ok = withContext(Dispatchers.IO) {
                val folder = "Pictures/Murai Edits/"
                val name = "murai_edit_${System.currentTimeMillis()}.jpg"
                out.compress(Bitmap.CompressFormat.JPEG, 95, java.io.ByteArrayOutputStream())
                container.operations.saveStream(
                    java.io.ByteArrayInputStream(compress(out)),
                    folder, name, false, "image/jpeg"
                ) != null
            }
            state.value = state.value.copy(working = false, saved = ok)
        }
    }

    private fun compress(bmp: Bitmap): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
        return bos.toByteArray()
    }

    companion object {
        /** Full render pipeline: adjustments -> crop -> stamps -> strokes. */
        fun render(source: Bitmap, s: EditorState): Bitmap {
            var bmp = ImageOps.applyAdjustments(source, s.adjust)
            s.cropRect?.let { rect ->
                val r = RectF(
                    rect.left * bmp.width, rect.top * bmp.height,
                    rect.right * bmp.width, rect.bottom * bmp.height
                )
                bmp = ImageOps.crop(bmp, r)
            }
            val output = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(output)
            for (stamp in s.text) {
                val paint = android.graphics.Paint().apply {
                    textSize = stamp.sizePx * output.width
                    color = 0xFFFFFFFF.toInt()
                    setShadowLayer(6f, 2f, 2f, 0x88000000)
                    isAntiAlias = true
                }
                canvas.drawText(stamp.text, stamp.x * output.width, stamp.y * output.height, paint)
            }
            var prev: StrokePoint? = null
            for (p in s.strokes) {
                val x = p.x * output.width
                val y = p.y * output.height
                val before = prev
                if (before != null && !p.startNew) {
                    val paint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = p.width * output.width / 1000f
                        color = p.color
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        isAntiAlias = true
                    }
                    canvas.drawLine(before.x * output.width, before.y * output.height, x, y, paint)
                }
                prev = p
            }
            return output
        }
    }
}
