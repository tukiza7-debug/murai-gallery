package com.murai.gallery.ui.screens.tools.editor

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.image.Filter
import com.murai.gallery.ui.components.ProgressOverlay
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    container: AppContainer,
    startUri: String,
    onBack: () -> Unit
) {
    val vm: EditorViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { EditorViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var pendingText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(startUri) { vm.load(startUri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_editor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo))
                    }
                    IconButton(onClick = {
                        vm.pushHistory()
                        vm.save(context)
                    }) {
                        Icon(Icons.Filled.Check, stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val bmp = state.source
            if (bmp == null) {
                if (state.working) ProgressOverlay(stringResource(R.string.loading), null)
            } else {
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(canvasSize) {
                                    detectTapGestures { offset ->
                                        if (canvasSize == IntSize.Zero) return@detectTapGestures
                                        if (state.mode == EditorMode.TEXT || state.mode == EditorMode.STICKER) {
                                            if (state.text.isNotEmpty()) {
                                                vm.moveStamp(
                                                    state.text.lastIndex,
                                                    offset.x / canvasSize.width,
                                                    offset.y / canvasSize.height
                                                )
                                            }
                                        }
                                    }
                                }
                                .pointerInput(canvasSize, state.mode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            if (state.mode == EditorMode.DRAW && canvasSize != IntSize.Zero) {
                                                vm.pushHistory()
                                                vm.addStrokePoint(
                                                    offset.x / canvasSize.width,
                                                    offset.y / canvasSize.height,
                                                    true
                                                )
                                            }
                                        }
                                    ) { change, _ ->
                                        change.consume()
                                        if (state.mode == EditorMode.DRAW && canvasSize != IntSize.Zero) {
                                            vm.addStrokePoint(
                                                change.position.x / canvasSize.width,
                                                change.position.y / canvasSize.height,
                                                false
                                            )
                                        } else if (state.mode == EditorMode.CROP && canvasSize != IntSize.Zero) {
                                            val r = state.cropRect
                                            if (r != null) {
                                                val nx = (change.position.x / canvasSize.width).coerceIn(0f, 1f)
                                                val ny = (change.position.y / canvasSize.height).coerceIn(0f, 1f)
                                                vm.setCropRect(
                                                    RectF(
                                                        r.left, r.top,
                                                        max(r.left + 0.1f, nx),
                                                        max(r.top + 0.1f, ny)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                        ) {
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                dstOffset = IntOffset(0, 0)
                            )
                            state.cropRect?.let { r ->
                                val dim = Color.Black.copy(alpha = 0.45f)
                                drawRect(dim, Offset(0f, 0f), Size(size.width, r.top * size.height))
                                drawRect(dim, Offset(0f, r.bottom * size.height), Size(size.width, (1f - r.bottom) * size.height))
                                drawRect(dim, Offset(0f, r.top * size.height), Size(r.left * size.width, (r.bottom - r.top) * size.height))
                                drawRect(dim, Offset(r.right * size.width, r.top * size.height), Size((1f - r.right) * size.width, (r.bottom - r.top) * size.height))
                                drawRect(
                                    Color(0xFFF5A623),
                                    Offset(r.left * size.width, r.top * size.height),
                                    Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
                                    style = Stroke(width = 3f)
                                )
                            }
                            var prev: StrokePoint? = null
                            for (p in state.strokes) {
                                val before = prev
                                if (before != null && !p.startNew) {
                                    drawLine(
                                        color = Color(p.color),
                                        start = Offset(before.x * size.width, before.y * size.height),
                                        end = Offset(p.x * size.width, p.y * size.height),
                                        strokeWidth = p.width
                                    )
                                }
                                prev = p
                            }
                            drawContext.canvas.nativeCanvas.let { native ->
                                for (stamp in state.text) {
                                    val paint = android.graphics.Paint().apply {
                                        textSize = stamp.sizePx * size.width
                                        color = android.graphics.Color.WHITE
                                        setShadowLayer(6f, 2f, 2f, 0x88000000)
                                        isAntiAlias = true
                                    }
                                    native.drawText(stamp.text, stamp.x * size.width, stamp.y * size.height, paint)
                                }
                            }
                        }
                    }

                    when (state.mode) {
                        EditorMode.ADJUST -> Column(Modifier.padding(horizontal = 8.dp)) {
                            AdjustSlider(
                                stringResource(R.string.editor_brightness),
                                state.adjust.brightness,
                                -1f, 1f
                            ) { v -> vm.updateAdjustments { it.copy(brightness = v) } }
                            AdjustSlider(
                                stringResource(R.string.editor_contrast),
                                state.adjust.contrast,
                                -1f, 1f
                            ) { v -> vm.updateAdjustments { it.copy(contrast = v) } }
                            AdjustSlider(
                                stringResource(R.string.editor_saturation),
                                state.adjust.saturation,
                                -1f, 1f
                            ) { v -> vm.updateAdjustments { it.copy(saturation = v) } }
                        }
                        EditorMode.FILTER -> Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Filter.entries.forEach { f ->
                                FilterChip(
                                    selected = state.adjust.filter == f,
                                    onClick = { vm.setFilter(f) },
                                    label = { Text(filterLabel(f)) }
                                )
                            }
                        }
                        EditorMode.DRAW -> Row(
                            Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(0xFFF5A623.toInt(), 0xFF0F766E.toInt(), 0xFFB3261E.toInt(), 0xFFFFFFFF.toInt(), 0xFF101820.toInt()).forEach { c ->
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(Color(c))
                                        .pointerInput(c) {
                                            detectTapGestures {
                                                vm.state.value = vm.state.value.copy(brushColor = c)
                                            }
                                        }
                                )
                            }
                            Slider(
                                value = state.brushWidth,
                                onValueChange = { vm.state.value = vm.state.value.copy(brushWidth = it) },
                                valueRange = 2f..30f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> Unit
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ToolChip(stringResource(R.string.editor_adjust)) { vm.setMode(EditorMode.ADJUST) }
                        ToolChip(stringResource(R.string.editor_filter)) { vm.setMode(EditorMode.FILTER) }
                        ToolChip(stringResource(R.string.editor_crop)) { vm.setMode(EditorMode.CROP) }
                        ToolChip(stringResource(R.string.editor_draw)) { vm.setMode(EditorMode.DRAW) }
                        ToolChip(stringResource(R.string.editor_text)) { pendingText = "text" }
                        ToolChip(stringResource(R.string.editor_sticker)) { pendingText = "sticker" }
                    }
                }

                if (state.working) {
                    ProgressOverlay(stringResource(R.string.saving), null, Modifier.align(Alignment.Center))
                }
                if (state.saved) {
                    AlertDialog(
                        onDismissRequest = onBack,
                        title = { Text(stringResource(R.string.editor_saved_title)) },
                        text = { Text(stringResource(R.string.editor_saved_text)) },
                        confirmButton = {
                            TextButton(onClick = onBack) { Text(stringResource(R.string.action_ok)) }
                        }
                    )
                }

                pendingText?.let { kind ->
                    TextDialog(
                        isSticker = kind == "sticker",
                        onDone = { value ->
                            if (value.isNotBlank()) {
                                vm.pushHistory()
                                if (kind == "sticker") vm.addSticker(value) else vm.addText(value)
                            }
                            pendingText = null
                        }
                    )
                }
            }
        }
    }
}

