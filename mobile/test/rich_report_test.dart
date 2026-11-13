import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/rich.dart';
import 'package:dailyworklog/export/report_docx.dart';
import 'package:dailyworklog/export/report_pdf.dart';
import 'package:dailyworklog/export/report_xlsx.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

Map<String, String> _unzip(List<int> bytes) => {
      for (final f in ZipDecoder().decodeBytes(bytes).files)
        if (f.isFile && !f.name.endsWith('.jpeg')) f.name: utf8.decode(f.content),
    };

void main() {
  final day = DateTime(2026, 11, 10, 7, 30).millisecondsSinceEpoch;
  late Project p;
  late DailyLog log;

  setUp(() {
    p = Project()..name = 'B44';
    log = DailyLog()..date = '2026-11-10';
    const notes = 'Crane delayed by wind. Pump 3 on hold until parts arrive. Bring straps.';
    log.tasks.add(Task()
      ..title = 'Delay at gate 3'
      ..notes = notes
      ..notesStyle = [
        const Run(58, 63, RichStyle(bold: true, underline: true)), // "Bring"
        const Run(64, 70, RichStyle(colour: 'orange')), // "straps"
      ]);
    p.logs.add(log);
  });

  test('Word: Delay red bold, on hold highlighted, chosen words formatted', () {
    final doc = _unzip(ReportDocx.write(p, log, 'Tester', day, '/nowhere'))['word/document.xml']!;
    XmlDocument.parse(doc);
    expect(doc, contains('<w:b/><w:color w:val="D93B3F"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr><w:t xml:space="preserve">delayed</w:t>'));
    expect(doc, contains('<w:highlight w:val="yellow"/></w:rPr><w:t xml:space="preserve">on hold</w:t>'));
    expect(doc, contains('<w:u w:val="single"/></w:rPr><w:t xml:space="preserve">Bring</w:t>'));
    expect(doc, contains('<w:color w:val="E8730C"/>'));
    // The title's Delay too.
    expect(doc, contains('<w:color w:val="D93B3F"/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr><w:t xml:space="preserve">Delay</w:t>'));
  });

  test('Excel: rich runs, and a light yellow cell for on hold', () {
    final files = _unzip(ReportXlsx.write(p, log, 'Tester', day));
    final sheet = files['xl/worksheets/sheet1.xml']!;
    XmlDocument.parse(sheet);
    XmlDocument.parse(files['xl/styles.xml']!);
    expect(sheet, contains('<r><rPr><rFont val="Calibri"/><b/><color rgb="FFD93B3F"/><sz val="11"/></rPr><t xml:space="preserve">delayed</t></r>'));
    expect(sheet, contains('<c r="I8" s="11" t="inlineStr">'));
    expect(sheet, contains('<color rgb="FFE8730C"/><sz val="11"/></rPr><t xml:space="preserve">straps</t>'));
    expect(files['xl/styles.xml'], contains('FFFFF59D'));
  });

  test('a delayed field: Word line, Excel status and notes, carried over, saved', () {
    final t = log.tasks.single
      ..delayed = true
      ..delayReason = 'Waiting on crane'
      ..delayedBy = 'Sam'
      ..delayedAt = day;
    final doc = _unzip(ReportDocx.write(p, log, 'Tester', day, '/nowhere'))['word/document.xml']!;
    expect(doc, contains('<w:b/><w:color w:val="D93B3F"/><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr><w:t xml:space="preserve">Delayed: Waiting on crane</w:t>'));
    expect(doc, contains('<w:t xml:space="preserve"> (Sam, 11/10/2026 at 7:30 AM)</w:t>'));
    final sheet = _unzip(ReportXlsx.write(p, log, 'Tester', day))['xl/worksheets/sheet1.xml']!;
    expect(sheet, contains('<c r="C8" s="7" t="inlineStr"><is><t xml:space="preserve">Delayed</t>'));
    expect(sheet, contains('<t xml:space="preserve">Delayed</t></r><r><rPr><rFont val="Calibri"/><color rgb="FF000000"/><sz val="11"/></rPr><t xml:space="preserve">: Waiting on crane\nCrane </t>'));
    // The formatting on "straps" moved along with the notes.
    expect(sheet, contains('<color rgb="FFE8730C"/><sz val="11"/></rPr><t xml:space="preserve">straps</t>'));
    // Cleared 2 h 15 min later, with the go-ahead.
    t
      ..delayCleared = true
      ..delayClearedBy = 'Tanner'
      ..delayClearedAt = day + (2 * 60 + 15) * 60000
      ..delayGoAhead = 'Lee';
    expect(t.delayLength(), '2 h 15 min');
    final doc2 = _unzip(ReportDocx.write(p, log, 'Tester', day, '/nowhere'))['word/document.xml']!;
    expect(doc2, contains('<w:b/><w:color w:val="148A3A"/><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr><w:t xml:space="preserve">Delay cleared: 11/10/2026 at 9:45 AM</w:t>'));
    expect(doc2, contains(' (Tanner, go-ahead from Lee, </w:t>'));
    expect(doc2, contains('<w:t xml:space="preserve"> 2 h 15 min)</w:t>'));
    final sheet2 = _unzip(ReportXlsx.write(p, log, 'Tester', day))['xl/worksheets/sheet1.xml']!;
    expect(sheet2, contains('<c r="C8" s="7" t="inlineStr"><is><t xml:space="preserve">Not done</t>'));
    expect(sheet2, contains('<b/><color rgb="FF148A3A"/><sz val="11"/></rPr><t xml:space="preserve">Delay cleared</t>'));
    expect(sheet2, contains('<t xml:space="preserve">: 11/10/2026 at 9:45 AM (go-ahead from Lee)\nCrane </t>'));
    expect(t.carryCopy().delayed, isFalse); // a cleared delay doesn't carry over
    final again = Task.fromJson(jsonDecode(jsonEncode(t.toJson())) as Map<String, dynamic>);
    expect([again.delayCleared, again.delayClearedBy, again.delayClearedAt, again.delayGoAhead],
        [true, 'Tanner', day + 8100000, 'Lee']);
    expect([again.delayed, again.delayReason, again.delayedBy, again.delayedAt], [true, 'Waiting on crane', 'Sam', day]);
    expect(t.carryCopy().delayCleared, isFalse);
    expect(t.freshCopy().delayed, isFalse);
    expect(Task.fromJson({'title': 'old'}).delayed, isFalse);
  });

  test('how long a delay lasted', () {
    Task at(int mins) => Task()
      ..delayedAt = 1000000
      ..delayClearedAt = 1000000 + mins * 60000;
    expect(at(45).delayLength(), '45 min');
    expect(at(120).delayLength(), '2 h');
    expect(at(135).delayLength(), '2 h 15 min');
    expect(at(27 * 60 + 5).delayLength(), '1 day 3 h');
    expect(at(0).delayLength(), 'under a minute');
    expect(Task().delayLength(), '');
  });

  test('PDF builds with the formatted text', () async {
    log.tasks.single
      ..delayed = true
      ..delayReason = 'Waiting on crane'
      ..delayedBy = 'Sam'
      ..delayedAt = day;
    final bytes = await ReportPdf.write(p, log, 'Tester', day, '/nowhere');
    final out = Platform.environment['EXPORT_TEST_OUT'];
    if (out != null && out.isNotEmpty) File('$out/rich.pdf').writeAsBytesSync(bytes);
    expect(String.fromCharCodes(bytes.take(5)), '%PDF-');
  });
}
