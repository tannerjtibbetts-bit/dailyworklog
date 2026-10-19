import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/export/export.dart';
import 'package:dailyworklog/export/overtime_xlsx.dart';
import 'package:dailyworklog/export/photo_layout.dart';
import 'package:dailyworklog/export/report_docx.dart';
import 'package:dailyworklog/export/report_pdf.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:xml/xml.dart';

/// A small photo: a colour gradient with a dark band, so it's clearly visible in a render.
File _photo(Directory dir, String name, int w, int h, {int orientation = 1, int hue = 0}) {
  final im = img.Image(width: w, height: h);
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      final band = (y * 8 ~/ h).isEven;
      im.setPixelRgb(x, y, (x * 255 ~/ w + hue) % 256, band ? 160 : 60, (y * 255 ~/ h));
    }
  }
  if (orientation != 1) im.exif.imageIfd.orientation = orientation;
  return File('${dir.path}/$name')..writeAsBytesSync(img.encodeJpg(im, quality: 90));
}

Photo _ph(String file, int at, [String by = 'Tanner Tibbetts']) => Photo(file: file, addedBy: by, addedAt: at);

Map<String, String> _unzip(List<int> bytes) {
  final a = ZipDecoder().decodeBytes(bytes);
  return {
    for (final f in a.files)
      if (f.isFile) f.name: f.name.endsWith('.jpeg') ? '<${f.size} bytes>' : utf8.decode(f.content),
  };
}

