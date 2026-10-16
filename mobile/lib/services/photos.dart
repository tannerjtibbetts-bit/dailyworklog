// Taking and picking photos into the app's photo folder. Photos are kept at full resolution;
// only a sideways camera photo is turned upright (and then saved again as a high-quality JPEG).
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';

import '../data/store.dart';
import '../ui/common.dart';

final _random = Random();

/// "IMG_20261016_142233_3fa2c.jpg", like the Android app.
String newPhotoName([DateTime? now]) =>
    'IMG_${DateFormat('yyyyMMdd_HHmmss', 'en_US').format(now ?? DateTime.now())}'
    '_${_random.nextInt(0xFFFFF).toRadixString(16)}.jpg';

/// Applies the EXIF rotation. Upright JPEGs are kept exactly as they are; others are
/// re-encoded at full resolution. Returns null if there's nothing usable. Runs in an isolate.
Uint8List? normalizePhoto(Uint8List bytes) {
  if (bytes.isEmpty) return null;
  try {
    final isJpeg = img.findFormatForData(bytes) == img.ImageFormat.jpg;
    if (isJpeg) {
      final exif = img.decodeJpgExif(bytes);
      final o = exif?.imageIfd.orientation;
      if (o == null || o == 1) return bytes;
    }
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return isJpeg ? bytes : null;
    final upright = img.bakeOrientation(decoded);
    return img.encodeJpg(upright, quality: 95);
  } catch (_) {
    return bytes;
  }
}

class Photos {
  Photos._();

  static final _picker = ImagePicker();

  // iPhones may hand back HEIC; asking for a quality makes it a JPEG (100 = no visible loss).
  static int? get _quality => Platform.isIOS ? 100 : null;

  /// Saves picked/taken image bytes into the photo folder. Returns the file name, or null.
  static Future<String?> save(Uint8List bytes) async {
    final out = await compute(normalizePhoto, bytes);
    if (out == null) return null;
    final name = newPhotoName();
    try {
      final f = Store.instance.photoFile(name);
      await f.parent.create(recursive: true);
      await f.writeAsBytes(out, flush: true);
      return name;
    } catch (_) {
      return null;
    }
  }

  /// Takes a photo with the camera; returns the saved file name, or null.
  static Future<String?> take(BuildContext context) async {
    XFile? x;
    try {
      x = await _picker.pickImage(source: ImageSource.camera, imageQuality: _quality, requestFullMetadata: true);
    } catch (_) {
      if (context.mounted) toast(context, 'No camera app found', long: true);
      return null;
    }
    if (x == null) return null;
    final name = await save(await x.readAsBytes());
    _cleanup(x);
    if (name == null && context.mounted) toast(context, "Couldn't save that photo");
    return name;
  }

  /// Picks photos from the gallery; returns the saved file names.
  static Future<List<String>> pick(BuildContext context) async {
    List<XFile> xs;
    try {
      xs = await _picker.pickMultiImage(imageQuality: _quality, requestFullMetadata: true);
    } catch (_) {
      if (context.mounted) toast(context, 'No gallery app found', long: true);
      return [];
    }
    if (xs.isEmpty) return [];
    final names = <String>[];
    for (final x in xs) {
      String? n;
      try {
        n = await save(await x.readAsBytes());
      } catch (_) {}
      if (n != null) names.add(n);
      _cleanup(x);
    }
    if (names.length < xs.length && context.mounted) toast(context, "Some photos couldn't be added");
    return names;
  }

  /// Bottom sheet: Take photo / Choose from gallery. Returns the saved file names.
  static Future<List<String>> choose(BuildContext context) async {
    var choice = 0;
    await menu(context, null, [
      SheetItem(Icons.photo_camera_outlined, 'Take photo', () => choice = 1),
      SheetItem(Icons.photo_library_outlined, 'Choose from gallery', () => choice = 2),
    ]);
    if (!context.mounted) return [];
    if (choice == 1) {
      final n = await take(context);
      return n == null ? [] : [n];
    }
    if (choice == 2) return pick(context);
    return [];
  }

  // The picker's own temporary copy (Android cache / iOS tmp); ours is in the photo folder.
  static void _cleanup(XFile x) {
    try {
      final f = File(x.path);
      if (!f.path.startsWith(Store.instance.photoDir.path)) f.deleteSync();
    } catch (_) {}
  }
}
