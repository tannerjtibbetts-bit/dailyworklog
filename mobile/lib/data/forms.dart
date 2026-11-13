// Documents on the Links tab, and report forms (Apprentice reports, Disciplinary action): reading a form's
// text into questions, filling it in for a crew member, and working out the average of the ratings.
// The same rules as the classic app's FormReader.java / FormFill.java.
import 'dart:convert';

import 'package:archive/archive.dart';
import 'package:xml/xml.dart';

import 'models.dart';

/// A file saved on the Links tab (a photo, PDF, Word, Excel or other file).
class Doc {
  static const photo = 'photo', pdf = 'pdf', docx = 'docx', xlsx = 'xlsx', other = 'other';

  String id = newId();
  String title = '';
  String file = '';
  String kind = other;
  int addedAt = nowMs();

  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'file': file, 'kind': kind, 'addedAt': addedAt};

  static Doc fromJson(Map<String, dynamic> o) => Doc()
    ..id = (o['id'] ?? newId()).toString()
    ..title = (o['title'] ?? '').toString()
    ..file = (o['file'] ?? '').toString()
    ..kind = (o['kind'] ?? other).toString()
    ..addedAt = o['addedAt'] is int ? o['addedAt'] as int : 0;

  /// photo, pdf, docx, xlsx or other, from a file name.
  static String kindOf(String fileName) {
    final n = fileName.toLowerCase();
    final ext = n.contains('.') ? n.substring(n.lastIndexOf('.') + 1) : '';
    if (['jpg', 'jpeg', 'png', 'heic', 'heif', 'webp'].contains(ext)) return photo;
    if (ext == 'pdf') return pdf;
    if (ext == 'docx') return docx;
    if (ext == 'xlsx') return xlsx;
    return other;
  }

  static String kindLabel(String kind) => switch (kind) {
        photo => 'Photo',
        pdf => 'PDF',
        docx => 'Word',
        xlsx => 'Excel',
        _ => 'File',
      };
}

class Choice {
  final String label, value;

  const Choice(this.label, this.value);

  /// "Excellent (4.0)", or just the label when the scale had no numbers.
  String get display => value.isEmpty ? label : '$label ($value)';

  /// "Excellent 4.0" for the point system line.
  String get legend => value.isEmpty ? label : '$label $value';

  Map<String, dynamic> toJson() => {'label': label, 'value': value};

  @override
  bool operator ==(Object other) => other is Choice && other.label == label && other.value == value;

  @override
  int get hashCode => Object.hash(label, value);

  @override
  String toString() => legend;
}

/// One question on a report form.
class ReportQuestion {
  static const text = 'text', long = 'long', rating = 'rating', average = 'average';

  String id;
  String label;
  String kind;
  List<Choice> choices;

  ReportQuestion(this.id, this.label, this.kind, [List<Choice>? choices]) : choices = choices ?? [];

  Map<String, dynamic> toJson() => {
        'id': id,
        'label': label,
        'kind': kind,
        'choices': [for (final c in choices) c.toJson()],
      };

  static ReportQuestion fromJson(Map<String, dynamic> o) => ReportQuestion(
        (o['id'] ?? '').toString(),
        (o['label'] ?? '').toString(),
        const [text, long, rating, average].contains(o['kind']) ? o['kind'] as String : text,
        [
          if (o['choices'] is List)
            for (final c in o['choices'] as List)
              if (c is Map) Choice((c['label'] ?? '').toString(), (c['value'] ?? '').toString()),
        ],
      );

  /// "Short answer", "Rating (Excellent 4.0 … Poor 0.0)" and so on, for the Check the questions screen.
  String kindText() => switch (kind) {
        long => 'Long answer',
        rating => choices.isEmpty
            ? 'Rating'
            : 'Rating (${choices.first.legend}${choices.length > 1 ? ' … ${choices.last.legend}' : ''})',
        average => 'Worked out: average of the ratings',
        _ => 'Short answer',
      };
}

/// A blank form (photos or a file) and its questions, for Apprentice reports or Disciplinary action.
class ReportForm {
  static const apprentice = 'apprentice', discipline = 'discipline';

