import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:aves/model/entry/entry.dart';
import 'package:aves/tools/murai_save.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Murai Gallery Image Editor: crop, rotate/flip, brightness/contrast/saturation,
/// filter presets, text overlays, freehand drawing and emoji stickers.
/// All rendering is non-destructive until "Save as copy" composites the layers.
class MuraiImageEditorPage extends StatefulWidget {
  static const routeName = '/murai-editor';

  final AvesEntry? entry;
  final Uint8List? bytes;

  const new({super.key, this.entry, this.bytes});

  @override
  State<MuraiImageEditorPage> createState() => _MuraiImageEditorPageState();
}

enum _EditorTool { adjustments, crop, text, draw, sticker }

class EditorTextLayer {
  String text;
  Offset position;
  double size;
  Color color;

  new({required this.text, required this.position, required this.size, required this.color});
}

class EditorStroke {
  final List<Offset> points;
  final Color color;
  final double width;

  new({required this.color, required this.width}) : points = [];
}

class _MuraiImageEditorPageState extends State<MuraiImageEditorPage> {
  ui.Image? _image;
  Size _imageSize = Size.zero;
  bool _loading = true;

  _EditorTool _tool = _EditorTool.adjustments;
  double _brightness = 0, _contrast = 0, _saturation = 0;
  String _filter = 'none';
  double _cropLeft = 0, _cropTop = 0, _cropRight = 1, _cropBottom = 1;
  int _rotationQuarterTurns = 0;
  bool _flipH = false, _flipV = false;
  final List<EditorTextLayer> _texts = [];
  final List<EditorStroke> _strokes = [];
  Color _brushColor = Colors.red;
  double _brushWidth = 4;
  EditorStroke? _activeStroke;
  EditorTextLayer? _draggedText;

  static const filters = <String, String>{
    'none': 'Original',
    'mono': 'Mono',
    'sepia': 'Sepia',
    'cool': 'Cool',
    'warm': 'Warm',
    'vivid': 'Vivid',
    'fade': 'Fade',
    'invert': 'Invert',
  };

