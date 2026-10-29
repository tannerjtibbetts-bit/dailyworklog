import 'dart:io';

import 'package:dailyworklog/data/fmt.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/export/export.dart';
import 'package:dailyworklog/screens/attendance_pane.dart';
import 'package:dailyworklog/screens/note_screen.dart';
import 'package:dailyworklog/screens/notify_screen.dart';
import 'package:dailyworklog/screens/overtime_pane.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:dailyworklog/screens/projects_screen.dart';
import 'package:dailyworklog/ui/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Project sample() {
  final p = Project()
    ..name = 'B44 Zone 2 Tool Readiness'
    ..location = 'Kincardine, ON'
    ..crew = ['Al Smith', 'Bo Jones'];
  final l = DailyLog()..date = Fmt.todayIso();
  l.tasks.add(Task()
    ..title = 'Torque checks'
    ..done = true);
  l.tasks.add(Task()..title = 'Pump 3 seal');
  p.logs.add(l);
  p.notes.add(Note()
    ..text = 'Pump 3 leaking'
    ..by = 'Tanner');
  p.notes.add(Note()
    ..text = 'Harnesses in trailer'
    ..by = 'Tanner');
  p.notices.add(Notice()
    ..to = ['Al Smith']
    ..note = 'Meet at gate 2'
    ..sentAt = nowMs());
  return p;
}

Widget app(Widget home) => MaterialApp(theme: appTheme(), home: home);

