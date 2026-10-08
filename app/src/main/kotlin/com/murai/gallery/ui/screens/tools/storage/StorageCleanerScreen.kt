package com.murai.gallery.ui.screens.tools.storage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ConfirmDialog
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import com.murai.gallery.util.ConsentBus
import com.murai.gallery.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class CleanerState(
    val loading: Boolean = true,
    val folders: List<Triple<String, Int, Long>> = emptyList(),
    val bigFiles: List<com.murai.gallery.data.db.entity.LibraryItemEntity> = emptyList(),
    val screenshots: List<com.murai.gallery.data.db.entity.LibraryItemEntity> = emptyList(),
    val confirmDelete: List<Long>? = null,
    val deletedBytes: Long = 0
)

/** Storage cleaner: folder usage, biggest files, screenshots, one-tap sweeps. */
class CleanerViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(CleanerState())

    init {
        load()
    }

    fun load() {
        launchSafely(container.appContext, "cleaner") {
            val folders = container.repository.folderUsage()
                .map { Triple(it.folder, it.c, it.bytes) }
            val big = container.repository.biggestFiles(10 * 1024 * 1024, 15)
            val shots = container.repository.screenshots().take(60)
            state.value = CleanerState(
                loading = false,
                folders = folders,
                bigFiles = big,
                screenshots = shots
            )
        }
    }

    fun requestDelete(ids: List<Long>) {
        state.value = state.value.copy(confirmDelete = ids)
    }

    fun confirmDelete() {
        val ids = state.value.confirmDelete ?: return
        launchSafely(container.appContext, "cleaner") {
            val entities = container.db.libraryDao().byIds(ids)
            val result = container.operations.trash(entities)
            if (result.consent != null) {
                ConsentBus.request(result.consent) { granted ->
                    if (granted) launchSafely(container.appContext, "cleaner") {
                        container.db.libraryDao().setTrashed(ids, true)
                        state.value = state.value.copy(
                            confirmDelete = null,
                            deletedBytes = state.value.deletedBytes + entities.sumOf { it.size }
                        )
                        load()
                    }
                }
            } else {
                container.db.libraryDao().setTrashed(ids, true)
                state.value = state.value.copy(
                    confirmDelete = null,
                    deletedBytes = state.value.deletedBytes + entities.sumOf { it.size }
                )
                load()
            }
        }
    }
}

@Composable
fun StorageCleanerScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: CleanerViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { CleanerViewModel(container) })
    val state by vm.state.collectAsState()

    ToolScaffold(title = stringResource(R.string.tool_cleaner), onBack = onBack) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.loading) {
                ProgressOverlay(stringResource(R.string.loading), null)
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (state.deletedBytes > 0) {
                        Card {
                            Text(
                                stringResource(R.string.cleaner_freed, Formatters.fileSize(state.deletedBytes)),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Section(stringResource(R.string.cleaner_screenshots)) {
                        Text(
                            stringResource(R.string.cleaner_screenshots_count, state.screenshots.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.screenshots.isNotEmpty()) {
                            Button(
                                onClick = { vm.requestDelete(state.screenshots.map { it.id }) },
                                modifier = Modifier.padding(top = 6.dp)
                            ) { Text(stringResource(R.string.cleaner_sweep_shots)) }
                        }
                    }
                    Section(stringResource(R.string.cleaner_big_files)) {
                        state.bigFiles.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text(
                                    Formatters.fileSize(item.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                androidx.compose.material3.TextButton(onClick = { vm.requestDelete(listOf(item.id)) }) {
                                    Text(stringResource(R.string.action_trash))
                                }
                            }
                        }
                    }
                    Section(stringResource(R.string.cleaner_folders)) {
                        state.folders.take(12).forEach { (folder, count, bytes) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(folder, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("$count · ${Formatters.fileSize(bytes)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            state.confirmDelete?.let { ids ->
                ConfirmDialog(
                    title = stringResource(R.string.warn_trash_title),
                    text = stringResource(R.string.warn_trash_text, ids.size),
                    confirmLabel = stringResource(R.string.action_trash),
                    danger = true,
                    onConfirm = { vm.confirmDelete() },
                    onDismiss = { vm.state.value = state.copy(confirmDelete = null) }
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
