import 'package:dailyworklog/data/rich.dart';
import 'package:flutter_test/flutter_test.dart';

const b = RichStyle(bold: true), u = RichStyle(underline: true);

String show(List<Segment> s) => s.map((x) => '${x.text}[${x.style}]').join('|');

void main() {
  test('1. Delay red bold, on hold bold highlighted', () {
    expect(show(Rich.segments('Crane delayed, on  hold now', [])),
        'Crane []|delayed[b,red]|, []|on  hold[b,hl]| now[]');
  });

  test('2. user bold, keywords off', () {
    expect(show(Rich.segments('abc', [const Run(0, 2, b)], keywords: false)), 'ab[b]|c[]');
  });

  test('3. Delay beats the user colour, keeps underline', () {
    expect(show(Rich.segments('Delay', [const Run(0, 5, RichStyle(underline: true, colour: 'orange'))])),
        'Delay[b,u,red]');
  });

  test('4-6. toggles and colour', () {
    final r = Rich.applyToggle('hello', [], 1, 3, underline: false);
    expect(r, [const Run(1, 3, b)]);
    expect(Rich.applyToggle('hello', r, 1, 3, underline: false), isEmpty);
    expect(Rich.applyToggle('hello', [const Run(0, 2, b)], 1, 4, underline: false), [const Run(0, 4, b)]);
    expect(Rich.applyColour('hello', [const Run(0, 5, b)], 1, 3, 'red'),
        [const Run(0, 1, b), const Run(1, 3, RichStyle(bold: true, colour: 'red')), const Run(3, 5, b)]);
  });

  test('7-10. edits keep the formatting in place', () {
    expect(Rich.afterEdit('hello', 'heXYllo', [const Run(0, 5, b)], null), [const Run(0, 7, b)]);
    expect(Rich.afterEdit('hello', 'hello!', [const Run(0, 5, b)], null), [const Run(0, 6, b)]);
    expect(Rich.afterEdit('hello', 'hllo', [const Run(1, 3, u)], null), [const Run(1, 2, u)]);
    expect(Rich.afterEdit('ab', 'aXb', [], b), [const Run(1, 2, b)]);
  });

  test('11. bad runs dropped; relay is not a delay', () {
    expect(
        Rich.fromJson([
          {'s': 3, 'e': 3, 'b': true},
          {'s': 0, 'e': 99, 'u': true},
          {'s': 0, 'e': 2, 'c': 'pink'},
        ], 5),
        [const Run(0, 5, u)]);
    expect(show(Rich.segments('relay', [])), 'relay[]');
    expect(show(Rich.segments('Delays DELAYED', [])), 'Delays[b,red]| []|DELAYED[b,red]');
    expect(Rich.toJson([const Run(1, 3, RichStyle(bold: true, colour: 'red'))]), [
      {'s': 1, 'e': 3, 'b': true, 'c': 'red'}
    ]);
  });
}
