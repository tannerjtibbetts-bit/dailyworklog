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
          ..fromRow = '12'
          ..fromLevel = '2'
          ..toRow = '40'
          ..toLevel = '1',
        SeaCanMove(),
      ];
    final json = jsonEncode(p.toJson());
    expect(json, contains('"seaCans":[{"can":"TCLU 317503","fromRow":"12","fromLevel":"2","toRow":"40","toLevel":"1"}'));
    final q = Project.fromJson(jsonDecode(json) as Map<String, dynamic>);
    expect(q.seaCans.first.toRow, '40');
    expect(q.seaCans.last.isEmpty, isTrue);
    expect(Project.fromJson({'name': 'old'}).seaCans, isEmpty);
    prepareSeaCans(q);
    expect(q.seaCans.length, seaCanStartLines);
    expect(seaCansEntered(q), 1);
  });

  testWidgets('Sea Can Movements tab: type a number, pick rows and levels, add rows', (tester) async {
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
    expect(find.text('From location'), findsOneWidget);
    expect(find.text('Current location'), findsOneWidget);
    expect(p.seaCans.length, seaCanStartLines);

    await tester.enterText(find.byType(TextField).first, 'MSCU 123456');
    await tester.pump();
    expect(p.seaCans.first.can, 'MSCU 123456');
    expect(find.text('1 sea can entered'), findsOneWidget);

    // From row: rows go 1 to 40; open the first dropdown and pick 5
    final rowBox = tester.widget<DropdownButton<String>>(find.byType(DropdownButton<String>).first);
    expect(rowBox.items!.map((e) => e.value), ['–', for (var i = 1; i <= 40; i++) '$i']);
    await tester.tap(find.byType(DropdownButton<String>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('5').last);
    await tester.pumpAndSettle();
    expect(p.seaCans.first.fromRow, '5');
    // Current level: the fourth dropdown on the line, levels only go to 3
    await tester.tap(find.byType(DropdownButton<String>).at(3));
    await tester.pumpAndSettle();
    expect(find.text('4'), findsNothing);
    await tester.tap(find.text('3').last);
    await tester.pumpAndSettle();
    expect(p.seaCans.first.toLevel, '3');

    // Add rows at the bottom; then empty ones can be removed again
    await tester.scrollUntilVisible(find.text('+  Add 10 rows'), 400, scrollable: find.descendant(of: find.byType(SeaCanPane), matching: find.byType(Scrollable)).first);
    await tester.tap(find.text('+  Add 10 rows'));
    await tester.pumpAndSettle();
    expect(p.seaCans.length, seaCanStartLines + 10);
    await tester.tap(find.text('Remove empty rows'));
    await tester.pumpAndSettle();
    // One line is kept, then topped back up to the starting number of lines
    expect(p.seaCans.first.can, 'MSCU 123456');
    expect(p.seaCans.length, seaCanStartLines);
  });
}
