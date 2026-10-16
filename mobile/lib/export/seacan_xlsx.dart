// The Sea Can Movements tab as an Excel (.xlsx) file laid out like Tanner's master sheet, so the
// rows paste straight in: a peach title and heading row (Sea Can Movements / Blue Bin Movements),
// a black divider row, then one numbered light blue row per sea can (with its weight) and 5 spare rows to fill by hand.
//   Moves | Can # | From Location | To Location | Weight | Bin # | From Location | To Location
import 'dart:typed_data';

import 'package:archive/archive.dart';

import '../data/models.dart';

class SeaCanXlsx {
  SeaCanXlsx._();

  /// Column headings, A to H.
  static const headings = [
    'Moves',
    'Can #',
    'From Location',
    'To Location',
    'Weight',
    'Bin #',
    'From Location',
    'To Location',
  ];

  /// Column widths, A to H.
  static const widths = [16, 20, 20, 18, 11, 12, 20, 18];

  /// Empty numbered rows after the sea cans, for adding more by hand.
  static const spareRows = 5;

  /// Cell styles in styles.xml: 1 title / heading (bold Times New Roman 14, peach), 2 divider (near black),
  /// 3 move number (Times New Roman 12, right-aligned, light blue), 4 move cell (bold, centred, light blue).
  static const _head = 1, _divider = 2, _number = 3, _cell = 4;

  /// [by] and [at] aren't written: the sheet has no "exported by" lines so its rows paste straight in.
  static Uint8List write(Project p, String by, int at) {
    final cans = p.filledSeaCans();
    final rows = StringBuffer();
    const cols = 'ABCDEFGH';

    void text(String ref, String s, int style) {
      if (s.isEmpty) {
        rows.write('<c r="$ref" s="$style"/>');
      } else {
        rows.write('<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${_esc(s)}</t></is></c>');
      }
    }

    void row(int r, double? height, void Function() cells) {
      rows.write('<row r="$r"${height == null ? '' : ' ht="$height" customHeight="1"'}>');
      cells();
      rows.write('</row>');
    }

    // 1: titles (merged A1:E1 and F1:H1; the covered cells still carry the style so the borders show).
    row(1, 24, () {
      for (var i = 0; i < 8; i++) {
        text(
          '${cols[i]}1',
          i == 0
              ? 'Sea Can Movements'
              : i == 5
              ? 'Blue Bin Movements'
              : '',
          _head,
        );
      }
    });
    // 2: headings.
    row(2, 42, () {
      for (var i = 0; i < 8; i++) {
        text('${cols[i]}2', headings[i], _head);
      }
    });
    // 3: the black divider.
    row(3, 12, () {
      for (var i = 0; i < 8; i++) {
        text('${cols[i]}3', '', _divider);
      }
    });
    // 4 on: one numbered row per sea can, then the spare rows.
    for (var n = 1; n <= cans.length + spareRows; n++) {
      final r = n + 3;
      final m = n <= cans.length ? cans[n - 1] : null;
      row(r, null, () {
        rows.write('<c r="A$r" s="$_number"><v>$n</v></c>');
        final v = m == null ? const <String>[] : [m.can.trim(), m.fromLocation(), m.toLocation(), m.weight.trim()];
        for (var i = 1; i < 8; i++) {
          final s = i <= v.length ? v[i - 1] : '';
          final number = i == 4 ? _numberText(s) : null;
          if (number != null) {
            rows.write('<c r="${cols[i]}$r" s="$_cell"><v>$number</v></c>');
          } else {
            text('${cols[i]}$r', s, _cell);
          }
        }
      });
    }

    const head = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
    final sheet =
        '$head<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        '<cols>${[for (var i = 0; i < 8; i++) '<col min="${i + 1}" max="${i + 1}" width="${widths[i]}" customWidth="1"/>'].join()}</cols>'
        '<sheetData>$rows</sheetData>'
        '<mergeCells count="2"><mergeCell ref="A1:E1"/><mergeCell ref="F1:H1"/></mergeCells>'
        '</worksheet>';

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
              '<sheets><sheet name="Sea Can Movements" sheetId="1" r:id="rId1"/></sheets></workbook>',
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
              '<fonts count="4"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font>'
              '<font><b/><sz val="14"/><color rgb="FF000000"/><name val="Times New Roman"/><family val="1"/></font>'
              '<font><b/><sz val="12"/><color rgb="FF000000"/><name val="Times New Roman"/><family val="1"/></font>'
              '<font><sz val="12"/><color rgb="FF000000"/><name val="Times New Roman"/><family val="1"/></font></fonts>'
              '<fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>'
              '<fill><patternFill patternType="solid"><fgColor rgb="FFF8CBAD"/><bgColor indexed="64"/></patternFill></fill>'
              '<fill><patternFill patternType="solid"><fgColor rgb="FF262626"/><bgColor indexed="64"/></patternFill></fill>'
              '<fill><patternFill patternType="solid"><fgColor rgb="FFDCE6F1"/><bgColor indexed="64"/></patternFill></fill></fills>'
              '<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>'
              '<border><left style="thin"><color rgb="FF000000"/></left><right style="thin"><color rgb="FF000000"/></right>'
              '<top style="thin"><color rgb="FF000000"/></top><bottom style="thin"><color rgb="FF000000"/></bottom><diagonal/></border>'
              '</borders>'
              '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
              '<cellXfs count="5">'
              '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
              '<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" '
              'applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>'
              '<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>'
              '<xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" '
              'applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>'
              '<xf numFmtId="0" fontId="2" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" '
              'applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>'
              '</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
              '</styleSheet>',
        ),
      )
      ..addFile(ArchiveFile.string('xl/worksheets/sheet1.xml', sheet));
    return ZipEncoder().encodeBytes(zip);
  }

  /// A weight that reads as a number ("2", "2.5", ".5", "3.") as Excel number text, else null (kept as text).
  static String? _numberText(String s) {
    if (!RegExp(r'^-?(\d+\.?\d*|\.\d+)$').hasMatch(s)) return null;
    return s.contains('.') ? double.parse(s).toString() : s;
  }

  static String _esc(String s) => s
      .replaceAll(RegExp(r'[\x00-\x08\x0B\x0C\x0E-\x1F]'), '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
}
