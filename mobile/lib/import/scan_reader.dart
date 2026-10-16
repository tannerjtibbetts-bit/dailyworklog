// Reads the text in a photo on the phone itself (no internet) and lays it out as table rows.
// Only read() needs a device (ML Kit); everything else is plain Dart and unit-tested.
import 'dart:io';
import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';

import 'sheet_import.dart';
import 'zone_sheet.dart';

/// What a photo turned out to be: rows of a table, the zone task sheet, or an error message.
class ScanResult {
  final List<List<String>>? rows;

  /// The photo was the zone task sheet: whole tasks, already put together.
  final List<ZoneTask>? zone;
  final String? error;

  /// The text read, line by line, top to bottom (in the reader's block and line order).
  final List<String> lines;

  const ScanResult.rows(List<List<String>> this.rows, {this.lines = const []})
      : zone = null,
        error = null;

  const ScanResult.zone(List<ZoneTask> this.zone, {this.lines = const []})
      : rows = null,
        error = null;

  const ScanResult.error(String this.error)
      : rows = null,
        zone = null,
        lines = const [];

  const ScanResult._(this.rows, this.zone, this.error, this.lines);

  /// The same result, with the plain lines read.
  ScanResult withLines(List<String> lines) => ScanResult._(rows, zone, error, lines);
}

class ScanReader {
  ScanReader._();

  /// Big enough for small print, small enough not to run out of memory.
  static const maxSide = 3000;

  /// Reads the photo at [path]. Runs the photo work in the background.
  static Future<ScanResult> read(String path) async {
    final _Prepared? prepared;
    try {
      final bytes = await File(path).readAsBytes();
      final tmp = (await getTemporaryDirectory()).path;
      prepared = await Isolate.run(() => _prepare(bytes, path, tmp));
    } catch (_) {
      return const ScanResult.error("Couldn't open that photo");
    }
    if (prepared == null) return const ScanResult.error("Couldn't open that photo");
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final text = await recognizer.processImage(InputImage.fromFilePath(prepared.path));
      return interpret(boxes(text), ImagePixels(prepared.image)).withLines(textLines(text));
    } catch (_) {
      return const ScanResult.error("Couldn't read the text in that photo");
    } finally {
      await recognizer.close();
      if (prepared.path != path) {
        try {
          File(prepared.path).deleteSync();
        } catch (_) {
          // only a temp copy
        }
      }
    }
  }

  /// The zone sheet if the text looks like it, otherwise table rows.
  static ScanResult interpret(List<Box> found, Pixels? pixels) {
    final zone = ZoneSheet.parse(found, pixels);
    if (zone != null && zone.isNotEmpty) return ScanResult.zone(zone);
    return ScanResult.rows(SheetImport.fromBoxes(found));
  }

  static List<Box> boxes(RecognizedText text) => [
        for (final block in text.blocks)
          for (final line in block.lines)
            Box(line.text, line.boundingBox.left.round(), line.boundingBox.top.round(),
                line.boundingBox.right.round(), line.boundingBox.bottom.round())
      ];

  /// The lines read, in block and line order (top to bottom for a screenshot).
  static List<String> textLines(RecognizedText text) => [
        for (final block in text.blocks)
          for (final line in block.lines) line.text
      ];

  /// Decodes the photo upright (camera photos are often stored sideways) and not too large.
  /// Returns null if it isn't a picture.
  static img.Image? loadUpright(Uint8List bytes, {int max = maxSide}) {
    final b = img.decodeImage(bytes);
    return b == null ? null : upright(b, max: max);
  }

  static img.Image upright(img.Image b, {int max = maxSide}) {
    final turned = b.exif.imageIfd.hasOrientation && b.exif.imageIfd.orientation != 1;
    if (turned) b = img.bakeOrientation(b);
    final scale = math.min(1.0, max / math.max(b.width, b.height));
    if (scale < 1) {
      b = img.copyResize(b,
          width: (b.width * scale).round(), height: (b.height * scale).round(), interpolation: img.Interpolation.average);
    }
    return b;
  }

  /// The upright photo, and a file of it for the text reader (the original if it was already upright and small).
  static _Prepared? _prepare(Uint8List bytes, String path, String tmpDir) {
    final original = img.decodeImage(bytes);
    if (original == null) return null;
    final turned = original.exif.imageIfd.hasOrientation && original.exif.imageIfd.orientation != 1;
    final big = math.max(original.width, original.height) > maxSide;
    if (!turned && !big) return _Prepared(path, original);
    final b = upright(original);
    b.exif = img.ExifData();
    final out = '$tmpDir/scan_${DateTime.now().microsecondsSinceEpoch}.jpg';
    File(out).writeAsBytesSync(img.encodeJpg(b, quality: 92));
    return _Prepared(out, b);
  }
}

class _Prepared {
  final String path;
  final img.Image image;

  _Prepared(this.path, this.image);
}
