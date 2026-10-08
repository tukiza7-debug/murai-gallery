package com.murai.gallery.ui.screens.tools.trimmer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.video.VideoTrimmer
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ToolScaffold
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class TrimmerState(
    val uri: Uri? = null,
    val durationMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val working: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class TrimmerViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(TrimmerState())

    fun load(context: Context, uriOrPath: String) {
        launchSafely(container.appContext, "trimmer") {
            val uri = if (uriOrPath.startsWith("content:")) Uri.parse(uriOrPath)
            else Uri.fromFile(File(uriOrPath))
            val duration = VideoTrimmer.probeDurationMs(context, uri)
            state.value = TrimmerState(uri = uri, durationMs = duration, endMs = duration)
        }
    }

    fun setStart(v: Long) {
        state.value = state.value.copy(startMs = v.coerceIn(0, state.value.endMs - 500))
    }

    fun setEnd(v: Long) {
        state.value = state.value.copy(endMs = v.coerceIn(state.value.startMs + 500, state.value.durationMs))
    }

    fun save(context: Context) {
        val s = state.value
        val uri = s.uri ?: return
        launchSafely(container.appContext, "trimmer") {
            state.value = s.copy(working = true, error = null)
            val out = File(context.cacheDir, "murai_trim_${System.currentTimeMillis()}.mp4")
            val result = VideoTrimmer.trim(context, uri, s.startMs, s.endMs, out)
            if (result.ok && result.path != null) {
                val inserted = container.operations.saveStream(
                    out.inputStream(),
                    "Movies/Murai Trims/",
                    "murai_trim_${System.currentTimeMillis()}.mp4",
                    true,
                    "video/mp4"
                )
                out.delete()
                state.value = state.value.copy(working = false, saved = inserted != null)
            } else {
                state.value = state.value.copy(working = false, error = result.message)
            }
        }
    }
}

@Composable
fun VideoTrimmerScreen(
    container: AppContainer,
    startUri: String,
    onBack: () -> Unit
) {
    val vm: TrimmerViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { TrimmerViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(startUri) { vm.load(context, startUri) }

    ToolScaffold(title = stringResource(R.string.tool_trimmer), onBack = onBack) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val uri = state.uri
            if (uri == null) {
                ProgressOverlay(stringResource(R.string.loading), null, Modifier.align(Alignment.Center))
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                    Text(
                        stringResource(R.string.trim_range, Formatters.duration(state.startMs), Formatters.duration(state.endMs)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("0:00", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = state.startMs.toFloat(),
                            onValueChange = { vm.setStart(it.toLong()) },
                            valueRange = 0f..state.durationMs.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(Formatters.duration(state.durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("0:00", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = state.endMs.toFloat(),
                            onValueChange = { vm.setEnd(it.toLong()) },
                            valueRange = 0f..state.durationMs.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(Formatters.duration(state.durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { vm.save(context) },
                        enabled = !state.working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) { Text(stringResource(R.string.trim_save)) }
                    state.error?.let {
                        Text(
                            stringResource(R.string.trim_error, it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (state.working) {
                        ProgressOverlay(stringResource(R.string.trim_working), null, Modifier.align(Alignment.CenterHorizontally))
                    }
                }
                if (state.saved) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = onBack,
                        title = { Text(stringResource(R.string.trim_saved_title)) },
                        text = { Text(stringResource(R.string.trim_saved_text)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = onBack) { Text(stringResource(R.string.action_ok)) }
                        }
                    )
                }
            }
        }
    }
}
