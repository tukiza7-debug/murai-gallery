import 'dart:async';
import 'dart:typed_data';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/images.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

/// Murai Gallery Video Trimmer: choose start/end on the timeline and save
/// the trimmed clip as a new file (MP4 remux, no re-encode quality loss).
class MuraiVideoTrimmerPage extends StatefulWidget {
  static const routeName = '/murai-trimmer';

  const new({super.key});

  @override
  State<MuraiVideoTrimmerPage> createState() => _MuraiVideoTrimmerPageState();
}

class _MuraiVideoTrimmerPageState extends State<MuraiVideoTrimmerPage> {
  AvesEntry? _entry;
  List<Uint8List> _thumbs = [];
  double _durationSecs = 0;
  RangeValues _range = const RangeValues(0, 1);
  bool _working = false;

  static const _notificationId = 903;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final source = context.read<CollectionSource>();
      final videos = source.visibleEntries.where((e) => e.mimeType.startsWith('video/') && e.path != null && (e.durationMillis ?? 0) > 0).toList();
      if (videos.isNotEmpty) {
        _select(videos.first);
      }
    });
  }

  Future<void> _select(AvesEntry entry) async {
    setState(() {
      _entry = entry;
      _thumbs = [];
      _durationSecs = (entry.durationMillis ?? 0) / 1000;
      _range = const RangeValues(0, 1);
    });
    if (_durationSecs <= 0) return;
    try {
      const count = 8;
      final stamps = List.generate(count, (i) => _durationSecs * (i + .5) / count);
      final frames = await MuraiChannel.getVideoFrames(path: entry.path!, timestampsSecs: stamps, maxWidth: 160);
      if (mounted) {
        setState(() {
          _thumbs = frames;
          _range = RangeValues(0, _durationSecs);
        });
      }
    } catch (e) {
      debugPrint('frame extraction failed: $e');
    }
  }

  Future<void> _trim() async {
    final entry = _entry;
    if (entry == null || _working) return;
    final l10n = context.l10n;
    final startMs = (_range.start * 1000).round();
    final endMs = (_range.end * 1000).round();
    if (endMs - startMs < 100) return;

    setState(() => _working = true);
    unawaited(MuraiChannel.notifyProgress(
      id: _notificationId,
      title: l10n.muraiTrimTitle,
      text: l10n.muraiTrimWorking,
      progress: 0,
      indeterminate: true,
    ));
    try {
      final name = '${entry.fileNameWithoutExtension}_trimmed_${startMs}ms.mp4';
      final outPath = await MuraiChannel.trimVideo(path: entry.path!, startMs: startMs, endMs: endMs, destName: name);
      if (outPath.isNotEmpty) {
        await mediaStoreService.scanFile(outPath, 'video/mp4');
        unawaited(MuraiChannel.notifyFinished(id: _notificationId, title: l10n.muraiTrimTitle, text: l10n.muraiTrimDone));
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiTrimDone)));
        }
      }
    } catch (e) {
      debugPrint('trim failed: $e');
      await MuraiChannel.cancelNotification(id: _notificationId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiTrimFailed)));
      }
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  String _fmt(double secs) {
    final m = secs ~/ 60;
    final s = (secs % 60).toStringAsFixed(1).padLeft(4, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final videos = source.visibleEntries.where((e) => e.mimeType.startsWith('video/') && e.path != null && (e.durationMillis ?? 0) > 0).toList();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiTrimTitle)),
      body: _entry == null
          ? Center(child: Text(l10n.muraiTrimNoVideos, textAlign: TextAlign.center))
          : Column(
              children: [
                SizedBox(
                  height: 96,
                  child: ListView.separated(
                    padding: const EdgeInsets.all(8),
                    scrollDirection: Axis.horizontal,
                    itemCount: videos.length,
                    separatorBuilder: (context, i) => const SizedBox(width: 4),
                    itemBuilder: (context, i) {
                      final entry = videos[i];
                      return GestureDetector(
                        onTap: () => _select(entry),
                        child: Container(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: _entry?.uri == entry.uri ? Theme.of(context).colorScheme.primary : Colors.transparent,
                              width: 3,
                            ),
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image(
                              image: entry.getThumbnail(extent: 80),
                              fit: .cover,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text('${_entry!.bestTitle ?? ''} • ${_fmt(_durationSecs)}', style: Theme.of(context).textTheme.titleSmall),
                ),
                if (_thumbs.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: SizedBox(
                        height: 72,
                        child: Row(
                          children: _thumbs
                              .map((bytes) => Expanded(child: Image.memory(bytes, fit: .cover, gaplessPlayback: true)))
                              .toList(),
                        ),
                      ),
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: RangeSlider(
                    values: _range,
                    max: _durationSecs <= 0 ? 1 : _durationSecs,
                    divisions: _durationSecs <= 0 ? null : _durationSecs.floor(),
                    labels: RangeLabels(_fmt(_range.start), _fmt(_range.end)),
                    onChanged: (v) => setState(() => _range = v),
                  ),
                ),
                Text(l10n.muraiTrimRangeText(_fmt(_range.start), _fmt(_range.end), _fmt(_range.end - _range.start))),
                const Spacer(),
              ],
            ),
      bottomNavigationBar: _entry == null
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: FilledButton.icon(
                  icon: _working ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.content_cut),
                  label: Text(l10n.muraiTrimSave),
                  onPressed: _working ? null : _trim,
                ),
              ),
            ),
    );
  }
}
