import 'dart:convert';

import 'package:aves/model/settings/settings.dart';

/// Murai Gallery tool preferences, persisted through the Aves settings store
/// (SharedPreferences-backed, included in settings export/import).
class MuraiPrefs {
  // sort presets
  static const sortPresetsKey = 'murai_sort_presets';

  static List<Map<String, Object?>> getSortPresets() {
    final raw = settings.getString(sortPresetsKey);
    if (raw == null || raw.isEmpty) return [];
    try {
      final list = jsonDecode(raw) as List;
      return list.whereType<Map>().map((v) => v.map((k, value) => MapEntry(k.toString(), value))).toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> saveSortPreset({required String name, required Map<String, Object?> preset}) async {
    final presets = getSortPresets();
    presets.removeWhere((v) => v['name'] == name);
    presets.add({...preset, 'name': name});
    settings.set(sortPresetsKey, jsonEncode(presets));
  }

  static Future<void> deleteSortPreset(String name) async {
    final presets = getSortPresets();
    presets.removeWhere((v) => v['name'] == name);
    settings.set(sortPresetsKey, jsonEncode(presets));
  }

  // wallpaper changer
  static const wallpaperAlbumsKey = 'murai_wallpaper_albums';
  static const wallpaperShuffleKey = 'murai_wallpaper_shuffle';
  static const wallpaperIntervalKey = 'murai_wallpaper_interval';

  static List<String> getWallpaperAlbums() => (settings.getString(wallpaperAlbumsKey) ?? '').split('\n').where((v) => v.isNotEmpty).toList();

  static Future<void> setWallpaperAlbums(List<String> albums) async => settings.set(wallpaperAlbumsKey, albums.join('\n'));

  static bool getWallpaperShuffle() => settings.getBool(wallpaperShuffleKey) ?? true;

  static Future<void> setWallpaperShuffle(bool v) async => settings.set(wallpaperShuffleKey, v);

  /// interval in minutes (0 = off)
  static int getWallpaperInterval() => settings.getInt(wallpaperIntervalKey) ?? 0;

  static Future<void> setWallpaperInterval(int minutes) async => settings.set(wallpaperIntervalKey, minutes);

  // update check
  static const updateCheckEnabledKey = 'murai_update_check_enabled';
  static const lastUpdateCheckKey = 'murai_last_update_check';

  static bool getUpdateCheckEnabled() => settings.getBool(updateCheckEnabledKey) ?? true;

  static Future<void> setUpdateCheckEnabled(bool v) async => settings.set(updateCheckEnabledKey, v);

  static int getLastUpdateCheck() => settings.getInt(lastUpdateCheckKey) ?? 0;

  static Future<void> setLastUpdateCheck(int epochMillis) async => settings.set(lastUpdateCheckKey, epochMillis);

  // home tools strip
  static const homeToolsDismissedKey = 'murai_home_tools_dismissed';

  static bool getHomeToolsDismissed() => settings.getBool(homeToolsDismissedKey) ?? false;

  static Future<void> setHomeToolsDismissed(bool v) async => settings.set(homeToolsDismissedKey, v);

  // vault decoy
  static const vaultDecoyEnabledKey = 'murai_vault_decoy_enabled';

  static bool getVaultDecoyEnabled() => settings.getBool(vaultDecoyEnabledKey) ?? false;

  static Future<void> setVaultDecoyEnabled(bool v) async => settings.set(vaultDecoyEnabledKey, v);
}
