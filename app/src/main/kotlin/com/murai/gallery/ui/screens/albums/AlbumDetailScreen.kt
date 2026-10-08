package com.murai.gallery.ui.screens.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Text
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.model.MediaFilter
import com.murai.gallery.domain.model.SortDirection
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.model.SortSpec
import com.murai.gallery.data.prefs.SortScreen
import com.murai.gallery.ui.screens.gallery.SortSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AlbumDetailViewModel(private val container: AppContainer, private val bucketId: String) : ViewModel() {

    private val spec = MutableStateFlow(SortSpec())
    val specState = spec

    init {
        viewModelScope.launch {
            container.settings.sortSpec(SortScreen.ALBUM).collect { spec.value = it }
        }
    }

    fun items(): Flow<androidx.paging.PagingData<LibraryItemEntity>> =
        container.repository.paged(MediaFilter(bucketId = bucketId))

    fun setSpec(newSpec: SortSpec) {
        spec.value = newSpec
        viewModelScope.launch { container.settings.setSortSpec(SortScreen.ALBUM, newSpec) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    container: AppContainer,
    bucketId: String,
    onBack: () -> Unit,
    onOpenViewer: (String) -> Unit
) {
    val vm: AlbumDetailViewModel = viewModel(
        key = "album_$bucketId",
        factory = com.murai.gallery.ui.components.muraiFactory { AlbumDetailViewModel(container, bucketId) }
    )
    val spec by vm.specState.collectAsState()
    val items: LazyPagingItems<LibraryItemEntity> = vm.items().collectAsLazyPagingItems()
    var showSort by remember { mutableStateOf(false) }

    // grid order follows the stored spec (date-based sorts are DB-backed);
    // non-DB sorts apply their full order inside the viewer.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.album_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSort = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.sort_title))
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(items.itemCount, key = { idx -> items[idx]?.id ?: -idx.toLong() }) { idx ->
                val item = items[idx] ?: return@items
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .combinedClickable(
                            onClick = { onOpenViewer("album:$bucketId|${item.id}") },
                            onLongClick = { }
                        )
                ) {
                    coil.compose.AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.isVideo) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(4.dp)
                        )
                    }
                    if (item.favorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFF5A623),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSort) {
        SortSheet(
            spec = spec,
            presets = emptyList(),
            onApply = { vm.setSpec(it); showSort = false },
            onSavePreset = { _, _ -> },
            onReshuffle = { vm.setSpec(spec.copy(reshuffleSeed = System.nanoTime())) },
            onDismiss = { showSort = false }
        )
    }
}
