package com.murai.gallery.ui.screens.tools.telegram

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.telegram.TelegramClient
import com.murai.gallery.ui.components.launchSafely
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TelegramState(
    val sending: Boolean = false,
    val progress: Float = 0f,
    val ok: Boolean = false,
    val error: String? = null
)

/** Share to Telegram via a user-owned bot token + chat id. */
class TelegramViewModel(
    private val container: AppContainer,
    private val appContext: android.content.Context
) : ViewModel() {
    val state = MutableStateFlow(TelegramState())
    val config = MutableStateFlow<Pair<String, String>>("" to "")
    var asDocument = MutableStateFlow(false)

    fun load() {
        launchSafely(appContext, "telegram") {
            val token = container.settings.telegramToken.first()
            val chat = container.settings.telegramChat.first()
            config.value = token to chat
        }
    }

    fun saveConfig(token: String, chat: String) {
        launchSafely(appContext, "telegram") { container.settings.setTelegram(token, chat) }
    }

    fun sendLatest() {
        launchSafely(appContext, "telegram") {
            state.value = TelegramState(sending = true)
            val (token, chat) = config.value
            val client = TelegramClient(appContext, token, chat)
            val latest = container.db.libraryDao().recent(1).firstOrNull() ?: run {
                state.value = TelegramState(error = "empty")
                return@launchSafely
            }
            val result = client.send(
                itemUri = Uri.parse(latest.uri),
                fileName = latest.name,
                mime = latest.mime,
                asDocument = asDocument.value
            ) { sent, total ->
                if (total > 0) {
                    state.value = state.value.copy(progress = sent.toFloat() / total)
                }
            }
            state.value = TelegramState(ok = result.ok, error = result.message)
        }
    }

}

@Composable
fun TelegramScreen(container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: TelegramViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { TelegramViewModel(container, context.applicationContext) })
    val state by vm.state.collectAsState()
    val config by vm.config.collectAsState()
    val asDoc by vm.asDocument.collectAsState()
    var token by remember(config) { mutableStateOf(config.first) }
    var chat by remember(config) { mutableStateOf(config.second) }

    LaunchedEffect(Unit) { vm.load() }

    ToolScaffold(title = stringResource(R.string.tool_telegram), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.tg_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.tg_token)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            OutlinedTextField(
                value = chat,
                onValueChange = { chat = it },
                label = { Text(stringResource(R.string.tg_chat)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Button(
                onClick = { vm.saveConfig(token, chat) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) { Text(stringResource(R.string.tg_save)) }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.tg_as_document), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = asDoc, onCheckedChange = { vm.asDocument.value = it })
            }
            Button(
                onClick = { vm.sendLatest() },
                enabled = !state.sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) { Text(stringResource(R.string.tg_send_latest)) }
            if (state.sending) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
            state.error?.let {
                Text(
                    stringResource(R.string.tg_error, it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.ok) {
                Text(
                    stringResource(R.string.tg_sent),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                stringResource(R.string.tg_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
