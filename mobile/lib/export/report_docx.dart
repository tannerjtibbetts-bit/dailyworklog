// Builds a Word (.docx) report by hand: plain OOXML zipped up.
// Layout: header, progress summary, then each field with its photos in rows like the PDF.
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:intl/intl.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import 'photo_layout.dart';

/// Turns a photo file into the JPEG to embed, or null if it's missing.
typedef ImageEncoder = Encoded? Function(File f);

class ReportDocx {
  static const _emuPerInch = 914400;
  static const _navy = '14233A', _grey = '6B7685', _green = '1DB04B', _red = 'D93B3F', _text = '1B2533';

  final StringBuffer _body = StringBuffer();
  final StringBuffer _rels = StringBuffer();
  final Archive _media = Archive();
  final ImageEncoder _encoder;
  int _imageCount = 0;

  /// The log's date, so photo captions only show a date when it's a different day.
  String _logIso = '';

  ReportDocx._(this._encoder);

  /// Photos are shrunk to 1100 pixels on the long side, like the Android app.
  static Encoded? defaultEncoder(File f) => PhotoLayout.encode(f, 1100, 1100, quality: 82);

  static Uint8List write(
    Project p,
    DailyLog log,
    String generatedBy,
    int generatedAt,
    String photoDir, {
    ImageEncoder encoder = defaultEncoder,
  }) {
    final r = ReportDocx._(encoder);
    r._render(p, log, generatedBy, generatedAt, photoDir);
    return r._finish();
  }

  /// Just the Sea Can Movements tab as its own Word document.
  static Uint8List writeSeaCans(Project p, String generatedBy, int generatedAt) {
    final r = ReportDocx._(defaultEncoder);
    r._para('Sea can movements${p.name.isEmpty ? '' : ' - ${p.name}'}', true, 22, _navy, 60, false);
    if (p.location.isNotEmpty) r._para(p.location, false, 11, _grey, 20, false);
    r._para('Generated ${Fmt.longStamp(generatedAt)} by $generatedBy', false, 10, _grey, 200, false);
    if (p.filledSeaCans().isEmpty) {
      r._para('No sea cans entered yet.', false, 11, _text, 80, false);
    } else {
      r._seaCanTable(p.filledSeaCans());
    }
    return r._finish();
  }

  void _render(Project p, DailyLog log, String by, int at, String photoDir) {
    _logIso = log.date;
    final done = log.doneCount(), total = log.tasks.length;

    if (p.name.isNotEmpty) _para(p.name.toUpperCase(), false, 10, _grey, 40, false);
    _para(log.displayTitle(), true, 22, _navy, 60, false);
    if (p.location.isNotEmpty) _para(p.location, false, 11, _grey, 20, false);
    _para('Generated ${Fmt.longStamp(at)} by $by', false, 10, _grey, 120, false);

    final summary = '$done/$total fields completed';
    _body.write(
      '<w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="8" w:space="8" w:color="D5D9DE"/></w:pBdr>'
      '<w:spacing w:before="0" w:after="240"/></w:pPr>',
    );
    _run(summary, true, 12, done == total && total > 0 ? _green : _navy);
    final people = log.people();
    if (people.isNotEmpty) _run('     Crew: ${Fmt.join(people)}', false, 11, _grey);
    _body.write('</w:p>');

    for (final g in Task.reportGroups(log.tasks)) {
      if (g.key.isNotEmpty) {
        _body.write('<w:p><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="120"/></w:pPr>');
        _run('${g.key} (${g.value.length})', true, 14, _navy);
        _body.write('</w:p>');
      }
      for (final t in g.value) {
        _renderTask(t, photoDir);
      }
    }

    _renderOvertime(p);
    _renderSeaCans(p);

    final extras = log.includedExtras();
    if (extras.isNotEmpty) {
      _body.write('<w:p><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="80"/></w:pPr>');
      _run('Also today', true, 13, _navy);
      _body.write('</w:p>');
      for (final e in extras) {
        final time = DateFormat('h:mm a', 'en_US').format(DateTime.fromMillisecondsSinceEpoch(e.at));
        _para('$time   ${e.label()}', false, 11, _text, 30, true);
        if (e.detail.trim().isNotEmpty && e.type != Extra.template) {
          _para(e.detail.trim(), false, 9, _grey, 40, true);
        }
        if (e.photos.isNotEmpty) _photoTable(e.photos, photoDir);
      }
    }
  }

