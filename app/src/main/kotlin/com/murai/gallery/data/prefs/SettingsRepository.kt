package com.murai.gallery.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.murai.gallery.domain.model.GroupMode
import com.murai.gallery.domain.model.SortDirection
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.model.SortSpec
import com.murai.gallery.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.muraiDataStore: DataStore<Preferences> by preferencesDataStore(name = "murai_settings")

/** Sort memory is kept per screen so every surface remembers its own order. */
enum class SortScreen { HOME, ALBUM, SEARCH, FAVORITES, BIN }

class SettingsRepository(private val context: Context) {

    private object K {
        val themeMode = stringPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val languageTag = stringPreferencesKey("language_tag")
        val gridSize = intPreferencesKey("grid_span")
        val slideshowIntervalSec = intPreferencesKey("slideshow_interval")
        val wallpaperAlbums = stringPreferencesKey("wallpaper_albums")
        val wallpaperIntervalHours = intPreferencesKey("wallpaper_interval")
        val wallpaperEnabled = booleanPreferencesKey("wallpaper_enabled")
        val wallpaperBoth = booleanPreferencesKey("wallpaper_both")
        val tgToken = stringPreferencesKey("tg_token")
        val tgChat = stringPreferencesKey("tg_chat")
        val vaultPinHash = stringPreferencesKey("vault_pin_hash")
        val vaultSalt = stringPreferencesKey("vault_salt")
        val vaultDecoyHash = stringPreferencesKey("vault_decoy_hash")
        val vaultBiometric = booleanPreferencesKey("vault_biometric")
        val vaultHideRecents = booleanPreferencesKey("vault_hide_recents")
        val updateIgnored = stringPreferencesKey("update_ignored")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    private object SortKeys {
        fun option(screen: SortScreen) = stringPreferencesKey("sort_option_${screen.name}")
        fun dir(screen: SortScreen) = stringPreferencesKey("sort_dir_${screen.name}")
        fun group(screen: SortScreen) = stringPreferencesKey("sort_group_${screen.name}")
    }

