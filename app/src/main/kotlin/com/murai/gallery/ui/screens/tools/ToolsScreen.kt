package com.murai.gallery.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.nav.Routes

private data class ToolEntry(
    val route: String,
    val labelRes: Int,
    val descRes: Int,
    val icon: ImageVector
)

private val tools = listOf(
    ToolEntry(Routes.TOOL_DUPLICATES, R.string.tool_duplicates, R.string.tool_duplicates_desc, Icons.Filled.AutoAwesome),
    ToolEntry(Routes.TOOL_EDITOR, R.string.tool_editor, R.string.tool_editor_desc, Icons.Filled.ImageSearch),
    ToolEntry(Routes.TOOL_TRIMMER, R.string.tool_trimmer, R.string.tool_trimmer_desc, Icons.Filled.ContentCut),
    ToolEntry(Routes.TOOL_COMPRESSOR, R.string.tool_compressor, R.string.tool_compressor_desc, Icons.Filled.Compress),
    ToolEntry(Routes.TOOL_OCR, R.string.tool_ocr, R.string.tool_ocr_desc, Icons.Filled.DocumentScanner),
    ToolEntry(Routes.TOOL_COLLAGE, R.string.tool_collage, R.string.tool_collage_desc, Icons.Filled.Collections),
    ToolEntry(Routes.TOOL_GIF, R.string.tool_gif, R.string.tool_gif_desc, Icons.Filled.Gif),
    ToolEntry(Routes.TOOL_RENAME, R.string.tool_rename, R.string.tool_rename_desc, Icons.Filled.TextFields),
    ToolEntry(Routes.TOOL_CLEANER, R.string.tool_cleaner, R.string.tool_cleaner_desc, Icons.Filled.CleaningServices),
    ToolEntry(Routes.TOOL_QR, R.string.tool_qr, R.string.tool_qr_desc, Icons.Filled.QrCodeScanner),
    ToolEntry(Routes.TOOL_WALLPAPER, R.string.tool_wallpaper, R.string.tool_wallpaper_desc, Icons.Filled.Wallpaper),
    ToolEntry(Routes.TOOL_TELEGRAM, R.string.tool_telegram, R.string.tool_telegram_desc, Icons.Filled.Send),
    ToolEntry(Routes.TOOL_SORT, R.string.tool_sort, R.string.tool_sort_desc, Icons.Filled.Shuffle),
    ToolEntry(Routes.TOOL_BACKUP, R.string.tool_backup, R.string.tool_backup_desc, Icons.Filled.Backup)
)

@Composable
fun ToolsScreen(
    container: AppContainer,
    onOpen: (String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            stringResource(R.string.new_tools_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(
            stringResource(R.string.new_tools_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tools, key = { it.route }) { tool ->
                Card(
                    onClick = { onOpen(tool.route) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(tool.labelRes), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(tool.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                            maxLines = 2
                        )
                    }
                }
            }
            item {
                Card(
                    onClick = onOpenVault,
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.tool_vault), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.tool_vault_desc),
                            style = MaterialTheme.typography.bodySmall,
                            minLines = 2,
                            maxLines = 2
                        )
                    }
                }
            }
            item {
                Card(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_desc_short),
                            style = MaterialTheme.typography.bodySmall,
                            minLines = 2,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