  void _renderOvertime(Project p) {
    final ot = p.overtime;
    final rows = ot.filled();
    final notes = ot.notes.trim();
    if (!ot.include || (rows.isEmpty && notes.isEmpty)) return;
    _body.write('<w:p><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="100"/></w:pPr>');
    _run('Weekend overtime', true, 13, _navy);
    _body.write('</w:p>');
    if (rows.isEmpty) {
      _overtimeNotes(notes);
      return;
    }
    const b =
        '<w:top w:val="single" w:sz="4" w:color="D5D9DE"/><w:left w:val="single" w:sz="4" w:color="D5D9DE"/>'
        '<w:bottom w:val="single" w:sz="4" w:color="D5D9DE"/><w:right w:val="single" w:sz="4" w:color="D5D9DE"/>'
        '<w:insideH w:val="single" w:sz="4" w:color="D5D9DE"/><w:insideV w:val="single" w:sz="4" w:color="D5D9DE"/>';
    _body.write(
      '<w:tbl><w:tblPr><w:tblW w:w="10080" w:type="dxa"/><w:tblBorders>$b'
      '</w:tblBorders><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid><w:gridCol w:w="4080"/>'
      '<w:gridCol w:w="1500"/><w:gridCol w:w="1500"/><w:gridCol w:w="1500"/><w:gridCol w:w="1500"/></w:tblGrid>',
    );
    _otRow(const ['Name', 'Fri', 'Sat', 'Sun', 'Hours'], true, 'E4E7EC', true);
    for (final r in rows) {
      _otRow([r.name, _dash(r.fri), _dash(r.sat), _dash(r.sun), _dash(r.hours)], false, null, true);
    }
    _otRow(['Yes total', '${ot.yesCount(0)}', '${ot.yesCount(1)}', '${ot.yesCount(2)}', ''], true, null, false);
    _body.write('</w:tbl><w:p><w:pPr><w:spacing w:before="0" w:after="120"/></w:pPr></w:p>');
    _overtimeNotes(notes);
  }

  void _renderSeaCans(Project p) {
    final cans = p.filledSeaCans();
    if (!p.seaCanInclude || cans.isEmpty) return;
    _body.write('<w:p><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="100"/></w:pPr>');
    _run('Sea can movements', true, 13, _navy);
    _body.write('</w:p>');
    _seaCanTable(cans);
  }

