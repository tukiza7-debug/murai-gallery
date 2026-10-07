import 'dart:math';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/sort.dart';
import 'package:aves/model/filters/filters.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/view/src/source/section.dart';
import 'package:aves/view/src/source/sort.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/thumbnail/image.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:provider/provider.dart';

/// Murai Gallery advanced sort bottom sheet:
/// 10 sort types with ascending/descending toggle, live preview,
/// group-by combination, per-screen persistence and named presets.
class MuraiSortSheet extends StatefulWidget {
  final Set<CollectionFilter> filters;
  final SortFactor initialFactor;
  final bool initialReverse;
  final EntrySectionFactor initialSection;
  final VoidCallback onApplied;

  const new({
    super.key,
    required this.filters,
    required this.initialFactor,
    required this.initialReverse,
    required this.initialSection,
    required this.onApplied,
  });

  static Future<void> show(BuildContext context, {required VoidCallback onApplied}) {
    final filters = context.read<Set<CollectionFilter>>();
    return showModalBottomSheet(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => Consumer<Settings>(builder: (context, s, child) {
        return MuraiSortSheet(
          filters: filters,
          initialFactor: s.getEffectiveCollectionSortFactor(filters),
          initialReverse: s.getEffectiveCollectionSortReverse(filters),
          initialSection: s.getEffectiveCollectionSectionFactor(filters),
          onApplied: onApplied,
        );
      }),
    );
  }

  @override
  State<MuraiSortSheet> createState() => _MuraiSortSheetState();
}

class _MuraiSortSheetState extends State<MuraiSortSheet> {
  late SortFactor _factor = widget.initialFactor;
  late bool _reverse = widget.initialReverse;
  late EntrySectionFactor _section = widget.initialSection;

  static const advancedOptions = <SortFactor>[
    .date,
    .dateAdded,
    .albumItemName,
    .size,
    .type,
    .resolution,
    .duration,
    .rating,
    .location,
    .random,
  ];

