package com.murai.gallery.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.screens.about.AboutScreen
import com.murai.gallery.ui.screens.albums.AlbumDetailScreen
import com.murai.gallery.ui.screens.albums.AlbumsScreen
import com.murai.gallery.ui.screens.bin.BinScreen
import com.murai.gallery.ui.screens.favorites.FavoritesScreen
import com.murai.gallery.ui.screens.gallery.GalleryScreen
import com.murai.gallery.ui.screens.map.MapScreen
import com.murai.gallery.ui.screens.search.SearchScreen
import com.murai.gallery.ui.screens.settings.SettingsScreen
import com.murai.gallery.ui.screens.stats.StatsScreen
import com.murai.gallery.ui.screens.tools.ToolsScreen
import com.murai.gallery.ui.screens.tools.backup.BackupScreen
import com.murai.gallery.ui.screens.tools.collage.CollageScreen
import com.murai.gallery.ui.screens.tools.compressor.CompressorScreen
import com.murai.gallery.ui.screens.tools.duplicates.DuplicatesScreen
import com.murai.gallery.ui.screens.tools.editor.EditorScreen
import com.murai.gallery.ui.screens.tools.gifmaker.GifMakerScreen
import com.murai.gallery.ui.screens.tools.ocr.OcrScreen
import com.murai.gallery.ui.screens.tools.qrcode.QrScanScreen
import com.murai.gallery.ui.screens.tools.rename.BatchRenameScreen
import com.murai.gallery.ui.screens.tools.sort.SortPresetsScreen
import com.murai.gallery.ui.screens.tools.storage.StorageCleanerScreen
import com.murai.gallery.ui.screens.tools.telegram.TelegramScreen
import com.murai.gallery.ui.screens.tools.trimmer.VideoTrimmerScreen
import com.murai.gallery.ui.screens.tools.wallpaper.WallpaperScreen
import com.murai.gallery.ui.viewer.ViewerScreen
import com.murai.gallery.ui.viewer.VideoPlayerScreen

object Routes {
    const val GALLERY = "gallery"
    const val ALBUMS = "albums"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val FAVORITES = "favorites"
    const val BIN = "bin"
    const val SEARCH = "search"
    const val MAP = "map"
    const val STATS = "stats"
    const val ABOUT = "about"
    const val VAULT = "vault"
    const val SLIDESHOW = "slideshow"

    fun album(bucketId: String) = "album/$bucketId"
    fun viewer(payload: String) = "viewer/$payload"
    fun video(payload: String) = "video/$payload"

    const val TOOL_DUPLICATES = "tool/duplicates"
    const val TOOL_EDITOR = "tool/editor"
    const val TOOL_TRIMMER = "tool/trimmer"
    const val TOOL_COMPRESSOR = "tool/compressor"
    const val TOOL_OCR = "tool/ocr"
    const val TOOL_COLLAGE = "tool/collage"
    const val TOOL_GIF = "tool/gif"
    const val TOOL_RENAME = "tool/rename"
    const val TOOL_CLEANER = "tool/cleaner"
    const val TOOL_QR = "tool/qr"
    const val TOOL_WALLPAPER = "tool/wallpaper"
    const val TOOL_TELEGRAM = "tool/telegram"
    const val TOOL_SORT = "tool/sort"
    const val TOOL_BACKUP = "tool/backup"
}

private data class Tab(val route: String, val labelRes: Int, val icon: @Composable () -> Unit)

private val tabs = listOf(
    Tab(Routes.GALLERY, com.murai.gallery.R.string.tab_gallery) {
        Icon(Icons.Filled.Collections, contentDescription = null)
    },
    Tab(Routes.ALBUMS, com.murai.gallery.R.string.tab_albums) {
        Icon(Icons.Filled.PhotoAlbum, contentDescription = null)
    },
    Tab(Routes.TOOLS, com.murai.gallery.R.string.tab_tools) {
        Icon(Icons.Filled.Construction, contentDescription = null)
    },
    Tab(Routes.SETTINGS, com.murai.gallery.R.string.tab_settings) {
        Icon(Icons.Filled.Settings, contentDescription = null)
    }
)

