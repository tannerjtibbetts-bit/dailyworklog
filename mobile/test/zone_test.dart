// Ported from ZoneTest (Java): the North zone sheet photo and its OCR boxes.
import 'dart:convert';
import 'dart:io';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/import/sheet_import.dart';
import 'package:dailyworklog/import/zone_sheet.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;

void main() {
  final json = jsonDecode(File('test/fixtures/north_ocr.json').readAsStringSync()) as List;
  final boxes = [
    for (final o in json) Box(o['t'] as String, o['l'] as int, o['t0'] as int, o['r'] as int, o['b'] as int)
  ];

  test('north sheet: tasks, colours and side', () {
    final image = img.decodeJpg(File('test/fixtures/north.jpg').readAsBytesSync())!;
    final tasks = ZoneSheet.parse(boxes, ImagePixels(image))!;
    expect(boxes.length, 106);
    final got = [for (final t in tasks) '[${t.colour}] ${t.side} | ${t.title()} | ${t.notes().replaceAll('\n', ' / ')}'];
    expect(got, [
      '[blue] north | Unload Can – BPPU 211028-7 – Tent1A(west) | Job: CTreleaserefurb60/40 / Contact: HalderAkhtar / Location: Tent1A(west)',
      '[blue] north | Unload Can – BPPU 207022-4 – Tent1A(west) | Job: CTlreleaserefurb60/40 / Contact: HalderAkhtar / Location: Tent1A(west)',
      '[none] north | Misc. – BPPU 202050-0 – Tent1A(west) | Job: Fox / Location: Tent1A(west) / Comments: rlgging Inspectlons',
      '[blue] north | Decon – BPPU 207057-0 – Tent1B(east) | Job: CTl releaserefurb60/40 / Location: Tent1B(east)',
      '[blue] north | Returb inspections – BPPU 410018-5 – Mock-Up(West) | Job: BWSTrefurb60/40 / Contact: RickCluett / Location: Mock-Up(West)',
    ]);
    expect(tasks.every((t) => t.side == Task.north), isTrue);
  });

  test('without pixels every task has no colour', () {
    final tasks = ZoneSheet.parse(boxes, null)!;
    expect(tasks.length, 5);
    expect(tasks.every((t) => t.colour == ZoneSheet.none), isTrue);
  });

  test('not the zone sheet', () {
    expect(ZoneSheet.parse([const Box('Task', 0, 0, 50, 20)], null), isNull);
  });

  test('sea can numbers', () {
    expect(ZoneSheet.isSeacan('BPPU 211028-7'), isTrue);
    expect(ZoneSheet.isSeacan('BPPU 2IIO28'), isTrue);
    expect(ZoneSheet.isSeacan('Unload Can'), isFalse);
    expect(ZoneSheet.tidySeacan('bppu2110O8-7'), 'BPPU 211008-7');
  });

  test('colour classification', () {
    int cls(int r, int g, int b) => ZoneSheet.classify(ZoneSheet.hsv(r, g, b));
    expect(cls(0x1F, 0x4E, 0x9A), 0); // dark blue
    expect(cls(0xF4, 0xA2, 0x3C), 1); // orange
    expect(cls(0xF4, 0x8F, 0xB1), 2); // pink
    expect(cls(0x4C, 0xAF, 0x50), 3); // green
    expect(cls(0xFF, 0xEB, 0x3B), 4); // yellow
    expect(cls(0xFF, 0xFF, 0xFF), 5); // white
  });
}
