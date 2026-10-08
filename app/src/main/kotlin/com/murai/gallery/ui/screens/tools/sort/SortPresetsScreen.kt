package com.murai.gallery.ui.screens.tools.sort

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.SortPresetEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.model.GroupMode
import com.murai.gallery.domain.model.SortDirection
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.sort.SortEngine
import com.murai.gallery.ui.components.EmptyState
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SortPresetsViewModel(private val container: AppContainer) : ViewModel() {
    val presets: StateFlow<List<SortPresetEntity>> =
        container.db.sortPresetsDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { container.db.sortPresetsDao().delete(id) }
    }
}

@Composable
fun SortPresetsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SortPresetsViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { SortPresetsViewModel(container) })
    val presets by vm.presets.collectAsState()

    ToolScaffold(title = stringResource(R.string.tool_sort), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.sort_presets_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (presets.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.sort_presets_empty_title),
                    subtitle = stringResource(R.string.sort_presets_empty_text),
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                    items(presets, key = { it.id }) { preset ->
                        Card(Modifier.padding(vertical = 4.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(SortEngine.sortLabelRes(
                                            SortOption.fromId(preset.sortOptionId) ?: SortOption.DATE_TAKEN
                                        )) + " · " + (
                                            if (preset.ascending) stringResource(R.string.sort_ascending)
                                            else stringResource(R.string.sort_descending)
                                            ) + " · " + groupName(preset.groupId),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { vm.delete(preset.id) }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun groupName(id: String): String = when (GroupMode.fromId(id)) {
    GroupMode.DAY -> stringResource(R.string.group_day)
    GroupMode.MONTH -> stringResource(R.string.group_month)
    GroupMode.YEAR -> stringResource(R.string.group_year)
    GroupMode.ALBUM -> stringResource(R.string.group_album)
    GroupMode.TYPE -> stringResource(R.string.group_type)
    GroupMode.LOCATION -> stringResource(R.string.group_location)
    else -> stringResource(R.string.group_none)
}
