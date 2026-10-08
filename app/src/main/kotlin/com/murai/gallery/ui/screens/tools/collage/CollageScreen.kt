package com.murai.gallery.ui.screens.tools.collage

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import coil.compose.AsyncImage
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.collage.CollageRenderer
import com.murai.gallery.domain.image.ImageOps
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CollageState(
    val picks: List<String> = emptyList(),
    val templateIndex: Int = 0,
    val gapPx: Int = 12,
    val bgIndex: Int = 0,
    val preview: Bitmap? = null,
    val working: Boolean = false,
    val saved: Boolean = false
)

/** Collage maker: 2-9 photos, multiple grid templates, gap and background. */
class CollageViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(CollageState())

    init {
        loadCandidates()
    }

    fun loadCandidates() {
        viewModelScope.launch {
            val recent = container.db.libraryDao().recent(30).filter { !it.isVideo }
            state.value = state.value.copy(picks = recent.take(6).map { it.uri })
            renderPreview()
        }
    }

    fun togglePick(uri: String) {
        val current = state.value.picks
        val next = if (uri in current) current - uri else (current + uri).take(9)
        state.value = state.value.copy(picks = next)
        renderPreview()
    }

    fun setTemplate(i: Int) {
        state.value = state.value.copy(templateIndex = i)
        renderPreview()
    }

    fun setGap(g: Int) {
        state.value = state.value.copy(gapPx = g)
        renderPreview()
    }

    fun setBg(i: Int) {
        state.value = state.value.copy(bgIndex = i)
        renderPreview()
    }

    fun renderPreview() {
        viewModelScope.launch {
            val s = state.value
            if (s.picks.size < 2) return@launch
            state.value = s.copy(working = true)
            val bmp = withContext(Dispatchers.Default) {
                val images = s.picks.take(9).mapNotNull { uri ->
                    container.repository.decodeForHash(
                        container.db.libraryDao().byId(idFromUri(uri)) ?: return@mapNotNull null
                    )
                }
                if (images.size < 2) null
                else CollageRenderer.render(images, s.templateIndex, s.gapPx, CollageRenderer.bgColorFor(s.bgIndex), 720, 720)
            }
            state.value = state.value.copy(preview = bmp, working = false)
        }
    }

    fun save(context: Context) {
        val s = state.value
        val preview = s.preview ?: return
        viewModelScope.launch {
            state.value = state.value.copy(working = true)
            val saved = withContext(Dispatchers.IO) {
                val bos = java.io.ByteArrayOutputStream()
                preview.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                container.operations.saveStream(
                    bos.toByteArray().inputStream(),
                    "Pictures/Murai Collages/",
                    "murai_collage_${System.currentTimeMillis()}.jpg",
                    false, "image/jpeg"
                ) != null
            }
            state.value = state.value.copy(working = false, saved = saved)
        }
    }

    private fun idFromUri(uri: String): Long = runCatching {
        android.content.ContentUris.parseId(android.net.Uri.parse(uri))
    }.getOrDefault(0L)
}

@Composable
fun CollageScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: CollageViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { CollageViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    ToolScaffold(title = stringResource(R.string.tool_collage), onBack = onBack) { padding ->
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
                state.preview?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } ?: ProgressOverlay(stringResource(R.string.loading), null)
                Text(stringResource(R.string.collage_photos, state.picks.size), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    items(state.picks) { uri ->
                        Card(onClick = { vm.togglePick(uri) }) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }
                Text(stringResource(R.string.collage_template), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until CollageRenderer.templates(state.picks.size.coerceAtLeast(2).coerceAtMost(9)).size) {
                        FilterChip(
                            selected = state.templateIndex == i,
                            onClick = { vm.setTemplate(i) },
                            label = { Text("${i + 1}") }
                        )
                    }
                }
                Text(stringResource(R.string.collage_gap), style = MaterialTheme.typography.titleSmall)
                Slider(value = state.gapPx.toFloat(), onValueChange = { vm.setGap(it.toInt()) }, valueRange = 0f..48f)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    (0..5).forEach { i ->
                        FilterChip(
                            selected = state.bgIndex == i,
                            onClick = { vm.setBg(i) },
                            label = { Text("${i + 1}") }
                        )
                    }
                }
                Button(
                    onClick = { vm.save(context) },
                    enabled = !state.working && state.preview != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) { Text(stringResource(R.string.collage_save)) }
            }
            if (state.working) {
                ProgressOverlay(stringResource(R.string.collage_working), null, Modifier.align(Alignment.Center))
            }
        }
    }
}
