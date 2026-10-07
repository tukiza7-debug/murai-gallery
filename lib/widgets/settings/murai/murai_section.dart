import 'package:aves/theme/colors.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/tools/errors/error_library_page.dart';
import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/tools/tools_page.dart';
import 'package:aves/tools/update/update_check.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/settings/common/tile_leading.dart';
import 'package:aves/widgets/settings/common/tiles/switch_list.dart';
import 'package:aves/widgets/settings/settings_definition.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

/// Murai Gallery settings section: update checks, error library and tools hub.
class MuraiSection extends SettingsSection {

  @override
  String get key => 'murai';

  @override
  Widget icon(BuildContext context) => SettingsTileLeading(
    icon: AIcons.muraiTools,
    color: context.select<AvesColorsData, Color>((v) => v.fromString('murai')),
  );

  @override
  String title(BuildContext context) => context.l10n.muraiSettingsSectionTitle;

  @override
  Future<List<SettingsTile>> tiles(BuildContext context) => Future.value([
    SettingsTileMuraiUpdateCheck(),
    SettingsTileMuraiCheckNow(),
    SettingsTileMuraiErrorLibrary(),
    SettingsTileMuraiTools(),
  ]);
}

class SettingsTileMuraiUpdateCheck extends SettingsTile {

  @override
  List<String> get settingKeys => [MuraiPrefs.updateCheckEnabledKey];

  @override
  String title(BuildContext context) => context.l10n.muraiUpdateCheckTile;

  @override
  Widget build(BuildContext context) => SettingsSwitchListTile(
    selector: (context, s) => MuraiPrefs.getUpdateCheckEnabled(),
    onChanged: MuraiPrefs.setUpdateCheckEnabled,
    title: title,
  );
}

class SettingsTileMuraiCheckNow extends SettingsTile {

  @override
  List<String> get settingKeys => const [];

  @override
  String title(BuildContext context) => context.l10n.muraiUpdateCheckNow;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const Icon(Icons.system_update_outlined),
    title: Text(title(context)),
    subtitle: Text(context.l10n.muraiUpdateCheckNowHint),
    onTap: () => MuraiUpdater.checkNow(context),
  );
}

class SettingsTileMuraiErrorLibrary extends SettingsTile {

  @override
  List<String> get settingKeys => const [];

  @override
  String title(BuildContext context) => context.l10n.muraiErrorLibrary;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const Icon(Icons.bug_report_outlined),
    title: Text(title(context)),
    subtitle: Text(context.l10n.muraiErrorLibraryHint),
    onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (context) => const MuraiErrorLibraryPage())),
  );
}

class SettingsTileMuraiTools extends SettingsTile {

  @override
  List<String> get settingKeys => const [];

  @override
  String title(BuildContext context) => context.l10n.muraiToolsTitle;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const Icon(Icons.handyman_outlined),
    title: Text(title(context)),
    subtitle: Text(context.l10n.muraiToolsHint),
    onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (context) => const MuraiToolsPage())),
  );
}
