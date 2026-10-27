// The Attendance tab as a small Excel (.xlsx) file: Name / Here?, how many of each answer, and notes.
import 'dart:typed_data';

import 'package:archive/archive.dart';

import '../data/fmt.dart';
import '../data/models.dart';

class AttendanceXlsx {
  AttendanceXlsx._();

  /// Cell styles in styles.xml: 0 normal, 1 bold, 2 bold heading row (grey fill), 3 green bold, 4 red bold.
  static const _normal = 0, _bold = 1, _head = 2, _yes = 3, _no = 4;

  /// [by] and [at] aren't printed, so the rows paste cleanly; they match the other exports' calls.
  static Uint8List write(Project p, String by, int at) {
    final a = p.attendance;
    final rows = StringBuffer();
    var r = 0;

    void cell(String col, int row, String text, int style) {
      rows.write('<c r="$col$row" t="inlineStr"');
      if (style != _normal) rows.write(' s="$style"');
      rows.write('><is><t xml:space="preserve">${_esc(text)}</t></is></c>');
    }

    // A row of text cells starting at column A.
    void row(List<String> cells, int style) {
      r++;
      rows.write('<row r="$r">');
      for (var i = 0; i < cells.length; i++) {
        cell(String.fromCharCode(0x41 + i), r, cells[i], style);
      }
      rows.write('</row>');
    }

    row(['Attendance'], _bold);
    row(['Date: ${Fmt.longDay(a.date)}'], _normal);
    if (p.name.isNotEmpty) row([p.name], _normal);
    r++;
    row(['Name', 'Here?'], _head);
    for (final o in a.filled()) {
      r++;
      rows.write('<row r="$r">');
      cell('A', r, o.name, _normal);
      cell('B', r, o.status, _style(o.status));
      rows.write('</row>');
    }
    r++;
    // How many of each answer.
    for (final st in Attendance.statuses) {
      final n = a.count(st);
      if (n == 0) continue;
      r++;
      rows.write('<row r="$r">');
      cell('A', r, st, _bold);
      rows.write('<c r="B$r"><v>$n</v></c>');
      rows.write('</row>');
    }
    if (a.notes.trim().isNotEmpty) {
      r++;
      row(['Notes'], _bold);
      for (final line in a.notes.trim().split('\n')) {
        row([line], _normal);
      }
    }

    const head = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
    final sheet =
        '$head<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        '<cols><col min="1" max="1" width="28" customWidth="1"/>'
        '<col min="2" max="2" width="14" customWidth="1"/></cols>'
        '<sheetData>$rows</sheetData></worksheet>';

    final zip = Archive()
      ..addFile(
        ArchiveFile.string(
          '[Content_Types].xml',
          '$head<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
              '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
              '<Default Extension="xml" ContentType="application/xml"/>'
              '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
              '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
              '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
              '</Types>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          '_rels/.rels',
          '$head<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
              '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
              '</Relationships>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'xl/workbook.xml',
          '$head<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
              'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
              '<sheets><sheet name="Attendance" sheetId="1" r:id="rId1"/></sheets></workbook>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'xl/_rels/workbook.xml.rels',
          '$head<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
              '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
              '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
              '</Relationships>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'xl/styles.xml',
          '$head<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
              '<fonts count="4"><font><sz val="11"/><name val="Calibri"/></font>'
              '<font><b/><sz val="11"/><name val="Calibri"/></font>'
              '<font><b/><sz val="11"/><color rgb="FF148A3A"/><name val="Calibri"/></font>'
              '<font><b/><sz val="11"/><color rgb="FFC9353A"/><name val="Calibri"/></font></fonts>'
              '<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>'
              '<fill><patternFill patternType="solid"><fgColor rgb="FFE4E7EC"/><bgColor indexed="64"/></patternFill></fill></fills>'
              '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
              '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
              '<cellXfs count="5">'
              '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
              '<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>'
              '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>'
              '<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>'
              '<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>'
              '</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
              '</styleSheet>',
        ),
      )
      ..addFile(ArchiveFile.string('xl/worksheets/sheet1.xml', sheet));
    return ZipEncoder().encodeBytes(zip);
  }

  static int _style(String v) => v == Attendance.yes
      ? _yes
      : v == Attendance.no || v == 'AWOL'
      ? _no
      : _normal;

  static String _esc(String s) => s
      .replaceAll(RegExp(r'[\x00-\x08\x0B\x0C\x0E-\x1F]'), '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
}
