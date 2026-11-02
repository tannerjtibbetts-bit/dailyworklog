// Turns a photo's text or a spreadsheet file into rows of cells, so tasks can be picked from them.
// Every row has the same number of columns; empty cells are "".
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:xml/xml.dart';

/// One line of text found in a photo, with where it sits on the page.
class Box {
  final String text;
  final int left, top, right, bottom;

  const Box(this.text, this.left, this.top, this.right, this.bottom);

  double get centerY => (top + bottom) / 2;

  int get height => math.max(1, bottom - top);

  int get width => math.max(1, right - left);

  @override
  String toString() => '$text [$left,$top,$right,$bottom]';
}

/// Thrown when a file can't be read. [code] is 'old-xls', 'no-sheet', 'bad-strings' or 'bad-sheet'.
class SheetException implements Exception {
  final String code;

  const SheetException(this.code);

  @override
  String toString() => 'SheetException($code)';
}

/// Sorts in place, keeping equal items in their original order (like Java's List.sort).
void stableSort<T>(List<T> list, int Function(T a, T b) compare) {
  final indexed = [for (var i = 0; i < list.length; i++) MapEntry(i, list[i])];
  indexed.sort((a, b) {
    final c = compare(a.value, b.value);
    return c != 0 ? c : a.key.compareTo(b.key);
  });
  for (var i = 0; i < list.length; i++) {
    list[i] = indexed[i].value;
  }
}

final _letter = RegExp(r'\p{L}', unicode: true);

class SheetImport {
  SheetImport._();

  // ----- photos -----

  /// Groups lines of text into table rows (by height on the page) and columns (by left edge).
  static List<List<String>> fromBoxes(List<Box> input) {
    final boxes = [for (final b in input) if (b.text.trim().isNotEmpty) b];
    if (boxes.isEmpty) return [];

    final heights = [for (final b in boxes) b.height]..sort();
    final double h = heights[heights.length ~/ 2].toDouble();

    // Rows: lines whose middles are within half a line height of each other.
    stableSort<Box>(boxes, (a, b) => a.centerY.compareTo(b.centerY));
    final rows = <List<Box>>[];
    List<Box>? cur;
    double curY = 0;
    for (final b in boxes) {
      if (cur != null && (b.centerY - curY).abs() <= h * 0.5) {
        cur.add(b);
        curY = (curY * (cur.length - 1) + b.centerY) / cur.length;
      } else {
        cur = [b];
        curY = b.centerY;
        rows.add(cur);
      }
    }

    // Pieces of one cell can come back separately; join ones that nearly touch.
    for (var i = 0; i < rows.length; i++) {
      final row = rows[i];
      stableSort<Box>(row, (a, b) => a.left.compareTo(b.left));
      final merged = <Box>[];
      for (final b in row) {
        final last = merged.isEmpty ? null : merged.last;
        if (last != null && b.left - last.right < h * 1.2) {
          merged[merged.length - 1] = Box('${last.text.trim()} ${b.text.trim()}', last.left,
              math.min(last.top, b.top), math.max(last.right, b.right), math.max(last.bottom, b.bottom));
        } else {
          merged.add(b);
        }
      }
      rows[i] = merged;
    }

    // Columns: left edges that bunch together, split wherever there's a wide gap.
    final lefts = [for (final row in rows) for (final b in row) b.left]..sort();
    final centers = <double>[];
    double sum = lefts[0].toDouble();
    var n = 1, prev = lefts[0];
    for (var i = 1; i < lefts.length; i++) {
      final x = lefts[i];
      if (x - prev > h * 2) {
        centers.add(sum / n);
        sum = 0;
        n = 0;
      }
      sum += x;
      n++;
      prev = x;
    }
    centers.add(sum / n);

    final out = <List<String>>[];
    for (final row in rows) {
      final cells = List<String?>.filled(centers.length, null);
      for (final b in row) {
        final col = _nearest(centers, b.left);
        final t = b.text.trim();
        cells[col] = cells[col] == null ? t : '${cells[col]} $t';
      }
      out.add([for (final c in cells) c ?? '']);
    }
    return tidy(out);
  }

  static int _nearest(List<double> centers, int x) {
    var best = 0;
    for (var i = 1; i < centers.length; i++) {
      if ((centers[i] - x).abs() < (centers[best] - x).abs()) best = i;
    }
    return best;
  }

  // ----- files -----