  static const sectionOptions = <EntrySectionFactor>[
    .none,
    .day,
    .month,
    .year,
    .album,
    .type,
    .location,
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(child: Text(l10n.muraiAdvancedSort, style: Theme.of(context).textTheme.titleLarge)),
                IconButton(
                  tooltip: l10n.muraiSortReshuffle,
                  icon: const Icon(Symbols.shuffle),
                  onPressed: _factor != .random ? null : () => _apply(reshuffle: true),
                ),
                IconButton(
                  tooltip: l10n.muraiSortToggleDirection,
                  icon: Icon(_reverse ? Symbols.arrow_upward : Symbols.arrow_downward),
                  onPressed: () => setState(() => _reverse = !_reverse),
                ),
              ],
            ),
            Text(l10n.muraiSortOrderHint(_factor.getOrderName(context, _reverse)), style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 8),
            _buildPreview(),
            const SizedBox(height: 8),
            RadioGroup<SortFactor>(
              groupValue: _factor,
              onChanged: (v) => setState(() => _factor = v ?? _factor),
              child: Column(
                children: advancedOptions
                    .map((factor) => RadioListTile<SortFactor>(
                          value: factor,
                          title: Text(factor.getName(context)),
                          secondary: Icon(factor.icon),
                          dense: true,
                        ))
                    .toList(),
              ),
            ),
            const Divider(),
            Text(l10n.viewDialogGroupSectionTitle, style: Theme.of(context).textTheme.titleSmall),
            Wrap(
              spacing: 8,
              children: sectionOptions
                  .map((section) => ChoiceChip(
                        label: Text(section.getName(context)),
                        selected: _section == section,
                        onSelected: (_) => setState(() => _section = section),
                      ))
                  .toList(),
            ),
            const Divider(),
            _buildPresets(context),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Symbols.save),
                    label: Text(l10n.muraiSortSavePreset),
                    onPressed: _savePreset,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.icon(
                    icon: const Icon(AIcons.sort),
                    label: Text(l10n.applyButtonLabel),
                    onPressed: () => _apply(setAsDefault: false),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: TextButton.icon(
                icon: const Icon(Symbols.push_pin),
                label: Text(l10n.muraiSortSetDefault),
                onPressed: () => _apply(setAsDefault: true),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPreview() {
    final entries = _previewEntries();
    if (entries.isEmpty) {
      return SizedBox(
        height: 56,
        child: Center(child: Text(context.l10n.muraiSortEmptyPreview, style: Theme.of(context).textTheme.bodySmall)),
      );
    }
    return SizedBox(
      height: 64,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: entries.length,
        separatorBuilder: (context, i) => const SizedBox(width: 4),
        itemBuilder: (context, i) {
          final entry = entries[i];
          return ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: ThumbnailImage(
              entry: entry,
              extent: 64,
              devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
              fit: .cover,
            ),
          );
        },
      ),
    );
  }

  List<AvesEntry> _previewEntries() {
    final source = context.read<CollectionLens>();
    final entries = source.source.visibleEntries.toList();
    int Function(AvesEntry, AvesEntry) comparator;
    switch (_factor) {
      case .date:
        comparator = AvesEntrySort.compareByDate;
      case .dateAdded:
        comparator = AvesEntrySort.compareByDateAdded;
      case .albumItemName:
        comparator = AvesEntrySort.compareByName;
      case .size:
        comparator = AvesEntrySort.compareBySize;
      case .type:
        comparator = AvesEntrySort.compareByType;
      case .resolution:
        comparator = AvesEntrySort.compareByResolution;
      case .duration:
        comparator = AvesEntrySort.compareByDuration;
      case .rating:
        comparator = AvesEntrySort.compareByRating;
      case .location:
        comparator = AvesEntrySort.compareByLocation;
      case .random:
        final rng = Random(42);
        final ranks = {for (final entry in entries) entry: rng.nextInt(1 << 31)};
        comparator = (a, b) => (ranks[a] ?? 0).compareTo(ranks[b] ?? 0);
      case .path:
        comparator = AvesEntrySort.compareByPath;
      case .chipName:
      case .count:
        comparator = AvesEntrySort.compareByName;
    }
    entries.sort(comparator);
    if (_reverse && _factor != .random) {
      return entries.reversed.take(8).toList();
    }
    return entries.take(8).toList();
  }

  Widget _buildPresets(BuildContext context) {
    final l10n = context.l10n;
    final presets = MuraiPrefs.getSortPresets();
    if (presets.isEmpty) {
      return Text(l10n.muraiSortNoPresets, style: Theme.of(context).textTheme.bodySmall);
    }
    return Wrap(
      spacing: 8,
      children: presets.map((preset) {
        final name = preset['name']?.toString() ?? '';
        return InputChip(
          label: Text(name),
          tooltip: l10n.muraiSortPresetDeleteHint,
          onPressed: () {
            setState(() {
              _factor = SortFactor.values.asNameMap()[preset['factor']] ?? _factor;
              _reverse = preset['reverse'] == true;
              _section = EntrySectionFactor.values.asNameMap()[preset['section']] ?? .none;
            });
          },
          onDeleted: () async {
            final confirmed = await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: Text(l10n.muraiSortDeletePresetTitle),
                content: Text(l10n.muraiSortDeletePresetText(name)),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(context, false), child: Text(context.l10n.cancelTooltip)),
                  FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(context.l10n.deleteButtonLabel)),
                ],
              ),
            );
            if (confirmed == true) {
              await MuraiPrefs.deleteSortPreset(name);
              if (mounted) setState(() {});
            }
          },
        );
      }).toList(),
    );
  }

  Future<void> _savePreset() async {
    final controller = TextEditingController(text: _factor.getName(context));
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(context.l10n.muraiSortSavePreset),
        content: TextField(controller: controller, autofocus: true, decoration: InputDecoration(hintText: context.l10n.muraiSortPresetNameHint)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(context.l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, controller.text.trim()), child: Text(context.l10n.applyButtonLabel)),
        ],
      ),
    );
    if (name != null && name.isNotEmpty) {
      await MuraiPrefs.saveSortPreset(name: name, preset: {
        'factor': _factor.name,
        'reverse': _reverse,
        'section': _section.name,
      });
      if (mounted) setState(() {});
    }
  }

  void _apply({bool setAsDefault = false, bool reshuffle = false}) {
    final settings = context.read<Settings>();
    settings.setStoredCollectionSortFactor(widget.filters, _factor);
    settings.setStoredCollectionSortReverse(widget.filters, _reverse);
    settings.setStoredCollectionSectionFactor(widget.filters, _section);
    if (setAsDefault) {
      _setAsDefault(settings);
    }
    Navigator.pop(context);
    widget.onApplied();
  }

  /// murai: propagate this sort to every screen (home, albums, tags, countries,
  /// states, places, search results) so it becomes the app-wide default.
  void _setAsDefault(Settings settings) {
    final factor = _factor.name;
    final reverse = _reverse;
    final section = _section.name;
    const factorKeys = [
      SettingKeys.collectionSortFactorKey,
      SettingKeys.albumSortFactorKey,
      SettingKeys.countrySortFactorKey,
      SettingKeys.stateSortFactorKey,
      SettingKeys.placeSortFactorKey,
      SettingKeys.tagSortFactorKey,
    ];
    const reverseKeys = [
      SettingKeys.collectionSortReverseKey,
      SettingKeys.albumSortReverseKey,
      SettingKeys.countrySortReverseKey,
      SettingKeys.stateSortReverseKey,
      SettingKeys.placeSortReverseKey,
      SettingKeys.tagSortReverseKey,
    ];
    const sectionKeys = [
      SettingKeys.collectionSectionFactorKey,
      SettingKeys.albumSectionFactorKey,
      SettingKeys.tagSectionFactorKey,
    ];
    for (final key in factorKeys) {
      settings.set(key, factor);
    }
    for (final key in reverseKeys) {
      settings.set(key, reverse);
    }
    for (final key in sectionKeys) {
      settings.set(key, section);
    }
  }
}
