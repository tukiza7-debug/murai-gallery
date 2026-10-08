package com.murai.gallery.ui.screens.slideshow

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.model.SortSpec
import com.murai.gallery.domain.sort.SortEngine
import kotlinx.coroutines.delay

/** Auto-playing slideshow with crossfade transitions and configurable pace. */
@Composable
fun SlideshowScreen(container: AppContainer, onExit: () -> Unit) {
    val context = LocalContext.current
    var ids by remember { mutableStateOf<List<Long>>(emptyList()) }
    var position by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val intervalSec by container.settings.slideshowIntervalSec
        .collectAsState(initial = 5)

    LaunchedEffect(Unit) {
        val rows = container.db.libraryDao().sortKeyRows().toMutableList()
        SortEngine.sort(rows, SortOption.RANDOM, com.murai.gallery.domain.model.SortDirection.ASCENDING, System.nanoTime())
        ids = rows.filter { !it.isVideo }.map { it.id }
    }

    LaunchedEffect(playing, intervalSec, ids) {
        while (playing && ids.size > 1) {
            delay(intervalSec * 1000L)
            position = (position + 1) % ids.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val currentId = ids.getOrNull(position)
        if (currentId != null) {
            Crossfade(targetState = currentId, label = "slideshow") { id ->
                AsyncImage(
                    model = id,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = Color.White)
            }
            Text(
                "${position + 1} / ${ids.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            IconButton(onClick = { playing = !playing }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(R.string.slideshow_pause),
                    tint = Color.White
                )
            }
        }
    }
}

