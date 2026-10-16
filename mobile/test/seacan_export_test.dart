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

SeaCanMove _can(
  String can, [
  String fr = '',
  String fl = '',
  String tr = '',
  String tl = '',
  String fLetter = '',
  String tLetter = '',
]) => SeaCanMove()
  ..can = can
  ..fromLetter = fLetter
  ..fromRow = fr
  ..fromLevel = fl
  ..toLetter = tLetter
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
        _can('MSCU 123456', '12', '2', '40', '1', 'D', 'E'),
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
    const header = 'Moves,Can #,From Location,To Location,Weight,Bin #,From Location,To Location\r\n';
    expect(
      utf8.decode(bytes.sublist(3)),
      '${header}1,MSCU 123456,D12 L2,E40 L1,,,,\r\n'
      '2,"TCLU, ""A"" 7",3,,,,,\r\n'
      '3,"Line\nTwo & <co>",,9 L3,,,,\r\n',
    );
    expect(utf8.decode(Export.seaCanCsv(Project()).sublist(3)), header);
  });

  test('file names', () {
    expect(Export.seaCanFileName(p, day, 'csv'), 'Sea can movements - B44 Zone 2 - 05-10-2026.csv');
    expect(Export.seaCanFileName(Project(), day, 'pdf'), 'Sea can movements - 05-10-2026.pdf');
  });

  test('xlsx: laid out like the master sheet', () {
    final bytes = SeaCanXlsx.write(p, 'Tester', day);
    _keep(File('${tmp.path}/seacans.xlsx')..writeAsBytesSync(bytes));
    final files = _unzip(bytes);
    for (final x in files.values) {
      XmlDocument.parse(x);
    }
    expect(files['xl/workbook.xml'], contains('<sheet name="Sea Can Movements"'));
    final sheet = XmlDocument.parse(files['xl/worksheets/sheet1.xml']!);
    final xml = files['xl/worksheets/sheet1.xml']!;
    // No "exported by" lines, so the rows paste straight into the master sheet.
    expect(xml, isNot(contains('Tester')));
    expect(xml, isNot(contains('Exported')));

    // Cell text / number, and style, by reference.
    final cells = {for (final c in sheet.findAllElements('c')) c.getAttribute('r')!: c};
    String value(String ref) => cells[ref]!.innerText;
    String? style(String ref) => cells[ref]!.getAttribute('s');
    final rows = sheet.findAllElements('row').toList();

    // Merged titles.
    expect(sheet.findAllElements('mergeCell').map((e) => e.getAttribute('ref')), ['A1:E1', 'F1:H1']);
    expect(sheet.findAllElements('mergeCells').single.getAttribute('count'), '2');
    expect(value('A1'), 'Sea Can Movements');
    expect(value('F1'), 'Blue Bin Movements');
    for (final c in 'BCDEGH'.split('')) {
      expect(value('${c}1'), '');
      expect(style('${c}1'), style('A1')); // borders show all the way across
    }
    // Headings.
    expect(
      [for (final c in 'ABCDEFGH'.split('')) value('${c}2')],
      ['Moves', 'Can #', 'From Location', 'To Location', 'Weight', 'Bin #', 'From Location', 'To Location'],
    );
    expect(style('A2'), style('A1'));
    // Row heights: title 24, headings 42, divider 12.
    for (final (i, ht) in [(0, '24.0'), (1, '42.0'), (2, '12.0')]) {
      expect(rows[i].getAttribute('ht'), ht);
      expect(rows[i].getAttribute('customHeight'), '1');
    }
    // Divider: all 8 cells, no text.
    expect([for (final c in 'ABCDEFGH'.split('')) value('${c}3')], List.filled(8, ''));
    final divider = style('A3');
    expect(divider, isNot(style('A1')));

    // Moves: numbered rows, 3 sea cans then 5 spare rows (numbered), all 8 cells formatted.
    expect(rows.length, 3 + 3 + SeaCanXlsx.spareRows);
    for (var n = 1; n <= 8; n++) {
      final r = n + 3;
      expect(cells['A$r']!.getAttribute('t'), isNull); // a number, not text
      expect(cells['A$r']!.findElements('v').single.innerText, '$n');
      for (final c in 'BCDEFGH'.split('')) {
        expect(cells.containsKey('$c$r'), isTrue);
        expect(style('$c$r'), style('B4'));
      }
      expect(style('A$r'), style('A4'));
    }
    expect([for (final c in 'BCDEFGH'.split('')) value('${c}4')], ['MSCU 123456', 'D12 L2', 'E40 L1', '', '', '', '']);
    expect([value('B5'), value('C5'), value('D5')], ['TCLU, "A" 7', '3', '']);
    expect([value('B6'), value('C6'), value('D6')], ['Line\nTwo & <co>', '', '9 L3']);
    expect(xml, contains('Two &amp; &lt;co&gt;'));
    for (var r = 7; r <= 11; r++) {
      expect(value('B$r'), '');
    }
    expect(cells.containsKey('A12'), isFalse);

    // Column widths A..H.
    expect(
      sheet
          .findAllElements('col')
          .map((e) => '${e.getAttribute('min')}-${e.getAttribute('max')}:${e.getAttribute('width')}'),
      ['1-1:16', '2-2:20', '3-3:20', '4-4:18', '5-5:11', '6-6:12', '7-7:20', '8-8:18'],
    );

    // Styles: Times New Roman, peach / near black / light blue fills, thin black borders.
    final styles = XmlDocument.parse(files['xl/styles.xml']!);
    final fonts = styles.findAllElements('font').toList();
    final fills = styles.findAllElements('fill').toList();
    final borders = styles.findAllElements('border').toList();
    final xfs = styles.findAllElements('cellXfs').single.findElements('xf').toList();
    XmlElement xf(String ref) => xfs[int.parse(style(ref)!)];
    XmlElement font(String ref) => fonts[int.parse(xf(ref).getAttribute('fontId')!)];
    String fill(String ref) =>
        fills[int.parse(xf(ref).getAttribute('fillId')!)].findAllElements('fgColor').single.getAttribute('rgb')!;
    String fontDesc(XmlElement f) =>
        '${f.findElements('name').single.getAttribute('val')} '
        '${f.findElements('sz').single.getAttribute('val')}${f.findElements('b').isEmpty ? '' : ' bold'}';
    String? align(String ref, String a) => xf(ref).findElements('alignment').firstOrNull?.getAttribute(a);

    expect(fontDesc(font('A1')), 'Times New Roman 14 bold');
    expect(fill('A1'), 'FFF8CBAD');
    expect(align('A2', 'horizontal'), 'center');
    expect(align('A2', 'vertical'), 'center');
    expect(fill('A3'), 'FF262626');
    expect(fontDesc(font('A4')), 'Times New Roman 12');
    expect(align('A4', 'horizontal'), 'right');
    expect(fontDesc(font('B4')), 'Times New Roman 12 bold');
    expect(align('B4', 'horizontal'), 'center');
    expect(fill('A4'), 'FFDCE6F1');
    expect(fill('B4'), 'FFDCE6F1');
    for (final ref in ['A1', 'A2', 'A4', 'B4', 'H11']) {
      final border = borders[int.parse(xf(ref).getAttribute('borderId')!)];
      for (final side in ['left', 'right', 'top', 'bottom']) {
        final e = border.findElements(side).single;
        expect(e.getAttribute('style'), 'thin');
        expect(e.findElements('color').single.getAttribute('rgb'), 'FF000000');
      }
    }
  });

  test('xlsx with no sea cans: titles, headings, divider and 5 numbered rows', () {
    final sheet = XmlDocument.parse(_unzip(SeaCanXlsx.write(Project(), 'T', day))['xl/worksheets/sheet1.xml']!);
    final rows = sheet.findAllElements('row').toList();
    expect(rows.length, 3 + 5);
    final cells = {for (final c in sheet.findAllElements('c')) c.getAttribute('r')!: c.innerText};
    expect(cells['A1'], 'Sea Can Movements');
    expect(cells['B2'], 'Can #');
    expect([for (var r = 4; r <= 8; r++) cells['A$r']], ['1', '2', '3', '4', '5']);
    expect([for (var r = 4; r <= 8; r++) cells['B$r']], List.filled(5, ''));
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
      'Moves',
      'Can #',
      'From location',
      'Current location',
      'MSCU 123456',
      'D12 L2',
      'E40 L1',
      'TCLU, "A" 7',
      '3',
      '9 L3',
      '3 sea cans',
    ]) {
      expect(text, contains(s));
    }
    // Moves column: 1, 2, 3, each followed by its sea can.
    expect(text.sublist(text.indexOf('Current location') + 1, text.indexOf('Current location') + 5), [
      '1',
      'MSCU 123456',
      'D12 L2',
      'E40 L1',
    ]);
    expect(doc, contains('Two &amp; &lt;co&gt;'));
    expect(text.where((t) => t == '–').length, 2);
    expect(text, isNot(contains('Row')));
    expect(text, isNot(contains('Level')));
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
      'Moves',
      'Can #',
      'From location',
      'Current location',
      'MSCU 123456',
      'D12 L2',
      'E40 L1',
      '9 L3',
      'TCLU, "A" 7',
      '3 sea cans',
    ]) {
      expect(text, contains(s));
    }
    expect(text, contains(RegExp(r'1\s+MSCU 123456\s+D12 L2\s+E40 L1')));
    final e = File('${tmp.path}/seacans_empty.pdf')
      ..writeAsBytesSync(await ReportPdf.writeSeaCans(Project(), 'T', day));
    expect(await _pdfText(e), contains('No sea cans entered yet.'));
  });

  test('120 sea cans run onto more pages', () async {
    p.seaCans = [
      for (var i = 1; i <= 120; i++) _can('CAN ${i.toString().padLeft(3, '0')}', '${i % 40 + 1}', '${i % 3 + 1}'),
    ];
    final bytes = await ReportPdf.writeSeaCans(p, 'Tester', day);
    final f = File('${tmp.path}/seacans_120.pdf')..writeAsBytesSync(bytes);
    _keep(f);
    expect(_pages(bytes), greaterThanOrEqualTo(3));
    final text = await _pdfText(f);
    if (text == null) return;
    expect(text, contains('CAN 001'));
    expect(text, contains('CAN 120'));
    expect(text, contains('120 sea cans'));
    expect(text, contains(RegExp(r'120\s+CAN 120')));
    // The header repeats on every page.
    expect('Current location'.allMatches(text).length, _pages(bytes));
    expect(RegExp(r'Moves\s+Can #').allMatches(text).length, _pages(bytes));
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
    final f = File('${tmp.path}/report_both.pdf')
      ..writeAsBytesSync(await ReportPdf.write(p, log, 'Tester', day, tmp.path));
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
      find.byWidgetPredicate(
        (w) => w is Semantics && w.properties.label == 'Export sea cans to PDF, Excel, CSV or Word',
      ),
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