  String id = newId();
  String group = apprentice;
  String name = '';
  List<String> files = [];
  String kind = Doc.photo;
  List<ReportQuestion> questions = [];
  List<String> signatures = [];
  int createdAt = nowMs();

  Map<String, dynamic> toJson() => {
        'id': id,
        'group': group,
        'name': name,
        'files': files,
        'kind': kind,
        'questions': [for (final q in questions) q.toJson()],
        'signatures': signatures,
        'createdAt': createdAt,
      };

  static ReportForm fromJson(Map<String, dynamic> o) => ReportForm()
    ..id = (o['id'] ?? newId()).toString()
    ..group = o['group'] == discipline ? discipline : apprentice
    ..name = (o['name'] ?? '').toString()
    ..files = [if (o['files'] is List) for (final f in o['files'] as List) f.toString()]
    ..kind = (o['kind'] ?? Doc.photo).toString()
    ..questions = [
      if (o['questions'] is List)
        for (final q in o['questions'] as List)
          if (q is Map) ReportQuestion.fromJson(Map<String, dynamic>.from(q)),
    ]
    ..signatures = [if (o['signatures'] is List) for (final s in o['signatures'] as List) s.toString()]
    ..createdAt = o['createdAt'] is int ? o['createdAt'] as int : 0;

  /// The next free question id ("q7").
  String newQuestionId() {
    var n = questions.length + 1;
    while (questions.any((q) => q.id == 'q$n')) {
      n++;
    }
    return 'q$n';
  }

  /// The rating scale of the form (from its first rating question), for the PDF's point system line.
  List<Choice> scale() => questions.firstWhere((q) => q.kind == ReportQuestion.rating && q.choices.isNotEmpty,
      orElse: () => ReportQuestion('', '', ReportQuestion.text)).choices;

  static List<String> defaultSignatures(String group) => group == discipline
      ? ['Supervisor signature', 'Employee signature', 'Witness signature']
      : ['Supervisor signature', 'Apprentice signature'];

  static String groupTitle(String group) => group == discipline ? 'Disciplinary action' : 'Apprentice reports';

  /// "Apprentice" or "Employee", for the PDF.
  static String personWord(String group) => group == discipline ? 'Employee' : 'Apprentice';
}

/// A form filled in for one person.
class FilledReport {
  String id = newId();
  String formId = '';
  String formName = '';
  String group = ReportForm.apprentice;
  String person = '';
  String project = '';
  Map<String, String> answers = {};
  int createdAt = nowMs();
  late int updatedAt = createdAt;

  String answer(String questionId) => (answers[questionId] ?? '').trim();

  Map<String, dynamic> toJson() => {
        'id': id,
        'formId': formId,
        'formName': formName,
        'group': group,
        'person': person,
        'project': project,
        'answers': answers,
        'createdAt': createdAt,
        'updatedAt': updatedAt,
      };

  static FilledReport fromJson(Map<String, dynamic> o) {
    final r = FilledReport()
      ..id = (o['id'] ?? newId()).toString()
      ..formId = (o['formId'] ?? '').toString()
      ..formName = (o['formName'] ?? '').toString()
      ..group = o['group'] == ReportForm.discipline ? ReportForm.discipline : ReportForm.apprentice
      ..person = (o['person'] ?? '').toString()
      ..project = (o['project'] ?? '').toString()
      ..answers = {
        if (o['answers'] is Map)
          for (final e in (o['answers'] as Map).entries) e.key.toString(): (e.value ?? '').toString(),
      }
      ..createdAt = o['createdAt'] is int ? o['createdAt'] as int : 0;
    r.updatedAt = o['updatedAt'] is int ? o['updatedAt'] as int : r.createdAt;
    return r;
  }
}

/// What a form's text was read into.
class ReadForm {
  final List<ReportQuestion> questions;
  final List<String> signatures;

  ReadForm(this.questions, this.signatures);
}

class FormReader {
  FormReader._();