/** Builder mirroring the VM's android.graphics.RectF signature. */
private fun RectF(left: Float, top: Float, right: Float, bottom: Float): android.graphics.RectF =
    android.graphics.RectF(left, top, right, bottom)

@Composable
private fun AdjustSlider(label: String, value: Float, rangeStart: Float, rangeEnd: Float, onChange: (Float) -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Slider(
            value = value.coerceIn(rangeStart, rangeEnd),
            onValueChange = onChange,
            valueRange = rangeStart..rangeEnd
        )
    }
}

@Composable
private fun ToolChip(label: String, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(label) })
}

@Composable
private fun TextDialog(isSticker: Boolean, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone("") },
        title = {
            Text(if (isSticker) stringResource(R.string.editor_pick_sticker) else stringResource(R.string.editor_add_text))
        },
        text = {
            if (isSticker) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("😀", "❤️", "⭐", "🔥", "🌸", "🎉", "😎", "🐦").forEach { emoji ->
                        TextButton(onClick = { onDone(emoji) }) { Text(emoji) }
                    }
                }
            } else {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = {
            if (!isSticker) {
                TextButton(onClick = { onDone(text) }) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { onDone("") }) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun filterLabel(f: Filter): String = when (f) {
    Filter.NONE -> stringResource(R.string.filter_none)
    Filter.GRAYSCALE -> stringResource(R.string.filter_grayscale)
    Filter.SEPIA -> stringResource(R.string.filter_sepia)
    Filter.INVERT -> stringResource(R.string.filter_invert)
    Filter.WARM -> stringResource(R.string.filter_warm)
    Filter.COOL -> stringResource(R.string.filter_cool)
    Filter.FADE -> stringResource(R.string.filter_fade)
    Filter.DRAMATIC -> stringResource(R.string.filter_dramatic)
}
