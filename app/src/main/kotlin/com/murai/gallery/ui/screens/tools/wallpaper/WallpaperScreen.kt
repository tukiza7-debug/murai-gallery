package com.murai.gallery.ui.screens.tools.wallpaper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.components.ToolScaffold
import com.murai.gallery.work.WallpaperWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Auto wallpaper: pick albums + interval; WorkManager rotates the wallpaper. */
class WallpaperViewModel(
    private val container: AppContainer,
    private val appContext: android.content.Context
) : ViewModel() {

    val albums = container.repository.observeAlbums()
    val enabled = container.settings.wallpaperEnabled
    val both = container.settings.wallpaperBoth
    val interval = container.settings.wallpaperIntervalHours
    val selected = container.settings.wallpaperAlbums

    fun toggleEnabled(v: Boolean) {
        viewModelScope.launch {
            container.settings.setWallpaperEnabled(v)
            if (v) {
                val hours = container.settings.wallpaperIntervalHours.first()
                WallpaperWorker.schedule(appContext, hours)
            } else {
                WallpaperWorker.cancel(appContext)
            }
        }
    }

    fun toggleBoth(v: Boolean) {
        viewModelScope.launch { container.settings.setWallpaperBoth(v) }
    }

    fun setInterval(hours: Int) {
        viewModelScope.launch {
            container.settings.setWallpaperInterval(hours)
            if (container.settings.wallpaperEnabled.first()) {
                WallpaperWorker.schedule(appContext, hours)
            }
        }
    }

    fun toggleAlbum(bucketId: String) {
        viewModelScope.launch {
            val current = container.settings.wallpaperAlbums.first()
            val next = if (bucketId in current) current - bucketId else current + bucketId
            container.settings.setWallpaperAlbums(next)
        }
    }

}

@Composable
fun WallpaperScreen(container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: WallpaperViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { WallpaperViewModel(container, context.applicationContext) })
    val albums by vm.albums.collectAsState(initial = emptyList())
    val enabled by vm.enabled.collectAsState(initial = false)
    val both by vm.both.collectAsState(initial = true)
    val interval by vm.interval.collectAsState(initial = 24)
    val selected by vm.selected.collectAsState(initial = emptySet())

    ToolScaffold(title = stringResource(R.string.tool_wallpaper), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wallpaper_auto), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.wallpaper_auto_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { vm.toggleEnabled(it) })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.wallpaper_lock_too), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = both, onCheckedChange = { vm.toggleBoth(it) })
            }
            Text(stringResource(R.string.wallpaper_interval), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                listOf(1, 6, 12, 24).forEach { h ->
                    FilterChip(
                        selected = interval == h,
                        onClick = { vm.setInterval(h) },
                        label = { Text(stringResource(R.string.wallpaper_hours, h)) }
                    )
                }
            }
            Text(stringResource(R.string.wallpaper_albums), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                items(albums, key = { it.bucketId }) { album ->
                    Card(onClick = { vm.toggleAlbum(album.bucketId) }) {
                        Column {
                            AsyncImage(
                                model = album.coverUri,
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(64.dp)
                            )
                            FilterChip(
                                selected = album.bucketId in selected,
                                onClick = { vm.toggleAlbum(album.bucketId) },
                                label = { Text(album.name, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp)) }
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { vm.setInterval(interval) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) { Text(stringResource(R.string.wallpaper_apply_now)) }
        }
    }
}