  /// Stands for a fill-in line (____, ...., …) in the text.
  static const blank = '␣';

  static final _blanks = RegExp(r'_{3,}|\.{3,}|…+|-{3,}');
  static final _scale = RegExp(
    r'^(excellent|very good|good|above average|average plus|average|average minus|below average|fair|satisfactory'
    r'|needs improvement|unsatisfactory|poor)\s*[:=-]?\s*(\d+(?:\.\d+)?)?$',
    caseSensitive: false,
  );
  static final _scaleHeading = RegExp(r'^(point system|rating scale|scale|rating|ratings)\s*:?$', caseSensitive: false);
  static final _section = RegExp(r'^[A-H][.)]\s+');
  static final _numbered = RegExp(r'^(\d{1,2})[.)]\s+(.+)$');
  static final _longWords = RegExp(
    r'comment|work done|describe|description|details|explain|remarks|notes|incident|reason|corrective action'
    r'|action plan|improvement plan|what happened',
    caseSensitive: false,
  );

  /// The line with fill-in lines turned into [blank] and spaces collapsed.
  static String normalize(String line) =>
      line.replaceAll(_blanks, ' $blank ').replaceAll(RegExp(r'\s+'), ' ').trim();

  static ReadForm read(List<String> rawLines) {
    final found = <_Found>[];
    final choices = <Choice>[];
    final signatures = <String>[];
    var hasAverage = false;

    void add(String label, String kind) {
      final l = cleanLabel(label);
      if (l.isEmpty) return;
      if (found.any((f) => f.label.toLowerCase() == l.toLowerCase())) return;
      // "To" right after "... from" is the end of the same period.
      if (l.toLowerCase() == 'to' && found.isNotEmpty && found.last.label.toLowerCase().endsWith(' from')) {
        final prev = found.last.label;
        found.add(_Found('${prev.substring(0, prev.length - 5)} to', kind));
        return;
      }
      found.add(_Found(l, kind));
    }

    for (final raw in rawLines) {
      for (final part in raw.split('\n')) {
        var line = normalize(part);
        if (RegExp('[A-Za-z]').allMatches(line).length < 2) continue;
        if (line.startsWith('(') && line.endsWith(')')) continue;
        final noBlanks = line.replaceAll(blank, ' ').replaceAll(RegExp(r'\s+'), ' ').trim();

        // 1. signature lines
        if (noBlanks.toLowerCase().contains('signature')) {
          for (final s in noBlanks.split(RegExp(r'(?=\bsignature)', caseSensitive: false))) {
            final t = s.trim();
            if (t.isNotEmpty && !signatures.any((x) => x.toLowerCase() == t.toLowerCase())) signatures.add(t);
          }
          continue;
        }
        // 2. the rating scale
        final sc = _scale.firstMatch(noBlanks);
        if (sc != null) {
          final label = noBlanks.substring(0, sc.group(1)!.length).trim();
          if (!choices.any((c) => c.label.toLowerCase() == label.toLowerCase())) {
            choices.add(Choice(label, sc.group(2) ?? ''));
          }
          continue;
        }
        if (_scaleHeading.hasMatch(noBlanks)) continue;
        // 3. section letter: a longer answer
        var section = false;
        final sec = _section.firstMatch(line);
        if (sec != null) {
          section = true;
          line = line.substring(sec.end).trim();
        }
        // 4. numbered items
        final num = _numbered.firstMatch(line);
        if (num != null) {
          add(num.group(2)!, 'numbered');
          continue;
        }
        // 5. the overall average
        final lower = noBlanks.toLowerCase();
        if (lower.contains('average') &&
            (lower.contains('of above') || lower.contains('total') || noBlanks.endsWith(':'))) {
          if (!hasAverage) {
            hasAverage = true;
            found.add(_Found('Overall average', ReportQuestion.average));
          }
          continue;
        }
        // 6. labels with colons or fill-in lines
        final before = found.length;
        final segments = line.split(blank);
        for (var i = 0; i < segments.length; i++) {
          final s = segments[i].trim();
          if (s.isEmpty) continue;
          final followedByBlank = i < segments.length - 1;
          if (s.contains(':')) {
            final chunks = RegExp(r'([^:]+):').allMatches(s).toList();
            for (var c = 0; c < chunks.length; c++) {
              final label = chunks[c].group(1)!;
              final afterEnd = c + 1 < chunks.length ? chunks[c + 1].start : s.length;
              final after = s.substring(chunks[c].end, afterEnd).trim();
              if (after.isEmpty) add(label, section ? ReportQuestion.long : '');
            }
          } else if (followedByBlank) {
            add(s, section ? ReportQuestion.long : '');
          }
        }
        if (found.length > before || line.contains(':') || line.contains(blank)) continue;
        // 7. an ALL-CAPS label on its own
        if (noBlanks.length >= 2 &&
            noBlanks.length <= 30 &&
            noBlanks.split(' ').length <= 4 &&
            noBlanks == noBlanks.toUpperCase() &&
            RegExp('[A-Z]').hasMatch(noBlanks)) {
          add(noBlanks, section ? ReportQuestion.long : '');
          continue;
        }
        // 8. a question
        if (noBlanks.endsWith('?') && noBlanks.length <= 120) add(noBlanks, section ? ReportQuestion.long : '');
      }
    }

    final questions = <ReportQuestion>[];
    for (final f in found) {
      var kind = f.kind;
      if (kind == 'numbered') {
        kind = choices.length >= 2 ? ReportQuestion.rating : ReportQuestion.text;
      } else if (kind != ReportQuestion.average && kind != ReportQuestion.long) {
        kind = ReportQuestion.text;
      }
      if (kind == ReportQuestion.text && _longWords.hasMatch(f.label)) kind = ReportQuestion.long;
      questions.add(ReportQuestion('q${questions.length + 1}', f.label, kind,
          kind == ReportQuestion.rating ? List.of(choices) : null));
    }
    return ReadForm(questions, signatures);
  }

