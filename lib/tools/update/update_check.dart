import 'dart:convert';

import 'package:aves/model/device.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/widgets/aves_app.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';

/// Murai Gallery in-app update check against GitHub Releases.
class MuraiUpdater {
  new _private();

  static const repoApi = 'https://api.github.com/repos/tukiza7-debug/murai-gallery/releases/latest';
  static const repoWeb = 'https://github.com/tukiza7-debug/murai-gallery/releases/latest';

  /// Periodic (once a day) startup check; silent when offline or unreachable.
  static Future<void> checkOnStartup(BuildContext context) async {
    if (!MuraiPrefs.getUpdateCheckEnabled()) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - MuraiPrefs.getLastUpdateCheck() < const Duration(days: 1).inMilliseconds) return;
    await MuraiPrefs.setLastUpdateCheck(now);
    final latest = await _fetchLatestVersion();
    if (latest == null) return; // offline / GitHub unreachable: stay silent on startup
    final current = (await PackageInfo.fromPlatform()).version;
    if (_isNewer(latest, current) && context.mounted) {
      _showUpdateDialog(context, current: current, latest: latest);
    }
  }

  /// Manual "check now" from settings; reports offline state.
  static Future<void> checkNow(BuildContext context) async {
    final l10n = context.l10n;
    final latest = await _fetchLatestVersion();
    if (!context.mounted) return;
    if (latest == null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiUpdateOffline)));
      return;
    }
    final current = device.packageVersion;
    if (_isNewer(latest, current)) {
      _showUpdateDialog(context, current: current, latest: latest);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiUpdateUpToDate)));
    }
  }

  static Future<String?> _fetchLatestVersion() async {
    try {
      final res = await http.get(
        Uri.parse(repoApi),
        headers: const {'Accept': 'application/vnd.github+json'},
      ).timeout(const Duration(seconds: 12));
      if (res.statusCode != 200) return null;
      final tag = jsonDecode(res.body)['tag_name'];
      return tag?.toString().replaceFirst(RegExp(r'^v'), '');
    } catch (_) {
      return null;
    }
  }

  static bool _isNewer(String latest, String current) {
    List<int> parse(String v) => v.split('.').map((e) => int.tryParse(e) ?? 0).toList();
    final a = parse(latest);
    final b = parse(current);
    for (var i = 0; i < a.length && i < b.length; i++) {
      if (a[i] != b[i]) return a[i] > b[i];
    }
    return a.length > b.length;
  }

  static void _showUpdateDialog(BuildContext context, {required String current, required String latest}) {
    final l10n = context.l10n;
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.muraiUpdateAvailableTitle),
        content: Text(l10n.muraiUpdateAvailableText(current, latest)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancelTooltip)),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              AvesApp.launchUrl(repoWeb);
            },
            child: Text(l10n.muraiUpdateDownload),
          ),
        ],
      ),
    );
  }
}
