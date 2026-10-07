import 'dart:math';

import 'package:aves/locale/calendar/calendar_utils.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_lens.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/source/section_keys.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/collection/grid/headers/album.dart';
import 'package:aves/widgets/collection/grid/headers/date.dart';
import 'package:aves/widgets/collection/grid/headers/rating.dart';
import 'package:aves/widgets/common/grid/header.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:material_ui/material_ui.dart';

class const CollectionSectionHeader({
  super.key,
  required final CollectionLens collection,
  required final SectionKey sectionKey,
  required final double height,
  required final bool selectable,
}) extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final header = _buildHeader(context);
    return header != null
        ? SizedBox(
            height: height,
            child: header,
          )
        : const SizedBox();
  }

  Widget? _buildHeader(BuildContext context) {
    // murai: extended group-by headers apply to any sort
    final muraiHeader = _buildMuraiHeader(context);
    if (muraiHeader != null) return muraiHeader;
    switch (collection.sortFactor) {
      case .date:
        switch (collection.sectionFactor) {
          case .album:
            return _buildAlbumHeader(context);
          case .month:
            var k = sectionKey as EntryDateSectionKey;
            final date = collection.calendar.ops.fromYearMonthDay(k.year, k.month, k.day);
            return MonthSectionHeader<AvesEntry>(
              key: ValueKey(sectionKey),
              sectionKey: sectionKey,
              date: date,
              selectable: selectable,
            );
          case .day:
            var k = sectionKey as EntryDateSectionKey;
            final date = collection.calendar.ops.fromYearMonthDay(k.year, k.month, k.day);
            return DaySectionHeader<AvesEntry>(
              key: ValueKey(sectionKey),
              sectionKey: sectionKey,
              date: date,
              selectable: selectable,
            );
          case .none:
            break;
          case .name:
          case .rating:
          // murai: extended group-by factors (handled above)
          case .year:
          case .type:
          case .location:
            break;
        }
      case .albumItemName:
      case .path:
        return _buildAlbumHeader(context);
      case .rating:
        return RatingSectionHeader<AvesEntry>(
          key: ValueKey(sectionKey),
          rating: (sectionKey as EntryRatingSectionKey).rating,
          selectable: selectable,
        );
      case .size:
      case .duration:
        break;
      // murai: extended sort factors (headers handled by _buildMuraiHeader)
      case .dateAdded:
      case .type:
      case .resolution:
      case .location:
      case .random:
        break;
      case .chipName:
      case .count:
        throw UnimplementedError();
    }
    return null;
  }

  Widget? _buildMuraiHeader(BuildContext context) {
    final l10n = context.l10n;
    switch (collection.sectionFactor) {
      case .year:
        final k = sectionKey as EntryDateSectionKey;
        return SectionHeader<AvesEntry>(
          key: ValueKey(sectionKey),
          sectionKey: sectionKey,
          leading: const Icon(Symbols.calendar_today),
          title: k.year?.toString() ?? l10n.muraiGroupUnknown,
          selectable: selectable,
        );
      case .type:
        final k = sectionKey as EntryTypeSectionKey;
        return SectionHeader<AvesEntry>(
          key: ValueKey(sectionKey),
          sectionKey: sectionKey,
          leading: const Icon(Symbols.image),
          title: k.mimeType.toUpperCase(),
          selectable: selectable,
        );
      case .location:
        final k = sectionKey as EntryLocationSectionKey;
        return SectionHeader<AvesEntry>(
          key: ValueKey(sectionKey),
          sectionKey: sectionKey,
          leading: const Icon(Symbols.place),
          title: k.place ?? k.countryName ?? l10n.muraiGroupUnknown,
          selectable: selectable,
        );
      default:
        return null;
    }
  }

  Widget _buildAlbumHeader(BuildContext context) {
    final source = collection.source;
    final directory = (sectionKey as EntryAlbumSectionKey).directory;
    return AlbumSectionHeader(
      key: ValueKey(sectionKey),
      directory: directory,
      albumName: directory != null ? source.getStoredAlbumDisplayName(context, directory) : null,
      selectable: selectable,
    );
  }

  static double getPreferredHeight(BuildContext context, double maxWidth, CollectionSource source, SectionKey sectionKey) {
    var headerExtent = 0.0;
    if (sectionKey is EntryAlbumSectionKey) {
      // only compute height for album headers, as they're the only likely ones to split on multiple lines
      headerExtent = AlbumSectionHeader.getPreferredHeight(context, maxWidth, source, sectionKey);
    }

    final textScaler = MediaQuery.textScalerOf(context);
    headerExtent = max(headerExtent, textScaler.scale(SectionHeader.leadingSize.height)) + SectionHeader.padding.vertical + SectionHeader.margin.vertical;
    return headerExtent;
  }
}
