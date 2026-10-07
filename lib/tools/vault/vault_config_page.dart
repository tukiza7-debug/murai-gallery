import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_prefs.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/settings/privacy/hidden_items_page.dart';
import 'package:flutter/material.dart';

/// Murai Gallery vault upgrade:
/// - decoy PIN that opens a harmless fake "Gallery" page instead of the vault
/// - vault content stays hidden from recents while locked
class MuraiVaultConfigPage extends StatefulWidget {
  static const routeName = '/murai-vault-config';

  const new({super.key});

  static Future<void> open(BuildContext context) {
    return Navigator.of(context).push(MaterialPageRoute(builder: (context) => const MuraiVaultConfigPage()));
  }

  @override
  State<MuraiVaultConfigPage> createState() => _MuraiVaultConfigPageState();
}

class _MuraiVaultConfigPageState extends State<MuraiVaultConfigPage> {
  static const _decoyPinKey = 'murai_vault_decoy_pin';

  bool _decoyEnabled = false;
  bool _loading = true;
  final _pinController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _decoyEnabled = MuraiPrefs.getVaultDecoyEnabled();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final hasPin = (await securityService.readValue(_decoyPinKey))?.isNotEmpty == true;
      if (mounted) {
        setState(() {
          _decoyEnabled = _decoyEnabled && hasPin;
          _loading = false;
        });
      }
    });
  }

  @override
  void dispose() {
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _setDecoyPin() async {
    final l10n = context.l10n;
    final pin = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.muraiVaultDecoyPinTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l10n.muraiVaultDecoyPinHint),
            const SizedBox(height: 12),
            TextField(
              controller: _pinController,
              autofocus: true,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: 12,
              decoration: InputDecoration(hintText: l10n.muraiVaultDecoyPinField, border: const OutlineInputBorder()),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancelTooltip)),
          FilledButton(onPressed: () => Navigator.pop(context, _pinController.text.trim()), child: Text(l10n.applyButtonLabel)),
        ],
      ),
    );
    if (pin != null && pin.length >= 4) {
      await securityService.writeValue(_decoyPinKey, pin);
      await MuraiPrefs.setVaultDecoyEnabled(true);
      if (mounted) {
        setState(() => _decoyEnabled = true);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiVaultDecoySet)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiVaultTitle)),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: SwitchListTile(
                    title: Text(l10n.muraiVaultDecoyTitle),
                    subtitle: Text(l10n.muraiVaultDecoySubtitle),
                    value: _decoyEnabled,
                    onChanged: (v) async {
                      if (v) {
                        await _setDecoyPin();
                      } else {
                        await MuraiPrefs.setVaultDecoyEnabled(false);
                        if (mounted) setState(() => _decoyEnabled = false);
                      }
                    },
                  ),
                ),
                const SizedBox(height: 8),
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.security),
                    title: Text(l10n.muraiVaultLockTypes),
                    subtitle: Text(l10n.muraiVaultLockTypesHint),
                    onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (context) => const HiddenItemsPage())),
                  ),
                ),
                const SizedBox(height: 8),
                Text(l10n.muraiVaultRecentsNote, style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: 8),
                Text(l10n.muraiVaultBiometricNote, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
    );
  }
}

/// Decoy page shown when the decoy PIN is entered: a harmless, empty "Gallery".
class MuraiDecoyPage extends StatelessWidget {
  static const routeName = '/murai-decoy';

  const new({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiDecoyTitle)),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.photo_library_outlined, size: 72, color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: 16),
            Text(l10n.muraiDecoyEmpty, style: Theme.of(context).textTheme.bodyLarge),
          ],
        ),
      ),
    );
  }
}
