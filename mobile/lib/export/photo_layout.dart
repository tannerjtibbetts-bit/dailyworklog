// Report photo layout: photos sit side by side at the same height and wrap onto a new row
// when the next one won't fit. Tall phone photos pack several to a row; wide ones fewer.
// Used by both the PDF and Word reports so they match.
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as img;
import 'package:intl/intl.dart';
import 'package:pdf/pdf.dart' show PdfJpegInfo;

import '../data/fmt.dart';
import '../data/models.dart';

/// One photo's place in a row: which photo, its left offset in the row, and drawn size.
class Cell {
  final int index;
  final double x, w, h;

  const Cell(this.index, this.x, this.w, this.h);

  @override
  String toString() => '#$index@${x.round()}(${w.round()}x${h.round()})';
}

/// A photo's pixel size, upright.
class PhotoSize {
  final int width, height;

  const PhotoSize(this.width, this.height);
}

/// A photo re-encoded for a report.
class Encoded {
  final Uint8List jpeg;
  final int width, height;

  const Encoded(this.jpeg, this.width, this.height);
}

class PhotoLayout {
  PhotoLayout._();

  /// sizes: each photo's pixel size, or null if missing (drawn as a small placeholder).
  /// Every photo is scaled to [rowHeight], or narrower if it would overflow the width.
  static List<List<Cell>> rows(
    List<PhotoSize?> sizes,
    double width,
    double rowHeight,
    double gap,
    double missingWidth,
  ) {
    final rows = <List<Cell>>[];
    var row = <Cell>[];
    double used = 0;
    for (var i = 0; i < sizes.length; i++) {
      final s = sizes[i];
      double w, h;
      if (s == null || s.width <= 0 || s.height <= 0) {
        w = missingWidth;
        h = 14;
      } else {
        h = rowHeight;
        w = s.width * (rowHeight / s.height);
        if (w > width) {
          // very wide photo: fit to the width instead
          w = width;
          h = s.height * (width / s.width);
        }
      }
      var x = row.isEmpty ? 0.0 : used + gap;
      if (row.isNotEmpty && x + w > width) {
        rows.add(row);
        row = <Cell>[];
        x = 0;
      }
      row.add(Cell(i, x, w, h));
      used = x + w;
    }
    if (row.isNotEmpty) rows.add(row);
    return rows;
  }

  static double height(List<Cell> row) => row.fold(0.0, (h, c) => math.max(h, c.h));

  /// Short caption that fits under a narrow photo: initials and time, e.g. "TT 7:32 AM".
  /// The date is added only when the photo is from a different day than the log.
  static String caption(Photo p, String logIso) {
    final d = DateTime.fromMillisecondsSinceEpoch(p.addedAt);
    final day = Fmt.isoOf(d);
    final time = DateFormat(day == logIso ? 'h:mm a' : 'MM/dd h:mm a', 'en_US').format(d);
    final who = p.addedBy.trim().isEmpty ? '' : '${Fmt.initials(p.addedBy)} ';
    return who + time;
  }

  /// Upright pixel size of a saved photo without decoding it, or null if it's missing.
  static PhotoSize? size(File f) {
    Uint8List bytes;
    try {
      bytes = f.readAsBytesSync();
    } catch (_) {
      return null;
    }
    try {
      final info = PdfJpegInfo(bytes);
      final w = info.width ?? 0, h = info.height;
      if (w <= 0 || h <= 0) return null;
      // EXIF orientations 5 to 8 are turned a quarter, so width and height swap.
      return info.orientation.index >= 4 ? PhotoSize(h, w) : PhotoSize(w, h);
    } catch (_) {
      // Not a JPEG: decode it to find out.
      try {
        final im = img.decodeImage(bytes);
        if (im == null) return null;
        final o = im.exif.imageIfd.hasOrientation ? im.exif.imageIfd.orientation ?? 1 : 1;
        return o >= 5 ? PhotoSize(im.height, im.width) : PhotoSize(im.width, im.height);
      } catch (_) {
        return null;
      }
    }
  }

  /// Decodes a photo upright and shrinks it to fit [maxW] x [maxH] pixels (never enlarged),
  /// as a JPEG. A JPEG that is already small enough and upright is kept as it is.
  /// Returns null if the file is missing or can't be read.
  static Encoded? encode(File f, int maxW, int maxH, {int quality = 85}) {
    Uint8List bytes;
    try {
      bytes = f.readAsBytesSync();
    } catch (_) {
      return null;
    }
    try {
      maxW = math.max(1, maxW);
      maxH = math.max(1, maxH);
      int? jw, jh;
      var upright = false, turned = false;
      try {
        final info = PdfJpegInfo(bytes);
        jw = info.width;
        jh = info.height;
        upright = info.orientation.index == 0;
        turned = info.orientation.index >= 4;
        if (info.isCMYK) upright = false; // re-encode as RGB
      } catch (_) {}
      img.Image? im;
      if (jw != null && jw > 0 && jh != null && jh > 0) {
        final uw = turned ? jh : jw, uh = turned ? jw : jh;
        if (upright && uw <= maxW && uh <= maxH) return Encoded(bytes, uw, uh);
        // Decode at 1/2, 1/4 or 1/8 size when that's still big enough: much faster, less memory.
        var scale = 1;
        while (scale < 8 && (uw / (scale * 2)) >= maxW && (uh / (scale * 2)) >= maxH) {
          scale *= 2;
        }
        im = img.decodeJpg(bytes, scale: scale);
      } else {
        im = img.decodeImage(bytes);
      }
      if (im == null) return null;
      im = img.bakeOrientation(im);
      final k = math.min(1.0, math.min(maxW / im.width, maxH / im.height));
      if (k < 1.0) {
        im = img.copyResize(
          im,
          width: math.max(1, (im.width * k).round()),
          height: math.max(1, (im.height * k).round()),
          interpolation: img.Interpolation.average,
        );
      }
      im.exif = img.ExifData();
      return Encoded(img.encodeJpg(im, quality: quality), im.width, im.height);
    } catch (_) {
      return null;
    }
  }
}
