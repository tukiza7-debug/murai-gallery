package com.murai.gallery.ui.screens.tools.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.hash.Hashing
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ConfirmDialog
import com.murai.gallery.ui.components.EmptyState
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.util.ConsentBus
import com.murai.gallery.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DuplicateGroupUi(
    val key: String,
    val perceptual: Boolean,
    val items: List<LibraryItemEntity>
)

data class DuplicatesUiState(
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val groups: List<DuplicateGroupUi> = emptyList(),
    val removedForDisplay: Set<Long> = emptySet(),
    val confirmDelete: List<Long>? = null
)

/**
 * Duplicate finder: exact matching by SHA-256 plus near-duplicate matching by
 * 64-bit difference hash (hamming distance <= 8). Scanning runs off the main
 * thread with progress reporting.
 */
class DuplicatesViewModel(private val container: AppContainer) : ViewModel() {

    val state = MutableStateFlow(DuplicatesUiState())

    fun scan() {
        launchSafely(container.appContext, "duplicates") {
            state.value = state.value.copy(scanning = true, groups = emptyList())
            val all = container.db.libraryDao().sortKeyRows()
                .filter { !it.isVideo && it.width > 0 }
            var processed = 0
            val exactBuckets = HashMap<String, MutableList<Long>>()
            val phashValues = HashMap<Long, Long>()
            for (row in all) {
                val entity = container.db.libraryDao().byId(row.id) ?: continue
                val bytes = container.repository.openForHash(entity)
                    ?: continue
                bytes.use { input ->
                    val hash = Hashing.sha256(input)
                    if (hash != null) {
                        container.db.libraryDao().setExactHash(entity.id, hash)
                        exactBuckets.getOrPut(hash) { mutableListOf() }.add(entity.id)
                    }
                }
                val small = container.repository.decodeForHash(entity)
                if (small != null) {
                    val ph = Hashing.dHash(small)
                    container.db.libraryDao().setPHash(entity.id, ph)
                    phashValues[entity.id] = ph
                    small.recycle()
                }
                processed++
                if (processed % 25 == 0) {
                    state.value = state.value.copy(scanned = processed, total = all.size)
                }
            }
            val groups = ArrayList<DuplicateGroupUi>()
            for ((hashKey, ids) in exactBuckets) {
                if (ids.size > 1) {
                    groups.add(DuplicateGroupUi(hashKey.take(12), false, container.db.libraryDao().byIds(ids)))
                }
            }
            // near duplicates among remaining singles
            val inExact = groups.flatMap { it.items }.map { it.id }.toSet()
            val singles = phashValues.filterKeys { it !in inExact }
            val used = HashSet<Long>()
            for ((id, ph) in singles) {
                if (id in used) continue
                val near = singles.filterKeys { other ->
                    other != id && other !in used && Hashing.hamming(ph, singles[other] ?: 0L) <= 8
                }
                if (near.isNotEmpty()) {
                    val ids = (near.keys + id).toList()
                    used.addAll(ids)
                    groups.add(DuplicateGroupUi("p$id", true, container.db.libraryDao().byIds(ids)))
                }
            }
            state.value = state.value.copy(
                scanning = false,
                scanned = processed,
                total = all.size,
                groups = groups
            )
        }
    }

    fun toggleRemoved(id: Long) {
        state.value = state.value.copy(
            removedForDisplay = state.value.removedForDisplay + id
        )
    }

    fun requestDelete(ids: List<Long>) {
        state.value = state.value.copy(confirmDelete = ids)
    }

    fun confirmDelete() {
        val ids = state.value.confirmDelete ?: return
        launchSafely(container.appContext, "duplicates") {
            val entities = container.db.libraryDao().byIds(ids)
            val result = container.operations.trash(entities)
            if (result.consent != null) {
                ConsentBus.request(result.consent) { granted ->
                    if (granted) launchSafely(container.appContext, "duplicates") {
                        container.db.libraryDao().setTrashed(ids, true)
                        state.value = state.value.copy(confirmDelete = null)
                    }
                }
            }
            state.value = state.value.copy(confirmDelete = null)
        }
    }

    fun dismissConfirm() {
        state.value = state.value.copy(confirmDelete = null)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: DuplicatesViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { DuplicatesViewModel(container) })
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tool_duplicates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
            Column(Modifier.fillMaxSize()) {
                if (state.groups.isEmpty() && !state.scanning) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            title = stringResource(R.string.dup_scan_title),
                            subtitle = stringResource(R.string.dup_scan_text)
                        )
                        Button(onClick = { vm.scan() }) {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.dup_start_scan))
                        }
                    }
                }
                if (state.scanning) {
                    ProgressOverlay(
                        message = stringResource(R.string.dup_scanning, state.scanned, state.total),
                        progress = if (state.total > 0) state.scanned.toFloat() / state.total else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.groups.filter { g -> g.items.any { it.id !in state.removedForDisplay } }) { group ->
                        Card(Modifier.padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (group.perceptual) stringResource(R.string.dup_similar)
                                        else stringResource(R.string.dup_identical),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = {
                                        val keep = group.items.firstOrNull { it.id !in state.removedForDisplay }
                                        val remove = group.items.filter { it.id != keep?.id }
                                        vm.requestDelete(remove.map { it.id })
                                    }) { Text(stringResource(R.string.dup_keep_first)) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (item in group.items.take(3)) {
                                        Column {
                                            AsyncImage(
                                                model = item.uri,
                                                contentDescription = item.name,
                                                modifier = Modifier.size(96.dp)
                                            )
                                            Text(
                                                Formatters.fileSize(item.size),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            OutlinedButton(onClick = { vm.requestDelete(listOf(item.id)) }) {
                                                Icon(Icons.Filled.Delete, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        if (state.groups.isNotEmpty()) {
                            Text(
                                stringResource(R.string.dup_summary, state.groups.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
            state.confirmDelete?.let {
                ConfirmDialog(
                    title = stringResource(R.string.warn_trash_title),
                    text = stringResource(R.string.warn_trash_text, it.size),
                    confirmLabel = stringResource(R.string.action_trash),
                    danger = true,
                    onConfirm = { vm.confirmDelete() },
                    onDismiss = { vm.dismissConfirm() }
                )
            }
        }
    }
}
