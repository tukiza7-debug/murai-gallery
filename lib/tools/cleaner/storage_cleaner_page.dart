import 'dart:collection';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

/// Murai Gallery Storage Cleaner: usage by folder/type, large files and
/// screenshots, with one-tap cleanup behind explicit confirmations.
class MuraiStorageCleanerPage extends StatefulWidget {
  static const routeName = '/murai-cleaner';

  const new({super.key});

  @override
  State<MuraiStorageCleanerPage> createState() => _MuraiStorageCleanerPageState();
}

class _MuraiStorageCleanerPageState extends State<MuraiStorageCleanerPage> {
  bool _loading = true;
  int _freeBytes = 0;
  final Map<String, int> _bytesByFolder = {};
  int _imagesBytes = 0, _videosBytes = 0;
  List<AvesEntry> _largeFiles = [];
  List<AvesEntry> _screenshots = [];
  List<AvesEntry> _oldDownloads = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _analyze());
  }

  Future<void> _analyze() async {
    final source = context.read<CollectionSource>();
    final entries = source.visibleEntries.where((e) => !e.trashed).toList();

    final bytesByFolder = SplayTreeMap<String, int>();
    var imagesBytes = 0;
    var videosBytes = 0;
    for (final entry in entries) {
      final size = entry.sizeBytes ?? 0;
      final folder = entry.directory ?? '';
      bytesByFolder[folder] = (bytesByFolder[folder] ?? 0) + size;
      if (entry.mimeType.startsWith('video/')) {
        videosBytes += size;
      } else {
        imagesBytes += size;
      }
    }

    final largeFiles = entries.where((e) => (e.sizeBytes ?? 0) > 50 * 1024 * 1024).toList()
      ..sort((a, b) => (b.sizeBytes ?? 0).compareTo(a.sizeBytes ?? 0));
    final screenshots = entries.where((e) => e.mimeType.startsWith('image/') && (e.directory ?? '').toLowerCase().contains('screenshot')).toList();
    final monthAgo = DateTime.now().subtract(const Duration(days: 30));
    final oldDownloads = entries.where((e) {
      final dir = (e.directory ?? '').toLowerCase();
      final date = e.bestDate;
      return dir.contains('download') && date != null && date.isBefore(monthAgo);
    }).toList();

    int freeBytes = 0;
    try {
      freeBytes = await MuraiChannel.getFreeStorageBytes();
    } catch (_) {}

    if (mounted) {
      setState(() {
        _loading = false;
        _freeBytes = freeBytes;
        _bytesByFolder
          ..clear()
          ..addAll(Map.fromEntries(bytesByFolder.entries.toList()..sort((a, b) => b.value.compareTo(a.value))));
        _imagesBytes = imagesBytes;
        _videosBytes = videosBytes;
        _largeFiles = largeFiles.take(30).toList();
        _screenshots = screenshots;
        _oldDownloads = oldDownloads;
      });
    }
  }

  Future<void> _cleanup(List<AvesEntry> entries, String title) async {
    if (entries.isEmpty) return;
    final l10n = context.l10n;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(l10n.muraiCleanDeleteConfirm(entries.length, _formatSize(entries.fold<int>(0, (sum, e) => sum + (e.sizeBytes ?? 0))))),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancelTooltip)),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.deleteButtonLabel),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await mediaEditService.delete(opId: mediaEditService.newOpId, entries: entries).drain();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiCleanDone)));
      setState(() => _loading = true);
      await _analyze();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final totalUsed = _imagesBytes + _videosBytes;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.muraiCleanTitle),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: _analyze)],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(12),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(l10n.muraiCleanFreeSpace(_formatSize(_freeBytes)), style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(height: 8),
                        if (totalUsed > 0) ...[
                          ClipRRect(
                            borderRadius: BorderRadius.circular(6),
                            child: LinearProgressIndicator(
                              value: _imagesBytes / totalUsed,
                              minHeight: 12,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(l10n.muraiCleanBreakdown(_formatSize(_imagesBytes), _formatSize(_videosBytes))),
                        ],
                      ],
                    ),
                  ),
                ),
                _buildSection(
                  title: l10n.muraiCleanScreenshots(_screenshots.length),
                  subtitle: _formatSize(_screenshots.fold<int>(0, (sum, e) => sum + (e.sizeBytes ?? 0))),
                  entries: _screenshots,
                  onDelete: () => _cleanup(_screenshots, l10n.muraiCleanScreenshots(_screenshots.length)),
                  tileL10n: l10n,
                ),
                _buildSection(
                  title: l10n.muraiCleanLargeFiles(_largeFiles.length),
                  subtitle: l10n.muraiCleanLargeHint,
                  entries: _largeFiles,
                  onDelete: () => _cleanup(_largeFiles.take(10).toList(), l10n.muraiCleanLargeFiles(10)),
                  tileL10n: l10n,
                  showSize: true,
                ),
                _buildSection(
                  title: l10n.muraiCleanOldDownloads(_oldDownloads.length),
                  subtitle: l10n.muraiCleanOldHint,
                  entries: _oldDownloads,
                  onDelete: () => _cleanup(_oldDownloads, l10n.muraiCleanOldDownloads(_oldDownloads.length)),
                  tileL10n: l10n,
                ),
                const SizedBox(height: 8),
                Text(l10n.muraiCleanByFolder, style: Theme.of(context).textTheme.titleSmall),
                ..._bytesByFolder.entries.take(12).map((e) => ListTile(
                      dense: true,
                      leading: const Icon(Icons.folder_outlined),
                      title: Text(e.key.isEmpty ? '?' : e.key, maxLines: 1, overflow: TextOverflow.ellipsis),
                      trailing: Text(_formatSize(e.value)),
                    )),
              ],
            ),
    );
  }

  Widget _buildSection({
    required String title,
    required String subtitle,
    required List<AvesEntry> entries,
    required VoidCallback onDelete,
    required dynamic tileL10n,
    bool showSize = false,
  }) {
    if (entries.isEmpty) return const SizedBox();
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        children: [
          ListTile(
            title: Text(title),
            subtitle: Text(subtitle),
            trailing: FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
              onPressed: onDelete,
              child: Text(context.l10n.deleteButtonLabel),
            ),
          ),
          SizedBox(
            height: 84,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              scrollDirection: Axis.horizontal,
              itemCount: entries.length.clamp(0, 24),
              separatorBuilder: (context, i) => const SizedBox(width: 4),
              itemBuilder: (context, i) {
                final entry = entries[i];
                return Column(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: SizedBox(
                        width: 60,
                        height: 60,
                        child: ThumbnailImage(entry: entry, extent: 60, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
                      ),
                    ),
                    if (showSize)
                      Text(_formatSize(entry.sizeBytes ?? 0), style: const TextStyle(fontSize: 9)),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  static String _formatSize(int bytes) {
    if (bytes > 1024 * 1024 * 1024) return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(1)} GB';
    if (bytes > 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(bytes / 1024).round()} KB';
  }
}
