import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:aves/model/favourites.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

/// Murai Gallery Backup & Restore: export settings, favourites, sort presets
/// and tool configuration to a zip; import it back later on any device.
class MuraiBackupPage extends StatefulWidget {
  static const routeName = '/murai-backup';

  const new({super.key});

  @override
  State<MuraiBackupPage> createState() => _MuraiBackupPageState();
}

class _MuraiBackupPageState extends State<MuraiBackupPage> {
  bool _working = false;

  Future<void> _export() async {
    if (_working) return;
    final l10n = context.l10n;
    setState(() => _working = true);
    try {
      final source = context.read<CollectionSource>();
      final export = <String, Object?>{
        'meta': {
          'app': 'Murai Gallery',
          'schema': 1,
          'createdAt': DateTime.now().toIso8601String(),
        },
        'settings': settings.export(),
        'sortPresets': MuraiPrefs.getSortPresets(),
        'muraiPrefs': {
          'wallpaperAlbums': MuraiPrefs.getWallpaperAlbums(),
          'wallpaperShuffle': MuraiPrefs.getWallpaperShuffle(),
          'wallpaperInterval': MuraiPrefs.getWallpaperInterval(),
          'updateCheckEnabled': MuraiPrefs.getUpdateCheckEnabled(),
        },
        'favourites': favourites.export(source) ?? <String, List<String>>{},
      };

      final jsonBytes = utf8.encode(const JsonEncoder.withIndent('  ').convert(export));
      final archive = Archive()
        ..addFile(ArchiveFile('murai_backup.json', jsonBytes.length, jsonBytes));
      final zipBytes = Uint8List.fromList(ZipEncoder().encode(archive));

      final saved = await storageService.createFile(
        basename: 'murai_backup_${DateTime.now().millisecondsSinceEpoch ~/ 1000}.zip',
        mimeType: 'application/zip',
        bytes: zipBytes,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(saved == true ? l10n.muraiBackupSaved : l10n.muraiBackupCancelled)));
      }
    } catch (e) {
      debugPrint('backup export failed: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiBackupFailed)));
      }
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<void> _import() async {
    if (_working) return;
    final l10n = context.l10n;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.muraiBackupImportTitle),
        content: Text(l10n.muraiBackupImportConfirm),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.applyButtonLabel)),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _working = true);
    try {
      final bytes = await storageService.openFile('application/zip');
      final archive = ZipDecoder().decodeBytes(bytes);
      final jsonFile = archive.findFile('murai_backup.json');
      if (jsonFile == null) throw Exception('invalid backup archive');
      final data = jsonDecode(utf8.decode(jsonFile.content as List<int>)) as Map<String, Object?>;

      if (data['settings'] != null) {
        await settings.import(Map<String, Object?>.from(data['settings'] as Map));
      }
      final muraiPrefs = data['muraiPrefs'];
      if (muraiPrefs is Map) {
        final albums = (muraiPrefs['wallpaperAlbums'] as List?)?.cast<String>();
        if (albums != null) await MuraiPrefs.setWallpaperAlbums(albums);
        if (muraiPrefs['wallpaperShuffle'] is bool) await MuraiPrefs.setWallpaperShuffle(muraiPrefs['wallpaperShuffle'] as bool);
        if (muraiPrefs['wallpaperInterval'] is int) await MuraiPrefs.setWallpaperInterval(muraiPrefs['wallpaperInterval'] as int);
        if (muraiPrefs['updateCheckEnabled'] is bool) await MuraiPrefs.setUpdateCheckEnabled(muraiPrefs['updateCheckEnabled'] as bool);
      }
      if (data['sortPresets'] is List) {
        for (final preset in (data['sortPresets'] as List).whereType<Map>()) {
          final name = preset['name']?.toString();
          if (name != null) {
            await MuraiPrefs.saveSortPreset(name: name, preset: Map<String, Object?>.from(preset));
          }
        }
      }
      final favs = data['favourites'];
      if (favs is Map) {
        final source = context.read<CollectionSource>();
        favourites.import(favs, source);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiBackupImported)));
      }
    } catch (e) {
      debugPrint('backup import failed: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiBackupImportFailed)));
      }
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiBackupTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: const Icon(Icons.backup),
              title: Text(l10n.muraiBackupExportTitle),
              subtitle: Text(l10n.muraiBackupExportHint),
              onTap: _working ? null : _export,
            ),
          ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.restore),
              title: Text(l10n.muraiBackupImportTitle),
              subtitle: Text(l10n.muraiBackupImportHint),
              onTap: _working ? null : _import,
            ),
          ),
          if (_working) const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Text(l10n.muraiBackupWhatsIncluded, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 4),
          Text(l10n.muraiBackupIncludedList, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 12),
          Text(l10n.muraiBackupNote, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}
