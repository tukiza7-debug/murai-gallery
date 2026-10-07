import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/tools/murai_save.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:provider/provider.dart';

/// Murai Gallery Compressor & Resizer: batch-reduce quality/resolution of
/// photos. Encoding runs in background isolates with a progress notification,
/// and results are saved as copies with before/after sizes.
class MuraiCompressorPage extends StatefulWidget {
  static const routeName = '/murai-compressor';

  const new({super.key});

  @override
  State<MuraiCompressorPage> createState() => _MuraiCompressorPageState();
}

class CompressResultInfo {
  final AvesEntry entry;
  final int beforeBytes;
  final int afterBytes;

  new({required this.entry, required this.beforeBytes, required this.afterBytes});

  double get savedRatio => beforeBytes <= 0 ? 0 : 1 - afterBytes / beforeBytes;
}

class _MuraiCompressorPageState extends State<MuraiCompressorPage> {
  final Set<AvesEntry> _selected = {};
  double _quality = 80;
  int _maxDimension = 0; // 0 = keep original
  bool _running = false;
  double _progress = 0;
  final List<CompressResultInfo> _results = [];
  static const _notificationId = 902;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final source = context.read<CollectionSource>();
      setState(() {
        _selected.addAll(source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).take(24));
      });
    });
  }

  Future<void> _run() async {
    if (_running || _selected.isEmpty) return;
    final l10n = context.l10n;
    setState(() {
      _running = true;
      _progress = 0;
      _results.clear();
    });
    final entries = _selected.toList();
    unawaited(MuraiChannel.notifyProgress(
      id: _notificationId,
      title: l10n.muraiCompressTitle,
      text: l10n.muraiCompressProgressText(0, entries.length),
      progress: 0,
      cancellable: false,
    ));
    try {
      for (var i = 0; i < entries.length; i++) {
        final entry = entries[i];
        try {
          final originalBytes = await File(entry.path!).readAsBytes();
          final encoded = await _encode(originalBytes, _quality.round(), _maxDimension);
          final saved = await MuraiSave.saveCopy(
            context,
            bytes: encoded,
            baseName: '${entry.fileNameWithoutExtension}_compressed',
            mimeType: 'image/jpeg',
          );
          if (saved != null) {
            _results.add(CompressResultInfo(entry: entry, beforeBytes: originalBytes.length, afterBytes: encoded.length));
          }
        } catch (e) {
          debugPrint('compress failed for ${entry.uri}: $e');
        }
        if (mounted) setState(() => _progress = (i + 1) / entries.length);
        unawaited(MuraiChannel.notifyProgress(
          id: _notificationId,
          title: l10n.muraiCompressTitle,
          text: l10n.muraiCompressProgressText(i + 1, entries.length),
          progress: ((i + 1) / entries.length * 100).round(),
        ));
      }
      await MuraiChannel.notifyFinished(
        id: _notificationId,
        title: l10n.muraiCompressTitle,
        text: l10n.muraiCompressDoneText(_results.length),
      );
    } finally {
      if (mounted) setState(() => _running = false);
    }
  }

  static Future<Uint8List> _encode(Uint8List original, int quality, int maxDimension) {
    return Isolate.run(() {
      final decoded = img.decodeImage(original);
      if (decoded == null) throw Exception('decode failed');
      var image = decoded;
      if (maxDimension > 0 && (image.width > maxDimension || image.height > maxDimension)) {
        image = img.copyResize(image, width: image.width >= image.height ? maxDimension : null, height: image.height > image.width ? maxDimension : null);
      }
      return Uint8List.fromList(img.encodeJpg(image, quality: quality));
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final imageEntries = source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).toList();
    final totalSaved = _results.fold<int>(0, (sum, r) => sum + (r.beforeBytes - r.afterBytes).clamp(0, 1 << 40));

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiCompressTitle)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Column(
              children: [
                Row(
                  children: [
                    Text(l10n.muraiCompressQuality(_quality.round())),
                    Expanded(
                      child: Slider(
                        value: _quality,
                        min: 30,
                        max: 100,
                        divisions: 14,
                        label: '${_quality.round()}%',
                        onChanged: _running ? null : (v) => setState(() => _quality = v),
                      ),
                    ),
                  ],
                ),
                Wrap(
                  spacing: 8,
                  children: [
                    ChoiceChip(label: Text(l10n.muraiCompressKeepSize), selected: _maxDimension == 0, onSelected: (_) => setState(() => _maxDimension = 0)),
                    ChoiceChip(label: const Text('1080p'), selected: _maxDimension == 1920, onSelected: (_) => setState(() => _maxDimension = 1920)),
                    ChoiceChip(label: const Text('720p'), selected: _maxDimension == 1280, onSelected: (_) => setState(() => _maxDimension = 1280)),
                    ChoiceChip(label: const Text('480p'), selected: _maxDimension == 854, onSelected: (_) => setState(() => _maxDimension = 854)),
                  ],
                ),
              ],
            ),
          ),
          if (_running) LinearProgressIndicator(value: _progress),
          Expanded(
            child: _results.isEmpty ? _buildSelectionGrid(imageEntries) : _buildResults(),
          ),
          if (_results.isNotEmpty)
            Padding(
              padding: const EdgeInsets.all(8),
              child: Text(l10n.muraiCompressTotalSaved(_formatSize(totalSaved)), style: Theme.of(context).textTheme.titleSmall),
            ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: FilledButton.icon(
            icon: const Icon(Icons.compress),
            label: Text(l10n.muraiCompressStart(_selected.length)),
            onPressed: _running || _selected.isEmpty ? null : _run,
          ),
        ),
      ),
    );
  }

  Widget _buildSelectionGrid(List<AvesEntry> imageEntries) {
    return GridView.builder(
      padding: const EdgeInsets.all(8),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 120, mainAxisSpacing: 4, crossAxisSpacing: 4),
      itemCount: imageEntries.length,
      itemBuilder: (context, i) {
        final entry = imageEntries[i];
        final selected = _selected.contains(entry);
        return GestureDetector(
          onTap: () => setState(() {
            selected ? _selected.remove(entry) : _selected.add(entry);
          }),
          child: Stack(
            fit: StackFit.expand,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: ThumbnailImage(entry: entry, extent: 120, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
              ),
              PositionedDirectional(
                top: 4,
                end: 4,
                child: Icon(
                  selected ? Icons.check_circle : Icons.radio_button_unchecked,
                  color: selected ? Theme.of(context).colorScheme.primary : Colors.white,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildResults() {
    return ListView.builder(
      padding: const EdgeInsets.all(8),
      itemCount: _results.length,
      itemBuilder: (context, i) {
        final result = _results[i];
        return ListTile(
          leading: SizedBox(
            width: 48,
            height: 48,
            child: ThumbnailImage(entry: result.entry, extent: 48, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
          ),
          title: Text(result.entry.bestTitle ?? '', maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text('${_formatSize(result.beforeBytes)} → ${_formatSize(result.afterBytes)}'),
          trailing: Text('${(result.savedRatio * 100).round()}%', style: TextStyle(color: Theme.of(context).colorScheme.primary, fontWeight: .bold)),
        );
      },
    );
  }

  static String _formatSize(int bytes) {
    if (bytes > 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(bytes / 1024).round()} KB';
  }
}
