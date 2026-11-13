import 'dart:convert';

import 'package:archive/archive.dart';
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
      ...List.filled(7, 'text'),
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
      ..employeeNumbers = {'Ann Rigger': 'E-4471'};
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
    expect(a('Term'), '2nd term');
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
    expect(r.textSize, closeTo(0.0098, 0.0005));
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
}

