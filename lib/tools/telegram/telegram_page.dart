import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/model/entry/extensions/images.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;


import 'package:provider/provider.dart';

/// Murai Gallery Share to Telegram: sends selected media through the Telegram
/// Bot API (bot token + chat id stored encrypted), with progress notification
/// and automatic retries.
class MuraiTelegramConfig {
  static const _tokenKey = 'murai_telegram_bot_token';
  static const _chatIdKey = 'murai_telegram_chat_id';

  static Future<String> getBotToken() async => await securityService.readValue(_tokenKey) ?? '';
  static Future<void> setBotToken(String v) => securityService.writeValue(_tokenKey, v);
  static Future<String> getChatId() async => await securityService.readValue(_chatIdKey) ?? '';
  static Future<void> setChatId(String v) => securityService.writeValue(_chatIdKey, v);
}

class MuraiTelegramPage extends StatefulWidget {
  static const routeName = '/murai-telegram';

  const new({super.key});

  @override
  State<MuraiTelegramPage> createState() => _MuraiTelegramPageState();
}

class _MuraiTelegramPageState extends State<MuraiTelegramPage> {
  final _tokenController = TextEditingController();
  final _chatIdController = TextEditingController();
  bool _loadingConfig = true;
  final Set<AvesEntry> _selected = {};
  bool _sending = false;
  double _progress = 0;
  String _status = '';
  static const _notificationId = 905;

