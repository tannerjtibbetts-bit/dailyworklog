// The user manual: every link and picture works, and search finds the right topics.
import 'dart:io';

import 'package:dailyworklog/support/manual.dart';
import 'package:dailyworklog/support/support_screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final manual = Manual.parse(File('assets/manual/manual.json').readAsStringSync());

  test('every topic is complete, links go somewhere and pictures exist', () {
    expect(manual.sections.length, greaterThan(30));
    final ids = <String>{};
    for (final s in manual.sections) {
      expect(ids.add(s.id), isTrue, reason: 'duplicate id ${s.id}');
      expect(s.title, isNotEmpty);
      expect(s.blocks, isNotEmpty, reason: s.id);
    }
    for (final s in manual.sections) {
      for (final b in s.blocks) {
        if (b.type == 'see') expect(manual.byId(b.id), isNotNull, reason: '${s.id} links to ${b.id}');
        if (b.type == 'image') expect(File('assets/manual/${b.file}').existsSync(), isTrue, reason: b.file);
      }
    }
  });

  test('search finds the right topics', () {
    expect(manual.search('sea can').first.section.id, 'sea-can-movements');
    expect(manual.search('PIN').map((h) => h.section.title).join(' | '), contains('PIN'));
    expect(manual.search('overtime excel').first.section.title.toLowerCase(), contains('overtime'));
    expect(manual.search('zzzz nothing'), isEmpty);
    expect(manual.search('   '), isEmpty);
    // Every word has to match
    expect(manual.search('sea can pizza'), isEmpty);
  });

  testWidgets('manual: contents, search, open a topic', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MaterialApp(home: ManualScreen(manual: manual)));
    await tester.pump();
    expect(find.text('GETTING STARTED'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'sea can');
    await tester.pump();
    expect(find.textContaining('topic'), findsWidgets);
    await tester.tap(find.byType(InkWell).at(1)); // first result (0 is the clear button)
    await tester.pumpAndSettle();
    expect(find.text('Sea Can Movements'), findsWidgets);
    await tester.scrollUntilVisible(find.text('Next'), 500, scrollable: find.byType(Scrollable).last);
    expect(find.text('Next'), findsOneWidget);
  });
}
