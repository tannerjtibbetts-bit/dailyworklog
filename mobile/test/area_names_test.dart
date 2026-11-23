import 'dart:io';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/area_names.dart';
import 'package:dailyworklog/ui/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test("a project's own area names: on cards, in report headings, saved", () {
    final p = Project()..areaNames = {Task.south: 'Garage 1', Task.north: 'Garage 2'};
    expect(p.areaName(Task.south), 'Garage 1');
    expect(p.areaName(Task.north), 'Garage 2');
    expect(p.areaName(Task.unzoned), 'Unzoned');
    expect(Task.sideLabel(Task.south), 'South');
    final tasks = [Task()..side = Task.north, Task()..side = Task.south, Task()..side = Task.unzoned, Task()];
    expect(Task.reportGroups(tasks, p.areaNames).map((g) => g.key), ['Garage 1', 'Garage 2', 'Unzoned', 'Other']);
    final back = Project.fromJson(p.toJson());
    expect(back.areaNames, {Task.south: 'Garage 1', Task.north: 'Garage 2'});
    final l = DailyLog()..date = '2026-11-23';
    expect(p.forDay(l).areaNames['south'], 'Garage 1');
    // Junk in the file is ignored.
    expect(Project.fromJson({'areaNames': {'south': '  ', 'east': 'X'}}).areaNames, isEmpty);
  });

  testWidgets('Rename areas: type names, Save; Use usual names clears them', (tester) async {
    final dir = Directory.systemTemp.createTempSync('areas');
    await tester.runAsync(() => Store.open(dir));
    final p = Project();
    await tester.pumpWidget(MaterialApp(
      theme: appTheme(),
      home: Scaffold(body: Builder(builder: (c) => TextButton(onPressed: () => renameAreas(c, p), child: const Text('go')))),
    ));
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
    expect(find.text('Rename areas'), findsOneWidget);
    await tester.enterText(find.widgetWithText(TextField, 'South area'), 'Location 1');
    await tester.enterText(find.widgetWithText(TextField, 'Unzoned area'), 'Yard');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(p.areaNames, {Task.south: 'Location 1', Task.unzoned: 'Yard'});

    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Use usual names'));
    await tester.pump();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(p.areaNames, isEmpty);
  });
}
