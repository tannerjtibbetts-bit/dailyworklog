import 'package:dailyworklog/data/fmt.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/project_search.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  int at(String iso, [int h = 9]) => DateTime.parse('${iso}T0$h:00:00').millisecondsSinceEpoch;

  Project sample() {
    final p = Project()..name = 'B44';
    final d1 = DailyLog()..date = '2026-10-20';
    d1.tasks.add(Task()
      ..title = 'Move sea can BPPU 201006-1 to tent 1A'
      ..notes = 'Use the Hilti drill');
    d1.tasks.add(Task()
      ..title = 'Torque checks'
      ..assignees = ['Sam Maint']
      ..done = true
      ..completedBy = 'Sam Maint');
    d1.extras.add(Extra()
      ..type = Extra.link
      ..title = 'Tool portal'
      ..detail = 'https://tools.example.com/hilti');
    final d2 = DailyLog()..date = '2026-10-22';
    d2.tasks.add(Task()..title = 'Return Hilti drill to crib');
    d2.tasks.add(Task()
      ..title = ''
      ..notes = '${'x' * 70} the Hilti is in the trailer ${'y' * 70}');
    p.logs.addAll([d1, d2]);
    p.notices.add(Notice()
      ..to = ['Joe Fitter']
      ..note = 'Bring the hilti please'
      ..sentAt = at('2026-10-22'));
    p.notes.add(Note()
      ..text = 'Hilti battery is low'
      ..createdAt = at('2026-10-20'));
    p.seaCans = [
      SeaCanMove()..can = 'BPPU 201006-1'..fromLetter = 'D'..fromRow = '33'..fromLevel = '3'..toLetter = 'E'..toRow = '34'
        ..weight = '5',
      SeaCanMove()..can = 'LVNU 317524-6',
    ];
    return p;
  }

  test('finds every day a word is on, newest first, with where it was found', () {
    final r = ProjectSearch.run(sample(), 'hilti');
    expect(r.days.map((d) => d.log.date), ['2026-10-22', '2026-10-20']);
    expect(r.days[0].lines, [
      'Field: Return Hilti drill to crib',
      'Notes on (Untitled field): …${'x' * 15} the Hilti is in the trailer ${'y' * 16}…',
      'Sent note: Bring the hilti please',
    ]);
    expect(r.days[1].lines, [
      'Notes on Move sea can BPPU 201006-1 to tent 1A: Use the Hilti drill',
      'Also today: Link used: Tool portal: https://tools.example.com/hilti',
      'Note: Hilti battery is low',
    ]);
    expect(r.seaCans, isEmpty);
  });

  test('sea can numbers, names and checklist titles', () {
    final p = sample();
    final r = ProjectSearch.run(p, 'bppu 201006');
    expect(r.days.single.lines, ['Field: Move sea can BPPU 201006-1 to tent 1A']);
    expect(r.seaCans, ['BPPU 201006-1 · D33 L3 to E34 · 5']);
    expect(ProjectSearch.run(p, 'LVNU').seaCans, ['LVNU 317524-6 · – to –']);
    expect(ProjectSearch.run(p, 'sam').days.single.lines, ['Torque checks: Sam Maint']);
    expect(ProjectSearch.run(p, Fmt.logDate('2026-10-22')).days.single.lines,
        ['Checklist: Daily work log 22/10/2026']);
    expect(ProjectSearch.run(p, '  ').isEmpty, true);
    expect(ProjectSearch.run(p, 'nothing like this').isEmpty, true);
  });

  test('short text is shown whole', () {
    expect(ProjectSearch.snippet('a\n  b', 'b'), 'a b');
    expect(ProjectSearch.snippet('${'a' * 10}KEY${'b' * 80}', 'key'), '${'a' * 10}KEY${'b' * 47}…');
  });
}
