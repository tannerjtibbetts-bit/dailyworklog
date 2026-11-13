// Ported from ImportTest and CombineTest (Java), plus the review list's rules from ImportActivity.
import 'dart:io';
import 'dart:typed_data';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/import/import_review.dart';
import 'package:dailyworklog/import/scan_reader.dart';
import 'package:dailyworklog/import/sheet_import.dart';
import 'package:dailyworklog/import/zone_sheet.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('SheetImport (ImportTest)', () {
    test('xlsx', () {
      final x = SheetImport.fromFile(File('test/fixtures/import_sample.xlsx').readAsBytesSync());
      expect(x, [
        ['#', 'Task', 'Location', 'Sea can'],
        ['1', 'Pack up sea can and send out of zone 2', 'Zone 2', '317503'],
        ['2', 'Inspect torque wrenches', 'Tool crib', ''],
        ['3', 'Return rental generator', 'Laydown yard', 'SC-88'],
      ]);
      expect(SheetImport.likelyTaskColumn(x), 1);
    });

    test('csv', () {
      expect(SheetImport.fromCsv('Task,Where\n"Fix pump, north",Zone 1\r\nClean bay,\n'), [
        ['Task', 'Where'],
        ['Fix pump, north', 'Zone 1'],
        ['Clean bay', ''],
      ]);
      // Semicolons and tabs, a byte order mark, quotes inside quotes
      expect(SheetImport.fromCsv('﻿a;b\n"say ""hi""";2'), [
        ['a', 'b'],
        ['say "hi"', '2'],
      ]);
      expect(SheetImport.fromCsv('a\tb\nc\td'), [
        ['a', 'b'],
        ['c', 'd'],
      ]);
    });

    test('old xls is refused', () {
      expect(() => SheetImport.fromFile(Uint8List.fromList([0xD0, 0xCF, 0x11, 0xE0])),
          throwsA(isA<SheetException>().having((e) => e.code, 'code', 'old-xls')));
    });

    test('photo boxes', () {
      final b = [
        // simulated photo: 3 columns, slight vertical jitter
        const Box('Task', 100, 100, 160, 130), const Box('Location', 600, 102, 720, 131),
        const Box('Qty', 900, 99, 950, 128),
        const Box('Pack up sea can', 102, 150, 380, 180), const Box('Zone 2', 598, 153, 690, 183),
        const Box('4', 930, 151, 945, 181),
        const Box('Inspect torque', 99, 200, 330, 230), const Box('wrenches', 340, 201, 470, 231),
        const Box('Tool crib', 601, 198, 730, 228),
      ];
      final o = SheetImport.fromBoxes(b);
      expect(o, [
        ['Task', 'Location', 'Qty'],
        ['Pack up sea can', 'Zone 2', '4'],
        ['Inspect torque wrenches', 'Tool crib', ''],
      ]);
      expect(SheetImport.likelyTaskColumn(o), 0);
      // Not the zone sheet, so the photo reads as rows.
      final r = ScanReader.interpret(b, null);
      expect(r.zone, isNull);
      expect(r.rows, o);
    });

    test('columnIndex', () {
      expect(SheetImport.columnIndex('A1'), 0);
      expect(SheetImport.columnIndex('B12'), 1);
      expect(SheetImport.columnIndex('AA3'), 26);
    });
  });

  test('combineRows (CombineTest)', () {
    final rows = [
      ['Task', 'Where'],
      ['Pack up sea can', 'Zone 2'],
      ['and send out of', 'Zone 2'],
      ['zone 2 today', ''],
      ['Inspect wrenches', 'Crib'],
    ];
    SheetImport.combineRows(rows, [1, 2, 3], 0, 'Pack up sea can and send out of zone 2 today');
    expect(rows.toString(), '[[Task, Where], [Pack up sea can and send out of zone 2 today, Zone 2], [Inspect wrenches, Crib]]');
  });

  group('ImportReview', () {
    test('spreadsheet: heading row and number-only rows start unticked', () {
      final r = ImportReview.rows([
        ['#', 'Task', 'Where'],
        ['1', 'Pack up sea can', 'Zone 2'],
        ['2', '1234', ''],
        ['3', 'inspect wrenches', 'Crib'],
      ], 'the spreadsheet', header: true);
      expect(r.column, 1);
      expect(r.chosen(), [1, 3]);
      expect(r.notes(1), '1  ·  Zone 2');
      r.notesFromOthers = false;
      expect(r.notes(1), '');
      expect(r.intro(), startsWith('Found 4 rows in the spreadsheet.'));
      expect(r.columnOption(2), 'Column 3: Where');
      final tasks = r.toTasks('Pat');
      expect(tasks.map((t) => t.title), ['Pack up sea can', 'Inspect wrenches']);
      expect(tasks.every((t) => t.createdBy == 'Pat' && t.side.isEmpty), isTrue);
    });

    test('fix and combine keep ticks and fixes on other rows', () {
      final r = ImportReview.rows([
        ['Pack up sea can', 'Zone 2'],
        ['and send out of', 'Zone 2'],
        ['zone 2 today', ''],
        ['lnspect wrenches', 'Crib'],
        ['Clean bay', ''],
      ], 'the photo');
      r.fix(3, 'Inspect wrenches');
      r.tick(4, false);
      r.tick(3, false);
      expect(r.chosen(), [0, 1, 2]);
      expect(r.joinedTitles(r.chosen()), 'Pack up sea can and send out of zone 2 today');
      r.combine(r.chosen(), 'Pack up sea can and send out of zone 2 today');
      expect(r.rows.length, 3);
      expect(r.title(0), 'Pack up sea can and send out of zone 2 today');
      expect(r.rows[0][1], 'Zone 2');
      expect(r.title(1), 'Inspect wrenches');
      expect(r.ticked(1), isFalse);
      expect(r.ticked(2), isFalse);
      expect(r.chosen(), [0]);
    });

    test('changing the column re-ticks', () {
      final r = ImportReview.rows([
        ['1', 'A task'],
        ['2', 'Another'],
      ], 'the photo');
      r.setColumn(0);
      expect(r.chosen(), isEmpty);
      r.setColumn(1);
      expect(r.chosen(), [0, 1]);
    });

    test('zone tasks: only your colours ticked, side set', () {
      ZoneTask t(String task, String colour, String side) => ZoneTask()
        ..task = task
        ..seacan = 'BPPU 211028-7'
        ..job = 'CT release'
        ..colour = colour
        ..side = side;
      final r = ImportReview.zone([
        t('Unload Can', ZoneSheet.blue, Task.north),
        t('Misc.', ZoneSheet.none, Task.north),
        t('Decon', ZoneSheet.orange, Task.north),
      ], {'blue', 'orange'});
      expect(r.zone, isTrue);
      expect(r.source, 'the North sheet');
      expect(r.chosen(), [0, 2]);
      expect(r.subtitle(0), '■ Dark blue\nJob: CT release');
      expect(r.subtitle(1), startsWith('□ No colour'));
      final tasks = r.toTasks('Me');
      expect(tasks.map((t) => t.title), ['Unload Can – BPPU 211028-7', 'Decon – BPPU 211028-7']);
      expect(tasks.every((t) => t.side == Task.north), isTrue);
      expect(tasks[0].notes, 'Job: CT release');
    });
  });
}

