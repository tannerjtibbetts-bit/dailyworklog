// The daily work log report as an Excel (.xlsx) sheet, laid out like the PDF: title, progress,
// a table of every field grouped by area, then Also today, overtime, attendance and sea cans
// when they're included. Photos can't be laid out in a sheet, so the Photos column counts them.
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:intl/intl.dart';

import '../data/fmt.dart';
import '../data/models.dart';

class ReportXlsx {
  ReportXlsx._();

  // Cell styles (index into cellXfs in styles.xml).
  static const _normal = 0, _bold = 1, _title = 2, _meta = 3, _head = 4, _cell = 5, _green = 6, _red = 7;
  static const _section = 8, _num = 9, _area = 10;

  static Uint8List write(Project p, DailyLog log, String by, int at) {
    final b = _Sheet();

    b.row([log.displayTitle()], _title, height: 24);
    final place = [p.name, p.location].where((s) => s.trim().isNotEmpty).join(' · ');
    b.row(place.isEmpty ? [] : [place], _meta);
    b.row(['Generated ${Fmt.longStamp(at)} by $by'], _meta);
    b.row(['${log.doneCount()}/${log.tasks.length} fields completed'], _bold);
    final people = log.people();
    b.row(people.isEmpty ? [] : ['Crew: ${Fmt.join(people)}'], _meta);
    b.skip();

    b.row(const ['Area', 'Field', 'Status', 'Completed by', 'Completed at', 'Assigned', 'Load secured', 'Next shift',
      'Notes', 'Photos'], _head);
    if (log.tasks.isEmpty) {
      b.cells([for (var i = 0; i < 10; i++) _c(i == 1 ? 'No fields' : '', _cell)]);
    }
    for (final g in Task.reportGroups(log.tasks)) {
      for (final t in g.value) {
        final secured = t.loadSecured
            ? ['Yes', t.securedBy, if (t.securedAt > 0) Fmt.stamp(t.securedAt)].where((s) => s.isNotEmpty).join(', ') +
                  (t.securedPhotos.isEmpty ? '' : ' · ${Fmt.plural(t.securedPhotos.length, 'photo', 'photos')}')
            : '';
        b.cells([
          _c(g.key, _area),
          _c(t.title, _cell),
          t.done ? _c('Done', _green) : _c('Not done', _red),
          _c(t.done ? t.completedBy : '', _cell),
          _c(t.done && t.completedAt > 0 ? Fmt.stamp(t.completedAt) : '', _cell),
          _c(Fmt.join(t.assignees), _cell),
          _c(secured, _cell),
          _c(t.nextShift ? 'Yes' : '', _num),
          _c(t.notes.trim(), _cell),
          t.photos.isNotEmpty
              ? _n(t.photos.length)
              : t.photosRequired
              ? _c('Required, none', _red)
              : _c('', _num),
        ]);
      }
    }

    final extras = log.includedExtras();
    if (extras.isNotEmpty) {
      b.skip();
      b.row(['Also today'], _section);
      b.row(const ['Time', 'Item', 'Details'], _head);
      for (final e in extras) {
        b.cells([
          _c(DateFormat('h:mm a', 'en_US').format(DateTime.fromMillisecondsSinceEpoch(e.at)), _cell),
          _c(e.label(), _cell),
          _c(e.type == Extra.template ? '' : e.detail.trim(), _cell),
        ]);
      }
    }

    final ot = p.overtime;
    if (ot.include && ot.filled().isNotEmpty) {
      b.skip();
      b.row(['Weekend overtime'], _section);
      b.row(const ['Name', 'Fri', 'Sat', 'Sun', 'Hours'], _head);
      for (final r in ot.filled()) {
        b.cells([
          _c(r.name, _cell),
          for (var d = 0; d < 3; d++) _c(r.day(d), _yesNo(r.day(d))),
          RegExp(r'^\d+$').hasMatch(r.hours) ? _n(int.parse(r.hours)) : _c(r.hours, _num),
        ]);
      }
      b.cells([
        _c('Yes total', _bold),
        for (var d = 0; d < 3; d++) _n(ot.yesCount(d)),
      ]);
      if (ot.notes.trim().isNotEmpty) b.row(['Notes: ${ot.notes.trim()}'], _normal);
    }

    final a = p.attendance;
    if (a.include && a.filled().isNotEmpty) {
      b.skip();
      b.row(['Attendance ${Fmt.longDay(a.date)}'], _section);
      b.row(const ['Name', 'Here?', 'Comment'], _head);
      for (final r in a.filled()) {
        b.cells([
          _c(r.name, _cell),
          _c(r.status, _yesNo(r.status)),
          _c(r.comment.trim(), _cell),
        ]);
      }
      if (a.summary().isNotEmpty) b.row([a.summary()], _bold);
      if (a.notes.trim().isNotEmpty) b.row(['Notes: ${a.notes.trim()}'], _normal);
    }

    final cans = p.filledSeaCans();
    if (p.seaCanInclude && cans.isNotEmpty) {
      b.skip();
      b.row(['Sea can movements'], _section);
      b.row(const ['Moves', 'Can #', 'From location', 'Current location', 'Weight'], _head);
      for (var i = 0; i < cans.length; i++) {
        final m = cans[i], w = m.weight.trim();
        b.cells([
          _n(i + 1),
          _c(m.can.trim(), _cell),
          _c(m.fromLocation().isEmpty ? '–' : m.fromLocation(), _num),
          _c(m.toLocation().isEmpty ? '–' : m.toLocation(), _num),
          RegExp(r'^\d+(\.\d+)?$').hasMatch(w) ? _n(num.parse(w)) : _c(w, _num),
        ]);
      }
    }
    return _zip(b.xml());
  }

