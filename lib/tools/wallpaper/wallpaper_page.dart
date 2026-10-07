import 'package:aves/model/source/collection_source.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

/// Murai Gallery Auto Wallpaper Changer settings: pick albums, choose the
/// interval (hourly / every 6h / daily / weekly), shuffle, apply now.
/// Scheduling is performed on the platform side via WorkManager.
class MuraiWallpaperPage extends StatefulWidget {
  static const routeName = '/murai-wallpaper';

  const new({super.key});

  @override
  State<MuraiWallpaperPage> createState() => _MuraiWallpaperPageState();
}

class _MuraiWallpaperPageState extends State<MuraiWallpaperPage> {
  late final List<String> _albums = MuraiPrefs.getWallpaperAlbums();
  late bool _shuffle = MuraiPrefs.getWallpaperShuffle();
  late int _interval = MuraiPrefs.getWallpaperInterval();
  bool _applying = false;

  static const intervals = <int>[0, 60, 360, 1440, 10080];

  Future<void> _persistAndSchedule() async {
    final l10n = context.l10n;
    await MuraiPrefs.setWallpaperAlbums(_albums);
    await MuraiPrefs.setWallpaperShuffle(_shuffle);
    await MuraiPrefs.setWallpaperInterval(_interval);
    try {
      if (_interval <= 0 || _albums.isEmpty) {
        await MuraiChannel.cancelWallpaperChange();
      } else {
        await MuraiChannel.scheduleWallpaperChange(intervalMinutes: _interval);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(_interval <= 0 ? l10n.muraiWallpaperOff : l10n.muraiWallpaperScheduled)));
      }
    } catch (e) {
      debugPrint('wallpaper schedule failed: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiWallpaperScheduleFailed)));
      }
    }
  }

  Future<void> _applyNow() async {
    if (_applying) return;
    final l10n = context.l10n;
    setState(() => _applying = true);
    try {
      await MuraiPrefs.setWallpaperAlbums(_albums);
      await MuraiPrefs.setWallpaperShuffle(_shuffle);
      await MuraiChannel.applyWallpaperNow();
      await Future.delayed(const Duration(seconds: 2));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiWallpaperApplied)));
      }
    } catch (e) {
      debugPrint('wallpaper apply failed: $e');
    } finally {
      if (mounted) setState(() => _applying = false);
    }
  }

  String _intervalLabel(BuildContext context) {
    final l10n = context.l10n;
    switch (_interval) {
      case 0:
        return l10n.muraiWallpaperOff;
      case 60:
        return l10n.muraiWallpaperHourly;
      case 360:
        return l10n.muraiWallpaper6h;
      case 1440:
        return l10n.muraiWallpaperDaily;
      case 10080:
        return l10n.muraiWallpaperWeekly;
      default:
        return '$_interval min';
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final allAlbums = source.rawAlbums.toList()..sort();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiWallpaperTitle)),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          Text(l10n.muraiWallpaperAlbums, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          if (allAlbums.isEmpty)
            Text(l10n.muraiWallpaperNoAlbums)
          else
            ...allAlbums.map((album) {
              final selected = _albums.contains(album);
              return CheckboxListTile(
                dense: true,
                value: selected,
                title: Text(album, maxLines: 1, overflow: TextOverflow.ellipsis),
                onChanged: (v) => setState(() {
                  v == true ? _albums.add(album) : _albums.remove(album);
                }),
              );
            }),
          const Divider(),
          SwitchListTile(
            title: Text(l10n.muraiWallpaperShuffle),
            value: _shuffle,
            onChanged: (v) => setState(() => _shuffle = v),
          ),
          ListTile(
            title: Text(l10n.muraiWallpaperInterval),
            trailing: DropdownButton<int>(
              value: _interval,
              items: intervals
                  .map((v) => DropdownMenuItem(value: v, child: Text(_intervalLabel(context))))
                  .toList(),
              onChanged: (v) => setState(() => _interval = v ?? 0),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.wallpaper),
                  label: Text(l10n.muraiWallpaperApplyNow),
                  onPressed: _albums.isEmpty ? null : _applyNow,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.icon(
                  icon: const Icon(Icons.schedule),
                  label: Text(l10n.muraiWallpaperSaveSchedule),
                  onPressed: _persistAndSchedule,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(l10n.muraiWallpaperHint, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}