  /// Trimmed, without trailing ":" or blanks; ALL CAPS becomes sentence case ("Employee #").
  static String cleanLabel(String s) {
    var t = s.replaceAll(blank, ' ').replaceAll(RegExp(r'\s+'), ' ').trim();
    t = t.replaceAll(RegExp(r'[:\s]+$'), '').trim();
    if (t.isEmpty) return '';
    if (!RegExp('[a-z]').hasMatch(t)) t = t[0].toUpperCase() + t.substring(1).toLowerCase();
    return t;
  }

  // ----- text from Word and Excel files -----

  /// Each paragraph of a .docx (table cells too) as a line.
  static List<String> docxLines(List<int> bytes) {
    final zip = ZipDecoder().decodeBytes(bytes);
    final f = zip.findFile('word/document.xml');
    if (f == null) return [];
    final doc = XmlDocument.parse(utf8.decode(f.content as List<int>, allowMalformed: true));
    final out = <String>[];
    for (final p in doc.findAllElements('w:p')) {
      final b = StringBuffer();
      for (final n in p.descendants.whereType<XmlElement>()) {
        switch (n.name.qualified) {
          case 'w:t':
            b.write(n.innerText);
          case 'w:tab':
            b.write('    ');
          case 'w:br':
            b.write('\n');
        }
      }
      out.add(b.toString());
    }
    return out;
  }

  /// Each non-empty cell of every sheet of an .xlsx, row by row, as a line.
  static List<String> xlsxLines(List<int> bytes) {
    final zip = ZipDecoder().decodeBytes(bytes);
    final shared = <String>[];
    final ss = zip.findFile('xl/sharedStrings.xml');
    if (ss != null) {
      final x = XmlDocument.parse(utf8.decode(ss.content as List<int>, allowMalformed: true));
      for (final si in x.findAllElements('si')) {
        shared.add(si.findAllElements('t').map((t) => t.innerText).join());
      }
    }
    final sheets = zip.files.where((f) => RegExp(r'^xl/worksheets/sheet\d+\.xml$').hasMatch(f.name)).toList()
      ..sort((a, b) => a.name.compareTo(b.name));
    final out = <String>[];
    for (final s in sheets) {
      final x = XmlDocument.parse(utf8.decode(s.content as List<int>, allowMalformed: true));
      for (final c in x.findAllElements('c')) {
        final type = c.getAttribute('t');
        String v;
        if (type == 's') {
          final i = int.tryParse(c.getElement('v')?.innerText ?? '');
          v = i != null && i >= 0 && i < shared.length ? shared[i] : '';
        } else if (type == 'inlineStr') {
          v = c.findAllElements('t').map((t) => t.innerText).join();
        } else {
          v = c.getElement('v')?.innerText ?? '';
        }
        if (v.trim().isNotEmpty) out.add(v);
      }
    }
    return out;
  }
}

