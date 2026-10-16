// PDF report (US Letter), drawn line by line like the Android app's PdfDocument version.
// Each field's photos sit right under it in rows, captioned once.
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:intl/intl.dart';
import 'package:pdf/pdf.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import 'photo_layout.dart';

class _Style {
  final double size;
  final int argb;
  final bool bold;
  final double spacing;

  const _Style(this.size, this.argb, {this.bold = false, this.spacing = 0});

  PdfColor get color => PdfColor.fromInt(argb);

  /// Line height like Android's StaticLayout with 1.12 line spacing.
  double get lineH => size * 1.172 * 1.12;

  /// Top of the line to the baseline.
  double get ascent => size * 0.928;
}

class ReportPdf {
  static const double _w = 612, _h = 792, _m = 44, _indent = 24;

  /// Photos sit side by side at this height (about 1.8 inches) and wrap to a new row.
  static const double photoH = 130, photoGap = 8, captionH = 12;

  /// Print sharpness for report photos: 220 dpi is crisp on paper and screen, and keeps files emailable.
  static const double photoDpi = 220;

  static const _caps = _Style(8.5, 0xFF6B7685, spacing: 0.08);
  static const _title = _Style(22, 0xFF14233A, bold: true);
  static const _meta = _Style(10, 0xFF6B7685);
  static const _heading = _Style(13, 0xFF14233A, bold: true);
  static const _body = _Style(10.5, 0xFF1B2533);
  static const _small = _Style(8.5, 0xFF6B7685);
  static const _smallRed = _Style(8.5, 0xFFD93B3F);
  static const _tiny = _Style(7.5, 0xFF8A94A1);
  static const _smallGreen = _Style(8.5, 0xFF148A3A, bold: true);
  static const _smallBlue = _Style(8.5, 0xFF174A8B, bold: true);
  static const _otBold = _Style(9.5, 0xFF14233A, bold: true);
  static const _otYes = _Style(9.5, 0xFF148A3A, bold: true);
  static const _otNo = _Style(9.5, 0xFFC9353A, bold: true);

  static final _line = PdfColor.fromInt(0xFFE2E6EB);
  static final _green = PdfColor.fromInt(0xFF1DB04B);
  static final _ring = PdfColor.fromInt(0xFFA3ACB7);
  static final _barTrack = PdfColor.fromInt(0xFFE4E7EC);

  final PdfDocument _doc = PdfDocument();
  late final PdfFont _regular = PdfFont.helvetica(_doc);
  late final PdfFont _boldFont = PdfFont.helveticaBold(_doc);
  PdfGraphics? _g;
  double _y = 0;
  int _pageNo = 0;
  final String _footerTitle;
  final String _photoDir;

  /// Photos already in the file (the same photo can be on a field and a note).
  final Map<String, PdfImage> _images = {};

  /// The log's date, so photo captions only show a date when it's a different day.
  String _logIso = '';

  ReportPdf._(this._footerTitle, this._photoDir);

  /// The day's report. [photoDir] is the folder the photo files are in.
  static Future<Uint8List> write(Project p, DailyLog log, String generatedBy, int generatedAt, String photoDir) {
    final r = ReportPdf._(log.displayTitle(), photoDir);
    r._render(p, log, generatedBy, generatedAt);
    return r._doc.save();
  }

  /// Just the Overtime tab as its own PDF, to send on its own.
  static Future<Uint8List> writeOvertime(Project p, String generatedBy, int generatedAt) {
    final r = ReportPdf._('Weekend overtime${p.name.isEmpty ? '' : ' - ${p.name}'}', '');
    final ot = p.overtime;
    r._newPage();
    const w = _w - 2 * _m;
    if (p.name.isNotEmpty) r._text(p.name.toUpperCase(), _caps, _m, w, 4);
    r._text('Weekend overtime', _title, _m, w, 6);
    r._text('Weekend of ${Fmt.weekendDay(ot.weekOf, 0)} to ${Fmt.weekendDay(ot.weekOf, 2)}', _meta, _m, w, 2);
    if (p.location.isNotEmpty) r._text(p.location, _meta, _m, w, 2);
    r._text('Generated ${Fmt.longStamp(generatedAt)} by $generatedBy', _meta, _m, w, 10);
    if (ot.filled().isEmpty && ot.notes.trim().isEmpty) {
      r._text('No one has signed up yet.', _body, _m, w, 4);
    } else {
      r._drawOvertime(p, true);
    }
    r._finishPage();
    return r._doc.save();
  }

