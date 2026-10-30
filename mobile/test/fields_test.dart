import 'dart:io';
import 'dart:typed_data';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/assign_sheet.dart';
import 'package:dailyworklog/screens/field_widgets.dart';
import 'package:dailyworklog/screens/log_screen.dart';
import 'package:dailyworklog/screens/load_secure_sheet.dart';
import 'package:dailyworklog/screens/task_screen.dart';
import 'package:dailyworklog/services/photos.dart';
import 'package:dailyworklog/services/sms.dart';
import 'package:dailyworklog/ui/common.dart';
import 'package:dailyworklog/ui/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;

Project roundTrip(Project p) => Project.fromJson(p.toJson());

Task field(String title, [String side = '']) => Task()
  ..title = title
  ..side = side;

void main() {
  group('ShiftTest', () {
    test('next shift saved; carried copy starts fresh, keeps notes', () {
      final p = Project();
      final l = DailyLog();
      p.logs.add(l);
      l.tasks.add(
        Task()
          ..title = 'Finish torque checks'
          ..nextShift = true
          ..notes = 'bay 3',
      );
      final r = roundTrip(p).logs[0].tasks[0];
      expect(r.nextShift, isTrue);
      final c = r.carryCopy();
      expect(!c.nextShift && c.notes == 'bay 3' && !c.done, isTrue);
    });
  });

  group('CrewTest', () {
    test('groups and texting choice saved; old data defaults', () {
      final p = Project()..crew.addAll(['Al', 'Bo', 'Cy']);
      p.roles['Bo'] = Project.techRole;
      p.roles['Cy'] = Project.mgmtRole;
      p.assignTexts.add('Al');
      final q = roundTrip(p);
      expect([q.roleOf('Al'), q.roleOf('Bo'), q.roleOf('Cy')], ['crew', 'tech', 'mgmt']);
      expect(q.assignTexts.contains('Al') && !q.assignTexts.contains('Bo'), isTrue);
      final old = p.toJson()
        ..remove('roles')
        ..remove('assignTexts');
      final o = Project.fromJson(old);
      expect(o.roleOf('Bo'), 'crew');
      expect(o.assignTexts, isEmpty);
    });
  });

  group('RenameTest', () {
    test('rename everywhere', () {
      final p = Project()..crew.add('Jon');
      p.phones['Jon'] = '555';
      p.roles['Jon'] = Project.mgmtRole;
      p.assignTexts.add('Jon');
      final l = DailyLog()..crew.add('Jon');
      final t = Task()..assignees.add('Jon');
      l.tasks.add(t);
      p.logs.add(l);
      final r = OtRow()..name = 'Jon';
      p.overtime.rows.add(r);
      p.renamePerson('Jon', 'Jon Smith');
      expect(p.crew, ['Jon Smith']);
      expect(p.phones['Jon Smith'], '555');
      expect(p.roleOf('Jon Smith'), 'mgmt');
      expect(p.assignTexts.contains('Jon Smith'), isTrue);
      expect(l.crew.contains('Jon Smith') && t.assignees.contains('Jon Smith') && r.name == 'Jon Smith', isTrue);
      expect(Project.roleLabel(Project.mgmtRole), 'Supervision');
    });
  });

  group('SideTest', () {
    test('report order South, North, Other; copies keep the area', () {
      final p = Project();
      final l = DailyLog();
      p.logs.add(l);
      for (final x in [
        ['N1', 'north'],
        ['S1', 'south'],
        ['X1', ''],
        ['S2', 'south'],
        ['N2', 'north'],
      ]) {
        l.tasks.add(field(x[0], x[1]));
      }
      final q = roundTrip(p);
      final sb = StringBuffer();
      for (final g in Task.reportGroups(q.logs[0].tasks)) {
        sb.write('${g.key}:');
        for (final t in g.value) {
          sb.write('${t.title},');
        }
        sb.write(' ');
      }
      expect(sb.toString(), 'South:S1,S2, North:N1,N2, Other:X1, ');
      final plain = [Task()];
      expect(Task.reportGroups(plain).length, 1);
      expect(Task.reportGroups(plain)[0].key, '');
      expect(q.logs[0].tasks[0].freshCopy().side, 'north');
    });

    test('checklist shows South, then North, then the rest, keeping order within an area', () {
      final tasks = [
        field('N1', 'north'),
        field('S1', 'south'),
        field('X1'),
        field('S2', 'south'),
        field('N2', 'north'),
      ];
      expect(visibleFields(tasks, FieldFilter.none, 'Me').map((t) => t.title), ['S1', 'S2', 'N1', 'N2', 'X1']);
    });

    test('dragging stays inside an area', () {
      final v = visibleFields(
        [field('S1', 'south'), field('S2', 'south'), field('N1', 'north'), field('X1')],
        FieldFilter.none,
        'Me',
      );
      expect(moveField(v, 0, 1)!.map((t) => t.title), ['S2', 'S1', 'N1', 'X1']);
      expect(moveField(v, 1, 2), isNull); // South into North
      expect(moveField(v, 3, 0), isNull); // no area to the top
      expect(moveField(v, 0, 9), isNull);
    });
  });

  group('TextTest', () {
    final p = Project()..name = 'B44 Zone 2';
    final l = DailyLog()..date = '2026-10-05';

    test('assignment text has number', () {
      final t = Task()..title = 'Pack up sea can';
      final s = Sms.taskText(p, l, t, 'Tanner', '12266688639');
      expect(s, startsWith('Daily work log 05/10/2026 (B44 Zone 2)\n\nAssigned to you: Pack up sea can'));
      expect(s, endsWith('\n\n--------------------\nFrom: Tanner\nCall or text me at 1-226-668-8639'));
      expect(Sms.taskText(p, l, t, 'Tanner', ''), endsWith('--------------------\nFrom: Tanner'));
      expect(
        Sms.noticeText(p, 'Meet at gate 3', 'Tanner', '2266688639'),
        endsWith('From: Tanner (B44 Zone 2)\nCall or text me at 226-668-8639'),
      );
      expect(Sms.noticeText(p, '  Meet at gate 3 ', 'Tanner', ''), startsWith('Meet at gate 3\n\n----'));
    });

    test('TextTest2: no notes in text, photo reminder', () {
      final t = Task()
        ..title = 'Pack up sea can'
        ..notes = 'Use the north gate'
        ..photosRequired = true;
      final s = Sms.taskText(p, l, t, 'Tanner', '12266688639');
      expect(s.contains('Assigned to you: Pack up sea can\n\nPhotos required.\n\n----'), isTrue);
      expect(s.contains('north gate'), isFalse);
    });

    test('formatPhone and contactLine', () {
      expect(Sms.formatPhone('1 (226) 555-0123'), '1-226-555-0123');
      expect(Sms.formatPhone('2265550123'), '226-555-0123');
      expect(Sms.formatPhone(' +44 20 7946 0958 '), '+44 20 7946 0958');
      expect(Sms.contactLine(null), '');
      expect(Sms.contactLine('  '), '');
      expect(Sms.signOff('Al', ''), '\n\n--------------------\nFrom: Al');
    });

    test('sms links: semicolons on Android, addresses on iOS', () {
      expect(
        Sms.smsUri(['519-555-0101', '', '(519) 555 0102'], 'Hi there & bye', ios: false),
        'sms:5195550101;5195550102?body=Hi%20there%20%26%20bye',
      );
      expect(
        Sms.smsUri(['519-555-0101', '+15195550102'], 'Hi\nthere', ios: true),
        'sms:/open?addresses=5195550101,+15195550102&body=Hi%0Athere',
      );
      expect(Sms.smsUri([], 'x', ios: false), 'sms:?body=x');
    });
  });

  group('Checklist logic', () {
    test('filters', () {
      final a = field('A')
        ..assignees = ['Tanner']
        ..done = true;
      final b = field('B');
      final c = field('C')..assignees = ['Al'];
      final all = [a, b, c];
      String titles(FieldFilter f) => visibleFields(all, f, 'Tanner').map((t) => t.title).join();
      expect(titles(FieldFilter.none), 'ABC');
      expect(titles(FieldFilter.me), 'A');
      expect(titles(FieldFilter.incomplete), 'BC');
      expect(titles(FieldFilter.complete), 'A');
      expect(titles(FieldFilter.unassigned), 'B');
    });

    test('completing needs a photo when photos are required', () {
      final t = field('A')..photosRequired = true;
      expect(toggleDone(t, 'Tanner', 5), isFalse);
      expect(t.done, isFalse);
      t.photos.add(Photo(file: 'a.jpg'));
      expect(toggleDone(t, 'Tanner', 5), isTrue);
      expect([t.done, t.completedBy, t.completedAt], [true, 'Tanner', 5]);
      expect(toggleDone(t, 'Tanner', 9), isTrue);
      expect([t.done, t.completedBy, t.completedAt], [false, '', 0]);
      // Reopening a done field never needs a photo.
      final d = field('B')
        ..photosRequired = true
        ..done = true;
      expect(toggleDone(d, 'X', 1), isTrue);
    });

    test('new field and template', () {
      final t = newField('Pump', Task.south, 'Tanner');
      expect([t.title, t.side, t.createdBy], ['Pump', 'south', 'Tanner']);
      final l = DailyLog()
        ..tasks.add(
          t
            ..done = true
            ..notes = 'n'
            ..photosRequired = true,
        );
      final tp = templateFrom(l, 'Daily fields');
      expect(tp.name, 'Daily fields');
      final c = tp.tasks.single;
      expect([c.title, c.done, c.notes, c.photosRequired, c.side], ['Pump', false, '', true, 'south']);
    });

    test('Also today lines', () {
      final at = DateTime(2026, 10, 5, 14, 3).millisecondsSinceEpoch;
      final link = Extra()
        ..type = Extra.link
        ..title = 'Portal'
        ..at = at;
      expect(extraText(link), '2:03 PM  Link used: Portal');
      final note = Extra()
        ..type = Extra.note
        ..title = 'Tanner'
        ..detail = 'Pump 3 leaking'
        ..at = at
        ..photos = [Photo(file: 'a'), Photo(file: 'b')];
      expect(extraText(note), '2:03 PM  Note by Tanner\nPump 3 leaking\n2 photos');
    });

    test('card tints', () {
      final n = field('N', Task.north), s = field('S', Task.south), x = field('X');
      expect(fieldCardDecoration(n).color, C.northBg);
      expect(fieldCardDecoration(s).color, C.southBg);
      expect(fieldCardDecoration(x).color, Colors.white);
      expect((fieldCardDecoration(x).border as Border).top.color, C.cardStroke);
      for (final t in [n, s, x]) {
        t.done = true;
        final b = fieldCardDecoration(t).border as Border;
        expect(b.top.color, C.green);
        expect(b.bottom.color, C.green);
        expect(b.top.width, 2.5);
        expect(fieldAccent(t), C.green);
      }
      expect(fieldCardDecoration(n).color, C.northBg);
      expect(fieldCardDecoration(x).color, C.doneBg);
    });
  });

  group('Crew picker', () {
    test('groups and search', () {
      final p = Project()..crew.addAll(['Al Smith', 'Bo', 'Cy', 'Di']);
      p.roles['Bo'] = Project.techRole;
      p.roles['Cy'] = Project.mgmtRole;
      final g = crewGroups(p, '');
      expect(g.map((e) => '${e.key}:${e.value.join('/')}'), ['crew:Al Smith/Di', 'tech:Bo', 'mgmt:Cy']);
      expect(crewGroups(p, 'SMI').map((e) => e.value), [
        ['Al Smith'],
      ]);
      expect(offerAdd(p, 'bo'), isFalse);
      expect(offerAdd(p, 'Bob'), isTrue);
      expect(offerAdd(p, ''), isTrue);
    });

    test('add and edit crew', () {
      final p = Project()..crew.addAll(['Al', 'Bo']);
      final sel = <String>{};
      addCrewMember(p, const CrewDetails('Cy', '555', Project.techRole, true), sel);
      expect(p.crew.last, 'Cy');
      expect(sel, {'Cy'});
      expect([p.phoneOf('Cy'), p.roleOf('Cy'), p.assignTexts.contains('Cy')], ['555', 'tech', true]);
      final dir = Directory.systemTemp.createTempSync('fields_crew');
      addTearDown(() => dir.deleteSync(recursive: true));
      return Store.open(dir).then((store) {
        store.userName = 'Cy';
        expect(updateCrewMember(store, p, 'Cy', const CrewDetails('Bo', '', Project.crewRole, false)), isFalse);
        expect(p.crew, ['Al', 'Bo', 'Cy']);
        expect(updateCrewMember(store, p, 'Cy', const CrewDetails('Cy Young', '', Project.mgmtRole, false)), isTrue);
        expect(p.crew, ['Al', 'Bo', 'Cy Young']);
        expect([p.phoneOf('Cy Young'), p.roleOf('Cy Young'), p.assignTexts.contains('Cy Young')], ['', 'mgmt', false]);
        expect(store.userName, 'Cy Young');
      });
    });

    test('who gets an assignment text', () {
      final p = Project()..assignTexts.addAll(['Al', 'Tanner']);
      expect(newlyAssigned(['Al'], ['Al', 'Bo', 'Tanner']), ['Bo', 'Tanner']);
      expect(getsAssignText(p, 'Al', 'Tanner'), isTrue);
      expect(getsAssignText(p, 'Bo', 'Tanner'), isFalse); // not ticked
      expect(getsAssignText(p, 'Tanner', 'Tanner'), isFalse); // never yourself
    });

    test('load secured and added line', () {
      final t = Task()
        ..createdBy = 'Tanner'
        ..createdAt = DateTime(2026, 10, 5, 7, 2).millisecondsSinceEpoch;
      toggleSecured(t, 'Tanner', 7);
      expect([t.loadSecured, t.securedBy, t.securedAt], [true, 'Tanner', 7]);
      toggleSecured(t, 'Tanner', 8);
      expect([t.loadSecured, t.securedBy, t.securedAt], [false, '', 0]);
      expect(addedLine(t), 'Added by Tanner, 10/05/2026 at 7:02 AM');
      expect(addedLine(Task()..createdAt = 0), '');
    });
  });

  group('Photos', () {
    test('file names like the Android app', () {
      final n = newPhotoName(DateTime(2026, 10, 16, 14, 22, 33));
      expect(n, matches(RegExp(r'^IMG_20261016_142233_[0-9a-f]{1,5}\.jpg$')));
    });

    test('upright JPEG kept as is; sideways one turned upright at full size', () {
      final pic = img.Image(width: 40, height: 20);
      final upright = img.encodeJpg(pic, quality: 90);
      expect(identical(normalizePhoto(upright), upright), isTrue);

      final sideways = img.Image(width: 40, height: 20);
      sideways.exif.imageIfd.orientation = 6;
      final bytes = img.encodeJpg(sideways, quality: 90);
      final out = normalizePhoto(bytes)!;
      final back = img.decodeJpg(out)!;
      expect([back.width, back.height], [20, 40]);
      final o = back.exif.imageIfd.orientation;
      expect(o == null || o == 1, isTrue);

      expect(normalizePhoto(Uint8List(0)), isNull);
      // PNG from the gallery becomes a JPEG.
      final png = img.encodePng(img.Image(width: 8, height: 6));
      expect(img.findFormatForData(normalizePhoto(png)!), img.ImageFormat.jpg);
    });
  });

  group('Screens', () {
    late Directory dir;
    late Project p;
    late DailyLog l;

    setUp(() async {
      FlutterSecureStorage.setMockInitialValues({});
      dir = Directory.systemTemp.createTempSync('fields_screens');
      final s = await Store.open(dir);
      s.userName = 'Tanner';
      p = Project()
        ..name = 'B44 Zone 2'
        ..location = 'Bruce A'
        ..crew.addAll(['Tanner', 'Al', 'Bo']);
      p.roles['Bo'] = Project.techRole;
      p.phones['Al'] = '5195550101';
      l = DailyLog()
        ..date = '2026-10-05'
        ..crew = ['Al', 'Bo'];
      l.tasks.addAll([
        field('North one', Task.north)..assignees = ['Al'],
        field('South one', Task.south)
          ..done = true
          ..completedBy = 'Tanner'
          ..completedAt = 1
          ..loadSecured = true,
        field('Plain')
          ..photosRequired = true
          ..nextShift = true
          ..notes = 'bay 3',
      ]);
      l.extras.add(
        Extra()
          ..type = Extra.link
          ..title = 'Portal'
          ..at = 1
          ..include = true,
      );
      p.logs.add(l);
      s.projects.add(p);
    });

    tearDown(() {
      try {
        dir.deleteSync(recursive: true);
      } catch (_) {}
    });

    Widget app(Widget home) => MaterialApp(theme: appTheme(), home: home);

    testWidgets('checklist renders, sorts South first and filters', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.6;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(app(LogScreen(projectId: p.id, logId: l.id)));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Daily work log 05/10/2026'), findsWidgets);
      expect(find.text('1/3 fields completed'), findsOneWidget);
      expect(find.text('South one'), findsOneWidget);
      expect(tester.getTopLeft(find.text('South one')).dy, lessThan(tester.getTopLeft(find.text('North one')).dy));
      expect(find.text('✓  Load secured'), findsOneWidget);

      await tester.ensureVisible(find.text('Complete'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Complete'));
      await tester.pumpAndSettle();
      expect(find.text('North one'), findsNothing);
      expect(find.text('South one'), findsOneWidget);

      await tester.ensureVisible(find.text('Complete'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Complete'));
      await tester.pumpAndSettle();
      // Needs a photo first.
      await tester.tap(find.byType(CheckCircle).at(2));
      await tester.pump();
      expect(find.text('This field needs a photo before it can be completed'), findsOneWidget);
      expect(l.tasks[2].done, isFalse);
      await tester.tap(find.byType(CheckCircle).at(1));
      await tester.pumpAndSettle();
      expect(l.tasks[0].done, isTrue);
      expect(find.text('2/3 fields completed'), findsOneWidget);

      await tester.scrollUntilVisible(
        find.text('Also today'),
        300,
        scrollable: find.descendant(of: find.byType(ReorderableListView), matching: find.byType(Scrollable)).first,
      );
      expect(find.textContaining('Link used: Portal'), findsOneWidget);
    });

    testWidgets('new field goes on the list', (tester) async {
      await tester.pumpWidget(app(LogScreen(projectId: p.id, logId: l.id)));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.mic));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Sweep the bay');
      await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('South')));
      await tester.tap(find.text('Add'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final t = l.tasks.last;
      expect([t.title, t.side, t.createdBy], ['Sweep the bay', 'south', 'Tanner']);
    });

    testWidgets('field screen renders and edits', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.6;
      addTearDown(tester.view.reset);
      final t = l.tasks[0];
      await tester.pumpWidget(
        app(
          Builder(
            builder: (c) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.of(c).push(
                  MaterialPageRoute<void>(
                    builder: (_) => TaskScreen(projectId: p.id, logId: l.id, taskId: t.id),
                  ),
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Edit Field'), findsOneWidget);
      expect(find.text('North one'), findsOneWidget);
      expect(find.text('Al'), findsOneWidget);
      expect(find.text('5195550101'), findsOneWidget);

      await tester.ensureVisible(find.text('South'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('South'));
      await tester.pump();
      expect(t.side, Task.south);
      await tester.tap(find.text('South'));
      await tester.pump();
      expect(t.side, '');

      await tester.ensureVisible(find.text('Load securement completed'));
      await tester.pumpAndSettle();
      // Tapping opens the Load securement pop-up; Done with no photos just ticks it.
      await tester.tap(find.text('Load securement completed'));
      await tester.pumpAndSettle();
      expect(find.text('Load securement'), findsOneWidget);
      expect(find.text('To: Not set'), findsOneWidget);
      expect(find.text('Load securement - North one'), findsOneWidget);
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(t.loadSecured, isTrue);
      expect(t.securedBy, 'Tanner');
      expect(t.securedPhotos, isEmpty);
      // Open again: it can be unticked.
      await tester.tap(find.text('Load securement completed'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mark not secured'));
      await tester.pumpAndSettle();
      expect(t.loadSecured, isFalse);
      // Photos already on the field show under it, with the report tick.
      t.securedPhotos.add(Photo(file: 'x.jpg', addedBy: 'Tanner', addedAt: 1));
      Store.instance.save();
      await tester.pumpAndSettle();
      expect(find.text('Load securement photos (1)'), findsOneWidget);
      await tester.ensureVisible(find.text('Put load securement photos in the report'));
      await tester.tap(find.text('Put load securement photos in the report'));
      await tester.pump();
      expect(t.securedPhotosInReport, isFalse);

      await tester.enterText(find.byType(TextField).first, 'North one, bay 2');
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Back'));
      await tester.pumpAndSettle();
      expect(t.title, 'North one, bay 2');
    });

    testWidgets('Email sending remembers who gets load securement photos', (tester) async {
      Store.instance.loadEmailTo = 'old@aecon.com';
      await tester.pumpWidget(app(Builder(
        builder: (c) => Scaffold(body: TextButton(onPressed: () => emailSetupDialog(c), child: const Text('open'))),
      )));
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('old@aecon.com'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextField, 'Your Gmail address'), 'tanner@gmail.com');
      await tester.enterText(find.widgetWithText(TextField, 'App password'), 'abcd efgh ijkl mnop');
      await tester.enterText(find.widgetWithText(TextField, 'old@aecon.com'), 'new@aecon.com; boss@aecon.com');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(Store.instance.loadEmailTo, 'new@aecon.com, boss@aecon.com');
      expect(Store.instance.mailFrom, 'tanner@gmail.com');
    });

    testWidgets('assign sheet: groups, tick and assign', (tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.6;
      addTearDown(tester.view.reset);
      List<String>? got;
      await tester.pumpWidget(
        app(
          Builder(
            builder: (c) => Scaffold(
              body: TextButton(
                onPressed: () async => got = await showAssignSheet(c, p, ['Al'], 'Assign Users'),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('CREW'), findsOneWidget);
      expect(find.text('TECHS'), findsOneWidget);
      expect(find.text('+ Add phone number'), findsWidgets);
      await tester.tap(find.text('Bo'));
      await tester.pump();
      await tester.enterText(find.byType(TextField), 'zed');
      await tester.pump();
      expect(find.text('+  Add “zed” to crew'), findsOneWidget);
      await tester.tap(find.text('Assign'));
      await tester.pumpAndSettle();
      expect(got, ['Al', 'Bo']);
    });

    testWidgets('manage crew shows who is texted', (tester) async {
      p.assignTexts.add('Al');
      await tester.pumpWidget(
        app(
          Builder(
            builder: (c) => Scaffold(
              body: TextButton(onPressed: () => showManageCrew(c, p), child: const Text('open')),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('5195550101  ·  texted when assigned'), findsOneWidget);
      expect(find.text('Done'), findsOneWidget);
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });
  });
}
