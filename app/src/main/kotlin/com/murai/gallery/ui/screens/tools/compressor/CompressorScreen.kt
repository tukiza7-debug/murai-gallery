package com.murai.gallery.ui.screens.tools.compressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.image.ImageOps
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import com.murai.gallery.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class CompressTarget(val id: Long, val name: String, val origBytes: Long, val newBytes: Long = 0)

data class CompressorState(
    val quality: Int = 80,
    val maxWidth: Int = 1920,
    val working: Boolean = false,
    val progress: Float = 0f,
    val done: Boolean = false,
    val originals: List<CompressTarget> = emptyList()
)

/**
 * Batch compressor / resizer. Shows before/after sizes per item and total
 * savings when the batch completes.
 */
class CompressorViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(CompressorState())

    fun setQuality(q: Int) {
        state.value = state.value.copy(quality = q.coerceIn(30, 100))
    }

    fun setWidth(w: Int) {
        state.value = state.value.copy(maxWidth = w)
    }

    fun loadLargest() {
        launchSafely(container.appContext, "compressor") {
            val files = container.repository.biggestFiles(512 * 1024, 20)
            state.value = state.value.copy(
                originals = files.filter { !it.isVideo }
                    .map { CompressTarget(it.id, it.name, it.size) }
            )
        }
    }

    fun run(context: Context) {
        val s = state.value
        if (s.originals.isEmpty()) return
        launchSafely(container.appContext, "compressor") {
            state.value = s.copy(working = true, progress = 0f, done = false)
            var processed = 0
            for (target in s.originals) {
                val entity = container.db.libraryDao().byId(target.id) ?: continue
                val newBytes = withContext(Dispatchers.IO) {
                    runCatching {
                        // Decode through the content URI — the cached path
                        // column is not a real file path (fix #1).
                        val bmp = ImageOps.decodeSourceSampled(
                            container.appContext, entity.uri, s.maxWidth
                        ) ?: return@runCatching 0L
                        val resized = ImageOps.resize(bmp, s.maxWidth)
                        val bos = ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, s.quality, bos)
                        val bytes = bos.toByteArray()
                        val saved = container.operations.saveStream(
                            bytes.inputStream(), "Pictures/Murai Compressed/", "c_${entity.name}", false, "image/jpeg"
                        )
                        if (resized !== bmp) resized.recycle()
                        bmp.recycle()
                        if (saved != null) bytes.size.toLong() else 0L
                    }.getOrDefault(0L)
                }
                processed++
                state.value = state.value.copy(
                    originals = state.value.originals.map { if (it.id == target.id) it.copy(newBytes = newBytes) else it },
                    progress = processed.toFloat() / s.originals.size
                )
            }
            state.value = state.value.copy(working = false, done = true)
        }
    }
}

@Composable
fun CompressorScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: CompressorViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { CompressorViewModel(container) })
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadLargest() }

    ToolScaffold(title = stringResource(R.string.tool_compressor), onBack = onBack) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.compress_quality, state.quality), style = MaterialTheme.typography.titleSmall)
                Slider(value = state.quality.toFloat(), onValueChange = { vm.setQuality(it.toInt()) }, valueRange = 30f..100f)
                Text(stringResource(R.string.compress_width, state.maxWidth), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf(640, 1280, 1920, 4000).forEach { w ->
                        androidx.compose.material3.FilterChip(
                            selected = state.maxWidth == w,
                            onClick = { vm.setWidth(w) },
                            label = { Text("${w}px") }
                        )
                    }
                }
                if (state.originals.isEmpty()) {
                    Text(
                        stringResource(R.string.compress_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Text(
                        stringResource(R.string.compress_largest_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(state.originals, key = { it.id }) { t ->
                            Card(Modifier.padding(vertical = 3.dp)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Text(
                                            Formatters.fileSize(t.origBytes) +
                                                if (t.newBytes > 0) " → " + Formatters.fileSize(t.newBytes) else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { vm.run(context) },
                    enabled = !state.working && state.originals.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.compress_run)) }
                if (state.working) {
                    ProgressOverlay(
                        stringResource(R.string.compress_working),
                        state.progress,
                        Modifier.padding(top = 12.dp)
                    )
                }
                if (state.done) {
                    val before = state.originals.sumOf { it.origBytes }
                    val after = state.originals.sumOf { it.newBytes }
                    Text(
                        stringResource(R.string.compress_done, Formatters.fileSize(before - after)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
