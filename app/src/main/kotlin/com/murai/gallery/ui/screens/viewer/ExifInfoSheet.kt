package com.murai.gallery.ui.screens.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.murai.gallery.R
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.exif.ExifEditor
import com.murai.gallery.util.ConsentBus
import com.murai.gallery.util.ErrorLogger
import com.murai.gallery.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Metadata viewer + editor (date taken, GPS coordinates).
 *
 * v2.0.1: all reads and writes go through the item's content URI — the cached
 * relative path is never treated as a file. Reads use openInputStream; writes
 * round-trip through a private temp file inside MediaOperations.writeExif and
 * surface a consent request when the system requires one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifInfoSheet(
    item: LibraryItemEntity,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    // EXIF is read off the main thread from the content URI stream.
    val info by produceState(
        initialValue = ExifEditor.ExifInfo(item.dateTakenSec, item.latitude, item.longitude, null, 0),
        key1 = item.id
    ) {
        val read = withContext(Dispatchers.IO) {
            runCatching {
                item.uri.let { uri ->
                    container.appContext.contentResolver
                        .openInputStream(android.net.Uri.parse(uri))?.use { stream ->
                            ExifEditor.read(stream)
                        }
                }
            }.getOrNull()
        }
        if (read != null) value = read
    }
    var dateText by remember { mutableStateOf(if (info.dateTakenSec > 0) ExifEditor.format(info.dateTakenSec) else "") }
    var latText by remember { mutableStateOf(if (info.latitude != 0.0) info.latitude.toString() else "") }
    var lonText by remember { mutableStateOf(if (info.longitude != 0.0) info.longitude.toString() else "") }
    var saved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Formatters.fileSize(item.size) + " · " + item.mime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Formatters.megapixels(item.width, item.height),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            InfoRow(stringResource(R.string.meta_folder), item.folder)
            InfoRow(stringResource(R.string.meta_added), Formatters.dateTime(item.dateAddedSec))
            InfoRow(stringResource(R.string.meta_modified), Formatters.dateTime(item.dateModifiedSec))
            if (item.isVideo) InfoRow(stringResource(R.string.meta_duration), Formatters.duration(item.durationMs))
            val cameraModel = info.cameraModel
            if (cameraModel != null) InfoRow(stringResource(R.string.meta_camera), cameraModel)
            if (item.latitude != 0.0) {
                InfoRow(
                    stringResource(R.string.meta_location),
                    Formatters.coordinates(item.latitude, item.longitude)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.meta_edit_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text(stringResource(R.string.meta_date_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text(stringResource(R.string.meta_lat_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text(stringResource(R.string.meta_lon_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val epoch = ExifEditor.parse(dateText)
                        val lat = latText.toDoubleOrNull()
                        val lon = lonText.toDoubleOrNull()
                        val result = container.operations.writeExif(item) { tempFile ->
                            if (epoch > 0) ExifEditor.writeDate(tempFile, epoch)
                            if (lat != null && lon != null) ExifEditor.writeLocation(tempFile, lat, lon)
                        }
                        when {
                            result.ok -> saved = true
                            result.consent != null -> ConsentBus.request(result.consent) { granted ->
                                if (granted) {
                                    // Retry once with the same edit after consent.
                                    scope.launch {
                                        val retry = container.operations.writeExif(item) { tempFile ->
                                            if (epoch > 0) ExifEditor.writeDate(tempFile, epoch)
                                            if (lat != null && lon != null) ExifEditor.writeLocation(tempFile, lat, lon)
                                        }
                                        saved = retry.ok
                                        failed = !retry.ok
                                    }
                                } else failed = true
                            }
                            else -> failed = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        saved -> stringResource(R.string.meta_saved)
                        failed -> stringResource(R.string.action_failed)
                        else -> stringResource(R.string.action_save)
                    }
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_close)) }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
