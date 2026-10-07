
import 'package:flutter/services.dart';

/// Platform channel for Murai Gallery native tools
/// (MediaStore saving, video ops, QR, wallpaper, notifications).
class MuraiChannel {
  static const MethodChannel _channel = MethodChannel('com.murai.gallery/murai_tools');

  static Future<T?> _invoke<T>(String method, [Map<String, Object?>? args]) => _channel.invokeMethod<T>(method, args);

  static Future<String> saveBytesToMediaStore({
    required String displayName,
    required String relativePath,
    required String mimeType,
    required Uint8List bytes,
  }) async {
    return (await _invoke<String>('saveBytesToMediaStore', {
        'displayName': displayName,
        'relativePath': relativePath,
        'mimeType': mimeType,
        'bytes': bytes,
      }))!;
  }

  static Future<List<Uint8List>> getVideoFrames({
    required String path,
    required List<double> timestampsSecs,
    required int maxWidth,
  }) async {
    final res = await _invoke<List<dynamic>>('getVideoFrames', {
      'path': path,
      'timestampsSecs': timestampsSecs,
      'maxWidth': maxWidth,
    });
    return res?.cast<Uint8List>() ?? const [];
  }

  /// Trims an MP4 file between [startMs] and [endMs], returns the new file path.
  static Future<String> trimVideo({
    required String path,
    required int startMs,
    required int endMs,
    required String destName,
  }) async {
    return (await _invoke<String>('trimVideo', {
        'path': path,
        'startMs': startMs,
        'endMs': endMs,
        'destName': destName,
      }))!;
  }

  /// Decodes a QR/barcode from an image file, returns `{'text': ..., 'format': ...}` or null.
  static Future<Map<String, String>?> decodeQr({required String path}) async {
    final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('decodeQr', {'path': path});
    return res?.map((k, v) => MapEntry(k.toString(), v.toString()));
  }

  static Future<bool> setWallpaperFromPath({required String path}) async => (await _invoke<bool>('setWallpaperFromPath', {'path': path})) ?? false;

  static Future<bool> scheduleWallpaperChange({required int intervalMinutes}) async => (await _invoke<bool>('scheduleWallpaperChange', {'intervalMinutes': intervalMinutes})) ?? false;

  static Future<bool> cancelWallpaperChange() async => (await _invoke<bool>('cancelWallpaperChange')) ?? false;

  static Future<bool> applyWallpaperNow() async => (await _invoke<bool>('applyWallpaperNow')) ?? false;

  static Future<bool> notifyProgress({
    required int id,
    required String title,
    required String text,
    required int progress,
    bool indeterminate = false,
    bool cancellable = false,
  }) async {
    return (await _invoke<bool>('notifyProgress', {
        'id': id,
        'title': title,
        'text': text,
        'progress': progress,
        'indeterminate': indeterminate,
        'cancellable': cancellable,
      })) ?? false;
  }

  static Future<bool> notifyFinished({required int id, required String title, required String text}) async => (await _invoke<bool>('notifyFinished', {
        'id': id,
        'title': title,
        'text': text,
      })) ?? false;

  static Future<bool> cancelNotification({required int id}) async => (await _invoke<bool>('cancelNotification', {'id': id})) ?? false;

  static Future<bool> setRecentsScreenshotEnabled({required bool enabled}) async => (await _invoke<bool>('setRecentsScreenshotEnabled', {'enabled': enabled})) ?? false;

  static Future<int> getFreeStorageBytes() async => (await _invoke<int>('getFreeStorageBytes')) ?? 0;
}
