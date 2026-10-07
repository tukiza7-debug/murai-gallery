import 'package:aves/theme/icons.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter/widgets.dart';
import 'package:material_symbols_icons/symbols.dart';

extension ExtraEntrySectionFactorView on EntrySectionFactor {
  String getName(BuildContext context) {
    final l10n = context.l10n;
    return switch (this) {
      .album => l10n.collectionGroupAlbum,
      .month => l10n.collectionGroupMonth,
      .day => l10n.collectionGroupDay,
      .none => l10n.sectionNone,
      // murai: extended group-by factors
      .year => l10n.muraiGroupYear,
      .type => l10n.muraiGroupType,
      .location => l10n.muraiGroupLocation,
      // unselectable
      .name => throw UnimplementedError(),
      .rating => throw UnimplementedError(),
    };
  }

  IconData get icon {
    return switch (this) {
      .album => AIcons.album,
      .month => AIcons.dateByMonth,
      .day => AIcons.dateByDay,
      .none => AIcons.clear,
      // murai: extended group-by factors
      .year => Symbols.calendar_today,
      .type => Symbols.image,
      .location => Symbols.place,
      // unselectable
      .name => AIcons.name,
      .rating => AIcons.rating,
    };
  }
}

extension ExtraChipSectionFactorView on ChipSectionFactor {
  String getName(BuildContext context) {
    final l10n = context.l10n;
    return switch (this) {
      .importance => l10n.albumGroupTier,
      .mimeType => l10n.albumGroupType,
      .volume => l10n.albumGroupVolume,
      .none => l10n.sectionNone,
    };
  }

  IconData get icon {
    return switch (this) {
      .importance => AIcons.important,
      .mimeType => AIcons.mimeType,
      .volume => AIcons.storageCard,
      .none => AIcons.clear,
    };
  }
}
