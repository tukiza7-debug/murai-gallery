package com.murai.gallery.ui.recovery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.util.ErrorLogger
import com.murai.gallery.util.ShareHelper
import java.io.File

/**
 * Global recovery screen. When the previous run ended in an uncaught
 * exception, the next launch shows this instead of the gallery, with the
 * saved stack trace, a one-tap "Copy log" and an "Export log zip" share —
 * so a crash loop is visible and reportable rather than silent.
 */
@Composable
fun CrashRecoveryHost(
    container: AppContainer,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val crash = remember {
        ErrorLogger.lastCrash(context)
    }
    if (crash == null) {
        content()
        return
    }
    RecoveryBody(
        trace = crash.trace,
        context = context,
        onDismiss = {
            ErrorLogger.clearCrash(context)
        }
    )
}

@Composable
private fun RecoveryBody(
    trace: String,
    context: Context,
    onDismiss: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.recovery_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                trace.takeLast(4000),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    runCatching {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Murai log", trace)
                        )
                        copied = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (copied) stringResource(R.string.meta_saved)
                    else stringResource(R.string.action_copy_log)
                )
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        val zip = File(context.cacheDir, "murai-log-export.zip")
                        if (ErrorLogger.exportZip(context, zip)) {
                            val shareUri = ShareHelper.contentUriFor(context, zip)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, null)
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_export_zip))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.recovery_dismiss))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
