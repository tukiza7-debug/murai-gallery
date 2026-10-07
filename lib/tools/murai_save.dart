import 'dart:typed_data';

import 'package:aves/services/common/services.dart';
import 'package:aves/tools/murai_channel.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:flutter/material.dart';

/// Shared helpers to persist generated files (editor exports, collages, GIFs...).
class MuraiSave {
  static const baseRelativePath = 'Pictures/Murai Gallery';

  static String _extensionForMimeType(String mimeType) {
    switch (mimeType) {
      case 'image/png':
        return 'png';
      case 'image/gif':
        return 'gif';
      case 'image/webp':
        return 'webp';
      case 'video/mp4':
        return 'mp4';
      default:
        return 'jpg';
    }
  }

  /// Saves [bytes] as a new copy in `Pictures/Murai Gallery` (MediaStore).
  /// Returns the new entry URI, or null when the user cancelled / it failed.
  static Future<String?> saveCopy(BuildContext context, {
    required Uint8List bytes,
    required String baseName,
    required String mimeType,
  }) async {
    try {
      final ext = _extensionForMimeType(mimeType);
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final displayName = '${baseName}_$timestamp.$ext';
      final uri = await MuraiChannel.saveBytesToMediaStore(
        displayName: displayName,
        relativePath: baseRelativePath,
        mimeType: mimeType,
        bytes: bytes,
      );
      await mediaStoreService.scanFile(uri, mimeType);
      return uri;
    } catch (e) {
      debugPrint('MuraiSave.saveCopy failed: $e');
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.muraiSaveFailed)));
      }
      return null;
    }
  }
}