void main() {
  late Directory tmp;
  late Store store;
  late Project p;
  late DailyLog log;
  final day = DateTime(2026, 10, 5, 7, 32).millisecondsSinceEpoch;

  setUpAll(() async {
    tmp = await Directory.systemTemp.createTemp('export_test');
    store = await Store.open(tmp);
    final d = store.photoDir;
    _photo(d, 'tall1.jpg', 300, 650);
    _photo(d, 'tall2.jpg', 300, 650, hue: 80);
    _photo(d, 'wide.jpg', 800, 600, hue: 160);
    _photo(d, 'turned.jpg', 650, 300, orientation: 6, hue: 40);
    _photo(d, 'big.jpg', 2000, 4000, hue: 120);

    p = Project()
      ..name = 'B44 Zone 2 Tool Readiness'
      ..location = 'B44'
      ..emails = ['a@b.com'];
    log = DailyLog()
      ..date = '2026-10-05'
      ..crew = ['Jim McLaren', 'Alex Symonds', 'Zach McCabe'];
    Task t(String title, String side, {bool done = false, List<Photo> photos = const []}) => Task()
      ..title = title
      ..side = side
      ..done = done
      ..completedBy = done ? 'Tanner Tibbetts' : ''
      ..completedAt = day
      ..photos = List.of(photos);
    log.tasks.addAll([
      t('North field one', 'north', done: true, photos: [_ph('wide.jpg', day)]),
      t(
          'Remove sea can 202080 from off gas tent',
          'south',
          done: true,
          photos: [
            _ph('tall1.jpg', day),
            _ph('tall2.jpg', day + 3600000),
            _ph('turned.jpg', DateTime(2026, 10, 4, 15, 5).millisecondsSinceEpoch, 'Jim McLaren'),
            _ph('big.jpg', day),
            _ph('missing.jpg', day),
          ],
        )
        ..assignees = ['Jim McLaren', 'Alex Symonds']
        ..loadSecured = true
        ..securedBy = 'Jim McLaren'
        ..securedAt = day
        ..notes = 'Pulled both motors out as well. Smart ‘quotes’ – and ✔ and emoji \u{1F44D}.',
      t('Check torque on bay 3', 'south')
        ..nextShift = true
        ..photosRequired = true
        ..notes = List.generate(
          30,
          (i) => 'Line ${i + 1} of a long note that keeps going so the report has to break pages.',
        ).join('\n'),
      t('Pack up & go <home>', '', done: true),
    ]);
    log.extras.add(
      Extra()
        ..type = Extra.note
        ..title = 'Tanner'
        ..detail = 'Pump 3 leaking'
        ..at = day
        ..include = true
        ..photos = [_ph('tall1.jpg', day)],
    );
    log.extras.add(
      Extra()
        ..type = Extra.link
        ..title = 'Tool portal'
        ..detail = 'https://example.com/tools'
        ..at = day + 60000
        ..include = true,
    );
    log.extras.add(
      Extra()
        ..type = Extra.template
        ..title = 'Not ticked'
        ..include = false,
    );
    p.overtime
      ..weekOf = '2026-10-09'
      ..include = true
      ..notes = 'Bring harnesses\nGate 3 opens at 6';
    for (final r in [
      ['Jim McLaren', 'Yes', 'No', 'Yes', '8'],
      ['Alex Symonds', 'Yes', 'Yes', '', '10'],
      ['', '', '', '', ''],
      ['Beau & Co <test>', 'No', '', 'Yes', ''],
    ]) {
      p.overtime.rows.add(
        OtRow()
          ..name = r[0]
          ..fri = r[1]
          ..sat = r[2]
          ..sun = r[3]
          ..hours = r[4],
      );
    }
    p.logs.add(log);
  });

  tearDownAll(() async {
    await tmp.delete(recursive: true);
  });

  group('LayoutTest', () {
    test('photo rows', () {
      final s = <PhotoSize?>[
        for (var i = 0; i < 7; i++) const PhotoSize(612, 1320), // tall phone photos
        const PhotoSize(4000, 3000), // one wide photo
        null, // missing file
      ];
      final rows = PhotoLayout.rows(s, 500, 130, 8, 90);
      for (final r in rows) {
        expect(r.last.x + r.last.w, lessThanOrEqualTo(500.01), reason: 'row fits width');
      }
      expect(rows.first.length, greaterThanOrEqualTo(6), reason: 'tall photos pack 6+ per row');
      expect(rows.expand((r) => r).map((c) => c.index).toList(), List.generate(9, (i) => i));
      final missing = rows.last.last;
      expect([missing.w, missing.h], [90, 14]);
      expect(PhotoLayout.height(rows.first), 130);
    });

    test('report photos are half the old size and narrow ones keep room for a caption', () {
      expect(ReportPdf.photoH, 65);
      final s = [for (var i = 0; i < 3; i++) const PhotoSize(612, 1320)];
      final rows = PhotoLayout.rows(s, 500, PhotoLayout.reportH, 8, 90, minSlot: PhotoLayout.reportSlot);
      final r = rows.single;
      expect(r.first.w, closeTo(30.1, 0.1));
      expect(r[1].x - r[0].x, 48); // 40 slot + 8 gap
    });

    test('very wide photo fits the width', () {
      final rows = PhotoLayout.rows(const [PhotoSize(100, 10), PhotoSize(10, 10)], 500, 130, 8, 90);
      expect(rows.length, 2);
      expect(rows[0][0].w, 500);
      expect(rows[0][0].h, 50);
      expect(rows[1][0].x, 0);
    });

    test('captions', () {
      final ph = Photo(addedBy: 'Jim McLaren', addedAt: DateTime(2026, 10, 5, 7, 32).millisecondsSinceEpoch);
      expect(PhotoLayout.caption(ph, '2026-10-05'), 'JM 7:32 AM');
      expect(PhotoLayout.caption(ph, '2026-10-06'), 'JM 10/05 7:32 AM');
      expect(PhotoLayout.caption(Photo(addedAt: ph.addedAt), '2026-10-05'), '7:32 AM');
    });

    test('photo sizes read upright from the file', () {
      expect(PhotoLayout.size(store.photoFile('tall1.jpg'))?.width, 300);
      final turned = PhotoLayout.size(store.photoFile('turned.jpg'))!;
      expect([turned.width, turned.height], [300, 650]);
      expect(PhotoLayout.size(store.photoFile('missing.jpg')), isNull);
    });

    test('photos are shrunk to the printed size at 220 dpi', () {
      // 2000x4000 drawn 65x130 pt: 130 pt at 220 dpi is 397 px.
      final e = PhotoLayout.encode(store.photoFile('big.jpg'), (65 * 220 / 72).round(), (130 * 220 / 72).round())!;
      expect(e.height, 397);
      expect(e.width, closeTo(199, 1));
      final back = img.decodeJpg(e.jpeg)!;
      expect([back.width, back.height], [e.width, e.height]);
      // Turned photos come out upright.
      final t = PhotoLayout.encode(store.photoFile('turned.jpg'), 1000, 1000)!;
      expect([t.width, t.height], [300, 650]);
      // Small upright JPEGs are kept as they are.
      final small = PhotoLayout.encode(store.photoFile('tall1.jpg'), 1000, 1000)!;
      expect(small.jpeg, store.photoFile('tall1.jpg').readAsBytesSync());
      expect(PhotoLayout.encode(store.photoFile('missing.jpg'), 100, 100), isNull);
    });
  });

  group('OtTest', () {
    test('rows and notes saved, empty rows removed, clearing keeps notes', () {
      final p = Project();
      final ot = p.overtime..notes = 'Need 6 for Saturday\nBring harnesses';
      for (var i = 0; i < 40; i++) {
        ot.rows.add(OtRow());
      }
      ot.rows[0].name = 'Al';
      ot.rows[3].sat = 'Yes';
      ot.rows[7].hours = '8';
      final q = Project.fromJson(jsonDecode(jsonEncode(p.toJson())) as Map<String, dynamic>);
      expect(q.overtime.rows.length, 40);
      expect(q.overtime.notes, ot.notes);
      q.overtime.removeEmptyRows();
      expect(q.overtime.rows.length, 3);
      q.overtime.startWeekend('2026-10-09');
      expect(q.overtime.notes, ot.notes);
    });
  });

  test('overtime xlsx', () {
    final bytes = OvertimeXlsx.write(p, 'Tester', day);
    _keep(File('${tmp.path}/overtime.xlsx')..writeAsBytesSync(bytes));
    final files = _unzip(bytes);
    expect(
      files.keys,
      containsAll([
        '[Content_Types].xml',
        '_rels/.rels',
        'xl/workbook.xml',
        'xl/_rels/workbook.xml.rels',
        'xl/styles.xml',
        'xl/worksheets/sheet1.xml',
      ]),
    );
    final sheet = files['xl/worksheets/sheet1.xml']!;
    XmlDocument.parse(sheet);
    expect(sheet, contains('Weekend overtime - B44 Zone 2 Tool Readiness'));
    expect(sheet, contains('Weekend of Fri Oct 9 to Sun Oct 11'));
    expect(sheet, contains('Exported Oct 5, 2026 at 7:32 AM by Tester'));
    expect(sheet, contains('<row r="5"><c r="A5" t="inlineStr" s="2"><is><t xml:space="preserve">Name</t>'));
    expect(sheet, contains('Beau &amp; Co &lt;test&gt;'));
    expect(sheet, contains('<c r="E6"><v>8</v></c>'));
    expect(sheet, contains('<c r="B6" t="inlineStr" s="3"><is><t xml:space="preserve">Yes</t>'));
    expect(sheet, contains('<c r="C6" t="inlineStr" s="4"><is><t xml:space="preserve">No</t>'));
    // Yes totals: Fri 2, Sat 1, Sun 2, on row 9 (3 people).
    expect(sheet, contains('<c r="B9" s="3"><v>2</v></c><c r="C9" s="3"><v>1</v></c><c r="D9" s="3"><v>2</v></c>'));
    expect(sheet, contains('<row r="11"><c r="A11" t="inlineStr" s="1"><is><t xml:space="preserve">Notes</t>'));
    expect(sheet, contains('Gate 3 opens at 6'));
    for (final x in files.values) {
      XmlDocument.parse(x);
    }
  });

  test('word report', () {
    final bytes = ReportDocx.write(p, log, 'Tanner Tibbetts', day, store.photoDir.path);
    _keep(File('${tmp.path}/report.docx')..writeAsBytesSync(bytes));
    final files = _unzip(bytes);
    expect(
      files.keys,
      containsAll(['[Content_Types].xml', '_rels/.rels', 'word/_rels/document.xml.rels', 'word/document.xml']),
    );
    // 5 task photos (one missing) + 1 in the note.
    expect(files.keys.where((k) => k.startsWith('word/media/')).length, 6);
    final doc = files['word/document.xml']!;
    final xml = XmlDocument.parse(doc);
    final text = xml.findAllElements('w:t').map((e) => e.innerText).join('|');
    for (final s in [
      'B44 ZONE 2 TOOL READINESS',
      'Daily work log 05/10/2026',
      'Generated Oct 5, 2026 at 7:32 AM by Tanner Tibbetts',
      '3/4 fields completed',
      'Crew: Jim McLaren, Alex Symonds, Zach McCabe',
      'South (2)',
      'North (1)',
      'Other (1)',
      'Remove sea can 202080 from off gas tent',
      'Completed by Tanner Tibbetts, 10/05/2026 at 7:32 AM',
      'Load securement completed',
      ' by Jim McLaren, 10/05/2026 at 7:32 AM',
      'Carries over to next shift',
      'Assigned: Jim McLaren, Alex Symonds',
      'Photos required: none attached',
      'Not completed',
      '(Photo missing)',
      'TT 7:32 AM',
      'JM 10/04 3:05 PM',
      'Pack up & go <home>',
      'Weekend overtime',
      'Beau & Co <test>',
      'Yes total',
      'Also today',
      '7:32 AM   Note by Tanner',
      'Pump 3 leaking',
      '7:33 AM   Link used: Tool portal',
      'https://example.com/tools',
    ]) {
      expect(text, contains(s));
    }
    expect(text, isNot(contains('Not ticked')));
    // South comes before North, then Other.
    expect(
      text.indexOf('South (2)') < text.indexOf('North (1)') && text.indexOf('North (1)') < text.indexOf('Other (1)'),
      isTrue,
    );
    final rels = files['word/_rels/document.xml.rels']!;
    for (var i = 1; i <= 6; i++) {
      expect(rels, contains('Id="rIdImg$i"'));
      expect(doc, contains('r:embed="rIdImg$i"'));
    }
  });

  test('pdf report', () async {
    final bytes = await ReportPdf.write(p, log, 'Tanner Tibbetts', day, store.photoDir.path);
    expect(String.fromCharCodes(bytes.take(5)), '%PDF-');
    final f = File('${tmp.path}/report.pdf')..writeAsBytesSync(bytes);
    _keep(f);
    final text = await _pdfText(f);
    if (text == null) return; // no pdftotext here
    final pages = '\f'.allMatches(text).length;
    expect(pages, inInclusiveRange(2, 4));
    for (final s in [
      'B44 ZONE 2 TOOL READINESS',
      'Daily work log 05/10/2026',
      '3/4 fields completed',
      'South (2)',
      'North (1)',
      'Remove sea can 202080 from off gas tent',
      'Load securement completed by Jim McLaren',
      'Smart ‘quotes’ – and',
      'Line 30 of a long note',
      'Photos required: none attached',
      '(Photo missing)',
      'Pack up & go <home>',
      'Also today',
      'Note by Tanner',
      'Weekend overtime',
      'Yes total',
      'Gate 3 opens at 6',
      'Page 1',
    ]) {
      expect(text, contains(s));
    }
    expect(text.indexOf('South (2)') < text.indexOf('North (1)'), isTrue);
    expect(text.indexOf('Also today') < text.indexOf('Weekend overtime'), isTrue);
  });

  test('overtime pdf', () async {
    final bytes = await ReportPdf.writeOvertime(p, 'Tester', day);
    final f = File('${tmp.path}/ot.pdf')..writeAsBytesSync(bytes);
    _keep(f);
    final text = await _pdfText(f);
    if (text == null) return;
    for (final s in [
      'Weekend overtime',
      'Weekend of Fri Oct 9 to Sun Oct 11',
      'Jim McLaren',
      'Beau & Co <test>',
      'Yes total',
      'Notes',
    ]) {
      expect(text, contains(s));
    }
    final empty = await ReportPdf.writeOvertime(Project(), 'Tester', day);
    final e = File('${tmp.path}/ot_empty.pdf')..writeAsBytesSync(empty);
    expect(await _pdfText(e), contains('No one has signed up yet.'));
  });

  testWidgets('overtime export builds the file in the background and offers it', (tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/path_provider'),
      (call) async => tmp.path,
    );
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
    final done = Export.overtime(ctx, p, excel: true);
    await tester.pump();
    expect(find.text('Building Excel sheet\u2026'), findsOneWidget);
    const name = 'Weekend overtime Fri Oct 9 B44 Zone 2 Tool Readiness.xlsx';
    for (var i = 0; i < 50 && find.text('$name is ready').evaluate().isEmpty; i++) {
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 100)));
      await tester.pump(const Duration(milliseconds: 100));
    }
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Building Excel sheet\u2026'), findsNothing);
    expect(find.text('$name is ready'), findsOneWidget);
    expect(find.text('Share or email'), findsOneWidget);
    expect(find.text('Save to phone'), findsOneWidget);
    expect(File('${tmp.path}/exports/$name').existsSync(), isTrue);
    Navigator.of(ctx).pop();
    await tester.pumpAndSettle();
    await done;
  });

  test('email helpers', () {
    expect(Export.parseEmails('a@b.com, bad; c@d.org  a@b.com\nx@y'), ['a@b.com', 'c@d.org']);
    expect(Export.emailSubject(p, log), 'Daily work log 05/10/2026 - B44 Zone 2 Tool Readiness');
    expect(
      Export.emailBody(p, log, 'Tanner'),
      'Daily work log 05/10/2026 - B44 Zone 2 Tool Readiness: 3/4 fields completed.\nReport attached.\n\nTanner',
    );
    expect(Export.reportFileName(log, true), 'Daily work log 05-10-2026.docx');
  });
}

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
