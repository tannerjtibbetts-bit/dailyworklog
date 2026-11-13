// Reads a photo of the zone task sheet (the "North Zone 2" / "South" tabs) and puts each task back
// together. The sheet is laid out in location groups (Tent 1A, Tent 1B, Mock-Up...), each three
// columns wide:
//
//   top row:     Series (the job) | seacan #  | Owner (contact)
//   lower rows:  comments         | Task      | Delay code
//
// Each sea can starts a task; the Task text is under it in the same column, the job and contact are
// beside it, and comments / delay code are under those. The job cell's colour says whose task it is.
import 'dart:math' as math;

import 'package:image/image.dart' as img;

import '../data/models.dart' as m;
import 'sheet_import.dart';

/// Pixel colours from the photo, so the job cell's colour can be read.
abstract class Pixels {
  int get width;

  int get height;

  /// 0xRRGGBB
  int rgb(int x, int y);
}

/// Pixels of a decoded image (package:image).
class ImagePixels implements Pixels {
  final img.Image image;

  ImagePixels(this.image);

  @override
  int get width => image.width;

  @override
  int get height => image.height;

  @override
  int rgb(int x, int y) {
    final p = image.getPixel(x, y);
    return (p.r.toInt() & 255) << 16 | (p.g.toInt() & 255) << 8 | (p.b.toInt() & 255);
  }
}

class ZoneTask {
  String seacan = '', task = '', job = '', contact = '', comments = '', delay = '', location = '';

  /// Task.north / Task.south, or "" if the photo didn't show which zone.
  String side = '';

  /// Colour of the job cell: blue (your crew), orange, pink, green (done), yellow (ongoing) or none.
  String colour = ZoneSheet.none;

  /// "Unload Can – BPPU 211028-7 – Tent 1A (west)"
  String title() {
    final parts = <String>[];
    if (task.isNotEmpty) parts.add(task);
    if (seacan.isNotEmpty) parts.add(seacan);
    if (location.isNotEmpty) parts.add(location);
    if (parts.isEmpty && job.isNotEmpty) parts.add(job);
    return parts.join(' – ');
  }

  String notes() {
    final sb = StringBuffer();
    void line(String label, String value) {
      if (value.isEmpty) return;
      if (sb.isNotEmpty) sb.write('\n');
      sb.write('$label: $value');
    }

    line('Job', job);
    line('Contact', contact);
    line('Location', location);
    line('Comments', comments);
    line('Delay code', delay);
    if (colour == ZoneSheet.green) {
      line('Status', 'Completed (green on the sheet)');
    } else if (colour == ZoneSheet.yellow) {
      line('Status', 'Ongoing (yellow on the sheet)');
    }
    return sb.toString();
  }
}

/// One location group: three columns side by side.
class _Group {
  final double cx, w;
  String location = '';

  _Group(this.cx, this.w);

  /// 0 = Series/comments, 1 = seacan/Task, 2 = Owner/Delay code, -1 = not in this group.
  int column(double x) {
    final d = x - (cx - 1.5 * w);
    if (d < 0 || d >= 3 * w) return -1;
    return (d / w).floor();
  }
}

int _round(double x) => (x + 0.5).floor();

class ZoneSheet {
  ZoneSheet._();

  static const blue = 'blue', orange = 'orange', pink = 'pink', green = 'green', yellow = 'yellow', none = 'none';

