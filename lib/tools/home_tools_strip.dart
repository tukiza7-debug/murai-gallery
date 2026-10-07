import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/tools/tools_page.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';

/// Compact horizontal strip of Murai tools shown at the top of the home page.
/// Can be dismissed; comes back when new tools are highlighted.
class MuraiHomeToolsStrip extends StatefulWidget {
  const new({super.key});

  @override
  State<MuraiHomeToolsStrip> createState() => _MuraiHomeToolsStripState();
}

class _MuraiHomeToolsStripState extends State<MuraiHomeToolsStrip> {
  late bool _dismissed = MuraiPrefs.getHomeToolsDismissed();

  @override
  Widget build(BuildContext context) {
    if (_dismissed) return const SizedBox();
    final l10n = context.l10n;
    final tools = <(IconData, String)>[
      (Icons.copy_all, l10n.muraiDupTitle),
      (Icons.tune, l10n.muraiEditorTitle),
      (Icons.compress, l10n.muraiCompressTitle),
      (Icons.animation, l10n.muraiGifTitle),
      (Icons.qr_code_scanner, l10n.muraiQrTitle),
      (Icons.cleaning_services, l10n.muraiCleanTitle),
    ];
    return Card(
      margin: const EdgeInsets.fromLTRB(8, 4, 8, 4),
      child: SizedBox(
        height: 76,
        child: Stack(
          children: [
            ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              children: [
                ...tools.map((tool) => MuraiToolChip(
                      icon: tool.$1,
                      label: tool.$2,
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (context) => const MuraiToolsPage())),
                    )),
                MuraiToolChip(
                  icon: Icons.expand_more,
                  label: l10n.muraiToolsAll,
                  filled: true,
                  onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (context) => const MuraiToolsPage())),
                ),
              ],
            ),
            PositionedDirectional(
              top: 0,
              end: 0,
              child: InkResponse(
                onTap: () {
                  MuraiPrefs.setHomeToolsDismissed(true);
                  setState(() => _dismissed = true);
                },
                child: const Padding(
                  padding: EdgeInsets.all(6),
                  child: Icon(Icons.close, size: 18),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class MuraiToolChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool filled;

  const new({super.key, required this.icon, required this.label, required this.onTap, this.filled = false});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: SizedBox(
        width: 72,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 26, color: theme.colorScheme.primary),
              const SizedBox(height: 4),
              Text(
                label,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: theme.textTheme.labelSmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
