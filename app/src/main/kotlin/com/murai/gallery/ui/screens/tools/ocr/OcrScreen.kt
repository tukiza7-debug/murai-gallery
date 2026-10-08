package com.murai.gallery.ui.screens.tools.ocr

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.ocr.OcrEngine
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import com.murai.gallery.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OcrState(
    val target: String? = null,
    val running: Boolean = false,
    val text: String = "",
    val error: Boolean = false
)

/** Offline OCR over a single photo: extract, copy or share the text. */
class OcrViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(OcrState())

    fun loadLatestImage() {
        viewModelScope.launch {
            val latest = container.db.libraryDao().recent(1).firstOrNull { !it.isVideo }
            state.value = OcrState(target = latest?.uri)
        }
    }

    fun setTarget(uri: String) {
        state.value = OcrState(target = uri)
    }

    fun run(context: Context) {
        val target = state.value.target ?: return
        viewModelScope.launch {
            state.value = state.value.copy(running = true, text = "", error = false)
            val result = withContext(Dispatchers.IO) {
                runCatching { OcrEngine.recognize(context, Uri.parse(target)) }
            }
            state.value = result.fold(
                onSuccess = { state.value.copy(running = false, text = it) },
                onFailure = { state.value.copy(running = false, error = true) }
            )
        }
    }
}

@Composable
fun OcrScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: OcrViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { OcrViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadLatestImage() }

    ToolScaffold(title = stringResource(R.string.tool_ocr), onBack = onBack) { padding ->
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
                    stringResource(R.string.ocr_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { vm.run(context) },
                    enabled = state.target != null && !state.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) { Text(stringResource(R.string.ocr_run)) }
                if (state.running) {
                    ProgressOverlay(stringResource(R.string.ocr_running), null)
                }
                if (state.text.isNotBlank()) {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Row {
                                Text(
                                    stringResource(R.string.ocr_result),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("murai-ocr", state.text)
                                    )
                                }) {
                                    Icon(Icons.Filled.ContentCopy, stringResource(R.string.action_copy))
                                }
                                IconButton(onClick = { ShareHelper.shareText(context, state.text) }) {
                                    Icon(Icons.Filled.Share, stringResource(R.string.action_share))
                                }
                            }
                            Text(
                                state.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                if (state.error) {
                    Text(
                        stringResource(R.string.ocr_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
