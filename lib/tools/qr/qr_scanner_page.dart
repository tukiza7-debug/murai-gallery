import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/widgets/aves_app.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

/// Murai Gallery QR / Barcode Scanner: decodes codes found inside photos
/// (offline, ZXing) and opens links safely after user confirmation.
class MuraiQrScannerPage extends StatefulWidget {
  static const routeName = '/murai-qr';

  const new({super.key});

  @override
  State<MuraiQrScannerPage> createState() => _MuraiQrScannerPageState();
}

class _MuraiQrScannerPageState extends State<MuraiQrScannerPage> {
  bool _running = false;
  AvesEntry? _entry;
  Map<String, String>? _result;

  Future<void> _scan(AvesEntry entry) async {
    if (entry.path == null || _running) return;
    setState(() {
      _running = true;
      _entry = entry;
      _result = null;
    });
    try {
      final result = await MuraiChannel.decodeQr(path: entry.path!);
      if (mounted) setState(() => _result = result);
    } catch (e) {
      debugPrint('qr scan failed: $e');
      if (mounted) setState(() => _result = null);
    } finally {
      if (mounted) setState(() => _running = false);
    }
  }

  void _openLink(String url) {
    final l10n = context.l10n;
    final isHttp = url.startsWith('http://') || url.startsWith('https://');
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        icon: Icon(isHttp ? Icons.link : Icons.warning_amber_outlined),
        title: Text(l10n.muraiQrOpenTitle),
        content: Text(isHttp ? l10n.muraiQrOpenConfirm(url) : l10n.muraiQrUnsafeConfirm(url)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancelTooltip)),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              if (isHttp) {
                AvesApp.launchUrl(url);
              }
            },
            child: Text(l10n.muraiQrOpen),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final imageEntries = source.visibleEntries.where((e) => e.mimeType.startsWith('image/') && e.path != null).toList();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiQrTitle)),
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
                  onTap: () => _scan(entry),
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
            child: _result == null
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.qr_code_scanner, size: 64, color: Theme.of(context).colorScheme.primary),
                          const SizedBox(height: 16),
                          Text(l10n.muraiQrHint, textAlign: TextAlign.center),
                        ],
                      ),
                    ),
                  )
                : Padding(
                    padding: const EdgeInsets.all(16),
                    child: Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.qr_code_2),
                                const SizedBox(width: 8),
                                Text(_result!['format'] ?? '', style: Theme.of(context).textTheme.titleSmall),
                              ],
                            ),
                            const SizedBox(height: 12),
                            SelectableText(_result!['text'] ?? '', style: const TextStyle(fontFamily: 'monospace')),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                OutlinedButton.icon(
                                  icon: const Icon(Icons.copy),
                                  label: Text(l10n.muraiOcrCopy),
                                  onPressed: () {
                                    Clipboard.setData(ClipboardData(text: _result!['text'] ?? ''));
                                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiOcrCopied)));
                                  },
                                ),
                                const SizedBox(width: 8),
                                FilledButton.icon(
                                  icon: const Icon(Icons.open_in_new),
                                  label: Text(l10n.muraiQrOpen),
                                  onPressed: () => _openLink(_result!['text'] ?? ''),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
