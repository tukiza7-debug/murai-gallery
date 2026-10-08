package com.murai.gallery.ui.screens.favorites

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.components.EmptyState
import com.murai.gallery.ui.components.MediaThumb
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FavoritesViewModel(container: AppContainer) : ViewModel() {
    val favorites: StateFlow<List<com.murai.gallery.data.db.entity.LibraryItemEntity>> =
        container.repository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenViewer: (String) -> Unit
) {
    val vm: FavoritesViewModel = viewModel()
    val items by vm.favorites.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_favorites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.favorites_empty_title),
                subtitle = stringResource(R.string.favorites_empty_text),
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
                    MediaThumb(
                        item = item,
                        size = 120.dp,
                        selected = false,
                        selectionMode = false,
                        onClick = { onOpenViewer("favorites|${item.id}") },
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
