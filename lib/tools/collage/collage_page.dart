import 'dart:io';
import 'dart:ui' as ui;

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/images.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/tools/murai_save.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

/// Murai Gallery Collage Maker: combine 2-9 photos with grid templates,
/// borders and background color into a single image.
class MuraiCollagePage extends StatefulWidget {
  static const routeName = '/murai-collage';

  const new({super.key});

  @override
  State<MuraiCollagePage> createState() => _MuraiCollagePageState();
}

class _MuraiCollagePageState extends State<MuraiCollagePage> {
  final List<AvesEntry> _picked = [];
  int _template = 0;
  double _border = 4;
  Color _bg = Colors.white;
  bool _working = false;

  static const _maxImages = 9;
  static const _outputSize = 2048.0;

  /// ratio-based templates: each template is a list of cells (left, top, right, bottom)
  static const Map<int, List<List<double>>> _templates = {
    2: [
      // 2 columns
      [0, 0, .5, 1],
      [.5, 0, 1, 1],
    ],
    3: [
      // big left + 2 stacked right
      [0, 0, .6, 1],
      [.6, 0, 1, .5],
      [.6, .5, 1, 1],
    ],
    4: [
      // 2x2 grid
      [0, 0, .5, .5],
      [.5, 0, 1, .5],
      [0, .5, .5, 1],
      [.5, .5, 1, 1],
    ],
    5: [
      // big top + 4 below
      [0, 0, 1, .5],
      [0, .5, .25, 1],
      [.25, .5, .5, 1],
      [.5, .5, .75, 1],
      [.75, .5, 1, 1],
    ],
    6: [
      // 3x2 grid
      [0, 0, 1 / 3, .5],
      [1 / 3, 0, 2 / 3, .5],
      [2 / 3, 0, 1, .5],
      [0, .5, 1 / 3, 1],
      [1 / 3, .5, 2 / 3, 1],
      [2 / 3, .5, 1, 1],
    ],
    7: [
      // 3 columns x 3 rows minus bottom-right pair merged
      [0, 0, 1 / 3, 1 / 3],
      [1 / 3, 0, 2 / 3, 1 / 3],
      [2 / 3, 0, 1, 1 / 3],
      [0, 1 / 3, 1 / 3, 2 / 3],
      [1 / 3, 1 / 3, 2 / 3, 2 / 3],
      [2 / 3, 1 / 3, 1, 2 / 3],
      [0, 2 / 3, 1, 1],
    ],
    8: [
      // big left + 7 grid
      [0, 0, .5, 1],
      [.5, 0, 1, .25],
      [.5, .25, .75, .5],
      [.75, .25, 1, .5],
      [.5, .5, .75, .75],
      [.75, .5, 1, .75],
      [.5, .75, .75, 1],
      [.75, .75, 1, 1],
    ],
    9: [
      // 3x3 grid
      [0, 0, 1 / 3, 1 / 3],
      [1 / 3, 0, 2 / 3, 1 / 3],
      [2 / 3, 0, 1, 1 / 3],
      [0, 1 / 3, 1 / 3, 2 / 3],
      [1 / 3, 1 / 3, 2 / 3, 2 / 3],
      [2 / 3, 1 / 3, 1, 2 / 3],
      [0, 2 / 3, 1 / 3, 1],
      [1 / 3, 2 / 3, 2 / 3, 1],
      [2 / 3, 2 / 3, 1, 1],
    ],
  };

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final source = context.read<CollectionSource>();
      setState(() {
        _picked.addAll(source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).take(4));
        if (_picked.length < 2 && _templates[_picked.length] == null) {
          _template = _templates.keys.firstWhere((k) => k >= _picked.length || k == _maxImages, orElse: () => 2);
        }
      });
    });
  }

  Future<void> _create() async {
    final cells = _templates[_template];
    if (_working || cells == null || _picked.length < 2) return;
    final l10n = context.l10n;
    setState(() => _working = true);
    try {
      final uiImages = <ui.Image>[];
      for (final entry in _picked) {
        final bytes = await File(entry.path!).readAsBytes();
        final codec = await ui.instantiateImageCodec(bytes, targetWidth: 1024);
        final frame = await codec.getNextFrame();
        uiImages.add(frame.image);
      }

      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder, const Rect.fromLTWH(0, 0, _outputSize, _outputSize));
      canvas.drawRect(const Rect.fromLTWH(0, 0, _outputSize, _outputSize), Paint()..color = _bg);

      final border = _border * (_outputSize / 512);
      final cellCount = cells.length.clamp(2, _picked.length);
      for (var i = 0; i < cellCount && i < uiImages.length; i++) {
        final cell = cells[i];
        final dest = Rect.fromLTRB(
          cell[0] * _outputSize + border,
          cell[1] * _outputSize + border,
          cell[2] * _outputSize - border,
          cell[3] * _outputSize - border,
        );
        final image = uiImages[i];
        // cover-fit
        final scale = (dest.width / image.width, dest.height / image.height);
        final fitScale = scale.$1 > scale.$2 ? scale.$1 : scale.$2;
        final src = Rect.fromCenter(
          center: Offset(image.width / 2, image.height / 2),
          width: dest.width / fitScale,
          height: dest.height / fitScale,
        );
        canvas.drawImageRect(image, src, dest, Paint()..filterQuality = FilterQuality.medium);
      }

      final picture = recorder.endRecording();
      final image = await picture.toImage(_outputSize.round(), _outputSize.round());
      final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
      final png = byteData!.buffer.asUint8List();

      for (final uiImage in uiImages) {
        uiImage.dispose();
      }
      image.dispose();

      final uri = await MuraiSave.saveCopy(context, bytes: png, baseName: 'murai_collage', mimeType: 'image/png');
      if (mounted && uri != null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiCollageDone)));
      }
    } catch (e) {
      debugPrint('collage failed: $e');
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final images = source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).toList();
    final availableTemplates = _templates.entries.where((t) => t.value.length >= 2 && _picked.length >= 2).map((t) => t.key).toList();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiCollageTitle)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8),
            child: Text(l10n.muraiCollagePickImages(_picked.length, _maxImages)),
          ),
          SizedBox(
            height: 88,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              scrollDirection: Axis.horizontal,
              itemCount: images.length,
              separatorBuilder: (context, i) => const SizedBox(width: 4),
              itemBuilder: (context, i) {
                final entry = images[i];
                final index = _picked.indexWhere((e) => e.uri == entry.uri);
                final selected = index >= 0;
                return GestureDetector(
                  onTap: () => setState(() {
                    if (selected) {
                      _picked.removeAt(index);
                    } else if (_picked.length < _maxImages) {
                      _picked.add(entry);
                    }
                  }),
                  child: Stack(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: ThumbnailImage(entry: entry, extent: 80, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
                      ),
                      if (selected)
                        PositionedDirectional(
                          top: 2,
                          end: 2,
                          child: CircleAvatar(
                            radius: 10,
                            backgroundColor: Theme.of(context).colorScheme.primary,
                            child: Text('${index + 1}', style: const TextStyle(fontSize: 10, color: Colors.white)),
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              children: [
                Row(
                  children: [
                    Text(l10n.muraiCollageTemplate),
                    Expanded(
                      child: Wrap(
                        spacing: 4,
                        children: availableTemplates
                            .map((t) => ChoiceChip(label: Text('$t'), selected: _template == t, onSelected: (_) => setState(() => _template = t)))
                            .toList(),
                      ),
                    ),
                  ],
                ),
                Row(
                  children: [
                    Text(l10n.muraiCollageBorder),
                    Expanded(
                      child: Slider(
                        value: _border,
                        min: 0,
                        max: 20,
                        divisions: 10,
                        label: '${_border.round()}',
                        onChanged: (v) => setState(() => _border = v),
                      ),
                    ),
                    GestureDetector(
                      onTap: () => setState(() => _bg = _bg == Colors.white ? Colors.black : Colors.white),
                      child: CircleAvatar(radius: 14, backgroundColor: _bg, child: const SizedBox()),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Expanded(
            child: Center(
              child: AspectRatio(
                aspectRatio: 1,
                child: Container(
                  margin: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: _bg,
                    border: Border.all(color: Theme.of(context).dividerColor),
                    borderRadius: const BorderRadius.all(Radius.circular(8)),
                  ),
                  child: _picked.length >= 2 ? _buildPreview() : Center(child: Text(l10n.muraiCollageNeedMore)),
                ),
              ),
            ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: FilledButton.icon(
            icon: _working ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.grid_view),
            label: Text(l10n.muraiCollageCreate),
            onPressed: _working || _picked.length < 2 ? null : _create,
          ),
        ),
      ),
    );
  }

  Widget _buildPreview() {
    final cells = _templates[_template]!;
    final cellCount = cells.length.clamp(2, _picked.length);
    return LayoutBuilder(builder: (context, constraints) {
      final size = constraints.biggest;
      return Stack(
        children: [
          for (var i = 0; i < cellCount; i++)
            Positioned.fromRect(
              rect: Rect.fromLTRB(
                cells[i][0] * size.width + _border,
                cells[i][1] * size.height + _border,
                cells[i][2] * size.width - _border,
                cells[i][3] * size.height - _border,
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(2),
                child: Image(
                  image: _picked[i].getThumbnail(extent: 128),
                  fit: .cover,
                ),
              ),
            ),
        ],
      );
    });
  }
}
