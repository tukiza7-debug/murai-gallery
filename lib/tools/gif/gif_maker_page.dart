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

/// Murai Gallery GIF Maker: create an animated GIF from a video clip
/// (frame extraction) or from a sequence of images. Encoding runs in an isolate.
class MuraiGifMakerPage extends StatefulWidget {
  static const routeName = '/murai-gif';

  const new({super.key});

  @override
  State<MuraiGifMakerPage> createState() => _MuraiGifMakerPageState();
}

class _MuraiGifMakerPageState extends State<MuraiGifMakerPage> {
  bool _fromVideo = true;
  AvesEntry? _videoEntry;
  double _videoDuration = 0;
  RangeValues _clip = const RangeValues(0, 5);
  int _fps = 10;
  int _width = 480;
  final List<AvesEntry> _pickedImages = [];
  bool _working = false;
  double _progress = 0;
  static const _notificationId = 904;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final source = context.read<CollectionSource>();
      final videos = source.visibleEntries.where((e) => e.mimeType.startsWith('video/') && e.path != null && (e.durationMillis ?? 0) > 1000).toList();
      if (videos.isNotEmpty) {
        setState(() {
          _videoEntry = videos.first;
          _videoDuration = (videos.first.durationMillis ?? 0) / 1000;
          _clip = RangeValues(0, _videoDuration.clamp(0, 5));
        });
      }
    });
  }

  Future<void> _make() async {
    if (_working) return;
    final l10n = context.l10n;
    setState(() {
      _working = true;
      _progress = 0;
    });
    try {
      List<Uint8List> frames = [];
      if (_fromVideo) {
        final entry = _videoEntry;
        if (entry?.path == null) return;
        final duration = (_clip.end - _clip.start).clamp(0.5, 30.0);
        final frameCount = (duration * _fps).round().clamp(2, 120);
        final stamps = List.generate(frameCount, (i) => _clip.start + duration * i / frameCount);
        unawaited(MuraiChannel.notifyProgress(
          id: _notificationId,
          title: l10n.muraiGifTitle,
          text: l10n.muraiGifExtracting,
          progress: 0,
          indeterminate: true,
        ));
        frames = await MuraiChannel.getVideoFrames(path: entry!.path!, timestampsSecs: stamps, maxWidth: _width);
      } else {
        for (final entry in _pickedImages) {
          if (entry.path != null) {
            frames.add(await File(entry.path!).readAsBytes());
          }
        }
      }
      if (frames.length < 2) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiGifNotEnoughFrames)));
        }
        return;
      }
      unawaited(MuraiChannel.notifyProgress(
        id: _notificationId,
        title: l10n.muraiGifTitle,
        text: l10n.muraiGifEncoding,
        progress: 50,
        indeterminate: true,
      ));
      final gifBytes = await _encodeGif(frames, _width, (1000 ~/ _fps).clamp(20, 1000));
      final uri = await MuraiSave.saveCopy(
        context,
        bytes: gifBytes,
        baseName: 'murai_gif',
        mimeType: 'image/gif',
      );
      unawaited(MuraiChannel.notifyFinished(
        id: _notificationId,
        title: l10n.muraiGifTitle,
        text: uri != null ? l10n.muraiGifDone(_formatSize(gifBytes.length)) : l10n.muraiGifFailed,
      ));
      if (mounted && uri != null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiGifDone(_formatSize(gifBytes.length)))));
      }
    } catch (e) {
      debugPrint('gif make failed: $e');
      await MuraiChannel.cancelNotification(id: _notificationId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiGifFailed)));
      }
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  static Future<Uint8List> _encodeGif(List<Uint8List> frames, int width, int delayMs) {
    return Isolate.run(() {
      img.Image? animation;
      for (final bytes in frames) {
        var decoded = img.decodeImage(bytes);
        if (decoded == null) continue;
        if (decoded.width > width) {
          decoded = img.copyResize(decoded, width: width);
        }
        final frame = img.Image.from(decoded);
        frame.frameDuration = delayMs;
        if (animation == null) {
          animation = frame;
        } else {
          animation.addFrame(frame);
        }
      }
      if (animation == null) throw Exception('no frames decoded');
      final gif = img.encodeGif(animation, samplingFactor: 4);
      return Uint8List.fromList(gif);
    });
  }

  static String _formatSize(int bytes) => bytes > 1024 * 1024 ? '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB' : '${(bytes / 1024).round()} KB';

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final videos = source.visibleEntries.where((e) => e.mimeType.startsWith('video/') && e.path != null && (e.durationMillis ?? 0) > 1000).toList();
    final images = source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).toList();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiGifTitle)),
      body: Column(
        children: [
          SegmentedButton<bool>(
            segments: [
              ButtonSegment(value: true, icon: const Icon(Icons.movie), label: Text(l10n.muraiGifFromVideo)),
              ButtonSegment(value: false, icon: const Icon(Icons.photo), label: Text(l10n.muraiGifFromImages)),
            ],
            selected: {_fromVideo},
            onSelectionChanged: (v) => setState(() => _fromVideo = v.first),
          ),
          const SizedBox(height: 8),
          if (_fromVideo)
            Expanded(
              child: _buildVideoPane(videos, l10n),
            )
          else
            Expanded(
              child: _buildImagesPane(images, l10n),
            ),
        ],
      ),
      bottomNavigationBar: _working
          ? LinearProgressIndicator(value: _progress <= 0 ? null : _progress)
          : SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: FilledButton.icon(
            icon: _working ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.animation),
            label: Text(l10n.muraiGifCreate),
            onPressed: _working || (_fromVideo ? _videoEntry == null : _pickedImages.length < 2) ? null : _make,
          ),
        ),
      ),
    );
  }

  Widget _buildVideoPane(List<AvesEntry> videos, dynamic l10n) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (_videoEntry != null)
          Text('${_videoEntry!.bestTitle ?? ''} • ${(_videoDuration).toStringAsFixed(1)}s', style: Theme.of(context).textTheme.titleSmall),
        if (_videoDuration > 0)
          RangeSlider(
            values: _clip,
            max: _videoDuration,
            divisions: _videoDuration.floor(),
            labels: RangeLabels('${_clip.start.toStringAsFixed(1)}s', '${_clip.end.toStringAsFixed(1)}s'),
            onChanged: (v) => setState(() => _clip = v),
          ),
        const SizedBox(height: 8),
        Text(l10n.muraiGifFps, style: Theme.of(context).textTheme.titleSmall),
        Wrap(
          spacing: 8,
          children: [5, 10, 15].map((v) => ChoiceChip(label: Text('$v fps'), selected: _fps == v, onSelected: (_) => setState(() => _fps = v))).toList(),
        ),
        Text(l10n.muraiGifWidth, style: Theme.of(context).textTheme.titleSmall),
        Wrap(
          spacing: 8,
          children: [360, 480, 720].map((v) => ChoiceChip(label: Text('$v px'), selected: _width == v, onSelected: (_) => setState(() => _width = v))).toList(),
        ),
        const SizedBox(height: 8),
        Text(l10n.muraiGifPickVideo, style: Theme.of(context).textTheme.bodySmall),
        SizedBox(
          height: 80,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: videos.length,
            separatorBuilder: (context, i) => const SizedBox(width: 4),
            itemBuilder: (context, i) {
              final entry = videos[i];
              return GestureDetector(
                onTap: () => setState(() {
                  _videoEntry = entry;
                  _videoDuration = (entry.durationMillis ?? 0) / 1000;
                  _clip = RangeValues(0, _videoDuration.clamp(0, 5));
                }),
                child: Stack(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: ThumbnailImage(entry: entry, extent: 80, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
                    ),
                    if (_videoEntry?.uri == entry.uri)
                      Positioned.fill(
                        child: Container(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: Theme.of(context).colorScheme.primary, width: 3),
                          ),
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildImagesPane(List<AvesEntry> images, dynamic l10n) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8),
          child: Text(l10n.muraiGifPickImages(_pickedImages.length)),
        ),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(8),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 120, mainAxisSpacing: 4, crossAxisSpacing: 4),
            itemCount: images.length,
            itemBuilder: (context, i) {
              final entry = images[i];
              final index = _pickedImages.indexWhere((e) => e.uri == entry.uri);
              final selected = index >= 0;
              return GestureDetector(
                onTap: () => setState(() {
                  if (selected) {
                    _pickedImages.removeAt(index);
                  } else if (_pickedImages.length < 30) {
                    _pickedImages.add(entry);
                  }
                }),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: ThumbnailImage(entry: entry, extent: 120, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
                    ),
                    if (selected)
                      PositionedDirectional(
                        top: 4,
                        end: 4,
                        child: CircleAvatar(
                          radius: 12,
                          backgroundColor: Theme.of(context).colorScheme.primary,
                          child: Text('${index + 1}', style: const TextStyle(fontSize: 11, color: Colors.white)),
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
