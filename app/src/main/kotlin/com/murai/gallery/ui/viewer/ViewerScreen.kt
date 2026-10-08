package com.murai.gallery.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.screens.viewer.ExifInfoSheet
import com.murai.gallery.util.Formatters
import com.murai.gallery.util.ShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    container: AppContainer,
    payload: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenSlideshow: () -> Unit
) {
    // Decoded payloads use the safe "anchor@scope" format; legacy
    // "scope|anchor" strings from earlier builds still parse (fix #8).
    val (scopeKey, anchorId) = com.murai.gallery.util.RouteArgs.parseViewerPayload(payload)
    val vm: ViewerViewModel = viewModel(
        key = "viewer_$scopeKey-$anchorId",
        factory = com.murai.gallery.ui.components.muraiFactory { ViewerViewModel(container, scopeKey, anchorId) }
    )
    val ids by vm.ids.collectAsState()
    val context = LocalContext.current
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    val pagerState = rememberPagerState(initialPage = vm.index.value) { ids.size.coerceAtLeast(1) }
    LaunchedEffect(ids.size) {
        if (ids.isNotEmpty() && pagerState.currentPage != vm.index.value && vm.index.value < ids.size) {
            pagerState.scrollToPage(vm.index.value)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        vm.setPosition(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { pos -> ids.getOrNull(pos) ?: -pos.toLong() },
            userScrollEnabled = true
        ) { position ->
            ZoomablePage(
                container = container,
                position = position,
                loader = { vm.at(position) },
                refreshTick = refreshTick,
                onOpenVideo = onOpenVideo,
                onDoubleTapToggle = { },
                launchOp = { block -> vm.launchOp(block) }
            )
        }

        // top overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
            }
            var currentItem by remember { mutableStateOf<LibraryItemEntity?>(null) }
            LaunchedEffect(pagerState.currentPage, refreshTick) {
                currentItem = vm.at(pagerState.currentPage)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    currentItem?.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1
                )
                currentItem?.let {
                    Text(
                        Formatters.megapixels(it.width, it.height) + "  ·  " + Formatters.fileSize(it.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            currentItem?.let { item ->
                if (!item.isVideo) {
                    IconButton(onClick = {
                        vm.launchOp { vm.toggleFavorite(item); refreshTick++ }
                    }) {
                        Icon(
                            if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            stringResource(R.string.action_favorite), tint = Color(0xFFF5A623)
                        )
                    }
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, stringResource(R.string.info_title), tint = Color.White)
                }
            }
        }

        // bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            var currentItem by remember { mutableStateOf<LibraryItemEntity?>(null) }
            LaunchedEffect(pagerState.currentPage, refreshTick) {
                currentItem = vm.at(pagerState.currentPage)
            }
            currentItem?.let { item ->
                if (!item.isVideo) {
                    IconButton(onClick = { onOpenEditor(item.uri) }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.tool_editor), tint = Color.White)
                    }
                }
                IconButton(onClick = { ShareHelper.shareOne(context, item.uri, item.mime) }) {
                    Icon(Icons.Filled.Share, stringResource(R.string.action_share), tint = Color.White)
                }
                if (!item.isVideo) {
                    IconButton(onClick = { onOpenSlideshow() }) {
                        Icon(Icons.Filled.Slideshow, stringResource(R.string.slideshow_title), tint = Color.White)
                    }
                    IconButton(onClick = { ShareHelper.setWallpaper(context, item.uri) }) {
                        Icon(Icons.Filled.Wallpaper, stringResource(R.string.action_set_as), tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { onOpenVideo("media:${item.id}") }) {
                        Icon(Icons.Filled.PlayCircle, stringResource(R.string.video_play), tint = Color.White)
                    }
                }
                IconButton(onClick = { confirmDelete = item }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.action_trash), tint = Color.White)
                }
            }
        }
    }

    if (showInfo) {
        val item by androidx.compose.runtime.produceState<LibraryItemEntity?>(
            initialValue = null, key1 = pagerState.currentPage
        ) { value = vm.at(pagerState.currentPage) }
        item?.let {
            ExifInfoSheet(item = it, container = container, onDismiss = { showInfo = false })
        }
    }

    confirmDelete?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.warn_trash_title)) },
            text = { Text(stringResource(R.string.warn_trash_one, item.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val target = item
                    confirmDelete = null
                    vm.launchOp {
                        vm.trash(target) { refreshTick++ }
                    }
                }) {
                    Text(stringResource(R.string.action_trash), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** One page: pinch-zoom + double-tap + panorama detection + motion photo play. */
@Composable
private fun ZoomablePage(
    container: AppContainer,
    position: Int,
    loader: suspend (Int) -> LibraryItemEntity?,
    refreshTick: Int,
    onOpenVideo: (String) -> Unit,
    onDoubleTapToggle: () -> Unit,
    launchOp: (suspend () -> Unit) -> Unit
) {
    var item by remember(position) { mutableStateOf<LibraryItemEntity?>(null) }
    LaunchedEffect(position, refreshTick) { item = loader(position) }
    val context = LocalContext.current

    var scale by remember(position) { mutableFloatStateOf(1f) }
    var offsetX by remember(position) { mutableFloatStateOf(0f) }
    var offsetY by remember(position) { mutableFloatStateOf(0f) }

    val current = item
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(position) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1.02f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(position) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.5f) {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        contentAlignment = Alignment.Center
    ) {
        if (current != null) {
            if (current.isVideo) {
                // video pages show a poster + play affordance
                AsyncImage(
                    model = ImageRequest.Builder(context).data(current.uri).build(),
                    contentDescription = current.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(onClick = { onOpenVideo("media:${current.id}") }, modifier = Modifier.scale(1.6f)) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.PlayCircle,
                        contentDescription = stringResource(R.string.video_play),
                        tint = Color.White
                    )
                }
            } else if (current.isPano) {
                PanoView(uri = current.uri)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(current.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = current.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (current.isMotion) {
                    IconButton(
                        onClick = {
                            val path = current.path
                            launchOp {
                                val mp4 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    MotionPhotoExtractor.extract(context, path)
                                }
                                if (mp4 != null) onOpenVideo("file:${mp4.absolutePath}")
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp)
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.PlayCircle,
                            contentDescription = stringResource(R.string.motion_photo),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