class _Found {
  final String label, kind;

  _Found(this.label, this.kind);
}

/// One row of a pasted crew list.
class CrewNumberRow {
  final String name, employeeNo, bp, disn;

  const CrewNumberRow(this.name, this.employeeNo, this.bp, this.disn);

  @override
  String toString() => '$name/$employeeNo/$bp/$disn';
}

class CrewNumbersResult {
  final List<String> updated;
  final List<CrewNumberRow> missing;

  CrewNumbersResult(this.updated, this.missing);
}

/// "Paste crew numbers" in Manage Crew: employee, BP and DISN numbers from a pasted list.
class CrewNumbers {
  CrewNumbers._();

  static List<String> cells(String line) {
    final List<String> parts;
    if (line.contains('\t')) {
      parts = line.split('\t');
    } else if (line.contains('|')) {
      parts = line.split('|');
    } else if (line.contains(',')) {
      parts = line.split(',');
    } else {
      parts = line.trim().split(RegExp(r'\s{2,}'));
    }
    return [for (final p in parts) p.trim()];
  }

  static bool _isNumber(String s) => RegExp(r'^[0-9][0-9-]*$').hasMatch(s);
  static bool _isWords(String s) => RegExp('[A-Za-z].*[A-Za-z]').hasMatch(s) && !RegExp('[0-9]').hasMatch(s);

  static List<CrewNumberRow> parse(String text) {
    final out = <CrewNumberRow>[];
    int? nameCol, empCol, bpCol, disnCol;
    for (final raw in text.split('\n')) {
      if (raw.trim().isEmpty) continue;
      final c = cells(raw);
      final lower = [for (final x in c) x.toLowerCase()];
      if (nameCol == null &&
          lower.any((x) => x.contains('name')) &&
          lower.any((x) => x.contains('prnnr') || x.contains('employee') || x.startsWith('emp') ||
              x.startsWith('bp') || x.contains('disn'))) {
        int? find(bool Function(String) test) {
          for (var i = 0; i < lower.length; i++) {
            if (test(lower[i])) return i;
          }
          return null;
        }

        nameCol = find((x) => x.contains('name'));
        empCol = find((x) => x.contains('prnnr') || x.contains('employee') || x.startsWith('emp'));
        bpCol = find((x) => x.startsWith('bp'));
        disnCol = find((x) => x.contains('disn'));
        continue;
      }
      String at(int? i) => i != null && i < c.length ? c[i] : '';
      if (nameCol != null) {
        final name = at(nameCol);
        if (name.isNotEmpty) out.add(CrewNumberRow(name, at(empCol), at(bpCol), at(disnCol)));
        continue;
      }
      final words = c.where(_isWords).toList();
      final name = words.firstWhere((w) => w.contains(' '), orElse: () => words.isEmpty ? '' : words.first);
      if (name.isEmpty) continue;
      final nums = c.where(_isNumber).toList();
      String n(int i) => i < nums.length ? nums[i] : '';
      out.add(CrewNumberRow(name, n(0), n(1), n(2)));
    }
    return out;
  }

  static String key(String name) =>
      name.toLowerCase().replaceAll(RegExp(r"[.'\-]"), '').replaceAll(RegExp(r'\s+'), ' ').trim();