  static const stickers = ['★', '♥', '☺', '🌸', '🔥', '✨', '😎', '🎉', '🐱', '🌈'];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final bytes = widget.bytes ?? await File(widget.entry!.path!).readAsBytes();
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      if (mounted) {
        setState(() {
          _image = frame.image;
          _imageSize = Size(frame.image.width.toDouble(), frame.image.height.toDouble());
          _loading = false;
        });
      }
    } catch (e) {
      debugPrint('editor load failed: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.muraiEditorLoadFailed)));
      }
    }
  }

  ColorFilter _colorMatrix() {
    // brightness/contrast/saturation + filter presets, combined into one matrix
    final b = _brightness; // -1..1
    final c = 1 + _contrast; // -1..1 -> 0..2
    final s = 1 + _saturation;
    final sr = (1 - s) * 0.2126, sg = (1 - s) * 0.7152, sb = (1 - s) * 0.0722;
    var matrix = <double>[
      c, 0, 0, 0, b * 60, //
      0, c, 0, 0, b * 60, //
      0, 0, c, 0, b * 60, //
      0, 0, 0, 1, 0,
    ];
    if (s != 1) {
      matrix = <double>[
        c * (0.2126 + sr) + sr * 0, c * 0.7152 + sg * 0.7152 * 0 + sg, c * 0.0722 + sb, 0, b * 60, //
        c * 0.2126 + sr, c * (0.7152 + sg), c * 0.0722 + sb, 0, b * 60, //
        c * 0.2126 + sr, c * 0.7152 + sg, c * (0.0722 + sb), 0, b * 60, //
        0, 0, 0, 1, 0,
      ];
    }
    switch (_filter) {
      case 'mono':
        matrix = _multiplyMatrices(matrix, <double>[
          0.2126, 0.7152, 0.0722, 0, 0, //
          0.2126, 0.7152, 0.0722, 0, 0, //
          0.2126, 0.7152, 0.0722, 0, 0, //
          0, 0, 0, 1, 0,
        ]);
      case 'sepia':
        matrix = _multiplyMatrices(matrix, <double>[
          .393, .769, .189, 0, 0, //
          .349, .686, .168, 0, 0, //
          .272, .534, .131, 0, 0, //
          0, 0, 0, 1, 0,
        ]);
      case 'invert':
        matrix = _multiplyMatrices(matrix, <double>[
          -1, 0, 0, 0, 255, //
          0, -1, 0, 0, 255, //
          0, 0, -1, 0, 255, //
          0, 0, 0, 1, 0,
        ]);
      case 'fade':
        matrix = _multiplyMatrices(matrix, <double>[
          1, 0, 0, 0, 30, //
          0, 1, 0, 0, 30, //
          0, 0, 1, 0, 30, //
          0, 0, 0, 1, 0,
        ]);
    }
    return ColorFilter.matrix(matrix);
  }

  List<double> _multiplyMatrices(List<double> a, List<double> b) {
    final result = List<double>.filled(20, 0);
    for (var row = 0; row < 4; row++) {
      for (var col = 0; col < 5; col++) {
        var sum = 0.0;
        for (var k = 0; k < 4; k++) {
          sum += a[row * 5 + k] * b[k * 5 + col];
        }
        if (col == 4) {
          sum += a[row * 5 + 4];
        }
        result[row * 5 + col] = sum;
      }
    }
    return result;
  }

  Future<void> _save() async {
    final image = _image;
    if (image == null) return;
    final l10n = context.l10n;
    try {
      // crop rect in source pixels
      final crop = Rect.fromLTRB(
        _cropLeft * image.width,
        _cropTop * image.height,
        _cropRight * image.width,
        _cropBottom * image.height,
      );
      var outW = crop.width.round(), outH = crop.height.round();
      if (_rotationQuarterTurns.isOdd) {
        final t = outW;
        outW = outH;
        outH = t;
      }
      outW = outW.clamp(1, 8192);
      outH = outH.clamp(1, 8192);

      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder, Rect.fromLTWH(0, 0, outW.toDouble(), outH.toDouble()));
      canvas.translate(outW / 2, outH / 2);
      if (_rotationQuarterTurns.isOdd) canvas.rotate(math.pi / 2);
      canvas.rotate(_rotationQuarterTurns * math.pi / 2);
      var sx = 1.0, sy = 1.0;
      if (_flipH) sx = -1;
      if (_flipV) sy = -1;
      canvas.scale(sx, sy);
      canvas.translate(-outW / 2, -outH / 2);

      final paint = Paint()..filterQuality = FilterQuality.high;
      if (_brightness != 0 || _contrast != 0 || _saturation != 0 || _filter != 'none') {
        paint.colorFilter = _colorMatrix();
      }

      // draw base image cropped, centered
      final halfW = (_rotationQuarterTurns.isOdd ? outH : outW) / 2;
      final halfH = (_rotationQuarterTurns.isOdd ? outW : outH) / 2;
      final dest = Rect.fromLTWH(outW / 2 - halfW, outH / 2 - halfH, halfW * 2, halfH * 2);
      canvas.drawImageRect(image, crop, dest, paint);

      // reset for overlays: overlay coordinates are stored normalized to the displayed (cropped, unrotated) area
      canvas.save();
      canvas.translate(outW / 2, outH / 2);
      canvas.rotate(_rotationQuarterTurns * math.pi / 2);
      canvas.translate(-halfW, -halfH);

      final scaleX = dest.width;
      final scaleY = dest.height;
      for (final stroke in _strokes) {
        final path = Path();
        var first = true;
        for (final p in stroke.points) {
          final tp = Offset(p.dx * scaleX, p.dy * scaleY);
          first ? path.moveTo(tp.dx, tp.dy) : path.lineTo(tp.dx, tp.dy);
          first = false;
        }
        canvas.drawPath(
          path,
          Paint()
            ..color = stroke.color
            ..style = PaintingStyle.stroke
            ..strokeWidth = stroke.width
            ..strokeCap = StrokeCap.round
            ..strokeJoin = StrokeJoin.round,
        );
      }
      for (final text in _texts) {
        final builder = ui.ParagraphBuilder(ui.ParagraphStyle(fontSize: text.size, textAlign: TextAlign.left))
          ..pushStyle(ui.TextStyle(color: text.color))
          ..addText(text.text);
        final paragraph = builder.build();
        paragraph.layout(ui.ParagraphConstraints(width: scaleX * .9));
        canvas.drawParagraph(paragraph, Offset(text.position.dx * scaleX, text.position.dy * scaleY));
      }
      canvas.restore();

      final picture = recorder.endRecording();
      final rendered = await picture.toImage(outW, outH);
      final byteData = await rendered.toByteData(format: ui.ImageByteFormat.png);
      final png = byteData!.buffer.asUint8List();
      rendered.dispose();

      final uri = await MuraiSave.saveCopy(
        context,
        bytes: png,
        baseName: 'murai_edit',
        mimeType: 'image/png',
      );
      if (mounted && uri != null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiEditorSaved)));
      }
    } catch (e) {
      debugPrint('editor save failed: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiEditorSaveFailed)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(l10n.muraiEditorTitle),
        actions: [
          IconButton(
            tooltip: l10n.muraiEditorUndo,
            icon: const Icon(Icons.undo),
            onPressed: () => setState(() {
              if (_activeStroke != null) return;
              if (_strokes.isNotEmpty) {
                _strokes.removeLast();
              } else if (_texts.isNotEmpty) {
                _texts.removeLast();
              }
            }),
          ),
          FilledButton.icon(
            style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
            icon: const Icon(Icons.save_outlined),
            label: Text(l10n.saveCopyButtonLabel),
            onPressed: _image == null ? null : _save,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Expanded(child: _buildCanvas()),
                _buildToolbar(),
                _buildToolPanel(),
              ],
            ),
    );
  }

  Widget _buildCanvas() {
    final image = _image;
    if (image == null) return const SizedBox();
    return LayoutBuilder(builder: (context, constraints) {
      final cropW = (_cropRight - _cropLeft) * _imageSize.width;
      final cropH = (_cropBottom - _cropTop) * _imageSize.height;
      final rotated = _rotationQuarterTurns.isOdd;
      final displayW = rotated ? cropH : cropW;
      final displayH = rotated ? cropW : cropH;
      final scale = math.min(constraints.maxWidth / displayW, (constraints.maxHeight - 8) / displayH);

      return Center(
        child: GestureDetector(
          onPanStart: _tool == _EditorTool.draw
              ? (details) => setState(() {
                    final p = _toImageCoords(details.localPosition, scale, displayW, displayH);
                    _activeStroke = EditorStroke(color: _brushColor, width: _brushWidth * 2)..points.add(p);
                    _strokes.add(_activeStroke!);
                  })
              : null,
          onPanUpdate: _tool == _EditorTool.draw
              ? (details) => setState(() {
                    _activeStroke?.points.add(_toImageCoords(details.localPosition, scale, displayW, displayH));
                  })
              : _tool == _EditorTool.text || _tool == _EditorTool.sticker
                  ? (details) {
                      // drag nearest text layer
                      if (_draggedText == null && _texts.isNotEmpty) {
                        _draggedText = _texts.last;
                      }
                      if (_draggedText != null) {
                        setState(() {
                          _draggedText!.position = _toImageCoords(details.localPosition, scale, displayW, displayH);
                        });
                      }
                    }
                  : null,
          onPanEnd: (_) => setState(() => _draggedText = null),
          child: SizedBox(
            width: displayW * scale,
            height: displayH * scale,
            child: ClipRRect(
              child: CustomPaint(
                painter: EditorPainter(
                  image: image,
                  crop: Rect.fromLTRB(_cropLeft, _cropTop, _cropRight, _cropBottom),
                  rotationQuarterTurns: _rotationQuarterTurns,
                  flipH: _flipH,
                  flipV: _flipV,
                  colorMatrix: (_brightness != 0 || _contrast != 0 || _saturation != 0 || _filter != 'none') ? _colorMatrix() : null,
                  strokes: _strokes,
                  texts: _texts,
                  displaySize: Size(displayW, displayH),
                ),
                child: const SizedBox(),
              ),
            ),
          ),
        ),
      );
    });
  }

  Offset _toImageCoords(Offset local, double scale, double displayW, double displayH) {
    return Offset(
      (local.dx / scale / displayW).clamp(0.0, 1.0),
      (local.dy / scale / displayH).clamp(0.0, 1.0),
    );
  }

  Widget _buildToolbar() {
    return Container(
      color: Colors.grey.shade900,
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _toolButton(_EditorTool.adjustments, Icons.tune, context.l10n.muraiEditorAdjust),
          _toolButton(_EditorTool.crop, Icons.crop, context.l10n.muraiEditorCrop),
          _toolButton(_EditorTool.text, Icons.text_fields, context.l10n.muraiEditorText),
          _toolButton(_EditorTool.draw, Icons.draw, context.l10n.muraiEditorDraw),
          _toolButton(_EditorTool.sticker, Icons.emoji_emotions_outlined, context.l10n.muraiEditorSticker),
        ],
      ),
    );
  }

  Widget _toolButton(_EditorTool tool, IconData icon, String label) {
    final selected = _tool == tool;
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () => setState(() => _tool = tool),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: selected ? Theme.of(context).colorScheme.primary : Colors.white70),
            Text(label, style: TextStyle(fontSize: 10, color: selected ? Theme.of(context).colorScheme.primary : Colors.white70)),
          ],
        ),
      ),
    );
  }

  Widget _buildToolPanel() {
    return Container(
      color: Colors.grey.shade900,
      padding: const EdgeInsets.all(8),
      child: switch (_tool) {
        _EditorTool.adjustments => _buildAdjustPanel(),
        _EditorTool.crop => _buildCropPanel(),
        _EditorTool.text => _buildTextPanel(),
        _EditorTool.draw => _buildDrawPanel(),
        _EditorTool.sticker => _buildStickerPanel(),
      },
    );
  }

  Widget _buildAdjustPanel() {
    final l10n = context.l10n;
    return Column(
      children: [
        _slider(l10n.muraiEditorBrightness, _brightness, (v) => setState(() => _brightness = v)),
        _slider(l10n.muraiEditorContrast, _contrast, (v) => setState(() => _contrast = v)),
        _slider(l10n.muraiEditorSaturation, _saturation, (v) => setState(() => _saturation = v)),
        Row(
          children: [
            ...filters.entries.take(4).map((e) => _filterChip(e.key, e.value)),
            IconButton(
              icon: const Icon(Icons.rotate_right, color: Colors.white70),
              onPressed: () => setState(() => _rotationQuarterTurns = (_rotationQuarterTurns + 1) % 4),
            ),
            IconButton(
              icon: const Icon(Icons.flip, color: Colors.white70),
              onPressed: () => setState(() => _flipH = !_flipH),
            ),
          ],
        ),
        Row(
          children: [
            ...filters.entries.skip(4).map((e) => _filterChip(e.key, e.value)),
            IconButton(
              icon: const Icon(Icons.flip_outlined, color: Colors.white70),
              onPressed: () => setState(() => _flipV = !_flipV),
            ),
            IconButton(
              icon: const Icon(Icons.restart_alt, color: Colors.white70),
              onPressed: () => setState(() {
                _brightness = 0;
                _contrast = 0;
                _saturation = 0;
                _filter = 'none';
                _rotationQuarterTurns = 0;
                _flipH = false;
                _flipV = false;
              }),
            ),
          ],
        ),
      ],
    );
  }

  Widget _filterChip(String key, String label) {
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: ChoiceChip(
        label: Text(label, style: const TextStyle(fontSize: 11)),
        selected: _filter == key,
        visualDensity: VisualDensity.compact,
        onSelected: (_) => setState(() => _filter = key),
      ),
    );
  }

  Widget _buildCropPanel() {
    final l10n = context.l10n;
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            OutlinedButton(onPressed: () => setState(() { _cropLeft = 0; _cropTop = 0; _cropRight = 1; _cropBottom = 1; }), child: Text(l10n.muraiEditorCropFull)),
            OutlinedButton(onPressed: () => setState(() { _cropLeft = .125; _cropRight = .875; _cropTop = .2; _cropBottom = .8; }), child: const Text('4:3')),
            OutlinedButton(onPressed: () => setState(() { _cropLeft = .156; _cropRight = .844; _cropTop = .28; _cropBottom = .72; }), child: const Text('16:9')),
            OutlinedButton(onPressed: () => setState(() { _cropLeft = .25; _cropRight = .75; _cropTop = .125; _cropBottom = .875; }), child: const Text('1:1')),
          ],
        ),
        _slider(l10n.muraiEditorCropLeft, _cropLeft, (v) => setState(() => _cropLeft = math.min(v, _cropRight - .05)), min: 0, max: 1),
        _slider(l10n.muraiEditorCropRight, _cropRight, (v) => setState(() => _cropRight = math.max(v, _cropLeft + .05)), min: 0, max: 1),
        _slider(l10n.muraiEditorCropTop, _cropTop, (v) => setState(() => _cropTop = math.min(v, _cropBottom - .05)), min: 0, max: 1),
        _slider(l10n.muraiEditorCropBottom, _cropBottom, (v) => setState(() => _cropBottom = math.max(v, _cropTop + .05)), min: 0, max: 1),
      ],
    );
  }

  Widget _buildTextPanel() {
    final l10n = context.l10n;
    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.add),
            label: Text(l10n.muraiEditorAddText),
            onPressed: () async {
              final controller = TextEditingController();
              final text = await showDialog<String>(
                context: context,
                builder: (context) => AlertDialog(
                  title: Text(l10n.muraiEditorAddText),
                  content: TextField(controller: controller, autofocus: true),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancelTooltip)),
                    FilledButton(onPressed: () => Navigator.pop(context, controller.text), child: Text(l10n.applyButtonLabel)),
                  ],
                ),
              );
              if (text != null && text.isNotEmpty && mounted) {
                setState(() {
                  _texts.add(EditorTextLayer(text: text, position: const Offset(.3, .4), size: 32, color: Colors.white));
                });
              }
            },
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.delete_sweep_outlined),
            label: Text(l10n.deleteButtonLabel),
            onPressed: _texts.isEmpty ? null : () => setState(_texts.removeLast),
          ),
        ),
      ],
    );
  }

  Widget _buildDrawPanel() {
    final l10n = context.l10n;
    return Row(
      children: [
        ...[Colors.red, Colors.orange, Colors.yellow, Colors.green, Colors.blue, Colors.purple, Colors.white, Colors.black].map(
          (color) => GestureDetector(
            onTap: () => setState(() => _brushColor = color),
            child: Container(
              margin: const EdgeInsets.only(right: 4),
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: color,
                shape: BoxShape.circle,
                border: Border.all(color: _brushColor == color ? Theme.of(context).colorScheme.primary : Colors.transparent, width: 3),
              ),
            ),
          ),
        ),
        Expanded(
          child: Slider(
            value: _brushWidth,
            min: 1,
            max: 24,
            label: '${_brushWidth.round()}',
            onChanged: (v) => setState(() => _brushWidth = v),
          ),
        ),
        Text(l10n.muraiEditorDrawHint, style: const TextStyle(fontSize: 10, color: Colors.white54)),
      ],
    );
  }

  Widget _buildStickerPanel() {
    return Wrap(
      children: stickers
          .map((s) => InkWell(
                onTap: () => setState(() {
                  _texts.add(EditorTextLayer(text: s, position: const Offset(.35, .35), size: 96, color: Colors.white));
                  _tool = _EditorTool.text;
                }),
                child: Padding(
                  padding: const EdgeInsets.all(4),
                  child: Text(s, style: const TextStyle(fontSize: 28)),
                ),
              ))
          .toList(),
    );
  }

  Widget _slider(String label, double value, ValueChanged<double> onChanged, {double min = -1, double max = 1}) {
    return Row(
      children: [
        SizedBox(width: 90, child: Text(label, style: const TextStyle(fontSize: 12, color: Colors.white70))),
        Expanded(
          child: SliderTheme(
            data: SliderTheme.of(context).copyWith(activeTrackColor: Theme.of(context).colorScheme.primary),
            child: Slider(
              value: value,
              min: min,
              max: max,
              onChanged: onChanged,
            ),
          ),
        ),
      ],
    );
  }
}

