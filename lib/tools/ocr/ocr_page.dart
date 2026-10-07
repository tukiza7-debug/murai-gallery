import 'dart:io';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:provider/provider.dart';

/// Murai Gallery OCR Text Extractor: reads text from photos fully offline
/// via on-device ML Kit, with copy / share / save-as-text actions.
class MuraiOcrPage extends StatefulWidget {
  static const routeName = '/murai-ocr';

  const new({super.key});

  @override
  State<MuraiOcrPage> createState() => _MuraiOcrPageState();
}

class _MuraiOcrPageState extends State<MuraiOcrPage> {
  final TextRecognizer _recognizer = TextRecognizer(script: TextRecognitionScript.latin);
  bool _running = false;
  AvesEntry? _entry;
  String _text = '';

  @override
  void dispose() {
    _recognizer.close();
    super.dispose();
  }

  Future<void> _extract(AvesEntry entry) async {
    if (entry.path == null || !File(entry.path!).existsSync()) return;
    final l10n = context.l10n;
    setState(() {
      _running = true;
      _entry = entry;
      _text = '';
    });
    try {
      final inputImage = InputImage.fromFilePath(entry.path!);
      final result = await _recognizer.processImage(inputImage);
      if (mounted) setState(() => _text = result.text);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiOcrFailed)));
      }
    } finally {
      if (mounted) setState(() => _running = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final imageEntries = source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).toList();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiOcrTitle)),
      body: Column(
        children: [
          SizedBox(
            height: 96,
            child: ListView.separated(
              padding: const EdgeInsets.all(8),
              scrollDirection: Axis.horizontal,
              itemCount: imageEntries.length,
              separatorBuilder: (context, i) => const SizedBox(width: 4),
              itemBuilder: (context, i) {
                final entry = imageEntries[i];
                return GestureDetector(
                  onTap: () => _extract(entry),
                  child: Stack(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: ThumbnailImage(entry: entry, extent: 80, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
                      ),
                      if (_entry?.uri == entry.uri)
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
          if (_running) const LinearProgressIndicator(),
          Expanded(
            child: _text.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Text(
                        l10n.muraiOcrHint,
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ),
                  )
                : SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: SelectableText(_text, style: Theme.of(context).textTheme.bodyLarge),
                  ),
          ),
          if (_text.isNotEmpty)
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.copy),
                        label: Text(l10n.muraiOcrCopy),
                        onPressed: () {
                          Clipboard.setData(ClipboardData(text: _text));
                          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiOcrCopied)));
                        },
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        icon: const Icon(Icons.share),
                        label: Text(l10n.muraiOcrShare),
                        onPressed: () {
                          final text = _text;
                          showDialog(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: Text(l10n.muraiOcrShare),
                              content: SelectableText(text.length > 1200 ? '${text.substring(0, 1200)}…' : text),
                              actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancelTooltip))],
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
