import 'package:aves/theme/icons.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/widgets.dart';
import 'package:material_symbols_icons/symbols.dart';

extension ExtraEntrySortFactorView on SortFactor {
  String getName(BuildContext context) {
    final l10n = context.l10n;
    return switch (this) {
      .date => l10n.sortByDate,
      .chipName => l10n.sortByName,
      .albumItemName => l10n.sortByAlbumFileName,
      .count => l10n.sortByItemCount,
      .size => l10n.sortBySize,
      .path => l10n.sortByPath,
      .rating => l10n.sortByRating,
      .duration => l10n.sortByDuration,
      // murai: extended sort factors
      .dateAdded => l10n.muraiSortByDateAdded,
      .type => l10n.muraiSortByType,
      .resolution => l10n.muraiSortByResolution,
      .location => l10n.muraiSortByLocation,
      .random => l10n.muraiSortByRandom,
    };
  }

  IconData get icon {
    return switch (this) {
      .date => AIcons.date,
      .chipName => AIcons.name,
      .albumItemName => AIcons.name,
      .count => AIcons.count,
      .size => AIcons.size,
      .path => AIcons.path,
      .rating => AIcons.rating,
      .duration => AIcons.duration,
      // murai: extended sort factors
      .dateAdded => Symbols.today,
      .type => Symbols.folder,
      .resolution => Symbols.photo_size_select_large,
      .location => Symbols.place,
      .random => Symbols.shuffle,
    };
  }

  String getOrderName(BuildContext context, bool reverse) {
    final l10n = context.l10n;
    return switch (this) {
      .date => reverse ? l10n.sortOrderOldestFirst : l10n.sortOrderNewestFirst,
      .chipName || .albumItemName || .path => reverse ? l10n.sortOrderZtoA : l10n.sortOrderAtoZ,
      .count || .size => reverse ? l10n.sortOrderSmallestFirst : l10n.sortOrderLargestFirst,
      .rating => reverse ? l10n.sortOrderLowestFirst : l10n.sortOrderHighestFirst,
      .duration => reverse ? l10n.sortOrderShortestFirst : l10n.sortOrderLongestFirst,
      // murai: extended sort factors
      .dateAdded => reverse ? l10n.muraiSortOrderOldestAddedFirst : l10n.muraiSortOrderNewestAddedFirst,
      .type => reverse ? l10n.sortOrderZtoA : l10n.sortOrderAtoZ,
      .resolution => reverse ? l10n.muraiSortOrderSmallestFirst : l10n.muraiSortOrderLargestFirst,
      .location => reverse ? l10n.sortOrderZtoA : l10n.sortOrderAtoZ,
      .random => l10n.muraiSortOrderRandom,
    };
  }
}
