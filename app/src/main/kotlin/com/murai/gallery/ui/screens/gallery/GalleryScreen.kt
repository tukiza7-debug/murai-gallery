package com.murai.gallery.ui.screens.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.model.SortDirection
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.model.SortSpec
import com.murai.gallery.domain.model.GroupMode
import com.murai.gallery.domain.sort.SortEngine
import com.murai.gallery.ui.components.ConfirmDialog
import com.murai.gallery.ui.components.MediaThumb
import com.murai.gallery.ui.nav.Routes
import com.murai.gallery.util.MuraiPermission
import com.murai.gallery.util.ShareHelper
import com.murai.gallery.work.ScanWorker
import com.murai.gallery.work.WallpaperWorker
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    container: AppContainer,
    onOpenViewer: (String, Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenBin: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenTool: (String) -> Unit
) {
    val vm: GalleryViewModel = viewModel()
    val state by vm.state.collectAsState()
    val cells: LazyPagingItems<GalleryCell> = vm.pagesAsFlow().collectAsLazyPagingItems()
    val context = LocalContext.current
    var showSortSheet by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var limitedAccess by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            limitedAccess = MuraiPermission.LIBRARY.isPartial(context)
            ScanWorker.enqueue(context)
            vm.refreshLibrary()
        }
    }

    // The rationale shows at most once per composition and only while nothing
    // at all is granted — never in a loop after "Not now" (fix #6).
    var rationaleShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        limitedAccess = MuraiPermission.LIBRARY.isPartial(context)
        if (!MuraiPermission.LIBRARY.granted(context) && !rationaleShown) {
            rationaleShown = true
            showRationale = true
        }
    }

    // Incoming VIEW/SEND intents are handled at the nav-root level; nothing
    // to do here anymore (fix #7).

    // Limited access (Android 14 selected photos): refresh the flag whenever
    // the composition lands so the banner tracks reality.
    LaunchedEffect(Unit) {
        limitedAccess = MuraiPermission.LIBRARY.isPartial(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Masthead — sits under the status bar thanks to statusBarsPadding
            Column(modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            NumberFormat.getIntegerInstance().format(state.totalCount) + " " +
                                stringResource(R.string.items_count_suffix),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.scanning) {
                        Text(
                            stringResource(R.string.scanning_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.ImageSearch, contentDescription = stringResource(R.string.search_title))
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.sort_title))
                    }
                    IconButton(onClick = { vm.setSpan(if (state.spanCount >= 5) 2 else state.spanCount + 1) }) {
                        Icon(Icons.Filled.Grid3x3, contentDescription = stringResource(R.string.grid_density))
                    }
                }
            }

            // Quick destinations
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AssistChip(
                        onClick = onOpenFavorites,
                        label = { Text(stringResource(R.string.tab_favorites)) },
                        leadingIcon = { Icon(Icons.Filled.FavoriteBorder, null, Modifier.size(16.dp)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenBin,
                        label = { Text(stringResource(R.string.tab_bin)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenMap,
                        label = { Text(stringResource(R.string.map_title)) },
                        leadingIcon = { Icon(Icons.Filled.Map, null, Modifier.size(16.dp)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenStats,
                        label = { Text(stringResource(R.string.stats_title)) },
                        leadingIcon = { Icon(Icons.Filled.QueryStats, null, Modifier.size(16.dp)) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Partial grant (Android 14 "selected photos"): a small banner
            // with a Select more action instead of a permission nag loop.
            if (limitedAccess) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.limited_access_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.limited_access_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            permissionLauncher.launch(MuraiPermission.LIBRARY.permissions)
                        }) {
                            Text(stringResource(R.string.action_select_more))
                        }
                    }
                }
            }

            // New Tools rail — statusBarsPadding is applied by the masthead above,
            // so nothing here clips under system bars (v1.0.6 overlap bug fixed).
            Text(
                stringResource(R.string.new_tools_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { ToolCard(R.string.tool_duplicates, Icons.Filled.AutoAwesome) { onOpenTool(Routes.TOOL_DUPLICATES) } }
                item { ToolCard(R.string.tool_editor, Icons.Filled.ImageSearch) { onOpenTool(Routes.TOOL_EDITOR) } }
                item { ToolCard(R.string.tool_compressor, Icons.Filled.Archive) { onOpenTool(Routes.TOOL_COMPRESSOR) } }
                item { ToolCard(R.string.tool_ocr, Icons.Filled.ImageSearch) { onOpenTool(Routes.TOOL_OCR) } }
                item { ToolCard(R.string.tool_qr, Icons.Filled.QrCodeScanner) { onOpenTool(Routes.TOOL_QR) } }
                item { ToolCard(R.string.tool_wallpaper, Icons.Filled.LocationOn) { onOpenTool(Routes.TOOL_WALLPAPER) } }
            }

            // Timeline grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.spanCount),
                state = rememberLazyGridState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(count = cells.itemCount, key = { index ->
                    when (val c = cells[index]) {
                        is GalleryCell.Header -> "h_$index"
                        is GalleryCell.Item -> "i_${c.entity.id}"
                        null -> "n_$index"
                    }
                }) { index ->
                    when (val cell = cells[index]) {
                        is GalleryCell.Header -> {
                            Text(
                                cell.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        is GalleryCell.Item -> MediaThumb(
                            item = cell.entity,
                            size = 120.dp,
                            selected = cell.entity.id in state.selection,
                            selectionMode = state.selectionMode,
                            onClick = {
                                if (state.selectionMode) vm.toggleSelection(cell.entity.id)
                                else onOpenViewer("home", cell.entity.id)
                            },
                            onLongClick = { vm.enterSelection(cell.entity.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        null -> Box(Modifier.size(120.dp))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (cells.loadState.append is androidx.paging.LoadState.Loading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(R.string.loading_more),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Selection action bar
        if (state.selectionMode) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        vm.selectionAction { entities ->
                            container.operations.setFavorite(entities, true)
                        }
                    }) {
                        Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.action_favorite))
                    }
                    IconButton(onClick = {
                        vm.selectionAction { entities ->
                            ShareHelper.shareMultiple(context, entities.map { it.uri })
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = { onOpenTool(Routes.TOOL_WALLPAPER) }) {
                        Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.tool_wallpaper))
                    }
                    IconButton(onClick = { confirmTrash = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_trash))
                    }
                    IconButton(onClick = { vm.clearSelection() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortSheet(
            spec = state.spec,
            presets = vm.sortPresets.collectAsState().value,
            onApply = { spec -> vm.setSpec(spec); showSortSheet = false },
            onSavePreset = { name, spec -> vm.savePreset(name, spec) },
            onReshuffle = { vm.setSpec(state.spec.copy(reshuffleSeed = System.nanoTime())) },
            onDismiss = { showSortSheet = false }
        )
    }

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(R.string.warn_trash_title),
            text = stringResource(R.string.warn_trash_text, state.selection.size),
            confirmLabel = stringResource(R.string.action_trash),
            danger = true,
            onConfirm = {
                confirmTrash = false
                vm.trashSelection { vm.clearSelection() }
            },
            onDismiss = { confirmTrash = false }
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.perm_rationale_library_title)) },
            text = { Text(stringResource(R.string.perm_rationale_library)) },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    permissionLauncher.launch(MuraiPermission.LIBRARY.permissions)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.action_not_now))
                }
            }
        )
    }
}

