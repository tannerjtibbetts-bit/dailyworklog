// The review screen: ticks, the Add button, and fields landing in the log.
import 'dart:io';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/import/import_review.dart';
import 'package:dailyworklog/import/import_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Add puts the ticked rows in the log and pops with the count', (tester) async {
    final dir = Directory.systemTemp.createTempSync('import_screen');
    await tester.runAsync(() => Store.open(dir));
    Store.instance.userName = 'Pat Example';
    final project = Project()..name = 'B44';
    final log = DailyLog();
    project.logs.add(log);
    Store.instance.projects.add(project);
    final review = ImportReview.rows([
      ['Task', 'Where'],
      ['Pack up sea can', 'Zone 2'],
      ['inspect wrenches', 'Crib'],
    ], 'the spreadsheet', header: true);

    int? popped;
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (c) => Scaffold(
          body: TextButton(
            onPressed: () async {
              popped = await Navigator.of(c).push<int>(MaterialPageRoute(
                  builder: (_) =>
                      ImportScreen(project: project, log: log, source: ImportSource.file, review: review)));
            },
            child: const Text('go'),
          ),
        ),
      ),
    ));
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();

    expect(find.text('Add fields'), findsOneWidget);
    expect(find.text('Add 2 fields'), findsOneWidget);
    expect(find.text('Which column has the tasks?'), findsOneWidget);

    await tester.tap(find.text('Untick all'));
    await tester.pump();
    expect(find.text('Tick the fields to add'), findsOneWidget);
    await tester.tap(find.text('Tick all'));
    await tester.pump();
    expect(find.text('Add 3 fields'), findsOneWidget);
    await tester.tap(find.byType(Checkbox).at(1)); // the heading row
    await tester.pump();

    await tester.tap(find.text('Add 2 fields'));
    await tester.pumpAndSettle();
    expect(popped, 2);
    expect(log.tasks.map((t) => t.title), ['Pack up sea can', 'Inspect wrenches']);
    expect(log.tasks.map((t) => t.notes), ['Zone 2', 'Crib']);
    expect(log.tasks.every((t) => t.createdBy == 'Pat Example'), isTrue);
  }, timeout: const Timeout(Duration(seconds: 60)));
}