  /// Returns the tasks found, or null if the photo doesn't look like the zone sheet (no "seacan"
  /// column headings), so the normal photo import can be used instead. [pixels] may be null.
  static List<ZoneTask>? parse(List<Box> boxes, Pixels? pixels) {
    // 1. The "seacan #" headings mark the middle column of each location group.
    final anchors = [
      for (final b in boxes)
        if (_norm(b.text).contains('seacan') || _norm(b.text).contains('sea can')) b
    ];
    if (anchors.isEmpty) return null;
    // Keep the heading row only (a sea can mentioned in a task lower down isn't a heading).
    stableSort<Box>(anchors, (a, b) => a.top.compareTo(b.top));
    final headTop = anchors[0].top, headH = anchors[0].height;
    final heads = [for (final b in anchors) if ((b.top - headTop).abs() < headH * 2) b];
    stableSort<Box>(heads, (a, b) => a.left.compareTo(b.left));

    final colW = _columnWidth(heads, boxes);
    final groups = [for (final h in heads) _Group((h.left + h.right) / 2, colW)];

    // 2. Where the headings end and the task rows begin (and where they stop: the sheet tabs).
    var headerBottom = 0;
    for (final b in boxes) {
      final t = _norm(b.text);
      final label = t.contains('seacan') ||
          t == 'task' ||
          t.startsWith('comment') ||
          t.startsWith('delay') ||
          t.startsWith('owner') ||
          t.startsWith('series') ||
          t.startsWith('serles');
      if (label && b.top < headTop + headH * 6) headerBottom = math.max(headerBottom, b.bottom);
    }
    const maxInt = 0x7FFFFFFF;
    var dataEnd = maxInt;
    for (final b in boxes) {
      final t = _norm(b.text).replaceAll(' ', '');
      if (b.top > headerBottom &&
          (t == 'dashboard' ||
              t.contains('seacanmovements') ||
              t.contains('floorloading') ||
              t.contains('lastknownlocation'))) {
        dataEnd = math.min(dataEnd, b.top);
      }
    }

    // 3. Above the headings: the zone title (North / South) and each group's location.
    var side = '';
    for (final b in boxes) {
      if (b.bottom > headTop) continue;
      final t = _norm(b.text);
      if (t.contains('zone') || t == 'north' || t == 'south') {
        if (t.contains('north')) {
          side = m.Task.north;
        } else if (t.contains('south')) {
          side = m.Task.south;
        }
        continue;
      }
      if (b.bottom < headTop - headH * 5 || !SheetImport.hasLetters(b.text)) continue;
      final g = _nearest(groups, (b.left + b.right) / 2);
      if (g != null && ((b.left + b.right) / 2 - g.cx).abs() < 1.5 * g.w && g.location.isEmpty) {
        g.location = _tidy(b.text);
      }
    }

    // 4. Each group's task rows.
    final out = <ZoneTask>[];
    for (final g in groups) {
      final left = <Box>[], mid = <Box>[], right = <Box>[];
      for (final b in boxes) {
        if (b.top <= headerBottom || b.top >= dataEnd) continue;
        final col = g.column((b.left + b.right) / 2);
        if (col == 0) {
          left.add(b);
        } else if (col == 1) {
          mid.add(b);
        } else if (col == 2) {
          right.add(b);
        }
      }
      int yOrder(Box a, Box b) => a.top.compareTo(b.top);
      stableSort(left, yOrder);
      stableSort(mid, yOrder);
      stableSort(right, yOrder);

      // Sea cans start tasks; everything under one (before the next) is its Task text.
      final seacans = [for (final b in mid) if (isSeacan(b.text)) b];
      for (var i = 0; i < seacans.length; i++) {
        final sc = seacans[i];
        final h = sc.height;
        final topBandStart = sc.top - (h * 0.7).toInt(), topBandEnd = sc.bottom + (h * 0.7).toInt();
        final blockEnd = i + 1 < seacans.length ? seacans[i + 1].top - (h * 0.7).toInt() : dataEnd;
        final t = ZoneTask()
          ..seacan = tidySeacan(sc.text)
          ..location = g.location
          ..side = side
          ..task = _join(mid, sc.bottom, blockEnd, sc)
          ..job = _join(left, topBandStart, topBandEnd, null)
          ..contact = _join(right, topBandStart, topBandEnd, null)
          ..comments = _join(left, topBandEnd, blockEnd, null)
          ..delay = _join(right, topBandEnd, blockEnd, null);
        if (pixels != null) {
          t.colour = colour(pixels, _round(g.cx - 1.5 * g.w), _round(g.cx - 0.5 * g.w), topBandStart, topBandEnd);
        }
        if (t.task.isNotEmpty || t.job.isNotEmpty) out.add(t);
      }
    }
    return out;
  }

  /// Column width: a third of the distance between groups, or from the Series/Owner headings.
  static double _columnWidth(List<Box> heads, List<Box> boxes) {
    if (heads.length > 1) {
      final gaps = <double>[
        for (var i = 1; i < heads.length; i++)
          ((heads[i].left + heads[i].right) - (heads[i - 1].left + heads[i - 1].right)) / 2
      ]..sort();
      return gaps[gaps.length ~/ 2] / 3;
    }
    final h = heads[0];
    final cx = (h.left + h.right) / 2;
    for (final b in boxes) {
      final t = _norm(b.text);
      if ((t.startsWith('owner') || t.startsWith('series') || t.startsWith('serles')) &&
          (b.top - h.top).abs() < h.height * 2) {
        return ((b.left + b.right) / 2 - cx).abs();
      }
    }
    return h.width * 2.5;
  }