    val themeMode: Flow<ThemeMode> = context.muraiDataStore.data.map { p ->
        when (p[K.themeMode]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val amoled: Flow<Boolean> = context.muraiDataStore.data.map { it[K.amoled] ?: false }
    val dynamicColor: Flow<Boolean> = context.muraiDataStore.data.map { it[K.dynamicColor] ?: true }
    val languageTag: Flow<String> = context.muraiDataStore.data.map { it[K.languageTag] ?: "" }
    val gridSize: Flow<Int> = context.muraiDataStore.data.map { (it[K.gridSize] ?: 3).coerceIn(2, 6) }
    val slideshowIntervalSec: Flow<Int> = context.muraiDataStore.data.map { it[K.slideshowIntervalSec] ?: 5 }
    val wallpaperAlbums: Flow<Set<String>> = context.muraiDataStore.data.map { p ->
        p[K.wallpaperAlbums]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    val wallpaperIntervalHours: Flow<Int> = context.muraiDataStore.data.map { it[K.wallpaperIntervalHours] ?: 24 }
    val wallpaperEnabled: Flow<Boolean> = context.muraiDataStore.data.map { it[K.wallpaperEnabled] ?: false }
    val wallpaperBoth: Flow<Boolean> = context.muraiDataStore.data.map { it[K.wallpaperBoth] ?: true }
    val telegramToken: Flow<String> = context.muraiDataStore.data.map { it[K.tgToken] ?: "" }
    val telegramChat: Flow<String> = context.muraiDataStore.data.map { it[K.tgChat] ?: "" }
    val vaultPinHash: Flow<String> = context.muraiDataStore.data.map { it[K.vaultPinHash] ?: "" }
    val vaultSalt: Flow<String> = context.muraiDataStore.data.map { it[K.vaultSalt] ?: "" }
    val vaultDecoyHash: Flow<String> = context.muraiDataStore.data.map { it[K.vaultDecoyHash] ?: "" }
    val vaultBiometric: Flow<Boolean> = context.muraiDataStore.data.map { it[K.vaultBiometric] ?: false }
    val vaultHideRecents: Flow<Boolean> = context.muraiDataStore.data.map { it[K.vaultHideRecents] ?: true }
    val updateIgnored: Flow<String> = context.muraiDataStore.data.map { it[K.updateIgnored] ?: "" }

    suspend fun setThemeMode(mode: ThemeMode) = context.muraiDataStore.edit { it[K.themeMode] = mode.name }
    suspend fun setAmoled(v: Boolean) = context.muraiDataStore.edit { it[K.amoled] = v }
    suspend fun setDynamicColor(v: Boolean) = context.muraiDataStore.edit { it[K.dynamicColor] = v }
    suspend fun setLanguageTag(tag: String) = context.muraiDataStore.edit { it[K.languageTag] = tag }
    suspend fun setGridSize(n: Int) = context.muraiDataStore.edit { it[K.gridSize] = n.coerceIn(2, 6) }
    suspend fun setSlideshowInterval(sec: Int) = context.muraiDataStore.edit { it[K.slideshowIntervalSec] = sec.coerceIn(2, 60) }
    suspend fun setWallpaperAlbums(ids: Set<String>) = context.muraiDataStore.edit { it[K.wallpaperAlbums] = ids.joinToString(",") }
    suspend fun setWallpaperInterval(hours: Int) = context.muraiDataStore.edit { it[K.wallpaperIntervalHours] = hours.coerceIn(1, 168) }
    suspend fun setWallpaperEnabled(v: Boolean) = context.muraiDataStore.edit { it[K.wallpaperEnabled] = v }
    suspend fun setWallpaperBoth(v: Boolean) = context.muraiDataStore.edit { it[K.wallpaperBoth] = v }
    suspend fun setTelegram(token: String, chat: String) = context.muraiDataStore.edit {
        it[K.tgToken] = token.trim(); it[K.tgChat] = chat.trim()
    }
    suspend fun setVaultPinHash(hash: String, salt: String) = context.muraiDataStore.edit {
        it[K.vaultPinHash] = hash; it[K.vaultSalt] = salt
    }
    suspend fun setVaultDecoyHash(hash: String) = context.muraiDataStore.edit { it[K.vaultDecoyHash] = hash }
    suspend fun setVaultBiometric(v: Boolean) = context.muraiDataStore.edit { it[K.vaultBiometric] = v }
    suspend fun setVaultHideRecents(v: Boolean) = context.muraiDataStore.edit { it[K.vaultHideRecents] = v }
    suspend fun setUpdateIgnored(v: String) = context.muraiDataStore.edit { it[K.updateIgnored] = v }

    fun sortSpec(screen: SortScreen): Flow<SortSpec> = context.muraiDataStore.data.map { p ->
        SortSpec(
            option = SortOption.fromId(p[SortKeys.option(screen)]) ?: SortOption.DATE_TAKEN,
            direction = when (p[SortKeys.dir(screen)]) {
                "ASC" -> SortDirection.ASCENDING
                else -> SortDirection.DESCENDING
            },
            group = GroupMode.fromId(p[SortKeys.group(screen)]) ?: GroupMode.NONE
        )
    }

    suspend fun setSortSpec(screen: SortScreen, spec: SortSpec) = context.muraiDataStore.edit {
        it[SortKeys.option(screen)] = spec.option.id
        it[SortKeys.dir(screen)] =
            if (spec.direction == SortDirection.ASCENDING) "ASC" else "DESC"
        it[SortKeys.group(screen)] = spec.group.id
    }

    /** Serializable snapshot used by the backup bundle. */
    suspend fun exportAll(): Map<String, String> {
        val prefs = context.muraiDataStore.data.first()
        val out = mutableMapOf<String, String>()
        for (key in prefs.asMap().keys) {
            val v = when (val any = prefs[key]) {
                is Boolean -> any.toString()
                is Int -> any.toString()
                is Long -> any.toString()
                is String -> any
                else -> continue
            }
            out[key.name] = v
        }
        return out
    }

    suspend fun importAll(values: Map<String, String>) {
        val boolNames = setOf(
            K.amoled.name, K.dynamicColor.name, K.wallpaperEnabled.name,
            K.wallpaperBoth.name, K.vaultBiometric.name, K.vaultHideRecents.name,
            K.onboardingDone.name
        )
        val intNames = setOf(
            K.gridSize.name, K.slideshowIntervalSec.name, K.wallpaperIntervalHours.name
        )
        val sortOptionNames = SortScreen.entries.map { "sort_option_${it.name}" }.toSet()
        val sortDirNames = SortScreen.entries.map { "sort_dir_${it.name}" }.toSet()
        val sortGroupNames = SortScreen.entries.map { "sort_group_${it.name}" }.toSet()
        context.muraiDataStore.edit { prefs ->
            for ((name, value) in values) {
                when {
                    name in boolNames ->
                        prefs[booleanPreferencesKey(name)] = value.toBooleanStrictOrNull() ?: false
                    name in intNames ->
                        prefs[intPreferencesKey(name)] = value.toIntOrNull() ?: 0
                    name in sortOptionNames || name in sortDirNames ||
                        name in sortGroupNames || name == K.themeMode.name ||
                        name == K.languageTag.name || name == K.wallpaperAlbums.name ||
                        name == K.tgToken.name || name == K.tgChat.name ||
                        name == K.vaultPinHash.name || name == K.vaultSalt.name ||
                        name == K.vaultDecoyHash.name || name == K.updateIgnored.name ->
                        prefs[stringPreferencesKey(name)] = value
                }
            }
        }
    }
}
