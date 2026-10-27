import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/export/attendance_xlsx.dart';
import 'package:dailyworklog/export/report_docx.dart';
import 'package:dailyworklog/export/report_pdf.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

Map<String, String> _unzip(List<int> bytes) => {
      for (final f in ZipDecoder().decodeBytes(bytes).files)
        if (f.isFile) f.name: utf8.decode(f.content),
    };

Future<String?> _pdfText(File f) async {
  try {
    final r = await Process.run('pdftotext', ['-layout', f.path, '-']);
    return r.exitCode == 0 ? r.stdout as String : null;
  } catch (_) {
    return null;
  }
}

void main() {
  final day = DateTime(2026, 10, 27, 7, 32).millisecondsSinceEpoch;
  late Directory tmp;
  late Project p;
  late DailyLog log;

  setUpAll(() async {
    tmp = await Directory.systemTemp.createTemp('att_test');
    p = Project()..name = 'B44';
    p.attendance
      ..date = '2026-10-27'
      ..include = true
      ..notes = 'Cy at medical';
    for (final r in [
      ['Al Smith', 'Yes'],
      ['Bo & Co', 'AWOL'],
      ['', ''],
      ['Cy Lee', 'Step up'],
      ['Di Ray', 'Yes'],
    ]) {
      p.attendance.rows.add(AttRow()
        ..name = r[0]
        ..status = r[1]);
    }
    log = DailyLog()..date = '2026-10-27';
    log.tasks.add(Task()..title = 'Torque checks');
    p.logs.add(log);
  });

  test('attendance excel', () {
    final files = _unzip(AttendanceXlsx.write(p, 'Tester', day));
    final sheet = files['xl/worksheets/sheet1.xml']!;
    XmlDocument.parse(sheet);
    expect(files['xl/workbook.xml'], contains('<sheet name="Attendance"'));
    expect(sheet, contains('<t xml:space="preserve">Attendance</t>'));
    expect(sheet, contains('Date: Tue Oct 27, 2026'));
    expect(sheet, contains('<c r="A5" t="inlineStr" s="2"><is><t xml:space="preserve">Name</t>'));
    expect(sheet, contains('<c r="B6" t="inlineStr" s="3"><is><t xml:space="preserve">Yes</t>'));
    expect(sheet, contains('Bo &amp; Co'));
    expect(sheet, contains('<c r="B7" t="inlineStr" s="4"><is><t xml:space="preserve">AWOL</t>'));
    // 4 people on rows 6-9, a gap, then the counts.
    expect(sheet, contains('<c r="A11" t="inlineStr" s="1"><is><t xml:space="preserve">Yes</t></is></c><c r="B11"><v>2</v></c>'));
    expect(sheet, contains('<c r="B12"><v>1</v></c>'));
    expect(sheet, contains('Cy at medical'));
  });

  test('attendance pdf on its own and in the daily report', () async {
    final own = File('${tmp.path}/att.pdf')..writeAsBytesSync(await ReportPdf.writeAttendance(p, 'Tester', day));
    final text = await _pdfText(own);
    if (text != null) {
      for (final s in ['Attendance', 'B44 · Tue Oct 27, 2026', 'Here?', 'Bo & Co', 'AWOL', 'Yes 2 · AWOL 1 · Step up 1', 'Cy at medical']) {
        expect(text, contains(s));
      }
    }
    final rep = File('${tmp.path}/rep.pdf')..writeAsBytesSync(await ReportPdf.write(p, log, 'Tester', day, tmp.path));
    expect(await _pdfText(rep) ?? 'Attendance Tue Oct 27, 2026', contains('Attendance Tue Oct 27, 2026'));
    p.attendance.include = false;
    final off = File('${tmp.path}/off.pdf')..writeAsBytesSync(await ReportPdf.write(p, log, 'Tester', day, tmp.path));
    expect(await _pdfText(off) ?? '', isNot(contains('Attendance Tue')));
    p.attendance.include = true;
  });

  test('attendance in the Word report only when ticked', () {
    final doc = _unzip(ReportDocx.write(p, log, 'Tester', day, tmp.path))['word/document.xml']!;
    XmlDocument.parse(doc);
    expect(doc, contains('Attendance Tue Oct 27, 2026'));
    expect(doc, contains('Here?'));
    expect(doc, contains('Yes 2 · AWOL 1 · Step up 1'));
    p.attendance.include = false;
    final off = _unzip(ReportDocx.write(p, log, 'Tester', day, tmp.path))['word/document.xml']!;
    expect(off, isNot(contains('Attendance Tue')));
    p.attendance.include = true;
  });
}
