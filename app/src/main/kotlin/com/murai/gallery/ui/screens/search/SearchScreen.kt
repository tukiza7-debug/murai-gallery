package com.murai.gallery.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.murai.gallery.data.prefs.SortScreen
import kotlinx.coroutines.flow.Flow
import com.murai.gallery.ui.components.MediaThumb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Holds the most recent search filter so the viewer can mirror its order. */
object SearchQueryHolder {
    @Volatile var last: MediaFilter = MediaFilter()
}

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    val filter = MutableStateFlow(MediaFilter())

    fun setFilter(newFilter: MediaFilter) {
        filter.value = newFilter
        SearchQueryHolder.last = newFilter
        viewModelScope.launch {
            container.settings.setSortSpec(SortScreen.SEARCH, com.murai.gallery.domain.model.SortSpec())
        }
    }

    fun results(): Flow<androidx.paging.PagingData<LibraryItemEntity>> =
        container.repository.paged(filter.value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenViewer: (String) -> Unit
) {
    val vm: SearchViewModel = viewModel()
    val filter by vm.filter.collectAsState()
    val items: LazyPagingItems<LibraryItemEntity> = vm.results().collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = filter.query ?: "",
                onValueChange = { vm.setFilter(filter.copy(query = it)) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (!filter.query.isNullOrBlank()) {
                        IconButton(onClick = { vm.setFilter(filter.copy(query = "")) }) {
                            Icon(Icons.Filled.Close, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter.onlyImages,
                    onClick = { vm.setFilter(filter.copy(onlyImages = !filter.onlyImages, onlyVideos = false)) },
                    label = { Text(stringResource(R.string.filter_images)) }
                )
                FilterChip(
                    selected = filter.onlyVideos,
                    onClick = { vm.setFilter(filter.copy(onlyVideos = !filter.onlyVideos, onlyImages = false)) },
                    label = { Text(stringResource(R.string.filter_videos)) }
                )
                FilterChip(
                    selected = filter.favoriteOnly,
                    onClick = { vm.setFilter(filter.copy(favoriteOnly = !filter.favoriteOnly)) },
                    label = { Text(stringResource(R.string.tab_favorites)) }
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(items.itemCount, key = { idx -> items[idx]?.id ?: -idx.toLong() }) { idx ->
                    val item = items[idx] ?: return@items
                    MediaThumb(
                        item = item,
                        size = 120.dp,
                        selected = false,
                        selectionMode = false,
                        onClick = { onOpenViewer("search|${item.id}") },
                        onLongClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }
}
