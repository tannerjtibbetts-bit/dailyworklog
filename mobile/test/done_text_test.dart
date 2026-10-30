// "Text when completed" and "Add a field from a text screenshot".
import 'dart:io';

import 'package:dailyworklog/data/fmt.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/import/assignment_text.dart';
import 'package:dailyworklog/import/import_screen.dart';
import 'package:dailyworklog/screens/field_widgets.dart';
import 'package:dailyworklog/screens/log_screen.dart';
import 'package:dailyworklog/screens/task_screen.dart';
import 'package:dailyworklog/services/sms.dart';
import 'package:dailyworklog/ui/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Project project() => Project()..name = 'B44 Zone 2';

DailyLog dayLog() => DailyLog()..date = '2026-10-05';

List<String> linesOf(String text) => text.split('\n');

void main() {
  group('parseAssignmentTexts', () {
    test('reads the exact text the app sends', () {
      final p = project(), l = dayLog();
      final t = Task()
        ..title = 'Pack up sea can 4'
        ..photosRequired = true;
      final a = parseAssignmentTexts(linesOf(Sms.taskText(p, l, t, 'Tanner', '12265550123')));
      expect(a, hasLength(1));
      expect(a[0].title, 'Pack up sea can 4');
      expect(a[0].logTitle, 'Daily work log 05/10/2026');
      expect(a[0].project, 'B44 Zone 2');
      expect(a[0].from, 'Tanner');
      expect(a[0].phone, '12265550123');
      expect(a[0].photosRequired, isTrue);

      t.photosRequired = false;
      final b = parseAssignmentTexts(linesOf(Sms.taskText(Project(), l, t, 'Tanner', '')));
      expect(b, hasLength(1));
      expect(b[0].title, 'Pack up sea can 4');
      expect(b[0].logTitle, 'Daily work log 05/10/2026');
      expect(b[0].project, '');
      expect(b[0].from, 'Tanner');
      expect(b[0].phone, '');
      expect(b[0].photosRequired, isFalse);
    });

    test('title wrapped over two lines, then a timestamp', () {
      final a = parseAssignmentTexts([
        'Daily work log 05/10/2026 (B44)',
        'Assigned to you: Pack up the sea can and',
        'move it to bay 3.',
        'Photos required.',
        '--------------------',
        'From: Tanner',
        'Call or text me at 1-226-555-0123',
        '12:36 PM',
      ]);
      expect(a, hasLength(1));
      expect(a[0].title, 'Pack up the sea can and move it to bay 3');
      expect(a[0].photosRequired, isTrue);
      expect(a[0].phone, '12265550123');
      expect([a[0].logTitle, a[0].project], ['Daily work log 05/10/2026', 'B44']);
    });

    test('semicolon, OCR dashes, Notify-style From', () {
      final a = parseAssignmentTexts([
        'Today 12:30 PM',
        'Night shift checks (B44 Zone 2)',
        'Assigned to you; Torque checks bay 2',
        '———————',
        'From: Tanner (B44 Zone 2)',
        'Call or text me at 226-555-0123',
      ]);
      expect(a, hasLength(1));
      expect(a[0].title, 'Torque checks bay 2');
      expect(a[0].from, 'Tanner');
      expect(a[0].phone, '2265550123');
      expect(a[0].photosRequired, isFalse);
      expect([a[0].logTitle, a[0].project], ['Night shift checks', 'B44 Zone 2']);
    });

    test('two texts in one screenshot', () {
      final a = parseAssignmentTexts([
        'Daily work log 05/10/2026 (B44)',
        'Assigned to you: Sweep bay 1',
        'Photos required.',
        '--------------------',
        'From: Tanner',
        'Call or text me at 1-226-555-0123',
        '12:36 PM',
        'Daily work log 05/10/2026 (B44)',
        'Assigned to you: Inspect wrenches',
        '--------------------',
        'From: Al',
        '12:40 PM',
      ]);
      expect(a.map((x) => x.title), ['Sweep bay 1', 'Inspect wrenches']);
      expect(a.map((x) => x.photosRequired), [true, false]);
      expect(a.map((x) => x.from), ['Tanner', 'Al']);
      expect(a.map((x) => x.phone), ['12265550123', '']);
      expect(a[1].logTitle, 'Daily work log 05/10/2026');
    });

    test('nothing like an assignment text', () {
      expect(parseAssignmentTexts(['Hey are you coming in today?', 'Yes', '7:02 AM']), isEmpty);
      expect(parseAssignmentTexts([]), isEmpty);
    });
  });

  group('doneText', () {
    test('exact message', () {
      final p = project(), l = dayLog();
      final t = Task()
        ..title = 'Pack up sea can 4'
        ..completedAt = DateTime(2026, 10, 5, 14, 3).millisecondsSinceEpoch;
      expect(
        Sms.doneText(p, l, t, 'Tanner', '12265550123'),
        'Daily work log 05/10/2026 (B44 Zone 2)\n\nCompleted: Pack up sea can 4\n'
        'Done by Tanner, ${Fmt.stamp(t.completedAt)}'
        '\n\n--------------------\nFrom: Tanner\nCall or text me at 1-226-555-0123',
      );
      expect(Fmt.stamp(t.completedAt), '10/05/2026 at 2:03 PM');
      expect(
        Sms.doneText(Project(), l, t, 'Tanner', ''),
        'Daily work log 05/10/2026\n\nCompleted: Pack up sea can 4\nDone by Tanner, 10/05/2026 at 2:03 PM'
        '\n\n--------------------\nFrom: Tanner',
      );
    });

    test('saved, kept by copies, old data defaults', () {
      final t = Task()
        ..title = 'A'
        ..doneTextName = 'Al'
        ..doneTextPhone = '519-555-0101';
      final r = Task.fromJson(t.toJson());
      expect([r.doneTextName, r.doneTextPhone], ['Al', '519-555-0101']);
      expect(t.toJson()['doneTextName'], 'Al');
      expect(t.toJson()['doneTextPhone'], '519-555-0101');
      expect([t.freshCopy().doneTextName, t.freshCopy().doneTextPhone], ['Al', '519-555-0101']);
      expect([t.carryCopy().doneTextName, t.carryCopy().doneTextPhone], ['Al', '519-555-0101']);
      final old = t.toJson()
        ..remove('doneTextName')
        ..remove('doneTextPhone');
      final o = Task.fromJson(old);
      expect([o.doneTextName, o.doneTextPhone], ['', '']);
    });
  });

  group('Screens', () {
    late Directory dir;
    late Project p;
    late DailyLog l;
    final composed = <(List<String>, String)>[];

    setUp(() async {
      dir = Directory.systemTemp.createTempSync('done_text');
      final s = await Store.open(dir);
      s.userName = 'Tanner';
      s.contactPhone = '';
      p = Project()
        ..name = 'B44'
        ..crew.addAll(['Tanner', 'Al', 'Bo']);
      p.phones['Al'] = '5195550101';
      l = dayLog();
      l.tasks.add(Task()..title = 'Sweep bay 1');
      p.logs.add(l);
      s.projects.add(p);
      composed.clear();
      Sms.composeHook = (numbers, body) async => composed.add((numbers, body));
    });

    tearDown(() {
      Sms.composeHook = null;
      try {
        dir.deleteSync(recursive: true);
      } catch (_) {}
    });

    Widget app(Widget home) => MaterialApp(theme: appTheme(), home: home);

    Future<void> openField(WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.6;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(app(TaskScreen(projectId: p.id, logId: l.id, taskId: l.tasks[0].id)));
      await tester.pumpAndSettle();
    }

    Future<void> openDialog(WidgetTester tester) async {
      await tester.ensureVisible(find.text('Text when completed'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Text when completed'));
      await tester.pumpAndSettle();
      expect(
        find.text("When this field is marked complete, the app texts this person that it's done."),
        findsOneWidget,
      );
    }

    testWidgets('field page: pick a crew member, then turn off', (tester) async {
      await openField(tester);
      expect(find.text('Off — tap to choose who gets a text when this is done'), findsOneWidget);
      await openDialog(tester);
      expect(find.text('Turn off'), findsNothing);
      // Only crew with a number are listed.
      expect(find.text('Bo'), findsNothing);
      await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Al')));
      await tester.pump();
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      final t = l.tasks[0];
      expect([t.doneTextName, t.doneTextPhone], ['Al', '5195550101']);
      expect(find.text('Al · 519-555-0101'), findsOneWidget);

      await openDialog(tester);
      await tester.tap(find.text('Turn off'));
      await tester.pumpAndSettle();
      expect([t.doneTextName, t.doneTextPhone], ['', '']);
    });

    testWidgets('field page: someone else, then completing texts them', (tester) async {
      await openField(tester);
      await openDialog(tester);
      await tester.tap(find.text('Someone else'));
      await tester.pump();
      await tester.enterText(find.widgetWithText(TextField, 'Name'), 'Dee');
      await tester.enterText(find.widgetWithText(TextField, 'Phone number'), '555');
      await tester.tap(find.text('Save'));
      await tester.pump();
      expect(find.text('Enter a phone number'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextField, 'Phone number'), '226 555 0123');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      final t = l.tasks[0];
      expect([t.doneTextName, t.doneTextPhone], ['Dee', '226 555 0123']);
      expect(find.text('Dee · 226-555-0123'), findsOneWidget);

      await tester.ensureVisible(find.byType(CheckCircle));
      await tester.tap(find.byType(CheckCircle));
      await tester.pumpAndSettle();
      expect(t.done, isTrue);
      expect(composed, hasLength(1));
      expect(composed[0].$1, ['226 555 0123']);
      expect(composed[0].$2, Sms.doneText(p, l, t, 'Tanner', ''));
      expect(composed[0].$2, contains('Completed: Sweep bay 1\nDone by Tanner, '));

      // Unticking doesn't text.
      await tester.tap(find.byType(CheckCircle));
      await tester.pumpAndSettle();
      expect(t.done, isFalse);
      expect(composed, hasLength(1));
    });

    testWidgets('checklist tick texts too; no one set means no text', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.6;
      addTearDown(tester.view.reset);
      l.tasks.add(Task()..title = 'Quiet one');
      l.tasks[0]
        ..doneTextName = 'Al'
        ..doneTextPhone = '5195550101';
      await tester.pumpWidget(app(LogScreen(projectId: p.id, logId: l.id)));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(CheckCircle).at(1)); // Quiet one
      await tester.pumpAndSettle();
      expect(l.tasks[1].done, isTrue);
      expect(composed, isEmpty);
      await tester.tap(find.byType(CheckCircle).at(0));
      await tester.pumpAndSettle();
      expect(l.tasks[0].done, isTrue);
      expect(composed.single.$1, ['5195550101']);
      expect(composed.single.$2, startsWith('Daily work log 05/10/2026 (B44)\n\nCompleted: Sweep bay 1\n'));
    });

    testWidgets('Add from text adds the ticked fields with who to text', (tester) async {
      final texts = [
        const AssignmentText(title: 'sweep bay 1', from: 'Tanner', phone: '12265550123', photosRequired: true),
        const AssignmentText(title: 'Inspect wrenches', from: 'Al'),
        const AssignmentText(title: 'Not this one', from: 'Bo', phone: '5195550199'),
        const AssignmentText(title: 'Grease pins', from: 'Cy', phone: '5195550188'),
      ];
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.6;
      addTearDown(tester.view.reset);
      int? popped;
      await tester.pumpWidget(
        app(
          Builder(
            builder: (c) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  popped = await Navigator.of(c).push<int>(
                    MaterialPageRoute(
                      builder: (_) =>
                          ImportScreen(project: p, log: l, source: ImportSource.textScreenshot, texts: texts),
                    ),
                  );
                },
                child: const Text('go'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('go'));
      await tester.pumpAndSettle();
      expect(find.text('Add from text'), findsOneWidget);
      expect(find.text('Add 4 fields'), findsOneWidget);
      expect(find.text('Add this field  ·  Photos required'), findsOneWidget);
      expect(find.text('From Tanner · 1-226-555-0123'), findsOneWidget);
      expect(find.text('From Al'), findsOneWidget);
      expect(find.text("Text Tanner when it's done"), findsOneWidget);
      expect(find.text("Text Al when it's done"), findsNothing); // no number

      await tester.tap(find.text('Add this field').at(1)); // untick Bo's
      await tester.pump();
      await tester.ensureVisible(find.text("Text Cy when it's done"));
      await tester.pumpAndSettle();
      await tester.tap(find.text("Text Cy when it's done"));
      await tester.pump();
      expect(find.text('Add 3 fields'), findsOneWidget);
      await tester.tap(find.text('Add 3 fields'));
      await tester.pumpAndSettle();

      expect(popped, 3);
      final added = l.tasks.sublist(1);
      expect(added.map((t) => t.title), ['Sweep bay 1', 'Inspect wrenches', 'Grease pins']);
      expect(added.map((t) => t.photosRequired), [true, false, false]);
      expect(added.map((t) => t.doneTextName), ['Tanner', '', '']);
      expect(added.map((t) => t.doneTextPhone), ['12265550123', '', '']);
      expect(added.every((t) => t.createdBy == 'Tanner' && t.notes.isEmpty), isTrue);
    });
  });
}
