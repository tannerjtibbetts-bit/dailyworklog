import 'dart:convert';

import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:image/image.dart' as img;
import 'package:dailyworklog/data/form_lines.dart';
import 'package:dailyworklog/data/forms.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:flutter_test/flutter_test.dart';

/// The text read from Tanner's apprentice review (photographed off a screen, so with some screen words).
const sample = [
  'entice reviews',
  'Save as',
  'Print',
  'Save to OneDrive',
  'DATE:',
  'NAME:',
  'EMPLOYEE #',
  'EMPLOYER',
  'TERM:',
  'PERIOD COVERED FROM:',
  'TO:',
  'A. WORK DONE IN THE ABOVE PERIOD:',
  'B. 1. Improvement shown in above period',
  '2. Understanding of work',
  '3. Progress in learning this phase of work',
  '4. Work quality',
  '5. Interest shown in this period',
  '6. Attendance',
  '7. Punctuality',
  '8. Co-operation',
  '9. Safety Attitude',
  '10. Mechanical skills',
  'Point System',
  'Excellent 4.0',
  'Average Plus 3.0',
  'Average 2.0',
  'Average Minus 1.0',
  'Poor 0.0',
  'Overall average of above:',
  '(total #1 - #10 and divide by 10)',
  'C. Your overall comment on the apprentice:',
  'Signature Of Supervisor',
  'Signature Of Steward',
  'Signature Of Apprenticeship Chairman',
  'Signature Of Apprentice',
  'cc: Apprentice / Personnel / File',
  'FW: Apprentice Reviews',
  '(No subject)',
];

ReportForm formFrom(ReadForm r) => ReportForm()
  ..name = 'Apprentice review'
  ..questions = r.questions
  ..signatures = r.signatures;

