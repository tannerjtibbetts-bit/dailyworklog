import 'dart:io';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:dailyworklog/ui/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('a link reminder is saved with the link and described in words', () {
    final start = DateTime(2026, 11, 23, 7, 0).millisecondsSinceEpoch;
    final l = Link()
      ..title = 'BBO'
      ..url = 'https://example.com/bbo'
      ..reminder = (LinkReminder()
        ..start = start
        ..repeat = LinkReminder.weekly
        ..alert = 15
        ..eventId = '42');
    final back = Link.fromJson(l.toJson());
    expect(back.reminder!.repeat, 'weekly');
    expect(back.reminder!.alert, 15);
    expect(back.reminder!.eventId, '42');
    expect(back.reminder!.describe(), 'Every week, Mon at 7:00 AM');
    expect(Link.fromJson({'title': 'x', 'url': 'y'}).reminder, isNull);
    final r = back.reminder!;
    expect((r..repeat = LinkReminder.daily).describe(), 'Every day at 7:00 AM');
    expect((r..repeat = LinkReminder.monthly).describe(), 'Every month on the 23rd at 7:00 AM');
    expect((r..repeat = LinkReminder.yearly).describe(), 'Every year, Nov 23 at 7:00 AM');
    expect((r..repeat = LinkReminder.once).describe(), 'One time, Nov 23, 2026 at 7:00 AM');
    expect(LinkReminder.alertLabel(0), 'At the time');
    expect(LinkReminder.alertLabel(60), '1 hour before');
    expect(LinkReminder.alertLabel(120), '2 hours before');
    expect(LinkReminder.alertLabel(2880), '2 days before');
    expect(LinkReminder.alertLabel(10080), '1 week before');
    // A second alert: kept, and both go to the calendar.
    expect(r.alert2, LinkReminder.none);
    r.alert2 = 1440;
    expect(Link.fromJson((back..reminder = r).toJson()).reminder!.alert2, 1440);
    expect(r.alertList(), [15, 1440]);
    expect((r..alert2 = 15).alertList(), [15]);
  });

  testWidgets('Edit link: tick "Remind me in my calendar" and it goes in the calendar', (tester) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final dir = Directory.systemTemp.createTempSync('reminder');
    late Store store;
    await tester.runAsync(() async => store = await Store.open(dir));
    final calls = <MethodCall>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(const MethodChannel('dailyworklog/calendar'), (c) async {
      calls.add(c);
      return c.method == 'save' ? 'event-7' : true;
    });
    final p = Project()..name = 'B44';
    store.projects.add(p);
    final link = Link()
      ..title = 'BBO'
      ..url = 'https://example.com/bbo';
    store.links
      ..clear()
      ..add(link);
    await tester.pumpWidget(MaterialApp(theme: appTheme(), home: ProjectScreen(projectId: p.id, initialTab: ProjectTab.links)));
    await tester.pump();
    await tester.tap(find.byTooltip('More'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit link'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remind me in my calendar'));
    await tester.pumpAndSettle();
    expect(find.text('Every week'), findsOneWidget);
    expect(find.text('At the time'), findsOneWidget);
    // Every month instead.
    await tester.tap(find.text('Every week'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Every month').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(calls.single.method, 'save');
    expect((calls.single.arguments as Map)['repeat'], 'monthly');
    expect((calls.single.arguments as Map)['alerts'], [0]);
    expect((calls.single.arguments as Map)['title'], 'BBO');
    expect(link.reminder!.repeat, 'monthly');
    expect(link.reminder!.eventId, 'event-7');
    expect(find.textContaining('Every month on the'), findsWidgets);

    // Untick it: taken out of the calendar.
    await tester.tap(find.byTooltip('More'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Edit link'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remind me in my calendar'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(calls.last.method, 'delete');
    expect((calls.last.arguments as Map)['eventId'], 'event-7');
    expect(link.reminder, isNull);
    await tester.pump(const Duration(seconds: 5));
  });
}
