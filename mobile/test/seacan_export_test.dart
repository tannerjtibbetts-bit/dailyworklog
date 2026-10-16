import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/export/export.dart';
import 'package:dailyworklog/export/report_docx.dart';
import 'package:dailyworklog/export/report_pdf.dart';
import 'package:dailyworklog/export/seacan_xlsx.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

Map<String, String> _unzip(List<int> bytes) {
  final a = ZipDecoder().decodeBytes(bytes);
  return {
    for (final f in a.files)
      if (f.isFile) f.name: utf8.decode(f.content),
  };
}

SeaCanMove _can(String can, [String fr = '', String fl = '', String tr = '', String tl = '']) => SeaCanMove()
  ..can = can
  ..fromRow = fr
  ..fromLevel = fl
  ..toRow = tr
  ..toLevel = tl;

void main() {
  late Directory tmp;
  late Project p;
  late DailyLog log;
  final day = DateTime(2026, 10, 5, 7, 32).millisecondsSinceEpoch;

  setUpAll(() async {
    tmp = await Directory.systemTemp.createTemp('seacan_export_test');
  });

  setUp(() {
    p = Project()
      ..name = 'B44 Zone 2'
      ..location = 'B44'
      ..seaCans = [
        _can('MSCU 123456', '12', '2', '40', '1'),
        _can('TCLU, "A" 7', '3'),
        _can('', '5', '1'), // no number: left out
        _can('   '),
        _can('Line\nTwo & <co>', '', '', '9', '3'),
      ];
    log = DailyLog()..date = '2026-10-05';
  });

  test('filledSeaCans and seaCanInclude save and load', () {
    expect(p.filledSeaCans().map((m) => m.can), ['MSCU 123456', 'TCLU, "A" 7', 'Line\nTwo & <co>']);
    expect(p.seaCanInclude, isFalse);
    p.seaCanInclude = true;
    final q = Project.fromJson(jsonDecode(jsonEncode(p.toJson())) as Map<String, dynamic>);
    expect(q.seaCanInclude, isTrue);
    expect(q.filledSeaCans().length, 3);
    expect(Project.fromJson({'name': 'old'}).seaCanInclude, isFalse);
  });

  test('csv: header, quoting, CRLF and a BOM', () {
    final bytes = Export.seaCanCsv(p);
    expect(bytes.take(3), [0xEF, 0xBB, 0xBF]);
    // utf8.decode drops a leading BOM, so it's checked as bytes above.
    expect(
      utf8.decode(bytes.sublist(3)),
      'Sea can,From row,From level,Current row,Current level\r\n'
      'MSCU 123456,12,2,40,1\r\n'
      '"TCLU, ""A"" 7",3,,,\r\n'
      '"Line\nTwo & <co>",,,9,3\r\n',
    );
    expect(utf8.decode(Export.seaCanCsv(Project()).sublist(3)), 'Sea can,From row,From level,Current row,Current level\r\n');
  });

  test('file names', () {
    expect(Export.seaCanFileName(p, day, 'csv'), 'Sea can movements - B44 Zone 2 - 05-10-2026.csv');
    expect(Export.seaCanFileName(Project(), day, 'pdf'), 'Sea can movements - 05-10-2026.pdf');
  });

  test('xlsx', () {
    final bytes = SeaCanXlsx.write(p, 'Tester', day);
    _keep(File('${tmp.path}/seacans.xlsx')..writeAsBytesSync(bytes));
    final files = _unzip(bytes);
    for (final x in files.values) {
      XmlDocument.parse(x);
    }
    final sheet = files['xl/worksheets/sheet1.xml']!;
    expect(sheet, contains('Sea can movements - B44 Zone 2'));
    expect(sheet, contains('Exported Oct 5, 2026 at 7:32 AM by Tester'));
    expect(sheet, contains('<row r="5"><c r="A5" t="inlineStr" s="2"><is><t xml:space="preserve">Sea can</t>'));
    expect(sheet, contains('<t xml:space="preserve">Current level</t>'));
    expect(
      sheet,
      contains(
        '<row r="6"><c r="A6" t="inlineStr" s="1"><is><t xml:space="preserve">MSCU 123456</t></is></c>'
        '<c r="B6"><v>12</v></c><c r="C6"><v>2</v></c><c r="D6"><v>40</v></c><c r="E6"><v>1</v></c></row>',
      ),
    );
    expect(sheet, contains('TCLU, &quot;A&quot; 7'));
    expect(sheet, contains('<c r="B7"><v>3</v></c><c r="C7" t="inlineStr" s="3"><is><t xml:space="preserve">–</t>'));
    expect(sheet, contains('Two &amp; &lt;co&gt;'));
    expect(sheet, contains('3 sea cans'));
    expect(files['xl/workbook.xml'], contains('name="Sea cans"'));
    expect(_unzip(SeaCanXlsx.write(Project(), 'T', day))['xl/worksheets/sheet1.xml'], contains('No sea cans entered yet.'));
  });

  test('word', () {
    final bytes = ReportDocx.writeSeaCans(p, 'Tester', day);
    _keep(File('${tmp.path}/seacans.docx')..writeAsBytesSync(bytes));
    final files = _unzip(bytes);
    final doc = files['word/document.xml']!;
    XmlDocument.parse(doc);
    final text = XmlDocument.parse(doc).findAllElements('w:t').map((e) => e.innerText).toList();
    for (final s in [
      'Sea can movements - B44 Zone 2',
      'Generated Oct 5, 2026 at 7:32 AM by Tester',
      'Sea can',
      'From location',
      'Current location',
      'MSCU 123456',
      '40',
      'TCLU, "A" 7',
      '3 sea cans',
    ]) {
      expect(text, contains(s));
    }
    expect(doc, contains('Two &amp; &lt;co&gt;'));
    expect(text.where((t) => t == '–').length, 5);
    expect(doc, contains('<w:tblHeader/>'));
    final empty = _unzip(ReportDocx.writeSeaCans(Project(), 'T', day))['word/document.xml']!;
    expect(empty, contains('No sea cans entered yet.'));
  });

  test('pdf', () async {
    final bytes = await ReportPdf.writeSeaCans(p, 'Tester', day);
    expect(String.fromCharCodes(bytes.take(5)), '%PDF-');
    final f = File('${tmp.path}/seacans.pdf')..writeAsBytesSync(bytes);
    _keep(f);
    expect(_pages(bytes), 1);
    final text = await _pdfText(f);
    if (text == null) return; // no pdftotext here
    for (final s in [
      'Sea can movements - B44 Zone 2',
      'Generated Oct 5, 2026 at 7:32 AM by Tester',
      'From location',
      'Current location',
      'MSCU 123456',
      'TCLU, "A" 7',
      '3 sea cans',
    ]) {
      expect(text, contains(s));
    }
    final e = File('${tmp.path}/seacans_empty.pdf')..writeAsBytesSync(await ReportPdf.writeSeaCans(Project(), 'T', day));
    expect(await _pdfText(e), contains('No sea cans entered yet.'));
  });

  test('120 sea cans run onto more pages', () async {
    p.seaCans = [for (var i = 1; i <= 120; i++) _can('CAN ${i.toString().padLeft(3, '0')}', '${i % 40 + 1}', '${i % 3 + 1}')];
    final bytes = await ReportPdf.writeSeaCans(p, 'Tester', day);
    final f = File('${tmp.path}/seacans_120.pdf')..writeAsBytesSync(bytes);
    _keep(f);
    expect(_pages(bytes), greaterThanOrEqualTo(3));
    final text = await _pdfText(f);
    if (text == null) return;
    expect(text, contains('CAN 001'));
    expect(text, contains('CAN 120'));
    expect(text, contains('120 sea cans'));
    // The header repeats on every page.
    expect('Current location'.allMatches(text).length, _pages(bytes));
  });

  test('daily report has the sea cans only when ticked', () async {
    Future<String?> pdf() async {
      final f = File('${tmp.path}/report_${p.seaCanInclude}.pdf')
        ..writeAsBytesSync(await ReportPdf.write(p, log, 'Tester', day, tmp.path));
      _keep(f);
      return _pdfText(f);
    }

    String docx() {
      final doc = _unzip(ReportDocx.write(p, log, 'Tester', day, tmp.path))['word/document.xml']!;
      XmlDocument.parse(doc);
      return doc;
    }

    expect(docx(), isNot(contains('Sea can movements')));
    final off = await pdf();
    if (off != null) expect(off, isNot(contains('Sea can movements')));

    p.seaCanInclude = true;
    final doc = docx();
    expect(doc, contains('Sea can movements'));
    expect(doc, contains('MSCU 123456'));
    final on = await pdf();
    if (on != null) {
      expect(on, contains('Sea can movements'));
      expect(on, contains('MSCU 123456'));
      expect(on, contains('3 sea cans'));
    }

    // Ticked but no numbers typed: nothing.
    p.seaCans = [_can('', '1', '1')];
    expect(docx(), isNot(contains('Sea can movements')));
  });

  test('daily report: overtime comes before the sea cans', () async {
    p.seaCanInclude = true;
    p.overtime
      ..include = true
      ..weekOf = '2026-10-09'
      ..rows = [OtRow()..name = 'Jim McLaren'];
    final doc = _unzip(ReportDocx.write(p, log, 'Tester', day, tmp.path))['word/document.xml']!;
    expect(doc.indexOf('Weekend overtime') < doc.indexOf('Sea can movements'), isTrue);
    final f = File('${tmp.path}/report_both.pdf')..writeAsBytesSync(await ReportPdf.write(p, log, 'Tester', day, tmp.path));
    final text = await _pdfText(f);
    if (text == null) return;
    expect(text.indexOf('Weekend overtime') < text.indexOf('Sea can movements'), isTrue);
  });

  testWidgets('sea can export builds the file in the background and offers it', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/path_provider'),
      (call) async => tmp.path,
    );
    await tester.runAsync(() => Store.open(Directory('${tmp.path}/store')..createSync()));
    late BuildContext ctx;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (c) {
              ctx = c;
              return const SizedBox();
            },
          ),
        ),
      ),
    );
    final done = Export.seaCans(ctx, p, format: 'csv');
    await tester.pump();
    expect(find.text('Building CSV…'), findsOneWidget);
    final name = RegExp(r'^Sea can movements - B44 Zone 2 - \d\d-\d\d-\d{4}\.csv is ready$');
    bool ready() => find.textContaining(name).evaluate().isNotEmpty;
    for (var i = 0; i < 50 && !ready(); i++) {
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 100)));
      await tester.pump(const Duration(milliseconds: 100));
    }
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Building CSV…'), findsNothing);
    expect(ready(), isTrue);
    expect(find.text('Share or email'), findsOneWidget);
    expect(find.text('Save to phone'), findsOneWidget);
    final f = Directory('${tmp.path}/exports').listSync().whereType<File>().firstWhere((f) => f.path.endsWith('.csv'));
    expect(f.readAsBytesSync(), Export.seaCanCsv(p));
    Navigator.of(ctx).pop();
    await tester.pumpAndSettle();
    await done;
  });

  testWidgets('Sea Can tab: Export button and the include tick', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final dir = Directory.systemTemp.createTempSync('seacan_tab');
    late Store store;
    await tester.runAsync(() async => store = await Store.open(dir));
    final q = Project()..name = 'B44';
    store.projects.add(q);
    await tester.pumpWidget(MaterialApp(home: ProjectScreen(projectId: q.id)));
    await tester.tap(find.text('Sea Can Movements'));
    await tester.pumpAndSettle();

    expect(
      find.byWidgetPredicate((w) => w is Semantics && w.properties.label == 'Export sea cans to PDF, Excel, CSV or Word'),
      findsOneWidget,
    );
    expect(find.text('Include in my reports (PDF, Word and email)'), findsOneWidget);
    expect(q.seaCanInclude, isFalse);
    await tester.tap(find.byType(Checkbox));
    await tester.pumpAndSettle();
    expect(q.seaCanInclude, isTrue);
    expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isTrue);
    await tester.tap(find.text('Include in my reports (PDF, Word and email)'));
    await tester.pumpAndSettle();
    expect(q.seaCanInclude, isFalse);

    await tester.tap(find.text('Export'));
    await tester.pumpAndSettle();
    expect(find.text('Send just the sea can list. Your daily report still includes it when ticked.'), findsOneWidget);
    for (final s in ['PDF', 'Excel', 'CSV', 'Word']) {
      expect(find.text('Export sea cans to $s'), findsOneWidget);
    }
  });
}

/// Pages in a PDF the pdf package wrote (its page objects aren't compressed).
int _pages(List<int> bytes) => RegExp(r'/Type\s*/Page(?![s\w])').allMatches(latin1.decode(bytes)).length;

/// Set EXPORT_TEST_OUT to a folder to keep the generated files for a look.
void _keep(File f) {
  final out = Platform.environment['EXPORT_TEST_OUT'];
  if (out != null && out.isNotEmpty) f.copySync('$out/${f.uri.pathSegments.last}');
}

Future<String?> _pdfText(File f) async {
  try {
    final r = await Process.run('pdftotext', ['-layout', f.path, '-']);
    return r.exitCode == 0 ? r.stdout as String : null;
  } catch (_) {
    return null;
  }
}
