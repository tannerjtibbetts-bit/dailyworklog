import 'dart:convert';
import 'dart:io';

import 'package:dailyworklog/data/backup.dart';
import 'package:dailyworklog/data/fmt.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:flutter_test/flutter_test.dart';

/// A worklog.json as the Android app writes it.
const androidJson = '''
{"version":2,"userName":"Tanner Tibbetts","contactPhone":"12265550123","myColours":["blue","orange"],
 "formDetails":{"project":"FCFR","observation":"Single"},
 "projects":[{"id":"p1","name":"B44","location":"Zone 2","createdAt":1700000000000,"crew":["Sam Maint","Joe Fitter"],
   "logs":[{"id":"l1","date":"2026-10-15","name":"","createdAt":1700000000001,"crew":[],
     "tasks":[{"id":"t1","title":"D1716 sea can","notes":"bring straps","done":true,"completedBy":"Sam Maint",
       "completedAt":1700000000100,"createdBy":"Me","createdAt":1700000000002,"photosRequired":true,"loadSecured":false,
       "securedBy":"","securedAt":0,"nextShift":true,"side":"north","assignees":["Sam Maint"],
       "photos":[{"file":"a.jpg","addedBy":"Sam Maint","addedAt":1700000000200}]}],
     "extras":[{"id":"e1","type":"note","refId":"n1","title":"Me","detail":"hi","by":"Me","at":5,"include":true,"photos":[]}]}],
   "templates":[],"phones":{"Sam Maint":"519-555-0123"},"notices":[],"emails":["boss@example.com"],
   "overtime":{"weekOf":"2026-10-16","include":false,"notes":"","rows":[{"name":"Sam Maint","fri":"Yes","sat":"","sun":"No","hours":"8"}]},
   "roles":{"Joe Fitter":"tech"},"assignTexts":["Sam Maint"],
   "notes":[{"id":"n1","text":"hi","by":"Me","createdAt":3,"updatedAt":4,"photos":[]}]}],
 "links":[{"id":"k1","title":"Observation","url":"https://aecon.formstack.com/forms/x"}]}
''';

void main() {
  test('reads the Android app\'s worklog.json and writes the same data back', () {
    final o = jsonDecode(androidJson) as Map<String, dynamic>;
    final p = Project.fromJson((o['projects'] as List)[0] as Map<String, dynamic>);
    expect(p.name, 'B44');
    expect(p.logs.single.tasks.single.side, Task.north);
    expect(p.logs.single.tasks.single.photos.single.file, 'a.jpg');
    expect(p.phoneOf('Sam Maint'), '519-555-0123');
    expect(p.roleOf('Joe Fitter'), Project.techRole);
    expect(p.roleOf('Sam Maint'), Project.crewRole);
    expect(p.assignTexts, {'Sam Maint'});
    expect(p.overtime.yesCount(0), 1);
    expect(p.notes.single.updatedAt, 4);
    // Round trip keeps everything
    final again = Project.fromJson(jsonDecode(jsonEncode(p.toJson())) as Map<String, dynamic>);
    expect(jsonEncode(again.toJson()), jsonEncode(p.toJson()));
  });

  test('rename reaches crew, fields, notices and overtime', () {
    final o = jsonDecode(androidJson) as Map<String, dynamic>;
    final p = Project.fromJson((o['projects'] as List)[0] as Map<String, dynamic>);
    p.renamePerson('Sam Maint', 'Sam M');
    expect(p.crew.first, 'Sam M');
    expect(p.logs.single.tasks.single.assignees, ['Sam M']);
    expect(p.phoneOf('Sam M'), '519-555-0123');
    expect(p.assignTexts, {'Sam M'});
    expect(p.overtime.rows.single.name, 'Sam M');
  });

  test('report groups: South, North, then Other', () {
    final a = Task()..side = Task.north, b = Task()..side = Task.south, c = Task();
    final g = Task.reportGroups([a, b, c]);
    expect(g.map((e) => e.key), ['South', 'North', 'Other']);
    expect(Task.reportGroups([c]).single.key, '');
  });

  test('dates', () {
    expect(Fmt.logDate('2026-10-05'), '05/10/2026');
    expect(Fmt.upcomingFriday(DateTime(2026, 10, 14)), '2026-10-16'); // Wed
    expect(Fmt.upcomingFriday(DateTime(2026, 10, 18)), '2026-10-16'); // Sun
    expect(Fmt.weekendDay('2026-10-16', 1), 'Sat Oct 17');
    expect(Fmt.initials('sam de maint'), 'SM');
  });

  test('store saves and loads; backup restores on another phone', () async {
    final dir1 = await Directory.systemTemp.createTemp('dwl1');
    final dir2 = await Directory.systemTemp.createTemp('dwl2');
    await File('${dir1.path}/worklog.json').writeAsString(androidJson);
    final s1 = await Store.open(dir1);
    expect(s1.userName, 'Tanner Tibbetts');
    expect(s1.formAnswers['project'], 'FCFR');
    await s1.photoFile('a.jpg').writeAsBytes([1, 2, 3]);
    s1.contactPhone = '555';
    s1.save();
    await s1.flush();
    final reread = await Store.open(dir1);
    expect(reread.contactPhone, '555');

    final zip = await Backup.create(reread, into: dir2.parent);
    final info = await Backup.inspect(zip);
    expect(info!.projects, 1);
    expect(info.photos, 1);

    final s2 = await Store.open(dir2);
    expect(s2.projects, isEmpty);
    expect(await Backup.restore(s2, zip), isTrue);
    expect(s2.projects.single.name, 'B44');
    expect(await s2.photoFile('a.jpg').readAsBytes(), [1, 2, 3]);
    final s2again = await Store.open(dir2);
    expect(s2again.projects.single.logs.single.tasks.single.title, 'D1716 sea can');
  });
}
