package com.murai.gallery.ui.screens.tools.rename

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.murai.gallery.ui.components.ProgressOverlay
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RenameRow(val id: Long, val current: String, val next: String)

data class RenameState(
    val pattern: String = "{date}_{counter}",
    val custom: String = "",
    val rows: List<RenameRow> = emptyList(),
    val working: Boolean = false,
    val done: Boolean = false
)

/**
 * Batch rename with tokens: {date} (taken date), {counter} (1-based),
 * {name} (original stem), {ext}. Live preview list before applying.
 */
class RenameViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(RenameState())

    fun setPattern(p: String) {
        state.value = state.value.copy(pattern = p)
        preview()
    }

    fun setCustom(c: String) {
        state.value = state.value.copy(custom = c)
        preview()
    }

    fun preview() {
        launchSafely(container.appContext, "rename") {
            val s = state.value
            val items = container.db.libraryDao().recent(50)
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            var counter = 1
            val rows = items.map { item ->
                val stem = item.name.substringBeforeLast('.')
                val ext = item.name.substringAfterLast('.', "jpg")
                val datePart = if (item.dateTakenSec > 0) fmt.format(Date(item.dateTakenSec * 1000)) else "photo"
                val name = s.pattern
                    .replace("{date}", datePart)
                    .replace("{counter}", "%04d".format(Locale.US, counter))
                    .replace("{name}", stem)
                    .replace("{ext}", ext)
                    .ifBlank { stem }
                counter++
                RenameRow(item.id, item.name, "$name.$ext")
            }
            state.value = s.copy(rows = rows)
        }
    }

    fun apply(context: Context) {
        val s = state.value
        launchSafely(container.appContext, "rename") {
            state.value = s.copy(working = true)
            var ok = 0
            withContext(Dispatchers.IO) {
                for (row in s.rows) {
                    val entity = container.db.libraryDao().byId(row.id) ?: continue
                    val success = container.operations.rename(entity, row.next)
                    if (success) ok++
                }
            }
            state.value = s.copy(working = false, done = ok > 0)
            preview()
        }
    }
}

@Composable
fun BatchRenameScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: RenameViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { RenameViewModel(container) })
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.preview() }

    ToolScaffold(title = stringResource(R.string.tool_rename), onBack = onBack) { padding ->
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
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    listOf("{date}_{counter}", "{name}_copy", "{counter}_{ext}").forEach { p ->
                        FilterChip(
                            selected = state.pattern == p,
                            onClick = { vm.setPattern(p) },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = state.pattern,
                    onValueChange = { vm.setPattern(it) },
                    label = { Text(stringResource(R.string.rename_pattern)) },
                    supportingText = { Text(stringResource(R.string.rename_tokens)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                Text(
                    stringResource(R.string.rename_preview, state.rows.size),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    itemsIndexed(state.rows, key = { _, r -> r.id }) { _, row ->
                        Card(Modifier.padding(vertical = 2.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text(row.current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text("→ ${row.next}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
                Button(
                    onClick = { vm.apply(context) },
                    enabled = !state.working && state.rows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.rename_apply)) }
                if (state.working) {
                    ProgressOverlay(stringResource(R.string.rename_working), null)
                }
                if (state.done) {
                    Text(
                        stringResource(R.string.rename_done),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