  /// Sea can, then From and Current location, each split into Row and Level. The header repeats on each page.
  void _seaCanTable(List<SeaCanMove> cans) {
    const b =
        '<w:top w:val="single" w:sz="4" w:color="D5D9DE"/><w:left w:val="single" w:sz="4" w:color="D5D9DE"/>'
        '<w:bottom w:val="single" w:sz="4" w:color="D5D9DE"/><w:right w:val="single" w:sz="4" w:color="D5D9DE"/>'
        '<w:insideH w:val="single" w:sz="4" w:color="D5D9DE"/><w:insideV w:val="single" w:sz="4" w:color="D5D9DE"/>';
    _body.write(
      '<w:tbl><w:tblPr><w:tblW w:w="10080" w:type="dxa"/><w:tblBorders>$b'
      '</w:tblBorders><w:tblLayout w:type="fixed"/></w:tblPr><w:tblGrid><w:gridCol w:w="3680"/>'
      '<w:gridCol w:w="1600"/><w:gridCol w:w="1600"/><w:gridCol w:w="1600"/><w:gridCol w:w="1600"/></w:tblGrid>',
    );
    // Only the header rows keep with the next row, so a long list still breaks across pages.
    void cell(String text, int width, {bool bold = false, bool center = true, String? shade, String extra = ''}) {
      final keep = shade != null;
      _body.write('<w:tc><w:tcPr><w:tcW w:w="$width" w:type="dxa"/>$extra');
      if (shade != null) _body.write('<w:shd w:val="clear" w:color="auto" w:fill="$shade"/>');
      _body.write(
        '<w:vAlign w:val="center"/></w:tcPr><w:p><w:pPr>${keep ? '<w:keepNext/>' : ''}<w:spacing w:before="40" w:after="40"/>'
        '${center ? '<w:jc w:val="center"/>' : ''}</w:pPr>',
      );
      if (text.isNotEmpty) _run(text, bold, 10, text == '–' ? _grey : _text);
      _body.write('</w:p></w:tc>');
    }

    const head = 'E4E7EC';
    _body.write('<w:tr><w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>');
    cell('Sea can', 3680, bold: true, center: false, shade: head, extra: '<w:vMerge w:val="restart"/>');
    cell('From location', 3200, bold: true, shade: head, extra: '<w:gridSpan w:val="2"/>');
    cell('Current location', 3200, bold: true, shade: head, extra: '<w:gridSpan w:val="2"/>');
    _body.write('</w:tr><w:tr><w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>');
    cell('', 3680, shade: head, extra: '<w:vMerge/>');
    for (final s in const ['Row', 'Level', 'Row', 'Level']) {
      cell(s, 1600, bold: true, shade: head);
    }
    _body.write('</w:tr>');
    for (final m in cans) {
      _body.write('<w:tr><w:trPr><w:cantSplit/></w:trPr>');
      cell(m.can.trim(), 3680, bold: true, center: false);
      for (final v in [m.fromRow, m.fromLevel, m.toRow, m.toLevel]) {
        cell(v.isEmpty ? '–' : v, 1600);
      }
      _body.write('</w:tr>');
    }
    _body.write('</w:tbl>');
    _para(Fmt.plural(cans.length, 'sea can', 'sea cans'), false, 9, _grey, 160, false);
  }

  void _overtimeNotes(String notes) {
    if (notes.isEmpty) return;
    _para('Notes', true, 10, _navy, 40, false);
    _para(notes, false, 10, _text, 160, false);
  }

  static String _dash(String v) => v.isEmpty ? '-' : v;

  void _otRow(List<String> cells, bool bold, String? shade, bool keep) {
    const widths = [4080, 1500, 1500, 1500, 1500];
    _body.write('<w:tr><w:trPr><w:cantSplit/></w:trPr>');
    for (var i = 0; i < cells.length; i++) {
      _body.write('<w:tc><w:tcPr><w:tcW w:w="${widths[i]}" w:type="dxa"/>');
      if (shade != null) _body.write('<w:shd w:val="clear" w:color="auto" w:fill="$shade"/>');
      _body.write(
        '</w:tcPr><w:p><w:pPr>${keep ? '<w:keepNext/>' : ''}<w:spacing w:before="40" w:after="40"/>'
        '${i == 0 ? '' : '<w:jc w:val="center"/>'}</w:pPr>',
      );
      final v = cells[i];
      final yesNo = i > 0 && (v == Overtime.yes || v == Overtime.no);
      final col = i > 0 && v == Overtime.yes
          ? _green
          : i > 0 && v == Overtime.no
          ? _red
          : _text;
      _run(v, bold || yesNo, 10, col);
      _body.write('</w:p></w:tc>');
    }
    _body.write('</w:tr>');
  }