  /// Reads an Excel (.xlsx) or CSV file. Old .xls files aren't supported (throws SheetException('old-xls')).
  static List<List<String>> fromFile(Uint8List data) {
    if (data.length >= 2 && data[0] == 0x50 && data[1] == 0x4B) return fromXlsx(data);
    if (data.length >= 4 && data[0] == 0xD0 && data[1] == 0xCF) throw const SheetException('old-xls');
    return fromCsv(utf8.decode(data, allowMalformed: true));
  }

  static List<List<String>> fromCsv(String text) {
    if (text.startsWith('﻿')) text = text.substring(1);
    final sep = _guessSeparator(text);
    final rows = <List<String>>[];
    var row = <String>[];
    final cell = StringBuffer();
    var quoted = false;
    for (var i = 0; i < text.length; i++) {
      final c = text[i];
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length && text[i + 1] == '"') {
            cell.write('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          cell.write(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == sep) {
        row.add(cell.toString().trim());
        cell.clear();
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++;
        row.add(cell.toString().trim());
        cell.clear();
        rows.add(row);
        row = <String>[];
      } else {
        cell.write(c);
      }
    }
    if (cell.isNotEmpty || row.isNotEmpty) {
      row.add(cell.toString().trim());
      rows.add(row);
    }
    return tidy(rows);
  }

  static String _guessSeparator(String text) {
    final nl = text.indexOf('\n');
    final first = nl < 0 ? text : text.substring(0, nl);
    var commas = 0, semis = 0, tabs = 0;
    for (final c in first.split('')) {
      if (c == ',') {
        commas++;
      } else if (c == ';') {
        semis++;
      } else if (c == '\t') {
        tabs++;
      }
    }
    if (tabs > commas && tabs > semis) return '\t';
    return semis > commas ? ';' : ',';
  }

  static List<List<String>> fromXlsx(Uint8List data) {
    final parts = <String, Uint8List>{};
    try {
      final zip = ZipDecoder().decodeBytes(data);
      for (final e in zip.files) {
        final name = e.name;
        if (!e.isFile) continue;
        if (name == 'xl/sharedStrings.xml' ||
            name == 'xl/workbook.xml' ||
            name == 'xl/_rels/workbook.xml.rels' ||
            name.startsWith('xl/worksheets/sheet')) {
          parts[name] = e.content;
        }
      }
    } catch (_) {
      throw const SheetException('no-sheet');
    }
    final path = _firstSheetPath(parts);
    final sheet = path == null ? null : parts[path];
    if (sheet == null) throw const SheetException('no-sheet');
    final shared = _sharedStrings(parts['xl/sharedStrings.xml']);
    return tidy(_sheetRows(sheet, shared));
  }

  static XmlDocument _parse(Uint8List xml) => XmlDocument.parse(utf8.decode(xml, allowMalformed: true));

  static Iterable<XmlElement> _all(XmlNode n, String local) =>
      n.descendants.whereType<XmlElement>().where((e) => e.name.local == local);

  static String? _attr(XmlElement e, String local) {
    for (final a in e.attributes) {
      if (a.name.local == local && a.name.prefix == null) return a.value;
    }
    return null;
  }

  /// The first sheet tab in the workbook (falls back to sheet1.xml).
  static String? _firstSheetPath(Map<String, Uint8List> parts) {
    try {
      String? rid;
      final wb = _parse(parts['xl/workbook.xml']!);
      final sheet = _all(wb, 'sheet').firstOrNull;
      if (sheet != null) {
        for (final a in sheet.attributes) {
          if (a.name.local == 'id') rid = a.value;
        }
      }
      if (rid != null) {
        final rels = _parse(parts['xl/_rels/workbook.xml.rels']!);
        for (final r in _all(rels, 'Relationship')) {
          if (_attr(r, 'Id') == rid) {
            final target = _attr(r, 'Target')!;
            if (target.startsWith('/')) return target.substring(1);
            return 'xl/$target';
          }
        }
      }
    } catch (_) {
      // fall through to the usual name
    }
    if (parts.containsKey('xl/worksheets/sheet1.xml')) return 'xl/worksheets/sheet1.xml';
    for (final k in parts.keys) {
      if (k.startsWith('xl/worksheets/sheet')) return k;
    }
    return null;
  }

  static List<String> _sharedStrings(Uint8List? xml) {
    final out = <String>[];
    if (xml == null) return out;
    try {
      final doc = _parse(xml);
      for (final si in _all(doc, 'si')) {
        final sb = StringBuffer();
        // Phonetic guides (rPh) aren't part of the text.
        for (final t in _all(si, 't')) {
          var inPhonetic = false;
          for (XmlNode? p = t.parent; p != null && p != si; p = p.parent) {
            if (p is XmlElement && p.name.local == 'rPh') inPhonetic = true;
          }
          if (!inPhonetic) sb.write(t.innerText);
        }
        out.add(sb.toString());
      }
    } catch (_) {
      throw const SheetException('bad-strings');
    }
    return out;
  }

