// STUB (owner: fields agent). Taking and picking photos into the app's photo folder.
import 'package:flutter/material.dart';

class Photos {
  Photos._();

  /// Takes a photo with the camera; returns the saved file name, or null.
  static Future<String?> take(BuildContext context) async => null;

  /// Picks photos from the gallery; returns the saved file names.
  static Future<List<String>> pick(BuildContext context) async => [];

  /// Bottom sheet: Take photo / Choose from gallery. Returns the saved file names.
  static Future<List<String>> choose(BuildContext context) async => [];
}