class EditorPainter extends CustomPainter {
  final ui.Image image;
  final Rect crop;
  final int rotationQuarterTurns;
  final bool flipH, flipV;
  final ColorFilter? colorMatrix;
  final List<EditorStroke> strokes;
  final List<EditorTextLayer> texts;
  final Size displaySize;

  const new({
    required this.image,
    required this.crop,
    required this.rotationQuarterTurns,
    required this.flipH,
    required this.flipV,
    required this.colorMatrix,
    required this.strokes,
    required this.texts,
    required this.displaySize,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final rotated = rotationQuarterTurns.isOdd;
    final displayW = rotated ? displaySize.height : displaySize.width;
    final displayH = rotated ? displaySize.width : displaySize.height;
    final scale = size.width / displayW;

    canvas.save();
    canvas.scale(scale);
    canvas.translate(displayW / 2, displayH / 2);
    canvas.rotate(rotationQuarterTurns * math.pi / 2);
    var sx = 1.0, sy = 1.0;
    if (flipH) sx = -1;
    if (flipV) sy = -1;
    canvas.scale(sx, sy);
    canvas.translate(-displayW / 2, -displayH / 2);

    final srcW = crop.width * image.width;
    final srcH = crop.height * image.height;
    final src = Rect.fromLTWH(crop.left * image.width, crop.top * image.height, srcW, srcH);
    final dst = Rect.fromLTWH(0, 0, displayW, displayH);
    canvas.drawImageRect(image, src, dst, Paint()..filterQuality = FilterQuality.medium);
    canvas.restore();

    // overlays are drawn in display space
    for (final stroke in strokes) {
      final path = Path();
      var first = true;
      for (final p in stroke.points) {
        final tp = Offset(p.dx * displayW, p.dy * displayH);
        first ? path.moveTo(tp.dx, tp.dy) : path.lineTo(tp.dx, tp.dy);
        first = false;
      }
      canvas.drawPath(
        path,
        Paint()
          ..color = stroke.color
          ..style = PaintingStyle.stroke
          ..strokeWidth = stroke.width
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round,
      );
    }
    for (final text in texts) {
      final builder = ui.ParagraphBuilder(ui.ParagraphStyle(fontSize: text.size, textAlign: TextAlign.left))
        ..pushStyle(ui.TextStyle(color: text.color))
        ..addText(text.text);
      final paragraph = builder.build();
      paragraph.layout(ui.ParagraphConstraints(width: displayW * .9));
      canvas.drawParagraph(paragraph, Offset(text.position.dx * displayW, text.position.dy * displayH));
    }
  }

  @override
  bool shouldRepaint(covariant EditorPainter oldDelegate) =>
      oldDelegate.image != image || oldDelegate.crop != crop || oldDelegate.rotationQuarterTurns != rotationQuarterTurns || oldDelegate.flipH != flipH || oldDelegate.flipV != flipV || oldDelegate.colorMatrix != colorMatrix || oldDelegate.strokes != strokes || oldDelegate.texts != texts;
}