  /// Just the Sea Can Movements tab as its own PDF, to send on its own.
  static Future<Uint8List> writeSeaCans(Project p, String generatedBy, int generatedAt) {
    final title = 'Sea can movements${p.name.isEmpty ? '' : ' - ${p.name}'}';
    final r = ReportPdf._(title, '');
    r._newPage();
    const w = _w - 2 * _m;
    r._text(title, _title, _m, w, 6);
    if (p.location.isNotEmpty) r._text(p.location, _meta, _m, w, 2);
    r._text('Generated ${Fmt.longStamp(generatedAt)} by $generatedBy', _meta, _m, w, 10);
    if (p.filledSeaCans().isEmpty) {
      r._text('No sea cans entered yet.', _body, _m, w, 4);
    } else {
      r._drawSeaCans(p, true);
    }
    r._finishPage();
    return r._doc.save();
  }

  void _render(Project p, DailyLog log, String by, int at) {
    _logIso = log.date;
    _newPage();
    const w = _w - 2 * _m;
    if (p.name.isNotEmpty) _text(p.name.toUpperCase(), _caps, _m, w, 4);
    _text(log.displayTitle(), _title, _m, w, 6);
    if (p.location.isNotEmpty) _text(p.location, _meta, _m, w, 2);
    _text('Generated ${Fmt.longStamp(at)} by $by', _meta, _m, w, 14);

    final done = log.doneCount(), total = log.tasks.length;
    _text('$done/$total fields completed', _heading, _m, w, 6);
    _fillRRect(_m, _y, w, 6, 3, _barTrack);
    if (total > 0 && done > 0) _fillRRect(_m, _y, w * done / total, 6, 3, _green);
    _y += 14;
    final people = log.people();
    if (people.isNotEmpty) _text('Crew: ${Fmt.join(people)}', _meta, _m, w, 4);
    _y += 8;
    _hLine(_m, _w - _m, _y);
    _y += 16;

    for (final g in Task.reportGroups(log.tasks)) {
      if (g.key.isNotEmpty) {
        // Keep the area heading with its first field.
        final first = g.value.first;
        _ensure(
          math.min(
            math.max(70, _heading.lineH + 8 + _keep(first, _photoRows(first.photos, _w - 2 * _m - _indent))),
            _h - 2 * _m - 40,
          ),
        );
        _text('${g.key} (${g.value.length})', _heading, _m, w, 8);
      }
      for (final t in g.value) {
        _drawTask(t);
      }
    }
    _drawExtras(log);
    _drawOvertime(p, false);
    _drawSeaCans(p, false);
    _finishPage();
  }

  /// Weekend overtime table. In the daily report only when ticked; force draws it regardless.
  void _drawOvertime(Project p, bool force) {
    final ot = p.overtime;
    final rows = ot.filled();
    final notes = ot.notes.trim();
    if ((!force && !ot.include) || (rows.isEmpty && notes.isEmpty)) return;
    const w = _w - 2 * _m, rowH = 20.0, nameW = w * 0.40, dayW = (w - nameW) / 4;
    _ensure(70 + rowH * 2);
    _y += 6;
    _text('Weekend overtime', _heading, _m, w, 8);
    if (rows.isEmpty) {
      _drawOvertimeNotes(notes, w);
      return;
    }
    // header
    _fillRect(_m, _y, w, rowH, _barTrack);
    _drawString('Name', _otBold, _m + 6, _y + 13.5);
    const heads = ['Fri', 'Sat', 'Sun', 'Hours'];
    for (var d = 0; d < 4; d++) {
      _drawString(heads[d], _otBold, _m + nameW + d * dayW + (dayW - _measure(heads[d], _otBold)) / 2, _y + 13.5);
    }
    _y += rowH;
    for (final r in rows) {
      _ensure(rowH);
      _hLine(_m, _m + w, _y);
      _drawString(_ellipsize(r.name, _body, nameW - 10), _body, _m + 6, _y + 13.5);
      final v = [r.fri, r.sat, r.sun, r.hours];
      for (var d = 0; d < 4; d++) {
        final s = v[d].isEmpty ? '-' : v[d];
        final tp = v[d] == Overtime.yes
            ? _otYes
            : v[d] == Overtime.no
            ? _otNo
            : _small;
        _drawString(s, tp, _m + nameW + d * dayW + (dayW - _measure(s, tp)) / 2, _y + 13.5);
      }
      _y += rowH;
    }
    _ensure(rowH);
    _hLine(_m, _m + w, _y);
    _drawString('Yes total', _otBold, _m + 6, _y + 13.5);
    for (var d = 0; d < 3; d++) {
      final s = '${ot.yesCount(d)}';
      _drawString(s, _otYes, _m + nameW + d * dayW + (dayW - _measure(s, _otYes)) / 2, _y + 13.5);
    }
    _y += rowH + 10;
    _drawOvertimeNotes(notes, w);
  }