  static CrewNumbersResult apply(Project p, List<CrewNumberRow> rows) {
    final updated = <String>[];
    final missing = <CrewNumberRow>[];
    for (final r in rows) {
      final who = p.crew.where((n) => key(n) == key(r.name)).firstOrNull;
      if (who == null) {
        missing.add(r);
        continue;
      }
      _set(p, who, r);
      if (!updated.contains(who)) updated.add(who);
    }
    return CrewNumbersResult(updated, missing);
  }

  /// Adds the people who weren't on the crew (first group, no phone) with their numbers.
  static void addMissing(Project p, List<CrewNumberRow> rows) {
    for (final r in rows) {
      final name = r.name.trim();
      if (name.isEmpty || p.crew.any((n) => key(n) == key(name))) continue;
      p.crew.add(name);
      _set(p, name, r);
    }
  }

  static void _set(Project p, String who, CrewNumberRow r) {
    if (r.employeeNo.isNotEmpty) p.employeeNumbers[who] = r.employeeNo;
    if (r.bp.isNotEmpty) p.bpNumbers[who] = r.bp;
    if (r.disn.isNotEmpty) p.disnNumbers[who] = r.disn;
  }
}

/// Filling a form in for one person.
class FormFill {
  FormFill._();

  static const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  /// "Nov 13, 2026".
  static String date(DateTime d) => '${_months[d.month - 1]} ${d.day}, ${d.year}';

  static final _me = RegExp(
      r'supervisor|foreman|evaluator|evaluated by|reviewed by|completed by|issued by|manager|observer|prepared by');
  static const _dates = [
    'date',
    "today's date",
    'todays date',
    'date of report',
    'report date',
    'review date',
    'evaluation date',
    'date completed',
    'date issued',
  ];
  static final _employeeNo =
      RegExp(r'employee\s*(#|no\.?|number|id)|emp\.?\s*#|badge|payroll|clock\s*(#|number)|prnnr');
  static final _bp = RegExp(r'\bbp\b');
  static const _names = ['name', 'full name', 'employee', 'apprentice'];
  static const _nameParts = [
    'employee name',
    'apprentice name',
    "apprentice's name",
    'worker name',
    "worker's name",
    'name of employee',
    'name of apprentice',
    'name of worker',
  ];
  static final _carry = RegExp(r'employer|company|term|department|shift');

  /// Which detail a question asks for: 'me.name', 'me.phone', 'me.email', 'me.position', 'date', 'employeeNo',
  /// 'name', 'trade', 'local', 'level', 'phone', 'project', 'location', 'group', 'carry', or null.
  static String? ruleFor(ReportQuestion q) {
    if (q.kind != ReportQuestion.text && q.kind != ReportQuestion.long) return null;
    final l = q.label.toLowerCase().trim();
    if (_me.hasMatch(l)) {
      if (l.contains('phone') || l.contains('cell')) return 'me.phone';
      if (l.contains('email')) return 'me.email';
      if (l.contains('position') || l.contains('title')) return 'me.position';
      return 'me.name';
    }
    if (_dates.contains(l)) return 'date';
    if (_employeeNo.hasMatch(l)) return 'employeeNo';
    if (_bp.hasMatch(l)) return 'bp';
    if (l.contains('disn')) return 'disn';
    if (_names.contains(l) || _nameParts.any(l.contains)) return 'name';
    if (l.contains('trade') || l.contains('craft')) return 'trade';
    if (l.contains('union') || l.contains('local')) return 'local';
    if (l.contains('level') || l.contains('classification')) return 'level';
    if (l.contains('phone') || l.contains('cell')) return 'phone';
    if (l.contains('project') || l.contains('job name') || l.contains('job site')) return 'project';
    if (l.contains('location') || l.contains('area')) return 'location';
    if (l.contains('group') || l.contains('crew')) return 'group';
    if (_carry.hasMatch(l)) return 'carry';
    return null;
  }

