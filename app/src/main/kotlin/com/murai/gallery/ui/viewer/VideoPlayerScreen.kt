package com.murai.gallery.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer

/** Media3 video player used for regular videos, motion photo trailers and trim previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    container: AppContainer,
    payload: String,
    onBack: () -> Unit,
    onOpenTrimmer: (String) -> Unit
) {
    val context = LocalContext.current
    val source: String = when {
        payload.startsWith("media:") -> {
            val id = payload.removePrefix("media:").toLongOrNull() ?: 0L
            kotlinx.coroutines.runBlocking {
                container.db.libraryDao().byId(id)?.uri ?: ""
            }
        }
        payload.startsWith("file:") -> payload.removePrefix("file:")
        else -> payload
    }
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            if (source.isNotBlank()) {
                setMediaItem(
                    if (source.startsWith("content://"))
                        MediaItem.fromUri(android.net.Uri.parse(source))
                    else MediaItem.fromUri("file://$source")
                )
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                title = { Text(stringResource(R.string.video_play)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (source.isNotBlank()) {
                        IconButton(onClick = { onOpenTrimmer(source) }) {
                            Icon(Icons.Filled.ContentCut, stringResource(R.string.tool_trimmer))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        this.player = player
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            )
        }
    }
}