  static const double _canRowH = 20;

  /// Sea can table. In the daily report only when ticked (with a heading); force draws just the table.
  void _drawSeaCans(Project p, bool force) {
    final cans = p.filledSeaCans();
    if (cans.isEmpty || (!force && !p.seaCanInclude)) return;
    const w = _w - 2 * _m, rowH = _canRowH;
    // Keep the heading with the table's header and first row.
    _ensure((force ? 0 : 6 + _heading.lineH + 8) + rowH * 3);
    if (!force) {
      _y += 6;
      _text('Sea can movements', _heading, _m, w, 8);
    }
    _seaCanHeader();
    for (final m in cans) {
      if (_y + rowH > _h - _m - 18) {
        _finishPage();
        _newPage();
        _seaCanHeader();
      }
      _hLine(_m, _m + w, _y);
      const canW = w * 0.36, cellW = (w - canW) / 4;
      _drawString(_ellipsize(m.can.trim(), _otBold, canW - 10), _otBold, _m + 6, _y + 13.5);
      final v = [m.fromRow, m.fromLevel, m.toRow, m.toLevel];
      for (var i = 0; i < 4; i++) {
        final s = v[i].isEmpty ? '–' : v[i];
        final st = v[i].isEmpty ? _small : _body;
        _drawString(s, st, _m + canW + i * cellW + (cellW - _measure(s, st)) / 2, _y + 13.5);
      }
      _y += rowH;
    }
    _hLine(_m, _m + w, _y);
    _y += 8;
    _text(Fmt.plural(cans.length, 'sea can', 'sea cans'), _small, _m, w, 10);
  }

  /// The sea can table's two header rows: From / Current location over Row / Level. Repeats on each page.
  void _seaCanHeader() {
    const w = _w - 2 * _m, rowH = _canRowH, canW = w * 0.36, cellW = (w - canW) / 4;
    _fillRect(_m, _y, w, rowH * 2, _barTrack);
    _drawString('Sea can', _otBold, _m + 6, _y + rowH + 3.5);
    const groups = ['From location', 'Current location'];
    for (var i = 0; i < 2; i++) {
      final x = _m + canW + i * cellW * 2;
      _drawString(groups[i], _otBold, x + (cellW * 2 - _measure(groups[i], _otBold)) / 2, _y + 13.5);
      // A white rule under the group name, and between the two groups.
      _g!
        ..setStrokeColor(PdfColors.white)
        ..setLineWidth(1)
        ..drawLine(x + 8, _h - (_y + rowH), x + cellW * 2 - 8, _h - (_y + rowH))
        ..strokePath();
      if (i == 1) {
        _g!
          ..drawLine(x, _h - _y, x, _h - (_y + rowH * 2))
          ..strokePath();
      }
    }
    const subs = ['Row', 'Level', 'Row', 'Level'];
    for (var i = 0; i < 4; i++) {
      _drawString(subs[i], _small, _m + canW + i * cellW + (cellW - _measure(subs[i], _small)) / 2, _y + rowH + 13.5);
    }
    _y += rowH * 2;
  }

