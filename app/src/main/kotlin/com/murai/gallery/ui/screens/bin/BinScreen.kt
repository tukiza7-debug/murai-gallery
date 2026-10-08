package com.murai.gallery.ui.screens.bin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.EmptyState
import com.murai.gallery.ui.components.MediaThumb
import com.murai.gallery.util.ConsentBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BinViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<com.murai.gallery.data.db.entity.LibraryItemEntity>> =
        container.repository.observeBin().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun restore(ids: List<Long>) {
        launchSafely(container.appContext, "bin") {
            val entities = container.db.libraryDao().byIds(ids)
            val result = container.operations.restore(entities)
            if (result.consent != null) {
                ConsentBus.request(result.consent) { granted ->
                    if (granted) launchSafely(container.appContext, "bin") {
                        container.db.libraryDao().setTrashed(ids, false)
                    }
                }
            }
        }
    }

    fun deleteForever(ids: List<Long>, onDone: () -> Unit) {
        launchSafely(container.appContext, "bin") {
            val entities = container.db.libraryDao().byIds(ids)
            val result = container.operations.deleteForever(entities)
            if (result.consent != null) {
                ConsentBus.request(result.consent) { granted ->
                    if (granted) launchSafely(container.appContext, "bin") {
                        container.db.libraryDao().deleteByIds(ids)
                        onDone()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: BinViewModel = viewModel()
    val items by vm.items.collectAsState()
    var confirmPurge by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_bin)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmPurge = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.bin_empty))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.bin_empty_title),
                subtitle = stringResource(R.string.bin_empty_text),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        MediaThumb(
                            item = item,
                            size = 120.dp,
                            selected = false,
                            selectionMode = false,
                            onClick = { },
                            onLongClick = { }
                        )
                        FilledTonalButton(
                            onClick = { vm.restore(listOf(item.id)) },
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.RestoreFromTrash,
                                contentDescription = stringResource(R.string.bin_restore),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text(stringResource(R.string.bin_empty)) },
            text = { Text(stringResource(R.string.bin_empty_warning, items.size)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    confirmPurge = false
                    vm.deleteForever(items.map { it.id }) { }
                }) {
                    Text(stringResource(R.string.action_delete_forever), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { confirmPurge = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
