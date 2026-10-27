import 'dart:io';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Checklists always shows; hidden tabs are left out in order', () {
    expect(visibleTabs({}), ProjectTab.values);
    expect(visibleTabs({'links', 'seacans'}),
        [ProjectTab.checklists, ProjectTab.notify, ProjectTab.overtime, ProjectTab.attendance, ProjectTab.notes]);
    expect(tabIds[ProjectTab.attendance], 'attendance');
    // All seven: four on top, three below. Six or fewer: rows of three.
    expect(tabRows(ProjectTab.values).map((r) => r.length), [4, 3]);
    expect(tabRows(visibleTabs({'links'})).map((r) => r.length), [3, 3]);
    expect(visibleTabs(tabIds.values.toSet()), [ProjectTab.checklists]);
  });

  testWidgets('hiding tabs with Show or hide tabs', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final dir = Directory.systemTemp.createTempSync('tabs');
    late Store store;
    await tester.runAsync(() async => store = await Store.open(dir));
    final p = Project()..name = 'B44';
    store.projects.add(p);
    await tester.pumpWidget(MaterialApp(home: ProjectScreen(projectId: p.id, initialTab: ProjectTab.seaCans)));
    await tester.pump();
    expect(find.text('Sea Can Movements'), findsOneWidget);

    await tester.tap(find.byTooltip('Project options'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Show or hide tabs'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(CheckboxListTile, 'Links'));
    await tester.tap(find.widgetWithText(CheckboxListTile, 'Sea Can Movements'));
    await tester.pump();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(store.hiddenTabs, {'links', 'seacans'});
    expect(find.text('Links'), findsNothing);
    expect(find.text('Sea Can Movements'), findsNothing);
    // The Sea Can tab was open, so it went back to Checklists.
    expect(find.text('Search checklists'), findsOneWidget);
    expect(find.text('Notify'), findsOneWidget);
  });
}
