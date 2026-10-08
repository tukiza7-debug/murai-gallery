package com.murai.gallery.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Media3 video player used for regular videos, motion photo trailers and trim
 * previews.
 *
 * v2.0.1: no runBlocking anywhere. "media:<id>" payloads are resolved off the
 * main thread while a loading state shows; the player is created inside a
 * DisposableEffect keyed on the resolved source and released on source change
 * or disposal; decode failures surface as a friendly message instead of a
 * black screen.
 */
private sealed interface VideoSource {
    data object None : VideoSource
    data object Loading : VideoSource
    data class Ready(val uri: android.net.Uri) : VideoSource
    data class Failed(val message: String) : VideoSource
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    container: AppContainer,
    payload: String,
    onBack: () -> Unit,
    onOpenTrimmer: (String) -> Unit
) {
    val context = LocalContext.current

    val source: VideoSource by produceState<VideoSource>(VideoSource.Loading, payload) {
        value = withContext(Dispatchers.IO) {
            try {
                when {
                    payload.startsWith("media:") -> {
                        val id = payload.removePrefix("media:").toLongOrNull()
                        if (id == null) {
                            VideoSource.Failed("invalid payload")
                        } else {
                            val uri = container.db.libraryDao().byId(id)?.uri
                            if (uri.isNullOrBlank()) VideoSource.Failed("missing item")
                            else VideoSource.Ready(android.net.Uri.parse(uri))
                        }
                    }
                    payload.startsWith("file:") -> {
                        val path = payload.removePrefix("file:")
                        if (path.isBlank()) VideoSource.Failed("invalid path")
                        else VideoSource.Ready(android.net.Uri.fromFile(java.io.File(path)))
                    }
                    payload.startsWith("content:") ->
                        VideoSource.Ready(android.net.Uri.parse(payload))
                    payload.isNotBlank() ->
                        VideoSource.Ready(android.net.Uri.parse(payload))
                    else -> VideoSource.None
                }
            } catch (t: Throwable) {
                VideoSource.Failed(t.message ?: "unknown")
            }
        }
    }

    val readyUri = (source as? VideoSource.Ready)?.uri

    // One player per source; released on source change and on dispose.
    var playbackError by remember(readyUri) { mutableStateOf<String?>(null) }
    val player = remember(readyUri) {
        readyUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = error.errorCode.toString()
                    }
                })
            }
        }
    }
    DisposableEffect(readyUri) {
        onDispose { player?.release() }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = { Text(stringResource(R.string.video_play)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (readyUri != null) {
                        IconButton(onClick = { onOpenTrimmer(readyUri.toString()) }) {
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
            when {
                readyUri != null && player != null && playbackError == null -> {
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
                source is VideoSource.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                source is VideoSource.Failed || playbackError != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.video_error_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.video_error_text),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                        )
                        Button(
                            onClick = { playbackError = null; player?.seekTo(0); player?.prepare() },
                            modifier = Modifier.padding(top = 16.dp)
                        ) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                source is VideoSource.None -> {
                    Text(
                        stringResource(R.string.video_error_title),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
