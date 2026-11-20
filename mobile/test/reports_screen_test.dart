import 'dart:io';

import 'package:dailyworklog/data/forms.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/assign_sheet.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:dailyworklog/screens/reports_screen.dart';
import 'package:dailyworklog/ui/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'forms_test.dart' as ft;

Widget app(Widget home) => MaterialApp(theme: appTheme(), home: home);

void main() {
  late Store store;
  late Project p;

  setUpAll(() async {
    store = await Store.open(await Directory.systemTemp.createTemp('dwl_reports_'));
  });

  setUp(() {
    store.projects.clear();
    store.reportForms.clear();
    store.filledReports.clear();
    store.documents.clear();
    store.userName = 'Tanner Tibbetts';
    p = Project()
      ..name = 'Warehouse Expansion'
      ..crew = ['Tyson Zimmer', 'Dan Walker']
      ..levels = {'Tyson Zimmer': 'Apprentice'}
      ..employeeNumbers = {'Tyson Zimmer': '30027219'};
    store.projects.add(p);
    final r = FormReader.read(ft.sample);
    store.reportForms.add(ReportForm()
      ..id = 'f1'
      ..name = 'Apprentice review'
      ..questions = r.questions
      ..signatures = r.signatures);
  });

  testWidgets('fill in an apprentice review: pick the apprentice, answer, see the report', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(app(ReportsScreen(projectId: p.id, group: ReportForm.apprentice)));
    await tester.pump();
    await tester.tap(find.text('Apprentice review'));
    await tester.pumpAndSettle();
    expect(find.text('Who is this report for?'), findsOneWidget);
    // Only apprentices are offered.
    expect(find.text('Tyson Zimmer'), findsOneWidget);
    await tester.tap(find.text('Start'));
    await tester.pumpAndSettle();
    final r = store.filledReports.single;
    expect(r.person, 'Tyson Zimmer');
    expect(r.answer('q2'), 'Tyson Zimmer');
    expect(r.answer('q3'), '30027219');
    // Asked one at a time, starting with the first empty one.
    expect(find.text('Employer'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'Aecon');
    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();
    expect(find.text('Term'), findsOneWidget);
    await tester.tap(find.text('Skip'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Stop'));
    await tester.pumpAndSettle();
    expect(r.answer('q4'), 'Aecon');
    // The report screen.
    expect(find.text('Export PDF'), findsOneWidget);
    await tester.scrollUntilVisible(find.textContaining('Ask the missing questions'), 400,
        scrollable: find.byType(Scrollable).first);
    expect(find.textContaining('Ask the missing questions (15)'), findsOneWidget);
  });

  testWidgets('disciplinary action offers the whole crew; no apprentices explains why', (tester) async {
    store.reportForms.first.group = ReportForm.discipline;
    await tester.pumpWidget(app(ReportsScreen(projectId: p.id, group: ReportForm.discipline)));
    await tester.pump();
    await tester.tap(find.text('Apprentice review'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    expect(find.text('Dan Walker'), findsWidgets);
    await tester.tap(find.text('Dan Walker').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    store.reportForms.first.group = ReportForm.apprentice;
    p.levels.clear();
    await tester.pumpWidget(app(ReportsScreen(projectId: p.id, group: ReportForm.apprentice)));
    await tester.pump();
    await tester.tap(find.text('Apprentice review'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Nobody on this project'), findsOneWidget);
  });

  testWidgets('Links tab: one Documents button opens the documents', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    store.documents.add(Doc()
      ..title = 'Lifting plan'
      ..file = 'doc-x.pdf'
      ..kind = Doc.pdf);
    await tester.pumpWidget(app(ProjectScreen(projectId: p.id, initialTab: ProjectTab.links)));
    await tester.pump();
    expect(find.text('Apprentice reports'), findsNothing);
    expect(find.text('Disciplinary action'), findsNothing);
    expect(find.text('Lifting plan'), findsNothing);
    await tester.tap(find.text('Documents'));
    await tester.pumpAndSettle();
    expect(find.text('Lifting plan'), findsOneWidget);
    expect(find.text('Search documents'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'zzz');
    await tester.pump();
    expect(find.text('Lifting plan'), findsNothing);
    await tester.enterText(find.byType(TextField), 'lift');
    await tester.pump();
    expect(find.text('Lifting plan'), findsOneWidget);
  });

  testWidgets('Paste crew numbers in Manage Crew', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(app(Builder(
      builder: (c) => TextButton(onPressed: () => showManageCrew(c, p), child: const Text('open')),
    )));
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Paste crew numbers'), 200, scrollable: find.byType(Scrollable).last);
    await tester.tap(find.text('Paste crew numbers'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last,
        'Name | Employee # | BP # | DISN #\nDan Walker | 30041142 | 618780 | 539049\nHope Ostachuck | 30059656 | 624540 | 573633');
    await tester.tap(find.text('Add numbers'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Numbers added for 1 person.'), findsOneWidget);
    expect(find.textContaining("Not on this project's crew: Hope Ostachuck."), findsOneWidget);
    await tester.tap(find.text('Add them to the crew'));
    await tester.pumpAndSettle();
    expect(p.bpNumbers['Dan Walker'], '618780');
    expect(p.crew, contains('Hope Ostachuck'));
    expect(find.text('Employee # 30041142 · BP # 618780 · DISN # 539049'), findsOneWidget);
  });
}