@Composable
fun MuraiAppRoot(container: AppContainer, incomingUri: android.net.Uri?) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    for (tab in tabs) {
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(androidx.compose.ui.res.stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        MuraiNavHost(
            navController = navController,
            container = container,
            incomingUri = incomingUri,
            modifier = Modifier.padding(
                bottom = if (showBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)
            )
        )
    }
}

@Composable
private fun MuraiNavHost(
    navController: NavHostController,
    container: AppContainer,
    incomingUri: android.net.Uri?,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.GALLERY,
        modifier = modifier
    ) {
        composable(Routes.GALLERY) {
            GalleryScreen(
                container = container,
                incomingUri = incomingUri,
                onOpenViewer = { payload -> navController.navigate(Routes.viewer(payload)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                onOpenBin = { navController.navigate(Routes.BIN) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenTool = { route -> navController.navigate(route) }
            )
        }
        composable(Routes.ALBUMS) {
            AlbumsScreen(
                container = container,
                onOpenAlbum = { bucketId -> navController.navigate(Routes.album(bucketId)) }
            )
        }
        composable(
            route = "album/{bucketId}",
            arguments = listOf(navArgument("bucketId") { type = NavType.StringType })
        ) { entry ->
            val bucketId = entry.arguments?.getString("bucketId") ?: return@composable
            AlbumDetailScreen(
                container = container,
                bucketId = bucketId,
                onBack = { navController.popBackStack() },
                onOpenViewer = { payload -> navController.navigate(Routes.viewer(payload)) }
            )
        }
        composable(Routes.TOOLS) {
            ToolsScreen(
                container = container,
                onOpen = { route -> navController.navigate(route) },
                onOpenVault = { navController.navigate(Routes.VAULT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenVault = { navController.navigate(Routes.VAULT) },
                onOpenBackup = { navController.navigate(Routes.TOOL_BACKUP) }
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenViewer = { payload -> navController.navigate(Routes.viewer(payload)) }
            )
        }
        composable(Routes.BIN) {
            BinScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenViewer = { payload -> navController.navigate(Routes.viewer(payload)) }
            )
        }
        composable(Routes.MAP) {
            MapScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.VAULT) {
            com.murai.gallery.ui.screens.settings.VaultScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SLIDESHOW) {
            com.murai.gallery.ui.screens.slideshow.SlideshowScreen(
                container = container,
                onExit = { navController.popBackStack() }
            )
        }
        composable(
            route = "viewer/{payload}",
            arguments = listOf(navArgument("payload") { type = NavType.StringType })
        ) { entry ->
            ViewerScreen(
                container = container,
                payload = entry.arguments?.getString("payload") ?: "",
                onBack = { navController.popBackStack() },
                onOpenVideo = { p -> navController.navigate(Routes.video(p)) },
                onOpenEditor = { uri -> navController.navigate("tool/editor?uri=${android.net.Uri.encode(uri)}") },
                onOpenSlideshow = { navController.navigate(Routes.SLIDESHOW) }
            )
        }
        composable(
            route = "video/{payload}",
            arguments = listOf(navArgument("payload") { type = NavType.StringType })
        ) { entry ->
            VideoPlayerScreen(
                container = container,
                payload = entry.arguments?.getString("payload") ?: "",
                onBack = { navController.popBackStack() },
                onOpenTrimmer = { uri -> navController.navigate("tool/trimmer?uri=${android.net.Uri.encode(uri)}") }
            )
        }
        composable(Routes.TOOL_DUPLICATES) {
            DuplicatesScreen(container, onBack = { navController.popBackStack() })
        }
        composable(
            route = "tool/editor?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            EditorScreen(
                container = container,
                startUri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "tool/trimmer?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            VideoTrimmerScreen(
                container = container,
                startUri = entry.arguments?.getString("uri") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TOOL_COMPRESSOR) {
            CompressorScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_OCR) {
            OcrScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_COLLAGE) {
            CollageScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_GIF) {
            GifMakerScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_RENAME) {
            BatchRenameScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_CLEANER) {
            StorageCleanerScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_QR) {
            QrScanScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_WALLPAPER) {
            WallpaperScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_TELEGRAM) {
            TelegramScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_SORT) {
            SortPresetsScreen(container, onBack = { navController.popBackStack() })
        }
        composable(Routes.TOOL_BACKUP) {
            BackupScreen(container, onBack = { navController.popBackStack() })
        }
    }
}