  void _renderTask(Task t, String photoDir) {
    // Title line with a status mark; keepNext holds it on the page with its details.
    _body.write(
      '<w:p><w:pPr><w:keepNext/><w:spacing w:before="200" w:after="40"/><w:ind w:left="440" w:hanging="440"/></w:pPr>',
    );
    _run(t.done ? '✔  ' : '○  ', true, 13, t.done ? _green : 'A3ACB7', 'Segoe UI Symbol');
    _run(t.title.isEmpty ? '(Untitled field)' : t.title, true, 13, _navy);
    _body.write('</w:p>');

    if (t.done) {
      _para('Completed by ${t.completedBy}, ${Fmt.stamp(t.completedAt)}', false, 9, _grey, 40, true);
    } else {
      _para('Not completed', false, 9, _red, 40, true);
    }
    if (t.loadSecured) {
      _body.write('<w:p><w:pPr><w:spacing w:before="0" w:after="40"/><w:ind w:left="440"/></w:pPr>');
      _run('✔ ', true, 9, _green, 'Segoe UI Symbol');
      _run('Load securement completed', true, 9, _green);
      if (t.securedBy.isNotEmpty) _run(' by ${t.securedBy}, ${Fmt.stamp(t.securedAt)}', false, 9, _grey);
      _body.write('</w:p>');
    }
    if (t.nextShift) _para('Carries over to next shift', true, 9, '174A8B', 40, true);
    if (t.assignees.isNotEmpty) _para('Assigned: ${Fmt.join(t.assignees)}', false, 9, _grey, 40, true);
    if (t.notes.trim().isNotEmpty) _para(t.notes.trim(), false, 11, _text, 80, true);
    if (t.photosRequired && t.photos.isEmpty) _para('Photos required: none attached', false, 9, _red, 40, true);
    if (t.photos.isNotEmpty) _photoTable(t.photos, photoDir);
  }

  /// Photo rows like the PDF: same height side by side, each row only as tall as it needs.
  void _photoTable(List<Photo> photos, String photoDir) {
    final enc = [for (final p in photos) _encoder(File('$photoDir/${p.file}'))];
    final sizes = [for (final e in enc) e == null ? null : PhotoSize(e.width, e.height)];
    const gapPt = 8.0, widthPt = 9640 / 20; // 9640 twips of room after the indent
    for (final row in PhotoLayout.rows(sizes, widthPt, 130, gapPt, 90)) {
      final grid = StringBuffer();
      final colTw = <int>[];
      for (var k = 0; k < row.length; k++) {
        final w = row[k].w + (k < row.length - 1 ? gapPt : 0);
        colTw.add((w * 20).round());
        grid.write('<w:gridCol w:w="${colTw[k]}"/>');
      }
      _body.write(
        '<w:tbl><w:tblPr><w:tblInd w:w="440" w:type="dxa"/><w:tblLayout w:type="fixed"/>'
        '<w:tblCellMar><w:left w:w="0" w:type="dxa"/><w:right w:w="0" w:type="dxa"/></w:tblCellMar>'
        '</w:tblPr><w:tblGrid>$grid</w:tblGrid><w:tr><w:trPr><w:cantSplit/></w:trPr>',
      );
      for (var k = 0; k < row.length; k++) {
        final cell = row[k];
        _body.write('<w:tc><w:tcPr><w:tcW w:w="${colTw[k]}" w:type="dxa"/></w:tcPr>');
        _photoCell(photos[cell.index], enc[cell.index], cell.w, cell.h);
        _body.write('</w:tc>');
      }
      _body.write('</w:tr></w:tbl>');
    }
    _body.write('<w:p><w:pPr><w:spacing w:before="0" w:after="60"/></w:pPr></w:p>');
  }

