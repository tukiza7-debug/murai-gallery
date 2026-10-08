package com.murai.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.murai.gallery.data.db.entity.LibraryItemEntity

/** Thumbnail cell used across every grid in the app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumb(
    item: LibraryItemEntity,
    size: Dp,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    Icons.Filled.Videocam, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(11.dp)
                )
                Text(
                    com.murai.gallery.util.Formatters.duration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.secondary else Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ProgressOverlay(message: String, progress: Float?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Warning dialog used before every destructive action. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.murai.gallery.R.string.action_cancel)) }
        }
    )
}

/** Checkbox row for lists. */
@Composable
fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
