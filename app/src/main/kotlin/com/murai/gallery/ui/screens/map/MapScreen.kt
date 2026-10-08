package com.murai.gallery.ui.screens.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Photo map built on the open-source osmdroid stack with OpenStreetMap tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(4.5)
        }
    }

    LaunchedEffect(Unit) {
        val items = container.repository.geotagged()
        val overlays = ArrayList<Marker>(items.size)
        for (item in items.take(2000)) {
            if (item.latitude == 0.0 && item.longitude == 0.0) continue
            val marker = Marker(map)
            marker.position = GeoPoint(item.latitude, item.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = item.name
            marker.snippet = item.folder
            overlays.add(marker)
        }
        map.overlays.addAll(overlays)
        map.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { map },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