  @override
  void initState() {
    super.initState();
    _loadConfig();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final source = context.read<CollectionSource>();
      setState(() {
        _selected.addAll(source.visibleEntries.take(6));
      });
    });
  }

  Future<void> _loadConfig() async {
    _tokenController.text = await MuraiTelegramConfig.getBotToken();
    _chatIdController.text = await MuraiTelegramConfig.getChatId();
    if (mounted) setState(() => _loadingConfig = false);
  }

  Future<void> _saveConfig() async {
    final l10n = context.l10n;
    await MuraiTelegramConfig.setBotToken(_tokenController.text.trim());
    await MuraiTelegramConfig.setChatId(_chatIdController.text.trim());
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiTelegramSaved)));
    }
  }

  Future<bool> _testConnection() async {
    final token = _tokenController.text.trim();
    if (token.isEmpty) return false;
    try {
      final res = await http.get(Uri.parse('https://api.telegram.org/bot$token/getMe')).timeout(const Duration(seconds: 10));
      return res.statusCode == 200 && jsonDecode(res.body)['ok'] == true;
    } catch (_) {
      return false;
    }
  }

  Future<void> _send() async {
    if (_sending || _selected.isEmpty) return;
    final l10n = context.l10n;
    final token = _tokenController.text.trim();
    final chatId = _chatIdController.text.trim();
    if (token.isEmpty || chatId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.muraiTelegramNeedConfig)));
      return;
    }
    await _saveConfig();

    setState(() {
      _sending = true;
      _progress = 0;
      _status = '';
    });
    unawaited(MuraiChannel.notifyProgress(
      id: _notificationId,
      title: l10n.muraiTelegramTitle,
      text: l10n.muraiTelegramSending(0, _selected.length),
      progress: 0,
    ));

    var sent = 0;
    var failed = 0;
    final entries = _selected.toList();
    for (var i = 0; i < entries.length; i++) {
      final entry = entries[i];
      final ok = await _sendEntry(token, chatId, entry, i, entries.length);
      ok ? sent++ : failed++;
      setState(() => _progress = (i + 1) / entries.length);
      unawaited(MuraiChannel.notifyProgress(
        id: _notificationId,
        title: l10n.muraiTelegramTitle,
        text: l10n.muraiTelegramSending(i + 1, entries.length),
        progress: ((i + 1) / entries.length * 100).round(),
      ));
    }
    await MuraiChannel.notifyFinished(
      id: _notificationId,
      title: l10n.muraiTelegramTitle,
      text: l10n.muraiTelegramDoneText(sent, failed),
    );
    if (mounted) {
      setState(() {
        _sending = false;
        _status = l10n.muraiTelegramDoneText(sent, failed);
      });
    }
  }

  Future<bool> _sendEntry(String token, String chatId, AvesEntry entry, int index, int total) async {
    final path = entry.path;
    if (path == null || !File(path).existsSync()) return false;
    final isVideo = entry.mimeType.startsWith('video/');
    const maxRetries = 3;
    for (var attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        final bytes = await File(path).readAsBytes();
        final uri = Uri.parse('https://api.telegram.org/bot$token/${isVideo ? 'sendVideo' : 'sendPhoto'}');
        final request = http.MultipartRequest('POST', uri)
          ..fields['chat_id'] = chatId
          ..files.add(http.MultipartFile.fromBytes(
            isVideo ? 'video' : 'photo',
            bytes,
            filename: entry.bestTitle ?? 'media',

          ));
        final streamed = await request.send().timeout(const Duration(minutes: 5));
        final body = await streamed.stream.bytesToString();
        if (streamed.statusCode == 200) {
          final ok = jsonDecode(body)['ok'] == true;
          if (ok) return true;
        }
        if (attempt == maxRetries) return false;
      } catch (e) {
        debugPrint('telegram send attempt $attempt failed: $e');
        if (attempt == maxRetries) return false;
      }
      // exponential backoff before retry
      await Future.delayed(Duration(seconds: 1 << (attempt - 1)));
    }
    return false;
  }

  @override
  void dispose() {
    _tokenController.dispose();
    _chatIdController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final source = context.read<CollectionSource>();
    final media = source.visibleEntries.where((e) => e.path != null).toList();

    if (_loadingConfig) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.muraiTelegramTitle)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(l10n.muraiTelegramTitle)),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          TextField(
            controller: _tokenController,
            obscureText: true,
            decoration: InputDecoration(labelText: l10n.muraiTelegramToken, border: const OutlineInputBorder()),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _chatIdController,
            decoration: InputDecoration(labelText: l10n.muraiTelegramChatId, helperText: l10n.muraiTelegramChatIdHint, border: const OutlineInputBorder()),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              OutlinedButton.icon(
                icon: const Icon(Icons.wifi_tethering),
                label: Text(l10n.muraiTelegramTest),
                onPressed: () async {
                  final ok = await _testConnection();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ok ? l10n.muraiTelegramTestOk : l10n.muraiTelegramTestFail)));
                  }
                },
              ),
              const SizedBox(width: 8),
              FilledButton.icon(
                icon: const Icon(Icons.save),
                label: Text(l10n.muraiTelegramSaveConfig),
                onPressed: _saveConfig,
              ),
            ],
          ),
          const Divider(),
          Text(l10n.muraiTelegramPick(_selected.length)),
          if (_sending) ...[
            LinearProgressIndicator(value: _progress),
            const SizedBox(height: 4),
            Text(_status),
          ],
          SizedBox(
            height: 320,
            child: GridView.builder(
              padding: const EdgeInsets.symmetric(vertical: 8),
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 120, mainAxisSpacing: 4, crossAxisSpacing: 4),
              itemCount: media.length,
              itemBuilder: (context, i) {
                final entry = media[i];
                final selected = _selected.contains(entry);
                return GestureDetector(
                  onTap: () => setState(() {
                    selected ? _selected.remove(entry) : _selected.add(entry);
                  }),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image(
                          image: entry.getThumbnail(extent: 120),
                          fit: .cover,
                        ),
                      ),
                      PositionedDirectional(
                        top: 4,
                        end: 4,
                        child: Icon(
                          selected ? Icons.check_circle : Icons.radio_button_unchecked,
                          color: selected ? Theme.of(context).colorScheme.primary : Colors.white,
                        ),
                      ),
                    ],
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
            icon: _sending ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.send),
            label: Text(l10n.muraiTelegramSend(_selected.length)),
            onPressed: _sending || _selected.isEmpty ? null : _send,
          ),
        ),
      ),
    );
  }
}
