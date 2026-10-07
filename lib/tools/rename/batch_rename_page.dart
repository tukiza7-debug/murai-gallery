import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

/// Murai Gallery Batch Rename: pattern-based renaming with live preview.
/// Patterns: {date}, {time}, {counter}, {name}, {ext} and free text.
class MuraiBatchRenamePage extends StatefulWidget {
  static const routeName = '/murai-rename';

  const new({super.key});

  @override
  State<MuraiBatchRenamePage> createState() => _MuraiBatchRenamePageState();
}

class _MuraiBatchRenamePageState extends State<MuraiBatchRenamePage> {
  AvesEntry? _albumEntry;
  List<AvesEntry> _entries = [];
  final Set<String> _selectedUris = {};
  String _pattern = '{date}_{counter}';
  int _counterStart = 1;
  bool _working = false;

  List<AvesEntry> get _albumEntries {
    final source = context.read<CollectionSource>();
    final directory = _albumEntry?.directory;
    if (directory == null) return source.visibleEntries.where((e) => e.path != null).toList();
    return source.visibleEntries.where((e) => e.directory == directory && e.path != null).toList();
  }

  String _newName(AvesEntry entry, int index) {
    final now = entry.bestDate ?? DateTime.now();
    final date = DateFormat('yyyyMMdd').format(now);
    final time = DateFormat('HHmmss').format(now);
    final oldName = entry.fileNameWithoutExtension ?? '';
    final ext = (entry.extension ?? '.jpg').replaceFirst('.', '');
    return _pattern
        .replaceAll('{date}', date)
        .replaceAll('{time}', time)
        .replaceAll('{counter}', '${_counterStart + index}')
        .replaceAll('{name}', oldName)
        .replaceAll('{ext}', ext)
        .replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
  }

  Future<void> _apply() async {
    if (_working) return;
    final l10n = context.l10n;
    final toRename = _entries.where((e) => _selectedUris.contains(e.uri)).toList();
    if (toRename.isEmpty) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.muraiRenameTitle),
        content: Text(l10n.muraiRenameConfirm(toRename.length)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.applyButtonLabel)),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _working = true);
    try {
      var counter = 0;
      final renames = <AvesEntry, String>{};
      for (final entry in toRename) {
        final newName = _newName(entry, counter++);
        if (newName.isNotEmpty && newName != entry.fileNameWithoutExtension) {
          renames[entry] = newName;
        }
      }
      if (renames.isNotEmpty) {
        final opEvents = mediaEditService.rename(opId: mediaEditService.newOpId, entriesToNewName: renames);
        await for (final _ in opEvents) {
          // rename ops report through the stream; source refreshes itself
        }
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiRenameDone(renames.length))));
      }
    } catch (e) {
      debugPrint('batch rename failed: $e');
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final albums = source.rawAlbums.toList()..sort();

    _entries = _albumEntries;
    _selectedUris.addAll(_entries.map((e) => e.uri));

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiRenameTitle)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                DropdownButtonFormField<String>(
                  initialValue: _albumEntry?.directory,
                  decoration: InputDecoration(labelText: l10n.muraiRenameAlbum, border: const OutlineInputBorder()),
                  items: albums.map((album) => DropdownMenuItem(value: album, child: Text(album, overflow: TextOverflow.ellipsis))).toList(),
                  onChanged: (v) => setState(() {
                    _albumEntry = v == null ? null : _albumEntries.firstWhere((e) => e.directory == v, orElse: () => _entries.first);
                    _selectedUris.clear();
                  }),
                ),
                const SizedBox(height: 8),
                TextField(
                  decoration: InputDecoration(
                    labelText: l10n.muraiRenamePattern,
                    helperText: l10n.muraiRenamePatternHint('{date}', '{time}', '{counter}', '{name}', '{ext}'),
                    border: const OutlineInputBorder(),
                  ),
                  controller: TextEditingController(text: _pattern),
                  onChanged: (v) => _pattern = v,
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text(l10n.muraiRenameCounterStart),
                    Expanded(
                      child: Slider(
                        value: _counterStart.toDouble(),
                        min: 0,
                        max: 999,
                        divisions: 999,
                        label: '$_counterStart',
                        onChanged: (v) => setState(() => _counterStart = v.round()),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const Divider(),
          Expanded(
            child: _entries.isEmpty
                ? Center(child: Text(l10n.muraiRenameNoFiles))
                : ListView.builder(
                    itemCount: _entries.length.clamp(0, 200),
                    itemBuilder: (context, i) {
                      final entry = _entries[i];
                      final selected = _selectedUris.contains(entry.uri);
                      return CheckboxListTile(
                        value: selected,
                        onChanged: (v) => setState(() {
                          v == true ? _selectedUris.add(entry.uri) : _selectedUris.remove(entry.uri);
                        }),
                        secondary: SizedBox(
                          width: 48,
                          height: 48,
                          child: ThumbnailImage(entry: entry, extent: 48, devicePixelRatio: MediaQuery.devicePixelRatioOf(context), fit: .cover),
                        ),
                        title: Text(entry.fileNameWithoutExtension ?? '', maxLines: 1, overflow: TextOverflow.ellipsis),
                        subtitle: Text(
                          _newName(entry, i),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: Theme.of(context).colorScheme.primary),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: FilledButton.icon(
            icon: _working ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.drive_file_rename_outline),
            label: Text(l10n.muraiRenameApply(_selectedUris.length)),
            onPressed: _working || _selectedUris.isEmpty ? null : _apply,
          ),
        ),
      ),
    );
  }
}
