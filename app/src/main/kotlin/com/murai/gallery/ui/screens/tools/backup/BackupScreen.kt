package com.murai.gallery.ui.screens.tools.backup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.murai.gallery.domain.backup.BackupManager
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class BackupState(
    val working: Boolean = false,
    val message: String? = null
)

/**
 * Exports settings + tags + favorites + sort presets as a zip the user picks a
 * location for; importing merges a previously exported bundle.
 */
class BackupViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(BackupState())
    private val manager = BackupManager(
        container.appContext,
        container.db,
        container.settings
    )

    fun suggestName(): String = manager.suggestFileName()

    fun exportTo(uri: android.net.Uri) {
        launchSafely(container.appContext, "backup") {
            state.value = BackupState(working = true)
            val ok = runCatching {
                container.appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    manager.exportTo(out)
                } != null
            }.getOrDefault(false)
            state.value = BackupState(
                working = false,
                message = if (ok) "export-ok" else "export-fail"
            )
        }
    }

    fun importFrom(uri: android.net.Uri) {
        launchSafely(container.appContext, "backup") {
            state.value = BackupState(working = true)
            val summary = runCatching {
                container.appContext.contentResolver.openInputStream(uri)?.use { input ->
                    manager.importFrom(input)
                }
            }.getOrNull()
            state.value = BackupState(
                working = false,
                message = if (summary != null) "import-ok" else "import-fail"
            )
        }
    }
}

@Composable
fun BackupScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: BackupViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { BackupViewModel(container) })
    val state by vm.state.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { vm.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importFrom(it) } }

    ToolScaffold(title = stringResource(R.string.tool_backup), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(Modifier.padding(top = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.backup_export_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.backup_export_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { exportLauncher.launch(vm.suggestName()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) { Text(stringResource(R.string.backup_export)) }
                }
            }
            Card(Modifier.padding(top = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.backup_import_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.backup_import_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) { Text(stringResource(R.string.backup_import)) }
                }
            }
            if (state.working) {
                ProgressOverlay(stringResource(R.string.backup_working), null)
            }
            state.message?.let { msg ->
                Text(
                    when (msg) {
                        "export-ok" -> stringResource(R.string.backup_export_ok)
                        "import-ok" -> stringResource(R.string.backup_import_ok)
                        else -> stringResource(R.string.backup_fail)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.endsWith("ok")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
