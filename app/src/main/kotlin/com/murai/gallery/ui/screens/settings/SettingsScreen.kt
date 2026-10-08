package com.murai.gallery.ui.screens.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murai.gallery.BuildConfig
import com.murai.gallery.R
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.sort.SortEngine
import com.murai.gallery.ui.theme.ThemeMode
import com.murai.gallery.util.ErrorLogger
import com.murai.gallery.domain.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val updateMessage: String? = null,
    val updateAvailable: Boolean = false,
    val updateUrl: String? = null,
    val logExported: String? = null
)

/** All user preferences. Language changes apply to the whole app instantly. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(SettingsUiState())

    val themeMode = container.settings.themeMode
    val amoled = container.settings.amoled
    val dynamicColor = container.settings.dynamicColor
    val languageTag = container.settings.languageTag
    val gridSize = container.settings.gridSize
    val vaultBiometric = container.settings.vaultBiometric
    val vaultHasPin = container.settings.vaultPinHash

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(mode) }
    fun setAmoled(v: Boolean) = viewModelScope.launch { container.settings.setAmoled(v) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { container.settings.setDynamicColor(v) }
    fun setLanguage(tag: String) = viewModelScope.launch {
        container.settings.setLanguageTag(tag)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
        )
    }
    fun setVaultBiometric(v: Boolean) = viewModelScope.launch { container.settings.setVaultBiometric(v) }

    fun checkUpdates() {
        viewModelScope.launch {
            state.value = state.value.copy(updateMessage = "checking")
            when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                is UpdateChecker.CheckResult.Success -> {
                    if (result.isNewer) {
                        state.value = state.value.copy(
                            updateMessage = "available",
                            updateAvailable = true,
                            updateUrl = result.release.apkUrl
                        )
                    } else {
                        state.value = state.value.copy(updateMessage = "latest")
                    }
                }
                is UpdateChecker.CheckResult.Offline -> {
                    state.value = state.value.copy(updateMessage = "offline")
                }
            }
        }
    }

    fun exportLogs(context: Context, target: File, onReady: (File?) -> Unit) {
        viewModelScope.launch {
            val ok = ErrorLogger.exportZip(context, target)
            onReady(if (ok) target else null)
        }
    }
}

private val LANGS = listOf(
    "" to "System",
    "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch",
    "pt-BR" to "Português (BR)", "ru" to "Русский", "zh-CN" to "中文",
    "ja" to "日本語", "ar" to "العربية", "hi" to "हिन्दी"
)

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenAbout: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = com.murai.gallery.ui.components.muraiFactory { SettingsViewModel(container) })
    val themeMode by vm.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val amoled by vm.amoled.collectAsState(initial = false)
    val dynamic by vm.dynamicColor.collectAsState(initial = true)
    val languageTag by vm.languageTag.collectAsState(initial = "")
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val logLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val staged = File(context.cacheDir, "murai-logs.zip")
            vm.exportLogs(context, staged) { file ->
                if (file != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    file.delete()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.displaySmall)

        SettingsSection(stringResource(R.string.settings_appearance)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                ThemeMode.entries.forEach { mode ->
                    androidx.compose.material3.FilterChip(
                        selected = themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                }
                            )
                        }
                    )
                }
            }
            SwitchRow(stringResource(R.string.settings_amoled), amoled) { vm.setAmoled(it) }
            SwitchRow(stringResource(R.string.settings_dynamic), dynamic) { vm.setDynamic(it) }
        }

        SettingsSection(stringResource(R.string.settings_language)) {
            Text(stringResource(R.string.settings_language_desc), style = MaterialTheme.typography.bodySmall)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(LANGS) { lang ->
                    androidx.compose.material3.FilterChip(
                        selected = languageTag == lang.first,
                        onClick = { vm.setLanguage(lang.first) },
                        label = { Text(lang.second) }
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_vault_section)) {
            TextButton(onClick = onOpenVault) { Text(stringResource(R.string.settings_open_vault)) }
        }

        SettingsSection(stringResource(R.string.settings_updates)) {
            Text(stringResource(R.string.settings_updates_desc), style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.Button(
                onClick = { vm.checkUpdates() },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(stringResource(R.string.settings_check_updates)) }
            state.updateMessage?.let { msg ->
                Text(
                    when (msg) {
                        "checking" -> stringResource(R.string.update_checking)
                        "latest" -> stringResource(R.string.update_latest, BuildConfig.VERSION_NAME)
                        "available" -> stringResource(R.string.update_available)
                        else -> stringResource(R.string.update_offline)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg == "offline") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (state.updateUrl != null) {
                    Text(
                        state.updateUrl ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_logs)) {
            Text(
                stringResource(R.string.settings_logs_desc),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { logLauncher.launch("murai-logs.zip") }) {
                Text(stringResource(R.string.settings_export_logs))
            }
        }

        SettingsSection(stringResource(R.string.settings_data)) {
            TextButton(onClick = onOpenBackup) { Text(stringResource(R.string.settings_open_backup)) }
        }

        TextButton(onClick = onOpenAbout) { Text(stringResource(R.string.settings_about)) }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card {
        Column(Modifier
            .fillMaxWidth()
            .padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