@Composable
private fun ToolCard(labelRes: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Bottom sheet with the 10 advanced sorts, direction, grouping and presets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    spec: SortSpec,
    presets: List<com.murai.gallery.data.db.entity.SortPresetEntity>,
    onApply: (SortSpec) -> Unit,
    onSavePreset: (String, SortSpec) -> Unit,
    onReshuffle: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(spec) { mutableStateOf(spec) }
    var presetName by remember { mutableStateOf("") }
    var showSave by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(260.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(SortOption.entries.size) { i ->
                    val option = SortOption.entries[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = draft.option == option,
                            onClick = { draft = draft.copy(option = option) }
                        )
                        Text(stringResource(SortEngine.sortLabelRes(option)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draft = draft.copy(
                            direction = if (draft.direction == SortDirection.ASCENDING)
                                SortDirection.DESCENDING else SortDirection.ASCENDING
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (draft.direction == SortDirection.ASCENDING)
                            stringResource(R.string.sort_ascending)
                        else stringResource(R.string.sort_descending)
                    )
                }
                OutlinedButton(
                    onClick = { onReshuffle(); onDismiss() },
                    enabled = draft.option == SortOption.RANDOM,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sort_reshuffle)) }
            }
            Spacer(Modifier.height(10.dp))
            // group row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupMode.entries.take(4).forEach { mode ->
                    FilterChipLite(
                        label = groupLabelOf(mode),
                        selected = draft.group == mode
                    ) { draft = draft.copy(group = mode) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupMode.entries.drop(4).forEach { mode ->
                    FilterChipLite(
                        label = groupLabelOf(mode),
                        selected = draft.group == mode
                    ) { draft = draft.copy(group = mode) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sort_apply)) }
            TextButton(
                onClick = { showSave = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.sort_save_preset)) }
            if (presets.isNotEmpty()) {
                Text(stringResource(R.string.sort_presets_title), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        AssistChip(
                            onClick = {
                                onApply(
                                    SortSpec(
                                        option = SortOption.fromId(preset.sortOptionId) ?: SortOption.DATE_TAKEN,
                                        direction = if (preset.ascending) SortDirection.ASCENDING else SortDirection.DESCENDING,
                                        group = GroupMode.fromId(preset.groupId) ?: GroupMode.NONE
                                    )
                                )
                            },
                            label = { Text(preset.name) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(stringResource(R.string.sort_preset_name)) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    placeholder = { Text(stringResource(R.string.sort_preset_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        onSavePreset(presetName.trim(), draft)
                        presetName = ""
                        showSave = false
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun FilterChipLite(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface
    )) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun groupLabelOf(mode: GroupMode): String = when (mode) {
    GroupMode.NONE -> stringResource(R.string.group_none)
    GroupMode.DAY -> stringResource(R.string.group_day)
    GroupMode.MONTH -> stringResource(R.string.group_month)
    GroupMode.YEAR -> stringResource(R.string.group_year)
    GroupMode.ALBUM -> stringResource(R.string.group_album)
    GroupMode.TYPE -> stringResource(R.string.group_type)
    GroupMode.LOCATION -> stringResource(R.string.group_location)
}