  void _photoCell(Photo p, Encoded? e, double wPt, double hPt) {
    if (e == null) {
      _para('(Photo missing)', false, 9, _red, 40, false);
      return;
    }
    _imageCount++;
    final id = _imageCount;
    final rid = 'rIdImg$id';
    final name = 'image$id.jpeg';
    _media.addFile(ArchiveFile.bytes('word/media/$name', e.jpeg));
    _rels.write(
      '<Relationship Id="$rid" '
      'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$name"/>',
    );

    final cx = (wPt / 72.0 * _emuPerInch).round();
    final cy = (hPt / 72.0 * _emuPerInch).round();
    _body.write(
      '<w:p><w:pPr><w:keepNext/><w:spacing w:before="60" w:after="0"/></w:pPr><w:r><w:drawing>'
      '<wp:inline distT="0" distB="0" distL="0" distR="0">'
      '<wp:extent cx="$cx" cy="$cy"/>'
      '<wp:docPr id="$id" name="Photo $id"/>'
      '<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr>'
      '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
      '<pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="$name"/>'
      '<pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rid"/>'
      '<a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/>'
      '<a:ext cx="$cx" cy="$cy"/></a:xfrm>'
      '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
      '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>',
    );
    _para(PhotoLayout.caption(p, _logIso), false, 7, _grey, 60, false);
  }

  /// One paragraph. Line breaks in text become Word line breaks.
  void _para(String text, bool bold, int pt, String color, int afterTwips, bool indent, [String? font]) {
    _body.write('<w:p><w:pPr><w:spacing w:before="0" w:after="$afterTwips"/>');
    if (indent) _body.write('<w:ind w:left="440"/>');
    _body.write('</w:pPr>');
    final lines = text.split('\n');
    for (var i = 0; i < lines.length; i++) {
      if (i > 0) _body.write('<w:r><w:br/></w:r>');
      _run(lines[i], bold, pt, color, font);
    }
    _body.write('</w:p>');
  }

  void _run(String text, bool bold, int pt, String color, [String? font]) {
    final f = font ?? 'Calibri';
    _body.write('<w:r><w:rPr><w:rFonts w:ascii="$f" w:hAnsi="$f" w:cs="$f"/>');
    if (bold) _body.write('<w:b/>');
    _body.write(
      '<w:color w:val="$color"/><w:sz w:val="${pt * 2}"/><w:szCs w:val="${pt * 2}"/></w:rPr>'
      '<w:t xml:space="preserve">${esc(text)}</w:t></w:r>',
    );
  }

  static String esc(String s) {
    final b = StringBuffer();
    for (final c in s.runes) {
      switch (c) {
        case 0x26:
          b.write('&amp;');
        case 0x3C:
          b.write('&lt;');
        case 0x3E:
          b.write('&gt;');
        case 0x22:
          b.write('&quot;');
        default:
          // Drop control characters that aren't legal in XML.
          if (c >= 0x20 || c == 0x09) b.writeCharCode(c);
      }
    }
    return b.toString();
  }

  Uint8List _finish() {
    const head = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
    final zip = Archive()
      ..addFile(
        ArchiveFile.string(
          '[Content_Types].xml',
          '$head<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
              '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
              '<Default Extension="xml" ContentType="application/xml"/>'
              '<Default Extension="jpeg" ContentType="image/jpeg"/>'
              '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
              '</Types>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          '_rels/.rels',
          '$head<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
              '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
              '</Relationships>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'word/_rels/document.xml.rels',
          '$head<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
              '$_rels</Relationships>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'word/document.xml',
          '$head<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
              ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
              ' xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"'
              ' xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
              ' xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>'
              '$_body'
              '<w:sectPr><w:pgSz w:w="12240" w:h="15840"/>'
              '<w:pgMar w:top="1080" w:right="1080" w:bottom="1080" w:left="1080" w:header="708" w:footer="708" w:gutter="0"/>'
              '</w:sectPr></w:body></w:document>',
        ),
      );
    for (final f in _media.files) {
      zip.addFile(f);
    }
    return ZipEncoder().encodeBytes(zip);
  }
}