  void _drawOvertimeNotes(String notes, double w) {
    if (notes.isEmpty) return;
    _text('Notes', _otBold, _m, w, 3);
    for (final para in notes.split('\n')) {
      _text(para.isEmpty ? ' ' : para, _body, _m, w, 2);
    }
    _y += 8;
  }

  /// Links used, notes sent and templates used that were ticked for the report.
  void _drawExtras(DailyLog log) {
    final list = log.includedExtras();
    if (list.isEmpty) return;
    const w = _w - 2 * _m;
    _ensure(60);
    _text('Also today', _heading, _m, w, 8);
    for (final e in list) {
      const x = _m + _indent, iw = _w - _m - x;
      final time = DateFormat('h:mm a', 'en_US').format(DateTime.fromMillisecondsSinceEpoch(e.at));
      _ensure(30);
      _text('$time   ${e.label()}', _body, x, iw, 2);
      if (e.type == Extra.link && e.detail.isNotEmpty) _text(e.detail, _small, x, iw, 2);
      if ((e.type == Extra.notify || e.type == Extra.note) && e.detail.trim().isNotEmpty) {
        _text(e.detail.trim(), _small, x, iw, 2);
      }
      if (e.photos.isNotEmpty) {
        _y += 6;
        _drawPhotoGrid(e.photos, _photoRows(e.photos, iw), x);
      }
      _y += 8;
    }
  }

  void _drawTask(Task t) {
    const x = _m + _indent, w = _w - _m - x;
    final head = _wrap(t.title.isEmpty ? '(Untitled field)' : t.title, _heading, w);
    final rows = t.photos.isEmpty ? null : _photoRows(t.photos, w);
    _ensure(math.min(_keep(t, rows), _h - 2 * _m - 40));

    final g = _g!;
    final cy = _y + _heading.ascent - _heading.size * 0.35;
    const cx = _m + 8;
    if (t.done) {
      g.setFillColor(_green);
      g.drawEllipse(cx, _h - cy, 7.5, 7.5);
      g.fillPath();
      g
        ..setStrokeColor(PdfColors.white)
        ..setLineWidth(1.8)
        ..setLineCap(PdfLineCap.round)
        ..setLineJoin(PdfLineJoin.round)
        ..moveTo(cx - 3.4, _h - (cy + 0.2))
        ..lineTo(cx - 0.9, _h - (cy + 2.7))
        ..lineTo(cx + 3.6, _h - (cy - 2.4))
        ..strokePath();
    } else {
      g
        ..setStrokeColor(_ring)
        ..setLineWidth(1.4)
        ..drawEllipse(cx, _h - cy, 6.8, 6.8)
        ..strokePath();
    }
    _drawLines(head, _heading, x);
    _y += 3;

    if (t.done) {
      _text('Completed by ${t.completedBy}, ${Fmt.stamp(t.completedAt)}', _small, x, w, 2);
    } else {
      _text('Not completed', _smallRed, x, w, 2);
    }
    if (t.loadSecured) {
      final who = t.securedBy.isEmpty ? '' : ' by ${t.securedBy}, ${Fmt.stamp(t.securedAt)}';
      // Helvetica has no check mark character, so it's drawn.
      const tw = 10.0;
      final lines = _wrap('Load securement completed$who', _smallGreen, w - tw);
      _ensure(lines.length * _smallGreen.lineH);
      final by = _y + _smallGreen.ascent;
      g
        ..setStrokeColor(PdfColor.fromInt(0xFF148A3A))
        ..setLineWidth(1.2)
        ..setLineCap(PdfLineCap.round)
        ..setLineJoin(PdfLineJoin.round)
        ..moveTo(x + 0.5, _h - (by - 2.8))
        ..lineTo(x + 2.8, _h - (by - 0.4))
        ..lineTo(x + 7, _h - (by - 6))
        ..strokePath();
      _drawLines(lines, _smallGreen, x + tw);
      _y += 2;
    }
    if (t.nextShift) _text('Carries over to next shift', _smallBlue, x, w, 2);
    if (t.assignees.isNotEmpty) _text('Assigned: ${Fmt.join(t.assignees)}', _small, x, w, 2);
    if (t.notes.trim().isNotEmpty) {
      _y += 4;
      _text(t.notes.trim(), _body, x, w, 2);
    }
    if (t.photosRequired && t.photos.isEmpty) _text('Photos required: none attached', _smallRed, x, w, 2);

    if (rows != null) {
      _y += 6;
      _drawPhotoGrid(t.photos, rows, x);
    }
    _y += 10;
    _ensure(12);
    _hLine(_m, _w - _m, _y);
    _y += 16;
  }

