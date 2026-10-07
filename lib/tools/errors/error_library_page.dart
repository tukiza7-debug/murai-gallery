import 'package:aves/tools/errors/error_logger.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';

/// Murai Gallery error library page: browse, export and clear captured errors.
class MuraiErrorLibraryPage extends StatefulWidget {
  static const routeName = '/murai-errors';

  const new({super.key});

  @override
  State<MuraiErrorLibraryPage> createState() => _MuraiErrorLibraryPageState();
}

class _MuraiErrorLibraryPageState extends State<MuraiErrorLibraryPage> {
  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final entries = MuraiErrorLogger.instance.entries;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.muraiErrorLibrary),
        actions: [
          if (entries.isNotEmpty) ...[
            IconButton(
              tooltip: l10n.muraiErrorExport,
              icon: const Icon(Icons.file_upload_outlined),
              onPressed: _export,
            ),
            IconButton(
              tooltip: l10n.muraiErrorClear,
              icon: const Icon(Icons.delete_outline),
              onPressed: () async {
                final confirmed = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: Text(l10n.muraiErrorClear),
                    content: Text(l10n.muraiErrorClearConfirm),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancelTooltip)),
                      FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.deleteButtonLabel)),
                    ],
                  ),
                );
                if (confirmed == true) {
                  await MuraiErrorLogger.instance.clear();
                  if (mounted) setState(() {});
                }
              },
            ),
          ],
        ],
      ),
      body: entries.isEmpty
          ? Center(child: Text(l10n.muraiErrorEmpty, textAlign: TextAlign.center))
          : ListView.separated(
              padding: const EdgeInsets.all(8),
              itemCount: entries.length,
              separatorBuilder: (context, i) => const Divider(),
              itemBuilder: (context, i) {
                final entry = entries[i];
                return ExpansionTile(
                  title: Text(
                    entry['error']?.toString() ?? '',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
                  ),
                  subtitle: Text(entry['t']?.toString() ?? ''),
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: SelectableText(
                        'context: ${entry['context'] ?? '-'}\n\n${entry['stack'] ?? '-'}',
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                      ),
                    ),
                  ],
                );
              },
            ),
    );
  }

  Future<void> _export() async {
    final text = MuraiErrorLogger.instance.entries.map((e) => '${e['t']} | ${e['error']}\n${e['stack'] ?? ''}\n---').join('\n');
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(context.l10n.muraiErrorExport),
        content: SelectableText(text, style: const TextStyle(fontSize: 11)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(context.l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(context.l10n.applyButtonLabel)),
        ],
      ),
    );
    if (result != true || !mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.muraiErrorExported)));
  }
}