  static List<List<String>> _sheetRows(Uint8List xml, List<String> shared) {
    final rows = <List<String>>[];
    try {
      final doc = _parse(xml);
      var rowNum = 0;
      for (final r in _all(doc, 'row')) {
        final num = _parseInt(_attr(r, 'r'), rowNum + 1);
        while (rowNum < num - 1) {
          // keep blank rows in place
          rows.add(<String>[]);
          rowNum++;
        }
        rowNum = num;
        final row = <String>[];
        rows.add(row);
        for (final c in _all(r, 'c')) {
          final ref = _attr(c, 'r');
          final col = ref == null ? row.length : columnIndex(ref);
          final type = _attr(c, 't');
          for (final e in c.descendants.whereType<XmlElement>()) {
            final name = e.name.local;
            if (name != 'v' && name != 't') continue;
            var v = e.innerText;
            if (type == 's') {
              final i = _parseInt(v, -1);
              v = i >= 0 && i < shared.length ? shared[i] : '';
            } else if (type == 'b') {
              v = v == '1' ? 'TRUE' : 'FALSE';
            } else if (type == null || type == 'n') {
              v = _number(v);
            }
            while (row.length <= col) {
              row.add('');
            }
            final old = row[col];
            row[col] = old.isEmpty ? v : old + v;
          }
        }
      }
    } catch (_) {
      throw const SheetException('bad-sheet');
    }
    return rows;
  }

  /// "B12" -> 1, "AA3" -> 26.
  static int columnIndex(String ref) {
    var n = 0;
    for (final ch in ref.toUpperCase().codeUnits) {
      if (ch < 0x41 || ch > 0x5A) break;
      n = n * 26 + (ch - 0x41 + 1);
    }
    return math.max(0, n - 1);
  }

  /// Whole numbers without a trailing ".0", so tool numbers read naturally.
  static String _number(String v) {
    final d = double.tryParse(v.trim());
    if (d != null && d.isFinite && d == d.roundToDouble() && d.abs() < 1e15) return d.toInt().toString();
    return v;
  }

  static int _parseInt(String? s, int fallback) => s == null ? fallback : int.tryParse(s.trim()) ?? fallback;

  // ----- shared -----

  /// Drops empty rows and columns and pads every row to the same width.
  static List<List<String>> tidy(List<List<String>> rows) {
    var width = 0;
    for (final r in rows) {
      width = math.max(width, r.length);
    }
    final used = List<bool>.filled(width, false);
    final kept = <List<String>>[];
    for (final r in rows) {
      var any = false;
      for (var i = 0; i < r.length; i++) {
        if (r[i].trim().isNotEmpty) {
          used[i] = true;
          any = true;
        }
      }
      if (any) kept.add(r);
    }
    return [
      for (final r in kept)
        [
          for (var i = 0; i < width; i++)
            if (used[i]) i < r.length ? r[i].trim() : ''
        ]
    ];
  }

  /// The column with the most words in it: usually the task descriptions.
  static int likelyTaskColumn(List<List<String>> rows) {
    if (rows.isEmpty) return 0;
    final width = rows[0].length;
    var best = 0, bestScore = -1;
    for (var c = 0; c < width; c++) {
      var score = 0;
      for (final r in rows) {
        score += _letter.allMatches(r[c]).length;
      }
      if (score > bestScore) {
        bestScore = score;
        best = c;
      }
    }
    return best;
  }

  /// Merges the rows at [pick] (ascending) into one, kept where the first one was. The task column
  /// becomes [text]; other columns join the rows' distinct values with spaces.
  static void combineRows(List<List<String>> rows, List<int> pick, int taskColumn, String text) {
    final first = pick[0];
    final merged = List<String>.of(rows[first]);
    for (var c = 0; c < merged.length; c++) {
      if (c == taskColumn) {
        merged[c] = text;
        continue;
      }
      final sb = StringBuffer();
      for (final i in pick) {
        final v = rows[i][c].trim();
        if (v.isEmpty || sb.toString().contains(v)) continue;
        if (sb.isNotEmpty) sb.write(' ');
        sb.write(v);
      }
      merged[c] = sb.toString();
    }
    final kept = <List<String>>[];
    for (var i = 0; i < rows.length; i++) {
      if (i != first && pick.contains(i)) continue;
      kept.add(i == first ? merged : rows[i]);
    }
    rows
      ..clear()
      ..addAll(kept);
  }

  static bool hasLetters(String s) => _letter.hasMatch(s);
}
