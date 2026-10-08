package com.murai.gallery.ui.screens.tools.gifmaker

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.gif.GifEncoder
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GifState(
    val frameCount: Int = 8,
    val fps: Int = 5,
    val width: Int = 480,
    val working: Boolean = false,
    val preview: Bitmap? = null,
    val saved: Boolean = false,
    val error: Boolean = false
)

/**
 * GIF maker from the latest video: samples evenly spaced frames and encodes
 * them with the built-in original GIF encoder (median-cut palette + LZW).
 */
class GifViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(GifState())

    fun setFrameCount(n: Int) {
        state.value = state.value.copy(frameCount = n.coerceIn(2, 24))
    }

    fun setFps(n: Int) {
        state.value = state.value.copy(fps = n.coerceIn(2, 12))
    }

    fun setWidth(w: Int) {
        state.value = state.value.copy(width = w)
    }

    fun makeAndSave(context: Context) {
        val s = state.value
        viewModelScope.launch {
            state.value = s.copy(working = true, error = false, saved = false)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val video = container.db.libraryDao().listByType(true).firstOrNull() ?: return@runCatching false
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(video.uri))
                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: return@runCatching false
                        val frames = ArrayList<Bitmap>(s.frameCount)
                        for (i in 0 until s.frameCount) {
                            val tUs = durationMs * 1000L * i / s.frameCount
                            val frame = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: continue
                            frames.add(Bitmap.createScaledBitmap(frame, s.width, frame.height * s.width / frame.width.coerceAtLeast(1), true))
                        }
                        if (frames.isEmpty()) return@runCatching false
                        val pixels = frames.map { f ->
                            IntArray(f.width * f.height).also { f.getPixels(it, 0, f.width, 0, 0, f.width, f.height) }
                        }
                        val encoder = GifEncoder()
                        val bos = java.io.ByteArrayOutputStream()
                        encoder.encode(pixels, s.width, pixels[0].size / s.width, 100 / s.fps, bos)
                        frames.forEach { if (!it.isRecycled) it.recycle() }
                        val saved = container.operations.saveStream(
                            bos.toByteArray().inputStream(),
                            "Pictures/Murai GIFs/",
                            "murai_gif_${System.currentTimeMillis()}.gif",
                            false, "image/gif"
                        ) != null
                        saved
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrDefault(false)
            }
            state.value = s.copy(working = false, saved = result, error = !result)
        }
    }
}

@Composable
fun GifMakerScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: GifViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { GifViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    ToolScaffold(title = stringResource(R.string.tool_gif), onBack = onBack) { padding ->
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
                Text(
                    stringResource(R.string.gif_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.gif_frames, state.frameCount), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf(6, 8, 12, 16, 24).forEach { n ->
                        FilterChip(selected = state.frameCount == n, onClick = { vm.setFrameCount(n) }, label = { Text("$n") })
                    }
                }
                Text(stringResource(R.string.gif_fps, state.fps), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 8, 10).forEach { n ->
                        FilterChip(selected = state.fps == n, onClick = { vm.setFps(n) }, label = { Text("$n") })
                    }
                }
                Text(stringResource(R.string.gif_width, state.width), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf(320, 480, 720).forEach { n ->
                        FilterChip(selected = state.width == n, onClick = { vm.setWidth(n) }, label = { Text("${n}px") })
                    }
                }
                Button(
                    onClick = { vm.makeAndSave(context) },
                    enabled = !state.working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) { Text(stringResource(R.string.gif_create)) }
                if (state.working) {
                    ProgressOverlay(stringResource(R.string.gif_working), null)
                }
                if (state.saved) {
                    Text(
                        stringResource(R.string.gif_done),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (state.error) {
                    Text(
                        stringResource(R.string.gif_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
