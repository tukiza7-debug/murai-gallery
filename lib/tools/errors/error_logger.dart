import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:aves/services/common/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// Murai Gallery error library.
/// Captures uncaught errors for 30 days, with zip export for reporting.
class MuraiErrorLogger {
  new _private();

  static final MuraiErrorLogger instance = MuraiErrorLogger._private();

  static const maxAgeDays = 30;
  static const maxEntries = 500;

  final List<Map<String, Object?>> _entries = [];
  bool _initialized = false;

  Future<String> get _logFile async {
    final dirPath = await storageService.getExternalCacheDirectory();
    final dir = Directory(dirPath.isEmpty ? Directory.systemTemp.path : dirPath);
    await dir.create(recursive: true);
    return '${dir.path}/murai_errors.log';
  }

  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;
    try {
      final path = await _logFile;
      final file = File(path);
      if (await file.exists()) {
        final lines = await file.readAsLines();
        final cutoff = DateTime.now().subtract(const Duration(days: maxAgeDays));
        for (final line in lines) {
          try {
            final map = Map<String, Object?>.from(json.decode(line));
            final t = DateTime.tryParse(map['t']?.toString() ?? '');
            if (t != null && t.isBefore(cutoff)) continue;
            _entries.add(map);
          } catch (_) {
            // skip malformed lines
          }
        }
        if (_entries.length > maxEntries) {
          _entries.removeRange(0, _entries.length - maxEntries);
        }
      }
    } catch (e) {
      debugPrint('MuraiErrorLogger init failed: $e');
    }
  }

  void record(Object error, StackTrace? stackTrace) {
    _add({
      't': DateTime.now().toIso8601String(),
      'error': error.toString(),
      'stack': stackTrace?.toString().split('\n').take(12).join('\n'),
    });
  }

  void recordFlutterError(FlutterErrorDetails details) {
    _add({
      't': DateTime.now().toIso8601String(),
      'error': details.exception.toString(),
      'stack': details.stack?.toString().split('\n').take(12).join('\n'),
      'library': details.library ?? '',
      'context': details.context?.toDescription() ?? '',
    });
  }

  void _add(Map<String, Object?> entry) {
    _entries.add(entry);
    if (_entries.length > maxEntries) {
      _entries.removeRange(0, _entries.length - maxEntries);
    }
    _persist();
  }

  Future<void> _persist() async {
    try {
      final path = await _logFile;
      final file = File(path);
      await file.writeAsString(_entries.map(json.encode).join('\n'), flush: true);
    } catch (e) {
      debugPrint('MuraiErrorLogger persist failed: $e');
    }
  }

  List<Map<String, Object?>> get entries => List.unmodifiable(_entries.reversed);

  Future<void> clear() async {
    _entries.clear();
    await _persist();
  }
}