  static _Cell _c(String text, int style) => _Cell(text, style);

  static _Cell _n(num v) => _Cell('$v', _num, number: true);

  /// Yes green, No and AWOL red, anything else plain.
  static int _yesNo(String v) => v == 'Yes'
      ? _green
      : v == 'No' || v == 'AWOL'
      ? _red
      : _num;

  static const _widths = [12, 40, 11, 18, 20, 22, 26, 11, 40, 9];

  static Uint8List _zip(String rows) {
    const head = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
    final cols = StringBuffer('<cols>');
    for (var i = 0; i < _widths.length; i++) {
      cols.write('<col min="${i + 1}" max="${i + 1}" width="${_widths[i]}" customWidth="1"/>');
    }
    cols.write('</cols>');
    final sheet =
        '$head<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        '$cols<sheetData>$rows</sheetData></worksheet>';
    const border = '<border><left style="thin"><color rgb="FFD5D9DE"/></left><right style="thin"><color rgb="FFD5D9DE"/></right>'
        '<top style="thin"><color rgb="FFD5D9DE"/></top><bottom style="thin"><color rgb="FFD5D9DE"/></bottom><diagonal/></border>';
    const top = '<alignment vertical="top" wrapText="1"/>';
    const mid = '<alignment horizontal="center" vertical="top" wrapText="1"/>';
    const fonts = '<fonts count="8"><font><sz val="11"/><name val="Calibri"/></font>'
        '<font><b/><sz val="11"/><name val="Calibri"/></font>'
        '<font><b/><sz val="16"/><color rgb="FF14233A"/><name val="Calibri"/></font>'
        '<font><sz val="11"/><color rgb="FF6B7685"/><name val="Calibri"/></font>'
        '<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
        '<font><b/><sz val="11"/><color rgb="FF1DB04B"/><name val="Calibri"/></font>'
        '<font><b/><sz val="11"/><color rgb="FFD93B3F"/><name val="Calibri"/></font>'
        '<font><b/><sz val="13"/><color rgb="FF14233A"/><name val="Calibri"/></font></fonts>';
    const styleSheet =
        '$head<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$fonts'
        '<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>'
        '<fill><patternFill patternType="solid"><fgColor rgb="FF14233A"/><bgColor indexed="64"/></patternFill></fill>'
        '<fill><patternFill patternType="solid"><fgColor rgb="FFEEF2F7"/><bgColor indexed="64"/></patternFill></fill></fills>'
        '<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>$border</borders>'
        '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
        '<cellXfs count="11">'
        '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1">$top</xf>' // 0 normal
        '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>' // 1 bold
        '<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>' // 2 title
        '<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>' // 3 meta
        '<xf numFmtId="0" fontId="4" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" '
        'applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>' // 4 header
        '<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">$top</xf>' // 5 cell
        '<xf numFmtId="0" fontId="5" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">$mid</xf>' // 6 green
        '<xf numFmtId="0" fontId="6" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">$mid</xf>' // 7 red
        '<xf numFmtId="0" fontId="7" fillId="0" borderId="0" xfId="0" applyFont="1"/>' // 8 section
        '<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">$mid</xf>' // 9 number
        '<xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" '
        'applyAlignment="1">$top</xf>' // 10 area
        '</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
        '</styleSheet>';

    final zip = Archive()
      ..addFile(ArchiveFile.string(
        '[Content_Types].xml',
        '$head<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            '</Types>',
      ))
      ..addFile(ArchiveFile.string(
        '_rels/.rels',
        '$head<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>',
      ))
      ..addFile(ArchiveFile.string(
        'xl/workbook.xml',
        '$head<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets><sheet name="Daily report" sheetId="1" r:id="rId1"/></sheets></workbook>',
      ))
      ..addFile(ArchiveFile.string(
        'xl/_rels/workbook.xml.rels',
        '$head<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            '</Relationships>',
      ))
      ..addFile(ArchiveFile.string('xl/styles.xml', styleSheet))
      ..addFile(ArchiveFile.string('xl/worksheets/sheet1.xml', sheet));
    return ZipEncoder().encodeBytes(zip);
  }
}

class _Cell {
  final String text;
  final int style;
  final bool number;

  const _Cell(this.text, this.style, {this.number = false});
}

/// Builds the sheet's rows, from column A.
class _Sheet {
  final _rows = StringBuffer();
  int _r = 0;

  void skip() => _r++;

  /// A row with the same style on every given cell. An empty list leaves the row blank.
  void row(List<String> texts, int style, {int? height}) =>
      cells([for (final t in texts) _Cell(t, style)], height: height);

  void cells(List<_Cell> cells, {int? height}) {
    _r++;
    _rows.write('<row r="$_r"${height == null ? '' : ' ht="$height" customHeight="1"'}>');
    for (var i = 0; i < cells.length; i++) {
      final ref = '${String.fromCharCode(0x41 + i)}$_r';
      final c = cells[i];
      final s = c.style == 0 ? '' : ' s="${c.style}"';
      if (c.number) {
        _rows.write('<c r="$ref"$s><v>${c.text}</v></c>');
      } else if (c.text.isEmpty) {
        _rows.write('<c r="$ref"$s/>');
      } else {
        _rows.write('<c r="$ref"$s t="inlineStr"><is><t xml:space="preserve">${_esc(c.text)}</t></is></c>');
      }
    }
    _rows.write('</row>');
  }

  String xml() => _rows.toString();

  static String _esc(String s) => s
      .replaceAll(RegExp(r'[\x00-\x08\x0B\x0C\x0E-\x1F]'), '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
}