  /// Room to keep a field's title with its details and, when it has photos, the first row of them.
  double _keep(Task t, List<List<Cell>>? rows) {
    var keep =
        _wrap(t.title.isEmpty ? '(Untitled field)' : t.title, _heading, _w - 2 * _m - _indent).length * _heading.lineH +
        40;
    if (rows != null && rows.isNotEmpty) keep += 30 + PhotoLayout.height(rows.first) + captionH;
    return keep;
  }

  List<List<Cell>> _photoRows(List<Photo> photos, double width) {
    final sizes = [for (final p in photos) PhotoLayout.size(File('$_photoDir/${p.file}'))];
    return PhotoLayout.rows(sizes, width, photoH, photoGap, 90);
  }

  /// Draws photos in rows that are only as tall as they need to be.
  void _drawPhotoGrid(List<Photo> photos, List<List<Cell>> rows, double x) {
    for (final row in rows) {
      final rowH = PhotoLayout.height(row);
      _ensure(rowH + captionH);
      for (final cell in row) {
        final p = photos[cell.index];
        _drawPhoto(p, File('$_photoDir/${p.file}'), x + cell.x, cell.w, cell.h);
      }
      _y += rowH + captionH + 4;
    }
  }

  void _drawPhoto(Photo p, File f, double left, double dw, double dh) {
    // Shrink the photo to photoDpi for its printed size, so it's sharp but the file stays small.
    final pw = (dw * photoDpi / 72).round(), ph = (dh * photoDpi / 72).round();
    final key = '${f.path}@${pw}x$ph';
    var im = _images[key];
    if (im == null) {
      final e = PhotoLayout.encode(f, pw, ph);
      if (e == null) {
        _drawString('(Photo missing)', _smallRed, left, _y + 12);
        return;
      }
      im = _images[key] = PdfImage.jpeg(_doc, image: e.jpeg, orientation: PdfImageOrientation.topLeft);
    }
    _g!.drawImage(im, left, _h - (_y + dh), dw, dh);
    final cap = PhotoLayout.caption(p, _logIso);
    final room = math.max(dw + photoGap - 2, 40.0);
    _drawString(_ellipsize(cap, _tiny, room), _tiny, left, _y + dh + 9);
  }

  // ----- text -----

  /// Helvetica only has the WinAnsi characters: map the common ones and replace the rest.
  static String clean(String s) {
    final b = StringBuffer();
    for (final r in s.runes) {
      if (r == 0x09) {
        b.write(' ');
      } else if (r < 0x20 || (r >= 0x7F && r < 0xA0)) {
        continue;
      } else if (r <= 0xFF) {
        b.writeCharCode(r);
      } else {
        final m = _winAnsi[r];
        if (m != null) {
          b.writeCharCode(m);
        } else if ((r >= 0x2600 && r <= 0x27BF) || r >= 0x1F000 || (r >= 0xFE00 && r <= 0xFE0F) || r == 0x200D) {
          continue; // symbols and emoji: there's no way to draw them
        } else {
          b.write('?');
        }
      }
    }
    return b.toString();
  }

  static const _winAnsi = <int, int>{
    0x20AC: 0x80,
    0x201A: 0x82,
    0x0192: 0x83,
    0x201E: 0x84,
    0x2026: 0x85,
    0x2020: 0x86,
    0x2021: 0x87,
    0x02C6: 0x88,
    0x2030: 0x89,
    0x0160: 0x8A,
    0x2039: 0x8B,
    0x0152: 0x8C,
    0x017D: 0x8E,
    0x2018: 0x91,
    0x2019: 0x92,
    0x201C: 0x93,
    0x201D: 0x94,
    0x2022: 0x95,
    0x2013: 0x96,
    0x2014: 0x97,
    0x02DC: 0x98,
    0x2122: 0x99,
    0x0161: 0x9A,
    0x203A: 0x9B,
    0x0153: 0x9C,
    0x017E: 0x9E,
    0x0178: 0x9F,
    0x2212: 0x2D,
    0x00A0: 0x20,
    0x2010: 0x2D,
    0x2011: 0x2D,
    0x2032: 0x27,
    0x2033: 0x22,
  };

