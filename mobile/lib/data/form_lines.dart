// Finding the fill-in lines (the printed lines answers are written on) in a form's page picture, so answers
// start where a line starts and fit its length. Pure Dart (runs in the background and in tests).
// The same steps as the classic app's FormLines.java.
import 'dart:typed_data';

import 'package:image/image.dart' as img;

/// A horizontal line on the page: from x0 to x1 at height y, all fractions of the page (0..1).
class Segment {
  final int page;
  final double x0, x1, y;

  const Segment(this.page, this.x0, this.x1, this.y);

  double get length => x1 - x0;

  @override
  String toString() => 'p$page(${x0.toStringAsFixed(3)}-${x1.toStringAsFixed(3)} @${y.toStringAsFixed(3)})';
}

class FormLines {
  FormLines._();

  /// Width the picture is scaled to before looking for lines.
  static const scanWidth = 1000;

  /// Lines found in a page picture (JPEG/PNG bytes).
  static List<Segment> find(Uint8List bytes, int page) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return const [];
    final im = decoded.width > scanWidth ? img.copyResize(decoded, width: scanWidth) : decoded;
    final w = im.width, h = im.height;
    final lum = Uint8List(w * h);
    final hist = List<int>.filled(256, 0);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final p = im.getPixel(x, y);
        final l = (0.299 * p.r + 0.587 * p.g + 0.114 * p.b).round().clamp(0, 255);
        lum[y * w + x] = l;
        hist[l]++;
      }
    }
    // Dark = well below the page's usual (median) brightness.
    var count = 0, median = 255;
    for (var i = 0; i < 256; i++) {
      count += hist[i];
      if (count >= w * h / 2) {
        median = i;
        break;
      }
    }
    final dark = (median * 0.65).round();
    bool isDark(int x, int y) => y >= 0 && y < h && lum[y * w + x] < dark;

    // 1. Long dark runs in each row (gaps up to 2 pixels allowed), thin (light 3 pixels above and below).
    final runs = <_Run>[];
    final minRun = (w * 0.04).round();
    for (var y = 0; y < h; y++) {
      var x = 0;
      while (x < w) {
        if (!isDark(x, y)) {
          x++;
          continue;
        }
        final start = x;
        var last = x, gap = 0;
        while (x < w && gap <= 2) {
          if (isDark(x, y)) {
            last = x;
            gap = 0;
          } else {
            gap++;
          }
          x++;
        }
        if (last - start + 1 >= minRun) {
          var above = 0, below = 0;
          for (var i = start; i <= last; i++) {
            if (isDark(i, y - 3)) above++;
            if (isDark(i, y + 3)) below++;
          }
          final len = last - start + 1;
          if (above < len * 0.3 && below < len * 0.3) runs.add(_Run(start, last, y));
        }
      }
    }
    // 2. Join runs in nearby rows (a line 2-3 pixels thick, or slightly slanted) into one line.
    runs.sort((a, b) => a.y != b.y ? a.y.compareTo(b.y) : a.x0.compareTo(b.x0));
    final groups = <_Group>[];
    for (final r in runs) {
      _Group? into;
      for (final g in groups) {
        if (r.y - g.yMax <= 3 && r.x0 <= g.x1 + 6 && r.x1 >= g.x0 - 6) {
          into = g;
          break;
        }
      }
      if (into == null) {
        groups.add(_Group(r));
      } else {
        into.add(r);
      }
    }
    return [
      for (final g in groups)
        if (g.x1 - g.x0 + 1 >= minRun) Segment(page, g.x0 / w, (g.x1 + 1) / w, g.yMean / h),
    ]..sort((a, b) => a.y != b.y ? a.y.compareTo(b.y) : a.x0.compareTo(b.x0));
  }
}

class _Run {
  final int x0, x1, y;

  _Run(this.x0, this.x1, this.y);
}

class _Group {
  int x0, x1, yMax;
  double ySum;
  int n;

  _Group(_Run r)
      : x0 = r.x0,
        x1 = r.x1,
        yMax = r.y,
        ySum = r.y.toDouble(),
        n = 1;

  void add(_Run r) {
    if (r.x0 < x0) x0 = r.x0;
    if (r.x1 > x1) x1 = r.x1;
    if (r.y > yMax) yMax = r.y;
    ySum += r.y;
    n++;
  }

  double get yMean => ySum / n;
}
