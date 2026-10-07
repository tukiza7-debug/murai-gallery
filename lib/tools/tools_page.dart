import 'package:aves/tools/backup/backup_page.dart';
import 'package:aves/tools/cleaner/storage_cleaner_page.dart';
import 'package:aves/tools/collage/collage_page.dart';
import 'package:aves/tools/compress/compressor_page.dart';
import 'package:aves/tools/duplicate/duplicate_finder_page.dart';
import 'package:aves/tools/editor/image_editor_page.dart';
import 'package:aves/tools/gif/gif_maker_page.dart';
import 'package:aves/tools/ocr/ocr_page.dart';
import 'package:aves/tools/qr/qr_scanner_page.dart';
import 'package:aves/tools/rename/batch_rename_page.dart';
import 'package:aves/tools/telegram/telegram_page.dart';
import 'package:aves/tools/trim/video_trimmer_page.dart';
import 'package:aves/tools/vault/vault_config_page.dart';
import 'package:aves/tools/wallpaper/wallpaper_page.dart';

import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/identity/aves_logo.dart';
import 'package:flutter/material.dart';
import 'package:material_symbols_icons/symbols.dart';
import 'package:permission_handler/permission_handler.dart';

/// Murai Gallery "New Tools" hub: all the extra features beyond the gallery core.
class MuraiToolsPage extends StatelessWidget {
  static const routeName = '/murai-tools';

  const new({super.key});

  static Future<void> ensureNotificationPermission(BuildContext context) async {
    final status = await Permission.notification.status;
    if (status.isGranted || status.isPermanentlyDenied) return;
    // short reason before the system request
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.notifications_active_outlined),
        title: Text(context.l10n.muraiPermissionNotificationTitle),
        content: Text(context.l10n.muraiPermissionNotificationReason),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(context.l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(context.l10n.applyButtonLabel)),
        ],
      ),
    );
    if (proceed == true) {
      await Permission.notification.request();
    }
  }

  List<MuraiToolEntry> _tools(BuildContext context) {
    final l10n = context.l10n;
    return [
      MuraiToolEntry(icon: Icons.copy_all, title: l10n.muraiDupTitle, subtitle: l10n.muraiDupSubtitle, route: const MuraiDuplicateFinderPage(), needsNotification: true),
      MuraiToolEntry(icon: Icons.tune, title: l10n.muraiEditorTitle, subtitle: l10n.muraiEditorSubtitle, route: const MuraiImageEditorPage()),
      MuraiToolEntry(icon: Icons.content_cut, title: l10n.muraiTrimTitle, subtitle: l10n.muraiTrimSubtitle, route: const MuraiVideoTrimmerPage(), needsNotification: true),
      MuraiToolEntry(icon: Icons.compress, title: l10n.muraiCompressTitle, subtitle: l10n.muraiCompressSubtitle, route: const MuraiCompressorPage(), needsNotification: true),
      MuraiToolEntry(icon: Icons.text_snippet_outlined, title: l10n.muraiOcrTitle, subtitle: l10n.muraiOcrSubtitle, route: const MuraiOcrPage()),
      MuraiToolEntry(icon: Icons.grid_view, title: l10n.muraiCollageTitle, subtitle: l10n.muraiCollageSubtitle, route: const MuraiCollagePage()),
      MuraiToolEntry(icon: Icons.animation, title: l10n.muraiGifTitle, subtitle: l10n.muraiGifSubtitle, route: const MuraiGifMakerPage(), needsNotification: true),
      MuraiToolEntry(icon: Icons.drive_file_rename_outline, title: l10n.muraiRenameTitle, subtitle: l10n.muraiRenameSubtitle, route: const MuraiBatchRenamePage()),
      MuraiToolEntry(icon: Icons.cleaning_services, title: l10n.muraiCleanTitle, subtitle: l10n.muraiCleanSubtitle, route: const MuraiStorageCleanerPage()),
      MuraiToolEntry(icon: Icons.qr_code_scanner, title: l10n.muraiQrTitle, subtitle: l10n.muraiQrSubtitle, route: const MuraiQrScannerPage()),
      MuraiToolEntry(icon: Icons.wallpaper, title: l10n.muraiWallpaperTitle, subtitle: l10n.muraiWallpaperSubtitle, route: const MuraiWallpaperPage()),
      MuraiToolEntry(icon: Icons.send_outlined, title: l10n.muraiTelegramTitle, subtitle: l10n.muraiTelegramSubtitle, route: const MuraiTelegramPage(), needsNotification: true),
      MuraiToolEntry(icon: Icons.lock_outline, title: l10n.muraiVaultTitle, subtitle: l10n.muraiVaultSubtitle, route: null, vaultConfig: true),
      MuraiToolEntry(icon: Icons.backup_outlined, title: l10n.muraiBackupTitle, subtitle: l10n.muraiBackupSubtitle, route: const MuraiBackupPage()),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final tools = _tools(context);
    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            const AvesLogo(size: 32),
            const SizedBox(width: 8),
            Text(l10n.muraiToolsTitle),
          ],
        ),
      ),
      body: GridView.builder(
        padding: const EdgeInsets.all(12),
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 180,
          mainAxisSpacing: 8,
          crossAxisSpacing: 8,
          childAspectRatio: .95,
        ),
        itemCount: tools.length,
        itemBuilder: (context, i) {
          final tool = tools[i];
          return MuraiToolCard(tool: tool);
        },
      ),
    );
  }
}

class MuraiToolEntry {
  final IconData icon;
  final String title;
  final String subtitle;
  final Widget? route;
  final bool needsNotification;
  final bool vaultConfig;

  const new({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.route,
    this.needsNotification = false,
    this.vaultConfig = false,
  });
}

class MuraiToolCard extends StatelessWidget {
  final MuraiToolEntry tool;

  const new({super.key, required this.tool});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () async {
        if (tool.needsNotification) {
          await MuraiToolsPage.ensureNotificationPermission(context);
        }
        if (!context.mounted) return;
        if (tool.vaultConfig) {
          await MuraiVaultConfigPage.open(context);
          return;
        }
        final route = tool.route;
        if (route != null) {
          await Navigator.of(context).push(MaterialPageRoute(builder: (context) => route));
        }
      },
      child: Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(tool.icon, size: 36, color: theme.colorScheme.primary),
              const SizedBox(height: 8),
              Text(
                tool.title,
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall,
              ),
              const SizedBox(height: 4),
              Text(
                tool.subtitle,
                textAlign: TextAlign.center,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Murai Gallery icon for the home strip.
class MuraiToolsIcon extends StatelessWidget {
  final double size;

  const new({super.key, required this.size});

  @override
  Widget build(BuildContext context) {
    return Icon(Symbols.handyman, size: size, color: Theme.of(context).colorScheme.primary);
  }
}