  static _Group? _nearest(List<_Group> groups, double x) {
    _Group? best;
    for (final g in groups) {
      if (best == null || (g.cx - x).abs() < (best.cx - x).abs()) best = g;
    }
    return best;
  }

  /// Text of the boxes whose middle falls between y0 and y1, top to bottom.
  static String _join(List<Box> col, int y0, int y1, Box? skip) {
    final sb = StringBuffer();
    for (final b in col) {
      if (identical(b, skip) || isSeacan(b.text) && skip != null) continue;
      final cy = b.centerY;
      if (cy < y0 || cy >= y1) continue;
      if (sb.isNotEmpty) sb.write(' ');
      sb.write(_tidy(b.text));
    }
    return sb.toString();
  }

  static final _seacan = RegExp(r'^[A-Z]{2,6}[0-9OIl]{5,8}(-?[0-9OIl]{1,2})?$');
  static final _space = RegExp(r'\s+');

  /// Sea can numbers look like "BPPU 211028-7": letters, then 5-8 digits, maybe a check digit.
  static bool isSeacan(String s) => _seacan.hasMatch(s.replaceAll(_space, '').toUpperCase());

  /// "BPPU211028-7" -> "BPPU 211028-7"
  static String tidySeacan(String s) {
    final t = s.replaceAll(_space, '').toUpperCase();
    final mm = RegExp(r'^([A-Z]{2,6})(.*)$', dotAll: true).firstMatch(t);
    if (mm == null) return t;
    return '${mm.group(1)} ${mm.group(2)!.replaceAll('O', '0').replaceAll('I', '1').replaceAll('L', '1')}';
  }

  static String _tidy(String s) => s.replaceAll(_space, ' ').trim();

  static String _norm(String s) =>
      s.toLowerCase().replaceAll(RegExp(r'[^a-z0-9 #]'), '').replaceAll(_space, ' ').trim();

  // ----- cell colour -----

  /// The fill colour of a cell, by majority vote over a grid of pixels inside it. Dark pixels
  /// (the writing) are ignored, so black text on blue still reads as blue.
  static String colour(Pixels px, int x0, int x1, int y0, int y1) {
    final pad = math.max(2, (x1 - x0) ~/ 12);
    x0 = math.max(0, x0 + pad);
    x1 = math.min(px.width - 1, x1 - pad);
    y0 = math.max(0, y0);
    y1 = math.min(px.height - 1, y1);
    if (x1 <= x0 || y1 <= y0) return none;
    final votes = List<int>.filled(6, 0);
    const names = [blue, orange, pink, green, yellow, none];
    var total = 0;
    final stepX = math.max(1, (x1 - x0) ~/ 40), stepY = math.max(1, (y1 - y0) ~/ 12);
    for (var y = y0; y <= y1; y += stepY) {
      for (var x = x0; x <= x1; x += stepX) {
        final c = px.rgb(x, y);
        final v = hsv((c >> 16) & 255, (c >> 8) & 255, c & 255);
        if (v[2] < 0.22) continue; // the writing
        total++;
        votes[classify(v)]++;
      }
    }
    if (total == 0) return none;
    var best = 5;
    for (var i = 0; i < 5; i++) {
      if (votes[i] > votes[best] * 0.8 && votes[i] > total * 0.3 && (best == 5 || votes[i] > votes[best])) best = i;
    }
    return names[best];
  }

  /// 0 blue, 1 orange, 2 pink, 3 green, 4 yellow, 5 none (white / grey).
  static int classify(List<double> hsv) {
    final h = hsv[0], s = hsv[1], v = hsv[2];
    if (s < 0.28 || v < 0.25) return 5;
    if (h >= 185 && h < 260) return 0;
    if (h >= 15 && h < 45) return 1;
    if (h >= 45 && h < 70) return 4;
    if (h >= 70 && h < 170) return 3;
    if (h >= 285 || h < 15) return 2;
    return 5;
  }

  static List<double> hsv(int r, int g, int b) {
    final rf = r / 255, gf = g / 255, bf = b / 255;
    final mx = math.max(rf, math.max(gf, bf)), mn = math.min(rf, math.min(gf, bf)), d = mx - mn;
    double h;
    if (d == 0) {
      h = 0;
    } else if (mx == rf) {
      h = 60 * (((gf - bf) / d).remainder(6));
    } else if (mx == gf) {
      h = 60 * (((bf - rf) / d) + 2);
    } else {
      h = 60 * (((rf - gf) / d) + 4);
    }
    if (h < 0) h += 360;
    return [h, mx == 0 ? 0 : d / mx, mx];
  }
}
