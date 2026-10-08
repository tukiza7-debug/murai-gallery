package com.murai.gallery.ui.screens.tools.qrcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.murai.gallery.domain.barcode.BarcodeEngine
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QrState(
    val target: String? = null,
    val running: Boolean = false,
    val result: BarcodeEngine.ScanResult? = null,
    val confirmOpen: Boolean = false,
    val notFound: Boolean = false
)

/** QR / barcode scanner that works on photos already in the gallery. */
class QrViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(QrState())

    init {
        loadLatest()
    }

    fun loadLatest() {
        launchSafely(container.appContext, "qr") {
            val latest = container.db.libraryDao().recent(1).firstOrNull { !it.isVideo }
            state.value = QrState(target = latest?.uri)
        }
    }

    fun decode(context: Context) {
        val target = state.value.target ?: return
        launchSafely(container.appContext, "qr") {
            state.value = state.value.copy(running = true, notFound = false)
            val result = withContext(Dispatchers.IO) {
                runCatching { BarcodeEngine.decode(context, Uri.parse(target)) }.getOrNull()
            }
            if (result == null) {
                state.value = state.value.copy(running = false, notFound = true)
            } else {
                state.value = state.value.copy(running = false, result = result, confirmOpen = result.looksLikeUrl)
            }
        }
    }

    fun openLink(context: Context) {
        val text = state.value.result?.text ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
        }
        state.value = state.value.copy(confirmOpen = false)
    }
}

@Composable
fun QrScanScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: QrViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { QrViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContextProvider.current

    ToolScaffold(title = stringResource(R.string.tool_qr), onBack = onBack) { padding ->
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
                    stringResource(R.string.qr_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { vm.decode(context) },
                    enabled = state.target != null && !state.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) { Text(stringResource(R.string.qr_scan)) }
                if (state.running) ProgressOverlay(stringResource(R.string.qr_running), null)
                if (state.notFound) {
                    Text(
                        stringResource(R.string.qr_not_found),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                state.result?.let { r ->
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.qr_result_format, r.format), style = MaterialTheme.typography.labelMedium)
                            Text(r.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                            if (!r.looksLikeUrl) {
                                Text(
                                    stringResource(R.string.qr_text_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (state.confirmOpen) {
                AlertDialog(
                    onDismissRequest = { vm.state.value = state.copy(confirmOpen = false) },
                    title = { Text(stringResource(R.string.qr_open_title)) },
                    text = {
                        val safe = state.result?.let { BarcodeEngine.isSafeToOpen(it.text) } ?: false
                        Text(
                            if (safe) stringResource(R.string.qr_open_confirm, state.result?.text ?: "")
                            else stringResource(R.string.qr_open_unsafe, state.result?.text ?: "")
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.openLink(context) }) { Text(stringResource(R.string.qr_open_anyway)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.state.value = state.copy(confirmOpen = false) }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}

/** Avoids threading an activity context deep into the tree. */
object LocalContextProvider {
    val current: android.content.Context
        @Composable get() = androidx.compose.ui.platform.LocalContext.current
}
