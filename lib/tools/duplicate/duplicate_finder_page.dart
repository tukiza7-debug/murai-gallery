import 'dart:async';
import 'dart:collection';
import 'dart:io';
import 'dart:isolate';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:provider/provider.dart';

/// Murai Gallery Duplicate Finder.
/// Exact matches via MD5, similar photos via perceptual (difference) hash.
/// Hashing runs in background isolates; progress surfaces as a notification.
class MuraiDuplicateFinderPage extends StatefulWidget {
  static const routeName = '/murai-duplicates';

  const new({super.key});

  @override
  State<MuraiDuplicateFinderPage> createState() => _MuraiDuplicateFinderPageState();
}

class DuplicateGroupInfo {
  final String hash;
  final Set<AvesEntry> entries;
  final bool exact;

  new({required this.hash, required this.entries, required this.exact});
}

class _MuraiDuplicateFinderPageState extends State<MuraiDuplicateFinderPage> {
  bool _scanning = false;
  double _progress = 0;
  String _stage = '';
  List<DuplicateGroupInfo> _exactGroups = [];
  List<DuplicateGroupInfo> _similarGroups = [];
  final Set<String> _selectedForDeletion = {};
  static const _notificationId = 901;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _scan());
  }

  Set<AvesEntry> get _entries {
    final source = context.read<CollectionSource>();
    return source.visibleEntries.where((e) => !e.trashed).toSet();
  }

  Future<void> _scan() async {
    if (_scanning) return;
    final l10n = context.l10n;
    setState(() {
      _scanning = true;
      _progress = 0;
      _stage = l10n.muraiDupStageCollect;
      _exactGroups = [];
      _similarGroups = [];
      _selectedForDeletion.clear();
    });

    try {
      final entries = _entries.toList();
      if (entries.isEmpty) return;

      // stage 1: group by size (cheap pre-filter for exact duplicates)
      final bySize = <int, List<AvesEntry>>{};
      for (final entry in entries) {
        final size = entry.sizeBytes;
        if (size == null || size == 0) continue;
        (bySize[size] ??= []).add(entry);
      }
      final candidates = bySize.values.where((list) => list.length > 1).expand((v) => v).toList();

      // stage 2: MD5 the candidates in a background isolate
      setState(() => _stage = l10n.muraiDupStageHash);
      final hashes = await _hashEntries(candidates);

      final exactMap = SplayTreeMap<String, Set<AvesEntry>>();
      for (final entry in candidates) {
        final digest = hashes[entry.uri];
        if (digest != null) {
          (exactMap[digest] ??= {}).add(entry);
        }
      }
      _exactGroups = exactMap.entries.where((v) => v.value.length > 1).map((v) => DuplicateGroupInfo(hash: v.key, entries: v.value, exact: true)).toList();
      if (mounted) setState(() => _progress = .75);

      // stage 3: perceptual hash for similar images (excluding exact pairs)
      setState(() => _stage = l10n.muraiDupStageSimilar);
      final imageEntries = entries.where((e) => e.mimeType.startsWith('image/') && e.width > 0).toList();
      final pHashes = await _perceptualHashEntries(imageEntries);
      final uris = pHashes.keys.toList();
      final similarMap = <int, Set<AvesEntry>>{};
      final uriByHash = <String, AvesEntry>{for (final e in imageEntries) e.uri: e};
      for (var a = 0; a < uris.length; a++) {
        final ha = pHashes[uris[a]]!;
        for (var b = a + 1; b < uris.length; b++) {
          final hb = pHashes[uris[b]]!;
          if (_hamming64(ha, hb) <= 4) {
            final entryA = uriByHash[uris[a]]!;
            final entryB = uriByHash[uris[b]]!;
            final isExactPair = _exactGroups.any((g) => g.entries.contains(entryA) && g.entries.contains(entryB));
            if (isExactPair) continue;
            (similarMap[ha] ??= {})
              ..add(entryA)
              ..add(entryB);
          }
        }
        if (a % 50 == 0 && mounted) setState(() => _progress = .75 + a / uris.length * .25);
      }
      _similarGroups = similarMap.entries.where((v) => v.value.length > 1).map((v) => DuplicateGroupInfo(hash: v.key.toString(), entries: v.value, exact: false)).toList();

      unawaited(MuraiChannel.notifyFinished(
        id: _notificationId,
        title: l10n.muraiDupTitle,
        text: l10n.muraiDupDoneText(_exactGroups.length + _similarGroups.length),
      ));
    } catch (e) {
      debugPrint('duplicate scan failed: $e');
    } finally {
      if (mounted) setState(() => _scanning = false);
    }
  }

  static Future<Map<String, String>> _hashEntries(List<AvesEntry> entries) {
    final plain = entries.map((e) => {'uri': e.uri, 'path': e.path ?? ''}).toList();
    return Isolate.run(() async {
      final result = <String, String>{};
      for (final item in plain) {
        final path = item['path'] as String;
        try {
          final file = File(path);
          if (await file.exists()) {
            final digest = await md5.bind(file.openRead()).first;
            result[item['uri'] as String] = digest.toString();
          }
        } catch (_) {
          // unreadable file: skip
        }
      }
      return result;
    });
  }

  /// 64-bit difference hash computed on a downscaled grayscale copy.
  static Future<Map<String, int>> _perceptualHashEntries(List<AvesEntry> entries) {
    final plain = entries.map((e) => {'uri': e.uri, 'path': e.path ?? ''}).toList();
    return Isolate.run(() async {
      final result = <String, int>{};
      for (final item in plain) {
        try {
          final file = File(item['path'] as String);
          if (!await file.exists() || await file.length() > 30 * 1024 * 1024) continue;
          final bytes = await file.readAsBytes();
          final decoded = img.decodeImage(bytes);
          if (decoded == null) continue;
          final small = img.copyResize(decoded, width: 9, height: 8);
          var bits = 0;
          for (var y = 0; y < 8; y++) {
            for (var x = 0; x < 8; x++) {
              final l1 = img.getLuminance(small.getPixel(x, y));
              final l2 = img.getLuminance(small.getPixel(x + 1, y));
              bits = (bits << 1) | (l1 > l2 ? 1 : 0);
            }
          }
          result[item['uri'] as String] = bits;
        } catch (_) {
          // decode failures: skip entry
        }
      }
      return result;
    });
  }

  static int _hamming64(int a, int b) {
    var v = a ^ b;
    var count = 0;
    while (v != 0) {
      v &= v - 1;
      count++;
    }
    return count;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final groups = [..._exactGroups, ..._similarGroups];
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.muraiDupTitle),
        actions: [
          if (!_scanning)
            IconButton(
              tooltip: l10n.muraiDupScanAgain,
              icon: const Icon(Icons.refresh),
              onPressed: _scan,
            ),
        ],
      ),
      body: _scanning
          ? _buildScanning(l10n)
          : groups.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.done_all, size: 64),
                        const SizedBox(height: 16),
                        Text(l10n.muraiDupEmpty, textAlign: TextAlign.center),
                      ],
                    ),
                  ),
                )
              : _buildResults(groups),
      bottomNavigationBar: groups.isEmpty || _scanning ? null : _buildDeleteBar(l10n),
    );
  }

  Widget _buildScanning(dynamic l10n) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(_stage),
          const SizedBox(height: 16),
          SizedBox(
            width: 240,
            child: LinearProgressIndicator(value: _progress <= 0 ? null : _progress),
          ),
        ],
      ),
    );
  }

  Widget _buildResults(List<DuplicateGroupInfo> groups) {
    return ListView.builder(
      padding: const EdgeInsets.all(8),
      itemCount: groups.length,
      itemBuilder: (context, i) {
        final group = groups[i];
        return Card(
          margin: const EdgeInsets.symmetric(vertical: 4),
          child: Padding(
            padding: const EdgeInsets.all(8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(group.exact ? Icons.copy_all : Icons.image_search, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        group.exact ? context.l10n.muraiDupExactGroup : context.l10n.muraiDupSimilarGroup,
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                    ),
                    Text(context.l10n.muraiDupGroupSize(group.entries.length)),
                  ],
                ),
                const SizedBox(height: 8),
                SizedBox(
                  height: 140,
                  child: Row(
                    children: group.entries.map(_buildEntryTile).toList(),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildEntryTile(AvesEntry entry) {
    final selected = _selectedForDeletion.contains(entry.uri);
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() {
          selected ? _selectedForDeletion.remove(entry.uri) : _selectedForDeletion.add(entry.uri);
        }),
        child: Padding(
          padding: const EdgeInsets.all(2),
          child: Stack(
            fit: StackFit.expand,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: ThumbnailImage(entry: entry, extent: 140, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
              ),
              if (selected)
                Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    color: Theme.of(context).colorScheme.errorContainer.withValues(alpha: .6),
                  ),
                  child: const Icon(Icons.delete, color: Colors.white),
                ),
              Positioned(
                bottom: 4,
                left: 4,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                  decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(4)),
                  child: Text(
                    _formatSize(entry.sizeBytes ?? 0),
                    style: const TextStyle(color: Colors.white, fontSize: 10),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _formatSize(int bytes) {
    if (bytes > 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(bytes / 1024).round()} KB';
  }

  Widget _buildDeleteBar(dynamic l10n) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Expanded(
              child: Text(l10n.muraiDupSelectedCount(_selectedForDeletion.length)),
            ),
            FilledButton.icon(
              icon: const Icon(Icons.delete_sweep),
              label: Text(l10n.deleteButtonLabel),
              onPressed: _selectedForDeletion.isEmpty ? null : _deleteSelected,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _deleteSelected() async {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.muraiDupDeleteTitle),
        content: Text(l10n.muraiDupDeleteConfirm(_selectedForDeletion.length)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.deleteButtonLabel)),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    final toDelete = _entries.where((e) => _selectedForDeletion.contains(e.uri)).toSet();
    if (toDelete.isEmpty) return;

    unawaited(MuraiChannel.notifyProgress(
      id: _notificationId,
      title: l10n.muraiDupTitle,
      text: l10n.muraiDupDeletingText(toDelete.length),
      progress: 0,
      indeterminate: true,
    ));
    final opEvents = mediaEditService.delete(opId: mediaEditService.newOpId, entries: toDelete);
    await for (final _ in opEvents) {
      // progress is streamed; the source updates itself through its own subscriptions
    }
    unawaited(MuraiChannel.notifyFinished(id: _notificationId, title: l10n.muraiDupTitle, text: l10n.muraiDupDeletedText(toDelete.length)));
    unawaited(source.removeEntries(toDelete.map((e) => e.uri).toSet(), includeTrash: true));
    if (mounted) {
      setState(_selectedForDeletion.clear);
      unawaited(_scan());
    }
  }
}
