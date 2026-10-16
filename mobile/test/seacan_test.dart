import 'dart:convert';
import 'dart:io';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:dailyworklog/screens/seacan_pane.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('sea cans save and load, in the Android app\'s format', () {
    final p = Project()
      ..seaCans = [
        SeaCanMove()
          ..can = 'TCLU 317503'
          ..fromLetter = 'D'
          ..fromRow = '12'
          ..fromLevel = '2'
          ..toLetter = 'Z'
          ..toRow = '40'
          ..toLevel = '1',
        SeaCanMove(),
      ];
    final json = jsonEncode(p.toJson());
    expect(
      json,
      contains(
        '"seaCans":[{"can":"TCLU 317503","fromLetter":"D","fromRow":"12","fromLevel":"2",'
        '"toLetter":"Z","toRow":"40","toLevel":"1"}',
      ),
    );
    final q = Project.fromJson(jsonDecode(json) as Map<String, dynamic>);
    expect(q.seaCans.first.toRow, '40');
    expect(q.seaCans.first.fromLetter, 'D');
    expect(q.seaCans.first.toLetter, 'Z');
    expect(q.seaCans.first.fromLocation(), 'D12 L2');
    expect(q.seaCans.first.toLocation(), 'Z40 L1');
    expect(q.seaCans.last.isEmpty, isTrue);
    // A line saved before letters existed loads with no letters.
    final old = SeaCanMove.fromJson({'can': 'X', 'fromRow': '3', 'fromLevel': '1', 'toRow': '', 'toLevel': ''});
    expect(old.fromLetter, '');
    expect(old.toLetter, '');
    expect(old.fromLocation(), '3 L1');
    // Only a letter picked still counts as something on the line.
    expect((SeaCanMove()..fromLetter = 'A').isEmpty, isFalse);
    expect((SeaCanMove()..toLetter = 'B').isEmpty, isFalse);
    expect(Project.fromJson({'name': 'old'}).seaCans, isEmpty);
    prepareSeaCans(q);
    expect(q.seaCans.length, seaCanStartLines);
    expect(seaCansEntered(q), 1);
  });

  test('location text: letter and row, then the level', () {
    expect(SeaCanMove.location('D', '33', '3'), 'D33 L3');
    expect(SeaCanMove.location('', '12', '2'), '12 L2');
    expect(SeaCanMove.location('E', '', ''), 'E');
    expect(SeaCanMove.location('', '', ''), '');
    expect(SeaCanMove.location('', '', '3'), 'L3');
    expect(SeaCanMove.location('A', '', '1'), 'A L1');
    expect(SeaCanMove.location('', '7', ''), '7');
    expect(SeaCanMove.location('B', '40', ''), 'B40');
  });

  testWidgets('Sea Can Movements tab: type a number, pick letters, rows and levels, add rows', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final dir = Directory.systemTemp.createTempSync('seacan');
    late Store store;
    await tester.runAsync(() async => store = await Store.open(dir));
    final p = Project()..name = 'B44';
    store.projects.add(p);
    await tester.pumpWidget(MaterialApp(home: ProjectScreen(projectId: p.id)));
    await tester.tap(find.text('Sea Can Movements'));
    await tester.pumpAndSettle();
    expect(find.text('Sea can'), findsOneWidget);
    expect(find.text('Identifier'), findsOneWidget);
    expect(find.text('From location'), findsOneWidget);
    expect(find.text('Current location'), findsOneWidget);
    expect(find.text('Letter'), findsNWidgets(2));
    expect(find.text('Row'), findsNWidgets(2));
    expect(find.text('Level'), findsNWidgets(2));
    // One line per sea can, nothing spilling over at phone width.
    expect(tester.takeException(), isNull);
    expect(p.seaCans.length, seaCanStartLines);

    await tester.enterText(find.byType(TextField).first, 'MSCU 123456');
    await tester.pump();
    expect(p.seaCans.first.can, 'MSCU 123456');
    expect(find.text('1 sea can entered'), findsOneWidget);

    // The identifier box and six dropdowns share one line, the arrow between From and Current.
    final canBox = tester.getRect(find.byType(TextField).first);
    final boxes = find.byType(DropdownButton<String>);
    final rects = [for (var i = 0; i < 6; i++) tester.getRect(boxes.at(i))];
    for (final r in rects) {
      expect(r.center.dy, moreOrLessEquals(canBox.center.dy, epsilon: 2));
      expect(r.left, greaterThan(canBox.right));
    }
    // Letter and Level boxes the same width, Row a bit wider; same on both sides.
    expect(rects[0].width, moreOrLessEquals(rects[2].width, epsilon: 0.5));
    expect(rects[1].width, greaterThan(rects[0].width));
    for (var i = 0; i < 3; i++) {
      expect(rects[i + 3].width, moreOrLessEquals(rects[i].width, epsilon: 0.5));
    }
    expect(canBox.width, greaterThan(rects[1].width * 2));
    expect(rects[3].left - rects[2].right, greaterThan(20)); // room for the arrow
    expect(find.bySemanticsLabel('moved to'), findsWidgets);

    // From letter: a dash, then A to Z; pick D
    final letterBox = tester.widget<DropdownButton<String>>(boxes.first);
    expect(letterBox.items!.map((e) => e.value), ['–', for (var c = 0x41; c <= 0x5A; c++) String.fromCharCode(c)]);
    await tester.tap(boxes.first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('D').last);
    await tester.pumpAndSettle();
    expect(p.seaCans.first.fromLetter, 'D');

    // From row: rows go 1 to 40; open the second dropdown and pick 5
    final rowBox = tester.widget<DropdownButton<String>>(boxes.at(1));
    expect(rowBox.items!.map((e) => e.value), ['–', for (var i = 1; i <= 40; i++) '$i']);
    await tester.tap(boxes.at(1));
    await tester.pumpAndSettle();
    await tester.tap(find.text('5').last);
    await tester.pumpAndSettle();
    expect(p.seaCans.first.fromRow, '5');
    // Current letter: the fourth dropdown
    await tester.tap(boxes.at(3));
    await tester.pumpAndSettle();
    await tester.tap(find.text('E').last);
    await tester.pumpAndSettle();
    expect(p.seaCans.first.toLetter, 'E');
    // Current level: the sixth dropdown on the line, levels only go to 3
    await tester.tap(boxes.at(5));
    await tester.pumpAndSettle();
    expect(find.text('4'), findsNothing);
    await tester.tap(find.text('3').last);
    await tester.pumpAndSettle();
    expect(p.seaCans.first.toLevel, '3');
    expect(p.seaCans.first.fromLocation(), 'D5');
    expect(p.seaCans.first.toLocation(), 'E L3');
    expect(tester.takeException(), isNull);

    // Add rows at the bottom; then empty ones can be removed again
    await tester.scrollUntilVisible(
      find.text('+  Add 10 rows'),
      400,
      scrollable: find.descendant(of: find.byType(SeaCanPane), matching: find.byType(Scrollable)).first,
    );
    await tester.tap(find.text('+  Add 10 rows'));
    await tester.pumpAndSettle();
    expect(p.seaCans.length, seaCanStartLines + 10);
    await tester.tap(find.text('Remove empty rows'));
    await tester.pumpAndSettle();
    // One line is kept, then topped back up to the starting number of lines
    expect(p.seaCans.first.can, 'MSCU 123456');
    expect(p.seaCans.length, seaCanStartLines);
  });

  for (final (w, h, ratio) in [(1080.0, 2340.0, 2.75), (1080.0, 2220.0, 3.0), (1236.0, 2676.0, 3.0)]) {
    testWidgets('Sea Can tab fits at ${(w / ratio).round()} dp wide with the widest picks', (tester) async {
      tester.view.physicalSize = Size(w, h);
      tester.view.devicePixelRatio = ratio;
      addTearDown(tester.view.reset);
      final dir = Directory.systemTemp.createTempSync('seacan_fit');
      late Store store;
      await tester.runAsync(() async => store = await Store.open(dir));
      final p = Project()
        ..name = 'B44'
        ..seaCans = [
          for (var i = 0; i < 3; i++)
            SeaCanMove()
              ..can = i == 0 ? 'BPPU 201006-1' : 'BPPU 211028-7 LONG NUMBER $i'
              ..fromLetter = 'W'
              ..fromRow = '40'
              ..fromLevel = '3'
              ..toLetter = 'M'
              ..toRow = '38'
              ..toLevel = '2',
        ];
      store.projects.add(p);
      await tester.pumpWidget(
        MaterialApp(
          home: ProjectScreen(projectId: p.id, initialTab: ProjectTab.seaCans),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('W'), findsWidgets);
      expect(find.text('40'), findsWidgets);
      // The From / Current headings sit over their three dropdowns.
      final boxes = find.byType(DropdownButton<String>);
      // A long number wraps onto a second line (all of it shown); the dropdowns stay centred beside it.
      final canBox = tester.getRect(find.byType(TextField).first);
      final box = tester.getRect(boxes.first);
      expect(find.text('BPPU 201006-1'), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField).first).maxLines, 2);
      if (w / ratio < 400) expect(canBox.height, greaterThan(box.height + 10));
      expect(box.center.dy, moreOrLessEquals(canBox.center.dy, epsilon: 2));
      final from = tester.getCenter(find.text('From location'));
      final cur = tester.getCenter(find.text('Current location'));
      expect(from.dx, moreOrLessEquals(tester.getCenter(boxes.at(1)).dx, epsilon: 3));
      expect(cur.dx, moreOrLessEquals(tester.getCenter(boxes.at(4)).dx, epsilon: 3));
    });
  }
}