void main() {
  test("Tanner's apprentice review is read into its questions", () {
    final r = FormReader.read(sample);
    final ratings = [
      'Improvement shown in above period',
      'Understanding of work',
      'Progress in learning this phase of work',
      'Work quality',
      'Interest shown in this period',
      'Attendance',
      'Punctuality',
      'Co-operation',
      'Safety Attitude',
      'Mechanical skills',
    ];
    expect(r.questions.map((q) => q.label).toList(), [
      'Date',
      'Name',
      'Employee #',
      'Employer',
      'Term',
      'Period covered from',
      'Period covered to',
      'Work done in the above period',
      ...ratings,
      'Overall average',
      'Your overall comment on the apprentice',
    ]);
    expect(r.questions.map((q) => q.kind).toList(), [
      'date', 'text', 'text', 'text', 'text', 'date', 'date',
      'long',
      ...List.filled(10, 'rating'),
      'average',
      'long',
    ]);
    expect(r.questions.map((q) => q.id).take(3), ['q1', 'q2', 'q3']);
    expect(r.questions[8].choices.map((c) => c.legend), [
      'Excellent 4.0',
      'Average Plus 3.0',
      'Average 2.0',
      'Average Minus 1.0',
      'Poor 0.0',
    ]);
    expect(r.signatures, [
      'Signature Of Supervisor',
      'Signature Of Steward',
      'Signature Of Apprenticeship Chairman',
      'Signature Of Apprentice',
    ]);
  });

  test('labels and fill-in lines on one line, and filled-in text that is not a question', () {
    expect(FormReader.read(['NAME: ____________ DATE: _______']).questions.map((q) => q.label), ['Name', 'Date']);
    expect(FormReader.read(['Employer ________']).questions.single.label, 'Employer');
    expect(FormReader.read(['cc: Apprentice / Personnel / File', 'FW: Reviews']).questions, isEmpty);
    expect(FormReader.read(['Was PPE worn?']).questions.single.label, 'Was PPE worn?');
    expect(FormReader.read(['Description of incident:']).questions.single.kind, 'long');
    expect(FormReader.read(['Signature of Employee ____ Signature of Supervisor ____']).signatures,
        ['Signature of Employee', 'Signature of Supervisor']);
    // Numbered items without a scale are plain questions.
    expect(FormReader.read(['1. What went well', '2. What to improve']).questions.map((q) => q.kind),
        ['text', 'text']);
  });

  test('auto-fill from Manage Crew and my profile, and the average', () {
    final form = formFrom(FormReader.read([...sample, 'Supervisor name:', 'Trade:', 'Union local:']))..id = 'f1';
    final p = Project()
      ..name = 'Warehouse Expansion'
      ..location = 'Building 2'
      ..crew = ['Ann Rigger']
      ..trades = {'Ann Rigger': 'Boilermaker'}
      ..levels = {'Ann Rigger': 'Apprentice'}
      ..unionLocals = {'Ann Rigger': 'Local 128'}
      ..employeeNumbers = {'Ann Rigger': 'E-4471'}
      ..terms = {'Ann Rigger': '3rd term'};
    final earlier = FilledReport()
      ..formId = 'f1'
      ..person = 'Ann Rigger'
      ..answers = {'q4': 'Acme Mechanical', 'q5': '2nd term'};
    final r = FilledReport()
      ..formId = 'f1'
      ..person = 'Ann Rigger';
    FormFill.autoFill(r, form, p,
        myName: 'Tanner Tibbetts',
        myPhone: '226-555-0123',
        myEmail: 'tanner@example.com',
        myPosition: 'Foreman',
        myLocation: 'Main plant',
        today: DateTime(2026, 11, 13),
        earlier: [earlier]);
    String a(String label) => r.answer(form.questions.firstWhere((q) => q.label == label).id);
    expect(a('Date'), 'Nov 13, 2026');
    expect(a('Name'), 'Ann Rigger');
    expect(a('Employee #'), 'E-4471');
    expect(a('Employer'), 'Acme Mechanical');
    // Term comes from Manage Crew (not the last report); with none set it stays blank.
    expect(a('Term'), '3rd term');
    final r2 = FilledReport()
      ..formId = 'f1'
      ..person = 'Ann Rigger';
    p.terms.clear();
    FormFill.autoFill(r2, form, p,
        myName: '', myPhone: '', myEmail: '', myPosition: '', myLocation: '', today: DateTime(2026), earlier: [earlier]);
    expect(r2.answer(form.questions.firstWhere((q) => q.label == 'Term').id), '');
    expect(Project.fromJson((Project()..terms = {'Al': '2nd term', 'Bo': 'nonsense'}).toJson()).terms, {'Al': '2nd term'});
    expect(a('Supervisor name'), 'Tanner Tibbetts');
    expect(a('Trade'), 'Boilermaker');
    expect(a('Union local'), 'Local 128');
    expect(a('Period covered from'), '');
    expect(FormFill.missing(r, form).first.label, 'Period covered from');

    final ratings = form.questions.where((q) => q.kind == ReportQuestion.rating).toList();
    expect(FormFill.average(r, form), '');
    r.answers[ratings[0].id] = '4.0';
    r.answers[ratings[1].id] = '3.0';
    r.answers[ratings[2].id] = '2.0';
    expect(FormFill.average(r, form), '3.0');
    expect(FormFill.shown(ratings[0], r, form), 'Excellent (4.0)');
  });

  test('typed Employee #, trade and union local are kept on the person when theirs is empty', () {
    final form = formFrom(FormReader.read(['Employee #:', 'Trade:', 'Union local:']));
    final p = Project()
      ..crew = ['Bo']
      ..trades = {'Bo': 'Millwright'};
    final r = FilledReport()
      ..person = 'Bo'
      ..answers = {'q1': '123', 'q2': 'Pipefitter', 'q3': 'Local 1687'};
    expect(FormFill.saveBack(r, form, p), isTrue);
    expect(p.employeeNumbers['Bo'], '123');
    expect(p.trades['Bo'], 'Millwright');
    expect(p.unionLocals['Bo'], 'Local 1687');
  });

  test('text from Word and Excel files', () {
    final docx = Archive()
      ..addFile(ArchiveFile.string(
          'word/document.xml',
          '<w:document xmlns:w="w"><w:body><w:p><w:r><w:t>NAME:</w:t></w:r><w:r><w:tab/><w:t>____</w:t></w:r></w:p>'
              '<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Trade</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>'));
    expect(FormReader.docxLines(ZipEncoder().encode(docx)), ['NAME:    ____', 'Trade']);
    final xlsx = Archive()
      ..addFile(ArchiveFile.string('xl/sharedStrings.xml', '<sst><si><t>Employee #:</t></si><si><t>Date:</t></si></sst>'))
      ..addFile(ArchiveFile.string(
          'xl/worksheets/sheet1.xml',
          '<worksheet><sheetData><row><c t="s"><v>0</v></c><c><v>5</v></c></row>'
              '<row><c t="inlineStr"><is><t>Term:</t></is></c><c t="s"><v>1</v></c></row></sheetData></worksheet>'));
    expect(FormReader.xlsxLines(ZipEncoder().encode(xlsx)), ['Employee #:', '5', 'Term:', 'Date:']);
  });

  test('saved and loaded', () {
    final f = formFrom(FormReader.read(sample))
      ..group = ReportForm.discipline
      ..files = ['doc-1.jpg'];
    final g = ReportForm.fromJson(jsonDecode(jsonEncode(f.toJson())) as Map<String, dynamic>);
    expect([g.group, g.name, g.files, g.signatures.length, g.questions.length], ['discipline', 'Apprentice review',
      ['doc-1.jpg'], 4, 20]);
    expect(g.questions[8].choices.first, const Choice('Excellent', '4.0'));
    final r = FilledReport()
      ..person = 'Ann'
      ..answers = {'q1': 'x'};
    expect(FilledReport.fromJson(jsonDecode(jsonEncode(r.toJson())) as Map<String, dynamic>).answers, {'q1': 'x'});
    final d = Doc.fromJson((Doc()
          ..title = 'Lift plan'
          ..file = 'doc-2.pdf'
          ..kind = Doc.kindOf('Lift Plan.PDF'))
        .toJson());
    expect([d.title, d.kind], ['Lift plan', 'pdf']);
    final p = Project.fromJson((Project()
          ..crew = ['Al']
          ..employeeNumbers = {'Al': '77'})
        .toJson());
    expect(p.employeeNumbers, {'Al': '77'});
    p.renamePerson('Al', 'Al Smith');
    expect(p.employeeNumbers, {'Al Smith': '77'});
  });

  test('pasting crew numbers: from Excel with headings, with | and without headings', () {
    const excel = 'Aecon PRNNR #\tBP #\tDISN #\t\tPosition\tName\n'
        '30016159\t509056\t410727\tTrainer\tMW\tCallum Clarke\n'
        '30027219\t501226\t371151\t\tBM A\tTyson Zimmer\n'
        '30059656\t624540\t573633\t\tMW A\tHope Ostachuck\n';
    expect(CrewNumbers.parse(excel).map((r) => '$r'), [
      'Callum Clarke/30016159/509056/410727',
      'Tyson Zimmer/30027219/501226/371151',
      'Hope Ostachuck/30059656/624540/573633',
    ]);
    expect(CrewNumbers.parse('Name | Employee # | BP # | DISN #\nDan Walker | 30041142 | 618780 | 539049').single.toString(),
        'Dan Walker/30041142/618780/539049');
    expect(CrewNumbers.parse('Callum Clarke, 30016159, 509056, 410727').single.toString(),
        'Callum Clarke/30016159/509056/410727');
    expect(CrewNumbers.parse('30016159  509056  410727  Trainer  MW  Callum Clarke').single.toString(),
        'Callum Clarke/30016159/509056/410727');

    final p = Project()..crew = ['callum clarke', 'Tyson  Zimmer', 'Sam Maint'];
    final result = CrewNumbers.apply(p, CrewNumbers.parse(excel));
    expect(result.updated, ['callum clarke', 'Tyson  Zimmer']);
    expect(result.missing.map((r) => r.name), ['Hope Ostachuck']);
    expect(p.numbersLine('callum clarke'), 'Employee # 30016159 · BP # 509056 · DISN # 410727');
    CrewNumbers.addMissing(p, result.missing);
    expect(p.crew.last, 'Hope Ostachuck');
    expect(p.disnNumbers['Hope Ostachuck'], '573633');
    final q = Project.fromJson(p.toJson());
    expect(q.bpNumbers['Tyson  Zimmer'], '501226');
    q.renamePerson('Tyson  Zimmer', 'Tyson Zimmer');
    expect(q.numbersLine('Tyson Zimmer'), 'Employee # 30027219 · BP # 501226 · DISN # 371151');

    // Forms fill them in.
    final form = formFrom(FormReader.read(['BP #:', 'DISN #:', 'Aecon PRNNR #:']));
    final r = FilledReport()..person = 'Tyson Zimmer';
    FormFill.autoFill(r, form, q,
        myName: '', myPhone: '', myEmail: '', myPosition: '', myLocation: '', today: DateTime(2026), earlier: []);
    expect([r.answer('q1'), r.answer('q2'), r.answer('q3')], ['501226', '371151', '30027219']);
  });

  test('where answers are written on the form, from the line boxes', () {
    FormLine l(String t, double x1, double y1, double x2, double y2) =>
        FormLine(t, left: x1, top: y1, right: x2, bottom: y2);
    final r = FormReader.readLines([
      l('NAME:', 0.107, 0.274, 0.165, 0.287),
      l('A. WORK DONE IN THE ABOVE PERIOD:', 0.107, 0.336, 0.497, 0.349),
      l('1. Improvement shown in above period', 0.143, 0.403, 0.487, 0.416),
      l('3. Progress in learning this phase of work', 0.143, 0.430, 0.506, 0.443),
      l('Excellent 4.0', 0.66, 0.42, 0.87, 0.43),
      l('Poor 0.0', 0.66, 0.47, 0.87, 0.48),
      l('Overall average of above:', 0.32, 0.539, 0.529, 0.548),
      l('(total #1 - #10 and divide by 10)', 0.30, 0.549, 0.554, 0.558),
      l('DATE: ______  TERM: ______', 0.5, 0.2, 0.9, 0.21),
    ]);
    Place p(String label) => r.questions.firstWhere((q) => q.label == label).place!;
    expect(p('Name').x, closeTo(0.177, 0.002));
    expect(p('Name').y, closeTo(0.287, 0.002));
    expect(p('Work done in the above period').x, closeTo(0.107, 0.002));
    expect(p('Work done in the above period').y, closeTo(0.349 + 1.1 * 0.013, 0.002));
    expect(p('Improvement shown in above period').x, closeTo(0.566, 0.002));
    expect(p('Progress in learning this phase of work').y, closeTo(0.443, 0.002));
    // The average is after the "(total ...)" words, below the average line.
    expect(p('Overall average').x, closeTo(0.569, 0.002));
    expect(p('Overall average').y, closeTo(0.548 + 1.2 * 0.009, 0.002));
    // Two labels on one line: each answer goes after its own label.
    expect(p('Date').x, lessThan(p('Term').x));
    expect(r.textSize, closeTo(0.013 * 1.15, 0.0005)); // about the size of the printed words
    // No boxes (Word): no places.
    expect(FormReader.read(['NAME:']).questions.single.place, isNull);
  });

  test('ratings are saved as the word; places, sizes and the report copy are kept', () {
    final f = formFrom(FormReader.read(sample))
      ..id = 'f9'
      ..fontScale = 1.3
      ..textSize = 0.011;
    f.questions.first.place = const Place(0, 0.2, 0.3);
    final rating = f.questions.firstWhere((q) => q.kind == ReportQuestion.rating);
    final r = FilledReport()
      ..formId = 'f9'
      ..form = f.copy()
      ..answers = {rating.id: 'Excellent'};
    expect(FormFill.shown(rating, r, f), 'Excellent (4.0)');
    expect(FormFill.onForm(rating, r, f), '4.0');
    expect(FormFill.average(r, f), '4.0');
    final back = FilledReport.fromJson(jsonDecode(jsonEncode(r.toJson())) as Map<String, dynamic>);
    expect(back.form!.fontScale, 1.3);
    expect(back.form!.textSize, 0.011);
    expect(back.form!.questions.first.place.toString(), 'p0(0.200,0.300)');
    // "Work location" isn't a union local; "Term" carries over but "determine" doesn't.
    expect(FormFill.ruleFor(ReportQuestion('a', 'Work location', 'text')), 'location');
    expect(FormFill.ruleFor(ReportQuestion('a', 'Determine next steps', 'text')), isNull);
  });

  test('fill-in lines are found in a page picture and answers fit them', () {
    // A white 1000 x 1400 page with: a line right of "NAME:", a heading's underline, and 3 ruled lines.
    final page = img.Image(width: 1000, height: 1400);
    img.fill(page, color: img.ColorRgb8(250, 250, 250));
    void line(int x0, int x1, int y) =>
        img.fillRect(page, x1: x0, y1: y, x2: x1, y2: y + 1, color: img.ColorRgb8(20, 20, 20));
    line(220, 550, 300); // NAME blank
    line(600, 700, 420); // underline under the heading words
    for (final y in [520, 560, 600]) {
      line(150, 900, y); // comment lines
    }
    // Some "text": short dark blocks (letters) that must not count as lines.
    for (var x = 100; x < 400; x += 14) {
      img.fillRect(page, x1: x, y1: 700, x2: x + 9, y2: 716, color: img.ColorRgb8(20, 20, 20));
    }
    final segs = FormLines.find(Uint8List.fromList(img.encodePng(page)), 0);
    expect(segs.length, 5);
    expect(segs.first.x0, closeTo(0.22, 0.005));
    expect(segs.first.x1, closeTo(0.551, 0.005));
    expect(segs.first.y * 1400, closeTo(300.5, 1.5));

    FormLine l(String t, double x0, double y0, double x1, double y1) =>
        FormLine(t, left: x0 / 1000, top: y0 / 1400, right: x1 / 1000, bottom: y1 / 1400);
    final r = FormReader.readLines([
      l('NAME:', 100, 280, 160, 298),
      l('Point System', 600, 400, 700, 418),
      l('C. Your overall comment on the apprentice:', 100, 480, 480, 498),
    ], segments: segs);
    final name = r.questions.firstWhere((q) => q.label == 'Name').place!;
    expect(name.x, closeTo(0.226, 0.002)); // where the line starts, not beside the label
    expect(name.w, closeTo(0.551 - 0.22 - 0.012, 0.004)); // the line's length
    final comment = r.questions.firstWhere((q) => q.kind == ReportQuestion.long).place!;
    expect(comment.x, closeTo(0.156, 0.002));
    expect(comment.rows.length, 2); // carries on to the next two ruled lines
    expect(comment.y, closeTo(520.5 / 1400 - 0.0045, 0.002));

    // Fitting: 10 units per character at size 1.
    double at1(String t) => t.length * 10.0;
    expect(Fit.oneLine('Tyson Zimmer', 2000, 1, at1), 1); // fits
    expect(Fit.oneLine('Aecon Industrial Western', 120, 1, at1), 0.5); // shrinks, not below half
    final (size, lines) = Fit.rows('one two three four five six seven eight nine ten', 200, 2, 1, at1);
    expect(lines.length, lessThanOrEqualTo(3));
    expect(size, lessThan(1));
    expect(Fit.wrap('a b c', 30, (t) => t.length * 10.0), ['a b', 'c']);
    // Moving keeps the line length and moves the following lines too.
    final moved = comment.moved(0.01, 0.02);
    expect(moved.w, comment.w);
    expect(moved.rows.first, closeTo(comment.rows.first + 0.02, 1e-9));
    expect(Place.fromJson(comment.toJson())!.rows.length, 2);
  });

  test('safeguards: signature lines stay empty, answers stop before printed words, ruled lines belong together', () {
    FormLine l(String t, double x0, double y0, double x1, double y1) =>
        FormLine(t, left: x0, top: y0, right: x1, bottom: y1);
    const seg = Segment.new;
    final r = FormReader.readLines([
      l('Employee name:', 0.10, 0.100, 0.25, 0.110),
      l('Date:', 0.60, 0.100, 0.66, 0.110), // a second label on the same line as the name's blank
      l('Details of the incident:', 0.10, 0.200, 0.40, 0.210),
      l('Supervisor signature', 0.15, 0.300, 0.40, 0.310), // under the line at 0.296
      l('Position:', 0.10, 0.400, 0.20, 0.410),
    ], segments: [
      seg(0, 0.26, 0.90, 0.111), // runs under "Date:" too
      seg(0, 0.10, 0.90, 0.225),
      seg(0, 0.10, 0.90, 0.240),
      seg(0, 0.10, 0.90, 0.255),
      seg(0, 0.10, 0.45, 0.296), // the signature line: not for the incident's answer
      seg(0, 0.21, 0.60, 0.411),
    ]);
    Place p(String label) => r.questions.firstWhere((q) => q.label == label).place!;
    // The name stops before "Date:".
    expect(p('Employee name').x, closeTo(0.266, 0.001));
    expect(p('Employee name').x + p('Employee name').w, lessThan(0.60));
    // The incident uses its 3 ruled lines, not the signature line.
    expect(p('Details of the incident').rows.length, 2);
    expect(p('Details of the incident').rowFit, closeTo((0.015) * 0.66, 0.0005));
    // A job title is filled from Manage Crew.
    final form = formFrom(r);
    final proj = Project()
      ..crew = ['Al']
      ..trades = {'Al': 'Millwright'}
      ..levels = {'Al': 'Apprentice'};
    final rep = FilledReport()..person = 'Al';
    FormFill.autoFill(rep, form, proj,
        myName: 'Me', myPhone: '', myEmail: '', myPosition: '', myLocation: '', today: DateTime(2026), earlier: []);
    expect(rep.answer(form.questions.firstWhere((q) => q.label == 'Position').id), 'Millwright Apprentice');
    expect(rep.answer(form.questions.firstWhere((q) => q.label == 'Employee name').id), 'Al');
  });

  test('item numbers and points read as their own column are joined back to their words', () {
    FormLine l(String t, double x0, double y0, double x1, double y1) =>
        FormLine(t, left: x0, top: y0, right: x1, bottom: y1);
    final r = FormReader.readLines([
      // The numbers column, then the words column, as the phone may read them.
      l('1.', 0.14, 0.400, 0.16, 0.410), l('2.', 0.14, 0.414, 0.16, 0.424), l('10.', 0.13, 0.428, 0.16, 0.438),
      l('Improvement shown in above period', 0.19, 0.400, 0.49, 0.410),
      l('Understanding of work', 0.19, 0.414, 0.37, 0.424),
      l('Mechanical skills', 0.19, 0.428, 0.32, 0.438),
      // The point system: words and points apart.
      l('Excellent', 0.66, 0.40, 0.74, 0.41), l('4.0', 0.85, 0.40, 0.88, 0.41),
      l('Average', 0.66, 0.414, 0.73, 0.424), l('2.0', 0.85, 0.414, 0.88, 0.424),
      l('Poor', 0.66, 0.428, 0.70, 0.438), l('0.0', 0.85, 0.428, 0.88, 0.438),
      l('Overall average of above:', 0.32, 0.45, 0.53, 0.46),
    ]);
    expect(r.questions.map((q) => '${q.label}/${q.kind}'), [
      'Improvement shown in above period/rating',
      'Understanding of work/rating',
      'Mechanical skills/rating',
      'Overall average/average',
    ]);
    expect(r.questions.first.choices.map((c) => c.legend), ['Excellent 4.0', 'Average 2.0', 'Poor 0.0']);
  });

  test('dates: the period is one pick (from and to), dates read back, only a plain Date is today', () {
    final f = formFrom(FormReader.read(['DATE:', 'PERIOD COVERED FROM:', 'TO:', 'Date of incident:', 'Update:']));
    expect(f.questions.map((q) => '${q.label}/${q.kind}'),
        ['Date/date', 'Period covered from/date', 'Period covered to/date', 'Date of incident/date', 'Update/text']);
    expect(FormFill.rangeEnd(f, f.questions[1])!.label, 'Period covered to');
    expect(FormFill.rangeEnd(f, f.questions[0]), isNull);
    expect(FormFill.parseDate('Nov 3, 2026'), DateTime(2026, 11, 3));
    expect(FormFill.parseDate('soon'), isNull);
    final r = FilledReport();
    FormFill.autoFill(r, f, Project(),
        myName: '', myPhone: '', myEmail: '', myPosition: '', myLocation: '', today: DateTime(2026, 11, 13), earlier: []);
    expect(r.answers, {'q1': 'Nov 13, 2026'});
    expect(FormFill.missing(r, f).map((q) => q.label), ['Period covered from', 'Period covered to', 'Date of incident', 'Update']);
  });

  test('forms saved before dates had a calendar get one', () {
    final q = ReportQuestion.fromJson({'id': 'q1', 'label': 'Period covered from', 'kind': 'text'});
    expect(q.kind, 'date');
    expect(ReportQuestion.fromJson({'id': 'q2', 'label': 'Name', 'kind': 'text'}).kind, 'text');
    expect(ReportQuestion.fromJson({'id': 'q3', 'label': 'Date', 'kind': 'long'}).kind, 'long');
  });

  test('"To" printed in another column is joined to its "Period covered from"', () {
    // The phone read the right-hand column (Date, Employee #, Term, To) apart from the left one.
    final f = formFrom(FormReader.read(
        ['DATE:', 'EMPLOYEE #', 'TERM:', 'TO:', 'NAME:', 'EMPLOYER', 'PERIOD COVERED FROM:', 'A. WORK DONE IN THE ABOVE PERIOD:']));
    expect(f.questions.map((q) => '${q.label}/${q.kind}'), [
      'Date/date', 'Employee #/text', 'Term/text', 'Name/text', 'Employer/text',
      'Period covered from/date', 'Period covered to/date', 'Work done in the above period/long',
    ]);
    expect(FormFill.rangeEnd(f, f.questions[5])!.label, 'Period covered to');
    // A form saved that way before this fix is joined up when it's opened.
    final saved = ReportForm.fromJson({
      'name': 'Apprentice review',
      'questions': [
        {'id': 'q1', 'label': 'To', 'kind': 'text'},
        {'id': 'q2', 'label': 'Name', 'kind': 'text'},
        {'id': 'q3', 'label': 'Period covered from', 'kind': 'text'},
      ],
    });
    expect(saved.questions.map((q) => '${q.id}:${q.label}/${q.kind}'),
        ['q2:Name/text', 'q3:Period covered from/date', 'q1:Period covered to/date']);
    expect(FormFill.rangeEnd(saved, saved.questions[1])!.id, 'q1');
  });

  test('crew names that are close still get their numbers', () {
    final crew = ['Daniel Walker', 'Colby Davey', 'Tyson Zimmer', 'Sam Maint', 'Sam Mainte'];
    expect(CrewNumbers.match(crew, 'Dan Walker'), 'Daniel Walker');
    expect(CrewNumbers.match(crew, 'Colbe Davey'), 'Colby Davey');
    expect(CrewNumbers.match(crew, 'TYSON  zimmer'), 'Tyson Zimmer');
    expect(CrewNumbers.match(crew, 'Sam Maintt'), isNull); // two could fit
    expect(CrewNumbers.match(crew, 'Hope Ostachuck'), isNull);
    final p = Project()..crew = List.of(crew);
    final r = CrewNumbers.apply(p, CrewNumbers.parse('Dan Walker | 1 | 2 | 3\nColbe Davey | 4 | 5 | 6'));
    expect(r.updated, ['Daniel Walker', 'Colby Davey']);
    expect(p.bpNumbers['Colby Davey'], '5');
  });
}