void main() {
  late Directory dir;

  setUpAll(() async {
    dir = await Directory.systemTemp.createTemp('dwl_projects_');
    await Store.open(dir);
  });

  tearDownAll(() async {
    // No flush: saves started inside widget tests' fake clock never finish.
    try {
      await dir.delete(recursive: true);
    } catch (_) {}
  });

  setUp(() {
    final s = Store.instance;
    s.projects.clear();
    s.links.clear();
    s.userName = 'Tanner';
    s.contactPhone = '2265550123';
  });

  group('NoteTest', () {
    test('note roundtrip, report entry, forget, old data', () {
      final p = Project()..name = 'X';
      final l = DailyLog()..date = Fmt.todayIso();
      p.logs.add(l);
      final n = Note()
        ..text = 'Pump 3 leaking'
        ..by = 'Tanner';
      n.photos.add(Photo(file: 'a.jpg'));
      p.notes.add(n);
      final e = p.record(null, Fmt.todayIso(), Extra.note, n.id, n.by, n.text, n.photos, 'Tanner', 1)!;
      e.include = true;
      n.text = 'Pump 3 leaking, replaced seal';
      p.syncNote(n);
      final q = Project.fromJson(p.toJson());
      final m = q.findNote(n.id);
      expect(m != null && m.text == 'Pump 3 leaking, replaced seal' && m.photos.length == 1 && m.by == 'Tanner', true);
      final x = q.logs[0].includedExtras()[0];
      expect(x.label(), 'Note by Tanner');
      expect(x.detail, m!.text);
      expect(x.photos.length, 1);
      q.forgetExtras(n.id);
      expect(q.logs[0].extras, isEmpty);
      final old = p.toJson()..remove('notes');
      expect(Project.fromJson(old).notes, isEmpty);
    });

    test('clear all notes takes them off reports', () {
      final p = sample();
      for (final n in p.notes) {
        p.record(null, Fmt.todayIso(), Extra.note, n.id, n.by, n.text, n.photos, 'Tanner', 1)!.include = true;
      }
      expect(p.logs[0].extras.length, 2);
      clearAllNotes(Store.instance, p);
      expect(p.notes, isEmpty);
      expect(p.logs[0].extras, isEmpty);
    });
  });

  group('OtTest', () {
    test('40 rows + notes saved, remove empty rows, clear answers keeps notes', () {
      final p = Project();
      final ot = p.overtime..notes = 'Need 6 for Saturday\nBring harnesses';
      for (var i = 0; i < 40; i++) {
        ot.rows.add(OtRow());
      }
      ot.rows[0].name = 'Al';
      ot.rows[3].sat = 'Yes';
      ot.rows[7].hours = '8';
      final q = Project.fromJson(p.toJson());
      expect(q.overtime.rows.length, 40);
      expect(q.overtime.notes, ot.notes);
      q.overtime.removeEmptyRows();
      expect(q.overtime.rows.length, 3);
      q.overtime.startWeekend('2026-10-09');
      expect(q.overtime.notes, ot.notes);
      expect(q.overtime.rows.every((r) => r.sat.isEmpty && r.hours.isEmpty), true);
      expect(q.overtime.rows[0].name, 'Al');
    });

    test('hours box accepts 1 to 10', () {
      expect(otHours(''), '');
      expect(otHours(' 8 '), '8');
      expect(otHours('10'), '10');
      expect(otHours('0'), isNull);
      expect(otHours('11'), isNull);
      expect(otHours('07'), '7');
    });

    test('one row per crew member, names offered', () {
      final p = Project()..crew = ['Al', 'Bo', 'Cy'];
      prepareOvertime(p);
      expect(p.overtime.rows.length, 3);
      expect(p.overtime.weekOf, isNotEmpty);
      p.overtime.rows[0].name = 'Old Timer';
      expect(overtimeNames(p), ['Select…', 'Al', 'Bo', 'Cy', 'Old Timer']);
      final empty = Project();
      prepareOvertime(empty);
      expect(empty.overtime.rows.length, 1);
      // A removed row doesn't come back.
      p.overtime.rows.removeAt(1);
      prepareOvertime(p);
      expect(p.overtime.rows.length, 2);
    });

    test('yes totals count only named rows', () {
      final ot = Overtime();
      ot.rows.add(OtRow()
        ..name = 'Al'
        ..fri = 'Yes');
      ot.rows.add(OtRow()..fri = 'Yes');
      ot.rows.add(OtRow()
        ..name = 'Bo'
        ..fri = 'No'
        ..sat = 'Yes');
      expect(ot.yesCount(0), 1);
      expect(ot.yesCount(1), 1);
      expect(ot.yesCount(2), 0);
    });
  });

  group('AttendanceTest', () {
    test('rows start with the crew names, today\'s date, and old files load', () {
      final p = Project()..crew = ['Al', 'Bo'];
      prepareAttendance(p);
      expect(p.attendance.rows.map((r) => r.name), ['Al', 'Bo']);
      expect(p.attendance.date, Fmt.todayIso());
      p.attendance.rows[0].name = 'Old Timer';
      expect(attendanceNames(p), ['Select…', 'Al', 'Bo', 'Old Timer']);
      final empty = Project();
      prepareAttendance(empty);
      expect(empty.attendance.rows.single.name, '');
      // Rows aren't topped up again once there are some.
      p.attendance.rows.removeAt(1);
      prepareAttendance(p);
      expect(p.attendance.rows.length, 1);
      // A file from before Attendance existed.
      expect(Project.fromJson({'name': 'x'}).attendance.rows, isEmpty);
    });

    test('summary, save and load, clear answers, rename', () {
      final p = Project()..crew = ['Al', 'Bo', 'Cy', 'Di'];
      prepareAttendance(p);
      final a = p.attendance
        ..include = true
        ..notes = 'Cy at medical';
      a.rows[0].status = 'Yes';
      a.rows[1].status = 'Yes';
      a.rows[2].status = 'AWOL';
      a.rows[3].status = 'Step up';
      a.rows[3].comment = 'Covering for Lee';
      a.rows.add(AttRow()..status = 'Yes'); // no name: not counted
      a.rows.add(AttRow());
      a.rows.add(AttRow()..comment = 'Who was this?'); // only a comment: kept
      expect(a.summary(), 'Yes 2 · AWOL 1 · Step up 1');
      a.rows[1].status = 'Late';
      expect(a.summary(), 'Yes 1 · AWOL 1 · Step up 1 · Late 1');
      a.rows[1].status = 'Yes';
      // Files from before comments existed load with none.
      expect(AttRow.fromJson({'name': 'x', 'status': 'Yes'}).comment, '');
      expect(Attendance().summary(), '');
      final q = Project.fromJson(Map<String, dynamic>.from(p.toJson()));
      expect(q.attendance.rows.length, 7);
      expect(q.attendance.rows[3].comment, 'Covering for Lee');
      expect(q.attendance.include, true);
      expect(q.attendance.notes, 'Cy at medical');
      q.attendance.removeEmptyRows();
      expect(q.attendance.rows.length, 6);
      q.renamePerson('Cy', 'Cyrus');
      expect(q.attendance.rows[2].name, 'Cyrus');
      q.attendance.startDay('2026-10-28');
      expect(q.attendance.date, '2026-10-28');
      expect(q.attendance.rows.every((r) => r.status.isEmpty && r.comment.isEmpty), true);
      expect(q.attendance.rows[0].name, 'Al');
      expect(q.attendance.notes, 'Cy at medical');
      expect(Attendance.statuses,
          ['Yes', 'No', 'AWOL', 'Vacation', 'Off', 'Training', 'D72', 'Step up', 'Late', 'Left early']);
      expect(Fmt.longDay('2026-10-27'), 'Tue Oct 27, 2026');
    });
  });

  group('project screen logic', () {
    test('links: normalize and host', () {
      expect(normalizeUrl(' example.com/a b '), 'https://example.com/ab');
      expect(normalizeUrl('http://x.org'), 'http://x.org');
      expect(normalizeUrl('mailto:a@b.com'), 'mailto:a@b.com');
      expect(hostOf('https://www.brucepower.com/x'), 'brucepower.com');
      expect(hostOf('https://'), '');
    });

    test('email list keeps valid addresses once', () {
      expect(Export.parseEmails('a@b.com, bad; c@d.ca a@b.com'), ['a@b.com', 'c@d.ca']);
      expect(Export.parseEmails('nope'), isEmpty);
    });

    test('new checklist: carry over unfinished keeps notes, copy all is fresh, crew copied', () {
      final p = Project();
      final old = DailyLog()
        ..date = '2026-10-01'
        ..crew = ['Al'];
      old.tasks.add(Task()
        ..title = 'Done one'
        ..done = true);
      old.tasks.add(Task()
        ..title = 'Open one'
        ..notes = 'bay 3'
        ..nextShift = true);
      p.logs.add(old);
      final carry = createLog(p, '2026-10-02', old.tasks, true, null, 'Tanner');
      expect(carry.tasks.map((t) => t.title), ['Open one']);
      expect(carry.tasks[0].notes, 'bay 3');
      expect(carry.tasks[0].createdBy, 'Tanner');
      expect(carry.crew, ['Al']);
      final copy = createLog(p, '2026-10-03', old.tasks, false, null, 'Tanner');
      expect(copy.tasks.length, 2);
      expect(copy.tasks.every((t) => !t.done && t.notes.isEmpty), true);
      final marked = carryMarked(p, '2026-10-04', old.tasks.where((t) => t.nextShift).toList(), 'Tanner');
      expect(marked.tasks.single.notes, 'bay 3');
      final tmpl = Template()
        ..name = 'Outage'
        ..tasks = [Task()..title = 'A'];
      final fromT = createLog(p, Fmt.todayIso(), tmpl.tasks, false, tmpl, 'Tanner');
      expect(fromT.extras.single.label(), 'Template used: Outage');
      expect(fromT.extras.single.detail, '1 field');
      expect(sortedLogs(p).first, fromT);
    });

    test('notice is recorded on the project and today\'s checklist, not ticked', () {
      final p = sample();
      final n = recordNotice(p, ['Al Smith', 'Bo Jones'], 'Bring harnesses', ['x.jpg'], 'Tanner', 5);
      expect(p.notices.first, n);
      expect(n.photos.single.file, 'x.jpg');
      final e = p.logs[0].findExtra(n.id)!;
      expect(e.label(), 'Note sent to Al Smith, Bo Jones');
      expect(e.include, false);
      expect(e.at, 5);
    });

    test('phone format', () {
      expect(formatPhone('12265550123'), '1-226-555-0123');
      expect(formatPhone('(226) 555-0123'), '226-555-0123');
      expect(formatPhone(' 555 '), '555');
    });
  });

  group('widgets', () {
    testWidgets('projects list shows projects and the profile line', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      await tester.pumpWidget(app(const ProjectsScreen()));
      await tester.pump();
      expect(find.text('Projects'), findsOneWidget);
      expect(find.text('B44 Zone 2 Tool Readiness'), findsOneWidget);
      expect(find.text('Signed in as Tanner  ·  226-555-0123'), findsOneWidget);
      expect(find.textContaining('1 daily log, latest'), findsOneWidget);

      await tester.tap(find.byTooltip('Your name'));
      await tester.pumpAndSettle();
      expect(find.text('Your profile'), findsOneWidget);
      expect(find.text('My task colours on the zone sheet'), findsOneWidget);
      expect(find.text('More…'), findsOneWidget);
      await tester.tap(find.text('Orange (maintenance)'));
      await tester.pump();
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(Store.instance.myColours, containsAll(['blue', 'orange']));
    });

    testWidgets('empty projects list and new project', (tester) async {
      await tester.pumpWidget(app(const ProjectsScreen()));
      await tester.pump();
      expect(find.text('No projects yet.\nTap + to create your first one.'), findsOneWidget);
      await tester.tap(find.byIcon(Icons.add));
      await tester.pumpAndSettle();
      expect(find.text('New project'), findsWidgets);
      await tester.tap(find.text('Create'));
      await tester.pump();
      expect(find.text('Enter a project name'), findsOneWidget);
      await tester.enterText(find.byType(TextField).first, 'B10 Outage');
      await tester.tap(find.text('Create'));
      await tester.pumpAndSettle();
      expect(Store.instance.projects.single.name, 'B10 Outage');
      // Opens the new project.
      expect(find.text('Checklists'), findsOneWidget);
      expect(find.text("No daily logs yet.\nTap + to start today's."), findsOneWidget);
    });

    testWidgets('project screen: every tab renders', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      Store.instance.links.add(Link()
        ..title = 'Tool portal'
        ..url = 'https://www.example.com/tools');
      await tester.binding.setSurfaceSize(const Size(420, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(app(ProjectScreen(projectId: p.id)));
      await tester.pump();

      expect(find.text('B44 Zone 2 Tool Readiness'), findsOneWidget);
      for (final t in ['Checklists', 'Notify', 'Links', 'Overtime', 'Attendance', 'Notes', 'Sea Can Movements']) {
        expect(find.text(t), findsOneWidget);
      }
      expect(find.text('Daily work log ${Fmt.logDate(Fmt.todayIso())}'), findsOneWidget);
      expect(find.text('1/2 fields completed'), findsOneWidget);
      expect(find.text('Search all days: fields, notes, sea cans'), findsOneWidget);

      await tester.tap(find.text('Notify'));
      await tester.pump();
      expect(find.text('To Al Smith'), findsOneWidget);
      expect(find.text('Meet at gate 2'), findsOneWidget);
      expect(find.text("Include in today's report"), findsOneWidget);

      await tester.tap(find.text('Links'));
      await tester.pump();
      expect(find.text('Tool portal'), findsOneWidget);
      expect(find.text('example.com/tools'), findsOneWidget);
      // Ticking puts the link on today's report.
      await tester.tap(find.text("Include in today's report"));
      await tester.pump();
      final e = p.logs[0].findExtra(Store.instance.links[0].id)!;
      expect(e.include, true);
      expect(e.label(), 'Link used: Tool portal');

      await tester.tap(find.text('Overtime'));
      await tester.pump();
      expect(find.text('Weekend overtime'), findsOneWidget);
      expect(find.text('Export'), findsOneWidget);
      expect(find.text('Yes total'), findsOneWidget);
      expect(p.overtime.rows.length, 2);
      expect(find.text('Search all days: fields, notes, sea cans'), findsNothing);
      await tester.ensureVisible(find.text('+  Add a row'));
      await tester.tap(find.text('+  Add a row'));
      await tester.pumpAndSettle();
      expect(p.overtime.rows.length, 3);
      await tester.ensureVisible(find.text('Remove empty rows'));
      await tester.tap(find.text('Remove empty rows'));
      await tester.pumpAndSettle();
      // Empty rows go, then the sheet is redrawn with one row per crew member again.
      expect(p.overtime.rows.length, 2);

      await tester.tap(find.text('Notes').first);
      await tester.pumpAndSettle();
      expect(find.text('Pump 3 leaking'), findsOneWidget);
      expect(find.text('Harnesses in trailer'), findsOneWidget);
      expect(find.text('Clear all notes'), findsOneWidget);
      expect(find.text('By Tanner'), findsNWidgets(2));
    });

    testWidgets('attendance tab: names, Here? answers, remove one row', (tester) async {
      final p = sample()..crew = ['Al Smith', 'Bo Jones', 'Cy Lee'];
      Store.instance.projects.add(p);
      await tester.binding.setSurfaceSize(const Size(420, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(app(ProjectScreen(projectId: p.id, initialTab: ProjectTab.attendance)));
      await tester.pump();
      expect(find.text('Here?'), findsOneWidget);
      expect(find.text('Date: ${Fmt.longDay(Fmt.todayIso())}'), findsOneWidget);
      expect(find.text('Nobody marked yet'), findsOneWidget);
      expect(find.text('Bo Jones'), findsOneWidget);

      // Pick "Vacation" for Bo.
      await tester.tap(find.text('–').at(1));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Vacation').last);
      await tester.pumpAndSettle();
      expect(p.attendance.rows[1].status, 'Vacation');
      expect(find.text('Vacation 1'), findsOneWidget);

      // A comment for Al.
      await tester.enterText(find.widgetWithText(TextField, 'Comment').first, 'Left at 1pm');
      await tester.pump();
      expect(p.attendance.rows[0].comment, 'Left at 1pm');

      // Remove Bo's row: asks first, the crew list stays. On a narrow phone the × is off to
      // the right, so slide the table over first.
      await tester.ensureVisible(find.byTooltip('Remove row').at(1));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Remove row').at(1));
      await tester.pumpAndSettle();
      expect(find.text("Bo Jones comes off this sheet. Your crew list doesn't change."), findsOneWidget);
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(p.attendance.rows.map((r) => r.name), ['Al Smith', 'Cy Lee']);
      expect(p.crew, contains('Bo Jones'));

      // An empty row goes without asking.
      await tester.tap(find.text('+  Add a row'));
      await tester.pumpAndSettle();
      expect(p.attendance.rows.length, 3);
      await tester.ensureVisible(find.byTooltip('Remove row').last);
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Remove row').last);
      await tester.pumpAndSettle();
      expect(p.attendance.rows.length, 2);
    });

    testWidgets('overtime tab: remove one row from the middle', (tester) async {
      final p = sample()..crew = ['Al Smith', 'Bo Jones', 'Cy Lee'];
      prepareOvertime(p);
      for (var i = 0; i < 3; i++) {
        p.overtime.rows[i].name = p.crew[i];
      }
      Store.instance.projects.add(p);
      await tester.binding.setSurfaceSize(const Size(420, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(app(ProjectScreen(projectId: p.id, initialTab: ProjectTab.overtime)));
      await tester.pump();
      await tester.tap(find.byTooltip('Remove row').at(1));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(p.overtime.rows.map((r) => r.name), ['Al Smith', 'Cy Lee']);
    });

    testWidgets('Clear all notes removes notes after confirming', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      await tester.pumpWidget(app(ProjectScreen(projectId: p.id, initialTab: ProjectTab.notes)));
      await tester.pump();
      await tester.tap(find.text('Clear all notes'));
      await tester.pumpAndSettle();
      expect(find.text('Delete all 2 notes?'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(p.notes.length, 2);
      await tester.tap(find.text('Clear all notes'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete all'));
      await tester.pumpAndSettle();
      expect(p.notes, isEmpty);
      expect(find.text('Clear all notes'), findsNothing);
      expect(find.textContaining('No notes yet.'), findsOneWidget);
    });

    testWidgets('red trash on a note deletes just that note', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      await tester.pumpWidget(app(ProjectScreen(projectId: p.id, initialTab: ProjectTab.notes)));
      await tester.pump();
      await tester.tap(find.byTooltip('Delete note').first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();
      expect(p.notes.map((n) => n.text), ['Harnesses in trailer']);
    });

    testWidgets('search filters checklists', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      await tester.pumpWidget(app(ProjectScreen(projectId: p.id)));
      await tester.pump();
      await tester.enterText(find.byType(TextField), 'crane');
      await tester.pump();
      expect(find.text('Nothing matches “crane”'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'pump 3');
      await tester.pump();
      expect(find.text('1/2 fields completed'), findsOneWidget);
      // Where it was found shows under the day.
      expect(find.text('Field: Pump 3 seal'), findsOneWidget);
      expect(find.text('Note: Pump 3 leaking'), findsOneWidget);
      // A sea can number finds the Sea Can Movements tab.
      p.seaCans = [SeaCanMove()..can = 'BPPU 201006-1'..toLetter = 'E'..toRow = '34'];
      await tester.enterText(find.byType(TextField), '201006');
      await tester.pump();
      expect(find.text('BPPU 201006-1 · – to E34'), findsOneWidget);
      await tester.tap(find.text('Sea Can Movements').last);
      await tester.pumpAndSettle();
      expect(find.text('BPPU 201006-1'), findsWidgets);
    });

    testWidgets('note screen: new note is kept once written and can be ticked', (tester) async {
      final p = sample();
      p.notes.clear();
      Store.instance.projects.add(p);
      await tester.pumpWidget(app(Builder(
        builder: (c) => TextButton(
          onPressed: () => Navigator.of(c).push(MaterialPageRoute(builder: (_) => NoteScreen(projectId: p.id))),
          child: const Text('open'),
        ),
      )));
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('Note'), findsOneWidget);
      expect(find.byTooltip('Delete note'), findsOneWidget);
      // Ticking an empty note does nothing.
      await tester.tap(find.text("Include in today's report"));
      await tester.pump();
      expect(p.notes, isEmpty);
      await tester.enterText(find.byType(TextField), 'Crane booked for 7');
      await tester.tap(find.text("Include in today's report"));
      await tester.pump();
      expect(p.notes.single.text, 'Crane booked for 7');
      expect(p.logs[0].findExtra(p.notes.single.id)!.include, true);
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(find.text('open'), findsOneWidget);
      expect(p.notes.single.by, 'Tanner');
    });

    testWidgets('note screen: emptied note is removed on leaving; delete button', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      final first = p.notes[0], second = p.notes[1];
      Future<void> open(Note n) async {
        await tester.pumpWidget(app(Builder(
          builder: (c) => TextButton(
            onPressed: () =>
                Navigator.of(c).push(MaterialPageRoute(builder: (_) => NoteScreen(projectId: p.id, noteId: n.id))),
            child: const Text('open'),
          ),
        )));
        await tester.tap(find.text('open'));
        await tester.pumpAndSettle();
      }

      await open(first);
      expect(find.text('Pump 3 leaking'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '  ');
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(p.notes, [second]);

      await open(second);
      await tester.tap(find.byTooltip('Delete note'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();
      expect(p.notes, isEmpty);
      expect(find.text('open'), findsOneWidget);
    });

    testWidgets('notify screen renders and asks for recipients first', (tester) async {
      final p = sample();
      Store.instance.projects.add(p);
      await tester.pumpWidget(app(NotifyScreen(projectId: p.id)));
      await tester.pumpAndSettle();
      // The crew picker opens first.
      expect(find.text('Al Smith'), findsWidgets);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(find.text('Notify'), findsOneWidget);
      expect(find.text('Send to'), findsOneWidget);
      expect(find.text('Send note'), findsOneWidget);
      await tester.tap(find.text('Send note'));
      await tester.pump();
      expect(find.text('Choose who to send it to'), findsOneWidget);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(p.notices.length, 1);
    });
  });
}