  PdfFont _font(_Style s) => s.bold ? _boldFont : _regular;

  double _measure(String s, _Style st) {
    if (s.isEmpty) return 0;
    final c = clean(s);
    return _font(st).stringMetrics(c).advanceWidth * st.size + st.spacing * st.size * c.length;
  }

  String _ellipsize(String s, _Style st, double room) {
    if (_measure(s, st) <= room) return s;
    var t = s;
    while (t.isNotEmpty && _measure('$t…', st) > room) {
      t = t.substring(0, t.length - 1);
    }
    return '$t…';
  }

  /// Word wrap to [width]; line breaks in the text are kept, over-long words are split.
  List<String> _wrap(String s, _Style st, double width) {
    final out = <String>[];
    for (final para in s.replaceAll('\r\n', '\n').replaceAll('\t', ' ').split('\n')) {
      var line = '';
      for (final word in para.split(' ')) {
        final next = line.isEmpty ? word : '$line $word';
        if (_measure(next, st) <= width) {
          line = next;
          continue;
        }
        if (line.isNotEmpty) out.add(line);
        line = word;
        while (line.length > 1 && _measure(line, st) > width) {
          var n = line.length - 1;
          while (n > 1 && _measure(line.substring(0, n), st) > width) {
            n--;
          }
          out.add(line.substring(0, n));
          line = line.substring(n);
        }
      }
      out.add(line);
    }
    return out;
  }

  void _text(String s, _Style st, double x, double width, double after) {
    final lines = _wrap(s, st, width);
    _ensure(math.min(lines.length * st.lineH, _h - 2 * _m - 40));
    _drawLines(lines, st, x);
    _y += after;
  }

  /// Draws wrapped lines from the current position; a very long text carries on to the next page.
  void _drawLines(List<String> lines, _Style st, double x) {
    for (final l in lines) {
      _ensure(st.lineH);
      _drawString(l, st, x, _y + st.ascent);
      _y += st.lineH;
    }
  }

  /// Text with its baseline at [baseline] (measured down from the top of the page).
  void _drawString(String s, _Style st, double x, double baseline) {
    final c = clean(s);
    if (c.trim().isEmpty) return;
    _g!
      ..setFillColor(st.color)
      ..drawString(_font(st), st.size, c, x, _h - baseline, charSpace: st.spacing * st.size);
  }

  // ----- shapes -----

  void _hLine(double x1, double x2, double y) {
    _g!
      ..setStrokeColor(_line)
      ..setLineWidth(0.8)
      ..setLineCap(PdfLineCap.butt)
      ..drawLine(x1, _h - y, x2, _h - y)
      ..strokePath();
  }

  void _fillRect(double x, double y, double w, double h, PdfColor c) {
    _g!
      ..setFillColor(c)
      ..drawRect(x, _h - y - h, w, h)
      ..fillPath();
  }

  void _fillRRect(double x, double y, double w, double h, double r, PdfColor c) {
    final rr = math.min(r, w / 2);
    _g!
      ..setFillColor(c)
      ..drawRRect(x, _h - y - h, w, h, rr, rr)
      ..fillPath();
  }

  // ----- pages -----

  void _ensure(double height) {
    if (_y + height > _h - _m - 18) {
      _finishPage();
      _newPage();
    }
  }

  void _newPage() {
    _pageNo++;
    final page = PdfPage(_doc, pageFormat: const PdfPageFormat(_w, _h));
    _g = page.getGraphics();
    _y = _m;
  }

  void _finishPage() {
    if (_g == null) return;
    const fy = _h - 24;
    final n = 'Page $_pageNo';
    final nw = _measure(n, _tiny);
    _drawString(_ellipsize(_footerTitle, _tiny, _w - 2 * _m - nw - 16), _tiny, _m, fy);
    _drawString(n, _tiny, _w - _m - nw, fy);
    _g = null;
  }
}
