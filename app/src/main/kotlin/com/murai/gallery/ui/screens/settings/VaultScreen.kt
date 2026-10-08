package com.murai.gallery.ui.screens.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.R
import com.murai.gallery.data.vault.VaultRepository
import com.murai.gallery.di.AppContainer
import com.murai.gallery.ui.components.ToolScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VaultUiState(
    val unlocked: Boolean = false,
    val decoyUnlocked: Boolean = false,
    val hasPin: Boolean = false,
    val hasDecoy: Boolean = false,
    val error: Boolean = false
)

/**
 * Secure vault with PIN + biometric unlock and a decoy PIN: the decoy opens a
 * harmless decoy space, so the real PIN is never exposed under pressure.
 */
class VaultViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(VaultUiState())
    val vaultRepo = VaultRepository(container.appContext, container.db)

    init {
        viewModelScope.launch {
            val pin = container.settings.vaultPinHash.first()
            val decoy = container.settings.vaultDecoyHash.first()
            state.value = VaultUiState(hasPin = pin.isNotBlank(), hasDecoy = decoy.isNotBlank())
        }
    }

    fun setPin(pin: String, isDecoy: Boolean) {
        viewModelScope.launch {
            // decoy shares the master salt so one comparison path serves both
            val existingSalt = container.settings.vaultSalt.first()
            val salt = existingSalt.ifBlank { com.murai.gallery.domain.vault.VaultCrypto.randomSalt() }
            val hash = vaultRepo.hashOf(pin, salt)
            if (isDecoy) {
                if (existingSalt.isBlank()) container.settings.setVaultPinHash("", salt)
                container.settings.setVaultDecoyHash(hash)
                state.value = state.value.copy(hasDecoy = true)
            } else {
                container.settings.setVaultPinHash(hash, salt)
                state.value = state.value.copy(hasPin = true, unlocked = true)
            }
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            val salt = container.settings.vaultSalt.first()
            val pinHash = container.settings.vaultPinHash.first()
            val decoyHash = container.settings.vaultDecoyHash.first()
            val realOk = pinHash.isNotBlank() && vaultRepo.pinMatches(pin, salt, pinHash)
            val decoyOk = decoyHash.isNotBlank() && salt.isNotBlank() &&
                vaultRepo.pinMatches(pin, salt, decoyHash)
            state.value = state.value.copy(
                unlocked = realOk,
                decoyUnlocked = decoyOk && !realOk,
                error = !realOk && !decoyOk
            )
        }
    }

    fun lock() {
        state.value = state.value.copy(unlocked = false, decoyUnlocked = false, error = false)
    }

    fun authenticate(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val manager = BiometricManager.from(activity)
        val can = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(false)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.vault_biometric_title))
                .setSubtitle(activity.getString(R.string.vault_biometric_subtitle))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }
}

@Composable
fun VaultScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: VaultViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { VaultViewModel(container) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    ToolScaffold(title = stringResource(R.string.tool_vault), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                !state.hasPin -> PinSetup(vm)
                !state.unlocked && !state.decoyUnlocked -> PinEntry(vm)
                state.decoyUnlocked -> {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.vault_decoy_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.vault_decoy_text),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            TextButton(onClick = { vm.lock() }) { Text(stringResource(R.string.vault_lock)) }
                        }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.vault_unlocked_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.lock() }) { Text(stringResource(R.string.vault_lock)) }
                    }
                    Text(
                        stringResource(R.string.vault_items_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PinSetup(vm: VaultViewModel) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.vault_setup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.vault_setup_text),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                label = { Text(stringResource(R.string.vault_pin_new)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.filter(Char::isDigit).take(8) },
                label = { Text(stringResource(R.string.vault_pin_confirm)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Button(
                onClick = { if (pin.length >= 4 && pin == confirm) vm.setPin(pin, false) },
                enabled = pin.length >= 4 && pin == confirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) { Text(stringResource(R.string.vault_setup_done)) }
        }
    }
}

@Composable
private fun PinEntry(vm: VaultViewModel) {
    var pin by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.vault_unlock_title), style = MaterialTheme.typography.titleMedium)
            if (state.error) {
                Text(
                    stringResource(R.string.vault_wrong_pin),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                label = { Text(stringResource(R.string.vault_pin_enter)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Button(onClick = { vm.unlock(pin); pin = "" }) { Text(stringResource(R.string.vault_unlock)) }
                TextButton(onClick = { vm.setPin(pin, true); pin = "" }) {
                    Text(stringResource(R.string.vault_set_decoy))
                }
            }
        }
    }
}