  /// Fills in the empty answers of [r] it can work out. [earlier] are the reports already saved.
  static void autoFill(
    FilledReport r,
    ReportForm form,
    Project p, {
    required String myName,
    required String myPhone,
    required String myEmail,
    required String myPosition,
    required String myLocation,
    required DateTime today,
    required List<FilledReport> earlier,
  }) {
    final who = r.person;
    for (final q in form.questions) {
      if (r.answer(q.id).isNotEmpty) continue;
      final rule = ruleFor(q);
      if (rule == null) continue;
      final v = switch (rule) {
        'me.name' => myName,
        'me.phone' => myPhone,
        'me.email' => myEmail,
        'me.position' => myPosition,
        'date' => date(today),
        'employeeNo' => p.employeeNumbers[who] ?? '',
        'bp' => p.bpNumbers[who] ?? '',
        'disn' => p.disnNumbers[who] ?? '',
        'name' => who,
        'trade' => p.trades[who] ?? '',
        'local' => p.unionLocals[who] ?? '',
        'level' => p.levels[who] ?? '',
        'phone' => p.phoneOf(who),
        'project' => p.name,
        'location' => p.location.trim().isNotEmpty ? p.location : myLocation,
        'group' => p.crew.contains(who) ? p.roleLabel(p.roleOf(who)) : '',
        'carry' => _carried(q, form, who, earlier),
        _ => '',
      };
      if (v.trim().isNotEmpty) r.answers[q.id] = v.trim();
    }
  }

  /// The latest answer to the same question in an earlier report of this form: same person first; for
  /// employer or company, anyone's.
  static String _carried(ReportQuestion q, ReportForm form, String who, List<FilledReport> earlier) {
    final label = q.label.toLowerCase();
    final list = earlier.where((e) => e.formId == form.id).toList()..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    String find(Iterable<FilledReport> reports) {
      for (final e in reports) {
        for (final fq in form.questions) {
          if (fq.label.toLowerCase() == label && e.answer(fq.id).isNotEmpty) return e.answer(fq.id);
        }
      }
      return '';
    }

    final mine = find(list.where((e) => e.person == who));
    if (mine.isNotEmpty) return mine;
    if (label.contains('employer') || label.contains('company')) return find(list);
    return '';
  }

  /// Answers typed for Employee #, trade or union local are kept on the person when theirs is empty.
  static bool saveBack(FilledReport r, ReportForm form, Project p) {
    var changed = false;
    for (final q in form.questions) {
      final v = r.answer(q.id);
      if (v.isEmpty || !p.crew.contains(r.person)) continue;
      final m = switch (ruleFor(q)) {
        'employeeNo' => p.employeeNumbers,
        'bp' => p.bpNumbers,
        'disn' => p.disnNumbers,
        'trade' => p.trades,
        'local' => p.unionLocals,
        _ => null,
      };
      if (m != null && (m[r.person] ?? '').trim().isEmpty) {
        m[r.person] = v;
        changed = true;
      }
    }
    return changed;
  }

  /// The average of the numeric rating answers, to 1 decimal ("2.8"), or '' if there are none.
  static String average(FilledReport r, ReportForm form) {
    final nums = <double>[];
    for (final q in form.questions) {
      if (q.kind != ReportQuestion.rating) continue;
      final v = double.tryParse(r.answer(q.id));
      if (v != null) nums.add(v);
    }
    if (nums.isEmpty) return '';
    return (nums.reduce((a, b) => a + b) / nums.length).toStringAsFixed(1);
  }

  /// The answer as shown: a rating's "Excellent (4.0)", the worked-out average, or the text.
  static String shown(ReportQuestion q, FilledReport r, ReportForm form) {
    if (q.kind == ReportQuestion.average) return average(r, form);
    final a = r.answer(q.id);
    if (q.kind == ReportQuestion.rating) {
      for (final c in q.choices) {
        if ((c.value.isNotEmpty ? c.value : c.label) == a) return c.display;
      }
    }
    return a;
  }

  /// Questions still to answer (not the worked-out average).
  static List<ReportQuestion> missing(FilledReport r, ReportForm form) => [
        for (final q in form.questions)
          if (q.kind != ReportQuestion.average && r.answer(q.id).isEmpty) q,
      ];
}
