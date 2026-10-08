package com.murai.gallery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.murai.gallery.R
import com.murai.gallery.util.ErrorLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tool failure containment (fix #11): every tool ViewModel launches its work
 * through [safeLaunch]; anything thrown becomes a logged event plus a
 * visible, dismissible error state instead of a process kill. Each tool owns
 * its own state instance, so one broken tool never poisons another.
 */
class ToolErrorState {
    var failed by mutableStateOf(false)
        private set

    /** Marks the tool as failed; the UI shows the friendly error panel. */
    fun fail() {
        failed = true
    }

    /** Marks the tool as ready to retry. */
    fun retry() {
        failed = false
    }
}

fun ViewModel.safeLaunch(
    tool: ToolErrorState,
    context: android.content.Context,
    tag: String,
    block: suspend () -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (t: Throwable) {
            ErrorLogger.write(context, "tool:$tag", t)
            tool.fail()
        }
    }
}

/**
 * Drop-in replacement for `viewModelScope.launch { … }` inside tool screens:
 * anything thrown is logged to the on-device error log (and logcat) instead
 * of killing the process. The UI keeps showing its last state; tools that
 * expose an error flag set it themselves inside the block.
 */
fun ViewModel.launchSafely(
    context: android.content.Context?,
    tag: String,
    block: suspend () -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (t: Throwable) {
            context?.let { ErrorLogger.write(it, "tool:$tag", t) }
            android.util.Log.e("MuraiTool", tag, t)
        }
    }
}

/** Friendly failure panel shown inside a tool when its work threw. */
@Composable
fun ToolErrorPanel(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.error_tool_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.error_tool_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

/** Runs [block] swallowing exceptions into the error log (non-VM helpers). */
fun runSafely(context: android.content.Context, tag: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        ErrorLogger.write(context, "tool:$tag", t)
    }
}
