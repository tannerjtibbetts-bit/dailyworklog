import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/export/report_xlsx.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

Map<String, String> _unzip(List<int> bytes) => {
      for (final f in ZipDecoder().decodeBytes(bytes).files)
        if (f.isFile) f.name: utf8.decode(f.content),
    };

void main() {
  final day = DateTime(2026, 10, 29, 7, 32).millisecondsSinceEpoch;

  Project sample() {
    final p = Project()
      ..name = 'B44 Warehouse'
      ..location = 'Zone 2';
    final l = DailyLog()
      ..date = '2026-10-29'
      ..crew = ['Sam Maint', 'Joe Fitter'];
    l.tasks.add(Task()
      ..title = 'D1716 sea can to tent 1A'
      ..side = Task.south
      ..done = true
      ..completedBy = 'Sam Maint'
      ..completedAt = day
      ..assignees = ['Sam Maint']
      ..loadSecured = true
      ..securedBy = 'Joe Fitter'
      ..securedAt = day
      ..notes = 'Straps checked'
      ..photos = [Photo(file: 'a.jpg', addedBy: 'Sam', addedAt: day), Photo(file: 'b.jpg', addedBy: 'Sam', addedAt: day)]);
    l.tasks.add(Task()
      ..title = 'Pack up sea can 317503 & <send>'
      ..side = Task.north
      ..photosRequired = true
      ..nextShift = true);
    l.tasks.add(Task()..title = 'Clean laydown area');
    l.extras.add(Extra()
      ..type = Extra.note
      ..title = 'Tanner'
      ..detail = 'Crane booked'
      ..at = day
      ..include = true);
    p.logs.add(l);
    p.overtime
      ..include = true
      ..rows = [OtRow()..name = 'Sam Maint'..fri = 'Yes'..sat = 'No'..hours = '8'];
    p.attendance
      ..include = true
      ..date = '2026-10-29'
      ..rows = [AttRow()..name = 'Sam Maint'..status = 'Late'..comment = 'Traffic'];
    p.seaCanInclude = true;
    p.seaCans = [SeaCanMove()..can = 'BPPU 201006-1'..fromLetter = 'D'..fromRow = '33'..weight = '5'];
    return p;
  }

  test('daily report sheet: title, field table by area, and the included sections', () {
    final p = sample();
    final bytes = ReportXlsx.write(p, p.logs.single, 'Tanner Tibbetts', day);
    final out = Platform.environment['EXPORT_TEST_OUT'];
    if (out != null && out.isNotEmpty) File('$out/report.xlsx').writeAsBytesSync(bytes);
    final files = _unzip(bytes);
    expect(files['xl/workbook.xml'], contains('<sheet name="Daily report"'));
    XmlDocument.parse(files['xl/styles.xml']!);
    final sheet = files['xl/worksheets/sheet1.xml']!;
    XmlDocument.parse(sheet);
    expect(sheet, contains('<row r="1" ht="24" customHeight="1"><c r="A1" s="2" t="inlineStr"><is><t xml:space="preserve">Daily work log 29/10/2026</t>'));
    expect(sheet, contains('B44 Warehouse · Zone 2'));
    expect(sheet, contains('1/3 fields completed'));
    expect(sheet, contains('Crew: Sam Maint, Joe Fitter'));
    expect(sheet, contains('<c r="A7" s="4" t="inlineStr"><is><t xml:space="preserve">Area</t>'));
    expect(sheet, contains('<c r="J7" s="4" t="inlineStr"><is><t xml:space="preserve">Photos</t>'));
    // South first, then North, then no area.
    expect(sheet, contains('<c r="A8" s="10" t="inlineStr"><is><t xml:space="preserve">South</t>'));
    expect(sheet, contains('<c r="C8" s="6" t="inlineStr"><is><t xml:space="preserve">Done</t>'));
    expect(sheet, contains('Yes, Joe Fitter, 10/29/2026 at 7:32 AM'));
    expect(sheet, contains('<c r="J8" s="9"><v>2</v></c>'));
    expect(sheet, contains('<c r="A9" s="10" t="inlineStr"><is><t xml:space="preserve">North</t>'));
    expect(sheet, contains('Pack up sea can 317503 &amp; &lt;send&gt;'));
    expect(sheet, contains('<c r="C9" s="7" t="inlineStr"><is><t xml:space="preserve">Not done</t>'));
    expect(sheet, contains('<c r="J9" s="7" t="inlineStr"><is><t xml:space="preserve">Required, none</t>'));
    expect(sheet, contains('<c r="A10" s="10" t="inlineStr"><is><t xml:space="preserve">Other</t>'));
    for (final s in ['Also today', 'Note by Tanner', 'Crane booked', 'Weekend overtime', 'Yes total',
      'Attendance Thu Oct 29, 2026', 'Traffic', 'Late 1', 'Sea can movements', 'BPPU 201006-1', 'D33']) {
      expect(sheet, contains(s));
    }
  });

  test('sections that aren\'t ticked stay out; an empty checklist says so', () {
    final p = sample()
      ..seaCanInclude = false;
    p.overtime.include = false;
    p.attendance.include = false;
    p.logs.single.extras.clear();
    final sheet = _unzip(ReportXlsx.write(p, p.logs.single, 'T', day))['xl/worksheets/sheet1.xml']!;
    for (final s in ['Also today', 'Weekend overtime', 'Attendance', 'Sea can movements']) {
      expect(sheet, isNot(contains(s)));
    }
    final empty = _unzip(ReportXlsx.write(Project(), DailyLog()..date = '2026-10-29', 'T', day))['xl/worksheets/sheet1.xml']!;
    expect(empty, contains('No fields'));
    expect(empty, contains('0/0 fields completed'));
  });
}
