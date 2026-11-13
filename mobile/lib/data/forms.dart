// Documents on the Links tab, and report forms (Apprentice reports, Disciplinary action): reading a form's
// text into questions, filling it in for a crew member, and working out the average of the ratings.
// The same rules as the classic app's FormReader.java / FormFill.java.
import 'dart:convert';
import 'dart:math' as math;

import 'package:archive/archive.dart';
import 'package:xml/xml.dart';

import 'form_lines.dart';
import 'models.dart';

export 'form_lines.dart' show Segment;

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

/// Where an answer is written on the form's page picture: page number and x, y as fractions of the page's
/// width and height (y is the bottom of the text).
class Place {
  final int page;
  final double x, y;

  /// Width available (the fill-in line's length), or 0 when no line was found (then up to the page edge).
  final double w;

  /// For long answers: where the following lines go (y of each next ruled line).
  final List<double> rows;

  const Place(this.page, this.x, this.y, [this.w = 0, this.rows = const []]);

  /// The width to fit in: the line's length, else to the page's right edge.
  double get width => w > 0 ? w : (0.96 - x).clamp(0.04, 1.0);

  /// For long answers on ruled lines: the tallest text that fits between them (fraction of the page height),
  /// or null with no following lines.
  double? get rowFit {
    if (rows.isEmpty) return null;
    var gap = rows.first - y;
    for (var i = 1; i < rows.length; i++) {
      gap = math.min(gap, rows[i] - rows[i - 1]);
    }
    return gap * 0.62;
  }

  /// Moved by (dx, dy); following lines move with it.
  Place moved(double dx, double dy) => Place(page, (x + dx).clamp(0.0, 0.98), (y + dy).clamp(0.01, 1.0), w,
      [for (final r in rows) (r + dy).clamp(0.01, 1.0)]);

  Map<String, dynamic> toJson() => {
        'page': page,
        'x': x,
        'y': y,
        if (w > 0) 'w': w,
        if (rows.isNotEmpty) 'rows': rows,
      };

  static Place? fromJson(dynamic o) {
    if (o is! Map) return null;
    final x = o['x'], y = o['y'];
    if (x is! num || y is! num) return null;
    return Place(
      o['page'] is int ? o['page'] as int : 0,
      x.toDouble().clamp(0, 1),
      y.toDouble().clamp(0, 1),
      o['w'] is num ? (o['w'] as num).toDouble().clamp(0, 1) : 0,
      [if (o['rows'] is List) for (final r in o['rows'] as List) if (r is num) r.toDouble().clamp(0, 1)],
    );
  }

  @override
  String toString() => 'p$page(${x.toStringAsFixed(3)},${y.toStringAsFixed(3)})';
}

/// One question on a report form.
class ReportQuestion {
  static const text = 'text', long = 'long', rating = 'rating', average = 'average';

  String id;
  String label;
  String kind;
  List<Choice> choices;

  /// Where the answer goes on the form's pictures, or null.
  Place? place;

  ReportQuestion(this.id, this.label, this.kind, [List<Choice>? choices, this.place]) : choices = choices ?? [];

  Map<String, dynamic> toJson() => {
        'id': id,
        'label': label,
        'kind': kind,
        'choices': [for (final c in choices) c.toJson()],
        if (place != null) 'place': place!.toJson(),
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
        Place.fromJson(o['place']),
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

  /// Size of the answers written on the form: [textSize] (a fraction of the page height, from the form's own
  /// text) times [fontScale] (A- / A+ on Place the answers).
  double textSize = 0.014;
  double fontScale = 1.0;

  Map<String, dynamic> toJson() => {
        'id': id,
        'group': group,
        'name': name,
        'files': files,
        'kind': kind,
        'questions': [for (final q in questions) q.toJson()],
        'signatures': signatures,
        'createdAt': createdAt,
        'textSize': textSize,
        'fontScale': fontScale,
      };

  /// The page pictures answers are written on: a photo form's photos, a PDF form's page pictures.
  List<String> get pagePictures => kind == Doc.photo
      ? files
      : kind == Doc.pdf
          ? [for (final f in files) if (f.toLowerCase().endsWith('.png')) f]
          : const [];

  ReportForm copy() => ReportForm.fromJson(jsonDecode(jsonEncode(toJson())) as Map<String, dynamic>);

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
    ..createdAt = o['createdAt'] is int ? o['createdAt'] as int : 0
    ..textSize = o['textSize'] is num ? (o['textSize'] as num).toDouble().clamp(0.004, 0.05) : 0.014
    ..fontScale = o['fontScale'] is num ? (o['fontScale'] as num).toDouble().clamp(0.6, 2.0) : 1.0;

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

  /// A copy of the form as it was when this report was started, used if the form is later replaced or deleted.
  ReportForm? form;

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
        if (form != null) 'form': form!.toJson(),
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
      ..createdAt = o['createdAt'] is int ? o['createdAt'] as int : 0
      ..form = o['form'] is Map ? ReportForm.fromJson(Map<String, dynamic>.from(o['form'] as Map)) : null;
    r.updatedAt = o['updatedAt'] is int ? o['updatedAt'] as int : r.createdAt;
    return r;
  }
}

/// What a form's text was read into.
class ReadForm {
  final List<ReportQuestion> questions;
  final List<String> signatures;

  /// Size for answers written on the form (fraction of the page height).
  final double textSize;

  ReadForm(this.questions, this.signatures, [this.textSize = 0.014]);
}

/// A line of a form's text, with its box on the page picture as fractions (0..1) when it came from a picture.
class FormLine {
  final String text;
  final int page;
  final double left, top, right, bottom;
  final bool hasBox;

  const FormLine(this.text, {this.page = 0, this.left = 0, this.top = 0, this.right = 0, this.bottom = 0})
      : hasBox = right > left && bottom > top;

  double get height => bottom - top;
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

  static ReadForm read(List<String> rawLines) => readLines([for (final l in rawLines) FormLine(l)]);

  /// Reads the lines into questions and signature lines; when the lines have boxes, also works out where
  /// each answer is written on the page.
  static ReadForm readLines(List<FormLine> input, {List<Segment> segments = const []}) {
    final found = <_Found>[];
    final choices = <Choice>[];
    final signatures = <String>[];
    var hasAverage = false;

    void add(String label, String kind, FormLine src, double labelEnd) {
      final l = cleanLabel(label);
      if (l.isEmpty) return;
      if (found.any((f) => f.label.toLowerCase() == l.toLowerCase())) return;
      // "To" right after "... from" is the end of the same period.
      if (l.toLowerCase() == 'to' && found.isNotEmpty && found.last.label.toLowerCase().endsWith(' from')) {
        final prev = found.last.label;
        found.add(_Found('${prev.substring(0, prev.length - 5)} to', kind, src, labelEnd));
        return;
      }
      found.add(_Found(l, kind, src, labelEnd));
    }

    for (final src in input) {
      final parts = src.text.split('\n');
      for (final part in parts) {
        // A line split in two (Word line breaks) has no box of its own.
        final box = parts.length == 1 ? src : FormLine(part, page: src.page);
        final full = normalize(part);
        var line = full;
        if (RegExp('[A-Za-z]').allMatches(line).length < 2) continue;
        if (line.startsWith('(') && line.endsWith(')')) {
          if (found.isNotEmpty && found.last.kind == ReportQuestion.average && !found.last.parenAfter) {
            found.last.parenAfter = true;
            found.last.parenRight = box.right;
          }
          continue;
        }
        final noBlanks = line.replaceAll(blank, ' ').replaceAll(RegExp(r'\s+'), ' ').trim();
        double endAt(int charIndex) =>
            box.left + (box.right - box.left) * (full.isEmpty ? 1 : (charIndex / full.length).clamp(0.0, 1.0));

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
        var prefix = 0;
        final sec = _section.firstMatch(line);
        if (sec != null) {
          section = true;
          prefix = sec.end;
          line = line.substring(sec.end).trim();
        }
        // 4. numbered items
        final num = _numbered.firstMatch(line);
        if (num != null) {
          add(num.group(2)!, 'numbered', box, box.right);
          continue;
        }
        // 5. the overall average
        final lower = noBlanks.toLowerCase();
        if (lower.contains('average') &&
            (lower.contains('of above') || lower.contains('total') || noBlanks.endsWith(':'))) {
          if (!hasAverage) {
            hasAverage = true;
            found.add(_Found('Overall average', ReportQuestion.average, box, box.right));
          }
          continue;
        }
        // 6. labels with colons or fill-in lines
        final before = found.length;
        final segments = line.split(blank);
        var offset = prefix + (full.length - full.substring(prefix).trimLeft().length - prefix).clamp(0, 9999);
        final several = segments.where((x) => x.trim().isNotEmpty).length > 1 || ':'.allMatches(line).length > 1;
        for (var i = 0; i < segments.length; i++) {
          final seg = segments[i];
          final s = seg.trim();
          final segStart = offset + (seg.length - seg.trimLeft().length);
          offset += seg.length + 1;
          if (s.isEmpty) continue;
          final followedByBlank = i < segments.length - 1;
          if (s.contains(':')) {
            final chunks = RegExp(r'([^:]+):').allMatches(s).toList();
            for (var c = 0; c < chunks.length; c++) {
              final label = chunks[c].group(1)!;
              final afterEnd = c + 1 < chunks.length ? chunks[c + 1].start : s.length;
              final after = s.substring(chunks[c].end, afterEnd).trim();
              if (after.isEmpty) {
                add(label, section ? ReportQuestion.long : '', box, several ? endAt(segStart + chunks[c].end) : box.right);
              }
            }
          } else if (followedByBlank) {
            add(s, section ? ReportQuestion.long : '', box, several ? endAt(segStart + s.length) : box.right);
          }
        }
        if (found.length > before || line.contains(':') || line.contains(blank)) continue;
        // 7. an ALL-CAPS label on its own
        if (noBlanks.length >= 2 &&
            noBlanks.length <= 30 &&
            noBlanks.split(' ').length <= 4 &&
            noBlanks == noBlanks.toUpperCase() &&
            RegExp('[A-Z]').hasMatch(noBlanks)) {
          add(noBlanks, section ? ReportQuestion.long : '', box, box.right);
          continue;
        }
        // 8. a question
        if (noBlanks.endsWith('?') && noBlanks.length <= 120) {
          add(noBlanks, section ? ReportQuestion.long : '', box, box.right);
        }
      }
    }

    final kinds = <String>[];
    for (final f in found) {
      var kind = f.kind;
      if (kind == 'numbered') {
        kind = choices.length >= 2 ? ReportQuestion.rating : ReportQuestion.text;
      } else if (kind != ReportQuestion.average && kind != ReportQuestion.long) {
        kind = ReportQuestion.text;
      }
      if (kind == ReportQuestion.text && _longWords.hasMatch(f.label)) kind = ReportQuestion.long;
      kinds.add(kind);
    }

    // Where answers go: right of the label, under it for long answers, in one column for ratings.
    var ratingRight = 0.0;
    for (var i = 0; i < found.length; i++) {
      if (kinds[i] == ReportQuestion.rating && found[i].src.hasBox && found[i].src.right > ratingRight) {
        ratingRight = found[i].src.right;
      }
    }
    final column = ratingRight + 0.06;
    final heights = <double>[
      for (final f in found)
        if (f.src.hasBox) f.src.height,
    ]..sort();
    final textSize =
        heights.isEmpty ? 0.014 : (heights[heights.length ~/ 2] * 0.75).clamp(0.008, 0.03).toDouble();

    // The fill-in lines: not the underline of a heading (a line right under words that cover it).
    final lines = [
      for (final g in segments)
        if (!input.any((t) =>
            t.hasBox &&
            t.page == g.page &&
            !t.text.contains(RegExp(r'_{3,}')) &&
            g.y >= t.top &&
            g.y <= t.bottom + 0.006 &&
            (math.min(g.x1, t.right) - math.max(g.x0, t.left)) > 0.5 * g.length))
          g,
    ];
    // Signature lines are left empty: a line with "Signature ..." under it, or after it on the same line.
    final sigs = [for (final t in input) if (t.hasBox && t.text.toLowerCase().contains('signature')) t];
    final used = <Segment>{
      for (final g in lines)
        if (sigs.any((t) =>
            t.page == g.page &&
            ((math.min(g.x1, t.right) - math.max(g.x0, t.left) > 0.3 * math.min(g.length, t.right - t.left) &&
                    t.top - g.y >= -0.004 &&
                    t.top - g.y <= 1.5 * t.height) ||
                (g.x0 >= t.right - 0.01 && g.x0 - t.right < 0.1 && (g.y - t.bottom).abs() <= t.height))))
          g,
    };

    final questions = <ReportQuestion>[];
    for (var i = 0; i < found.length; i++) {
      final f = found[i], b = f.src, kind = kinds[i];
      Place? place;
      final onLine = b.hasBox ? _onLines(f, kind, lines, used) : null;
      if (onLine != null) {
        place = _clear(onLine, b, input);
      } else if (b.hasBox) {
        place = switch (kind) {
          ReportQuestion.long => Place(b.page, b.left, b.bottom + 1.1 * b.height),
          ReportQuestion.rating => Place(b.page, column, b.bottom),
          // On the rating column, but never on top of the "Overall average ... (total ...)" words.
          ReportQuestion.average => Place(
              b.page,
              math.max(ratingRight > 0 ? column : 0.0, (f.parenAfter ? math.max(b.right, f.parenRight) : b.right) + 0.015),
              f.parenAfter ? b.bottom + 1.2 * b.height : b.bottom),
          _ => Place(b.page, f.labelEnd + 0.012, b.bottom),
        };
        place = _clear(place, b, input);
      }
      questions.add(ReportQuestion('q${questions.length + 1}', f.label, kind,
          kind == ReportQuestion.rating ? List.of(choices) : null, place));
    }
    return ReadForm(questions, signatures, textSize);
  }

  /// Puts an answer on the form's own fill-in line: a short answer on the nearest line to the right of its
  /// label (or just under it), a long answer on the ruled lines under it.
  static Place? _onLines(_Found f, String kind, List<Segment> lines, Set<Segment> used) {
    final b = f.src;
    final h = b.height;
    const pad = 0.006, lift = 0.0045;
    if (kind == ReportQuestion.long) {
      final below = lines
          .where((g) => g.page == b.page && !used.contains(g) && g.y > b.bottom && g.y <= b.bottom + 10 * h && g.length >= 0.25)
          .toList()
        ..sort((a, c) => a.y.compareTo(c.y));
      if (below.isEmpty) return null;
      final rows = [below.first];
      double? spacing;
      for (final g in below.skip(1)) {
        final gap = g.y - rows.last.y;
        if (gap < 0.4 * h) continue; // the same line found twice
        if (gap > 3 * h) break;
        // Ruled lines are evenly spaced: a bigger gap means a different part of the form.
        if (spacing != null && gap > 1.8 * spacing) break;
        spacing ??= gap;
        rows.add(g);
      }
      used.addAll(rows);
      final first = rows.first;
      return Place(b.page, first.x0 + pad, first.y - lift, first.length - 2 * pad,
          [for (final g in rows.skip(1)) g.y - lift]);
    }
    // The average's label may run on to a "(total ...)" line: look from the end of that.
    final end = kind == ReportQuestion.average && f.parenAfter ? math.max(f.labelEnd, f.parenRight) : f.labelEnd;
    final bottom = kind == ReportQuestion.average && f.parenAfter ? b.bottom + 1.2 * h : b.bottom;
    Segment? best;
    var bestScore = double.infinity;
    for (final g in lines) {
      if (g.page != b.page || used.contains(g)) continue;
      if (g.y < bottom - 0.5 * h || g.y > bottom + 1.6 * h) continue;
      if (g.x1 <= end + 0.01 || g.x0 < end - 0.02) continue;
      final score = (g.x0 - end) + 2 * (g.y - bottom).abs();
      if (score < bestScore) {
        bestScore = score;
        best = g;
      }
    }
    // Or a line just under the label.
    if (best == null) {
      for (final g in lines) {
        if (g.page != b.page || used.contains(g)) continue;
        if (g.y <= b.bottom || g.y > b.bottom + 2.5 * h) continue;
        if (g.x1 < b.left || g.x0 > b.right) continue;
        if (best == null || g.y < best.y) best = g;
      }
    }
    if (best == null) return null;
    used.add(best);
    return Place(b.page, best.x0 + pad, best.y - lift, best.length - 2 * pad);
  }

  /// Keeps an answer off printed words: its width stops before any text on the page that sits in the band the
  /// answer is written in (from a little above its first line to its last line), other than its own label.
  static Place _clear(Place p, FormLine own, List<FormLine> input) {
    final h = own.height;
    final top = p.y - 1.1 * h, bottom = (p.rows.isEmpty ? p.y : p.rows.last) + 0.2 * h;
    var end = p.x + p.width;
    for (final t in input) {
      if (!t.hasBox || t.page != p.page || identical(t, own)) continue;
      if (t.bottom <= top || t.top >= bottom) continue;
      if (t.left <= p.x + 0.005 || t.left >= end) continue;
      end = t.left - 0.008;
    }
    final w = end - p.x;
    if (w >= p.width - 1e-9) return p;
    return Place(p.page, p.x, p.y, math.max(0.03, w), p.rows);
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
  final FormLine src;

  /// Where the label ends on the page (fraction of the width).
  final double labelEnd;

  /// The "(total #1 - #10 ...)" line comes right after the average, and where it ends.
  bool parenAfter = false;
  double parenRight = 0;

  _Found(this.label, this.kind, this.src, this.labelEnd);
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
  static final _carry = RegExp(r'\b(employer|company|department|shift)\b');

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
    // Single words match whole words only ("Work location" isn't a union local, "determine" isn't a term).
    bool w(String words) => RegExp('\\b($words)\\b').hasMatch(l);
    if (w('position|occupation') || l.contains('job title')) return 'position';
    if (w('trade|craft')) return 'trade';
    if (w('union|local')) return 'local';
    if (w('level|classification')) return 'level';
    if (w('phone|cell')) return 'phone';
    if (w('project') || l.contains('job name') || l.contains('job site')) return 'project';
    if (w('location|area')) return 'location';
    if (w('group|crew')) return 'group';
    if (w('term')) return 'term';
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
        // Their job: "Millwright", "Millwright Apprentice", or just the level when no trade is set.
        'position' => [p.trades[who] ?? '', p.levels[who] == 'Apprentice' ? 'Apprentice' : '']
            .where((x) => x.isNotEmpty)
            .join(' '),
        'local' => p.unionLocals[who] ?? '',
        'level' => p.levels[who] ?? '',
        'term' => p.terms[who] ?? '',
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
      final v = double.tryParse(choiceOf(q, r.answer(q.id))?.value ?? r.answer(q.id));
      if (v != null) nums.add(v);
    }
    if (nums.isEmpty) return '';
    return (nums.reduce((a, b) => a + b) / nums.length).toStringAsFixed(1);
  }

  /// The answer as shown: a rating's "Excellent (4.0)", the worked-out average, or the text.
  static String shown(ReportQuestion q, FilledReport r, ReportForm form) {
    if (q.kind == ReportQuestion.average) return average(r, form);
    final a = r.answer(q.id);
    if (q.kind == ReportQuestion.rating) return choiceOf(q, a)?.display ?? a;
    return a;
  }

  /// What's written on the form itself: a rating's number ("4.0", or its word when the scale has none),
  /// the average, or the text.
  static String onForm(ReportQuestion q, FilledReport r, ReportForm form) {
    if (q.kind == ReportQuestion.average) return average(r, form);
    final a = r.answer(q.id);
    if (q.kind == ReportQuestion.rating) {
      final c = choiceOf(q, a);
      if (c != null) return c.value.isNotEmpty ? c.value : c.label;
    }
    return a;
  }

  /// A rating answer is saved as the choice's word ("Excellent", as the classic app does); a saved number also
  /// finds its choice.
  static Choice? choiceOf(ReportQuestion q, String answer) {
    final a = answer.trim().toLowerCase();
    if (a.isEmpty) return null;
    for (final c in q.choices) {
      if (c.label.toLowerCase() == a) return c;
    }
    for (final c in q.choices) {
      if (c.value.isNotEmpty && c.value == a) return c;
    }
    return null;
  }

  /// The scale for a question changed to Rating on a form that has none.
  static const plainScale = [Choice('Excellent', ''), Choice('Good', ''), Choice('Fair', ''), Choice('Poor', '')];

  /// Questions still to answer (not the worked-out average).
  static List<ReportQuestion> missing(FilledReport r, ReportForm form) => [
        for (final q in form.questions)
          if (q.kind != ReportQuestion.average && r.answer(q.id).isEmpty) q,
      ];
}

/// Fitting answers into the form's lines (the PDF and the Place the answers screen use the same steps).
class Fit {
  Fit._();

  /// [text] in lines no wider than [width], breaking between words (a very long word is split).
  /// [measure] gives a string's width at the size being used.
  static List<String> wrap(String text, double width, double Function(String) measure) {
    final out = <String>[];
    for (final para in text.split('\n')) {
      var line = '';
      for (final word in para.split(RegExp(r'\s+')).where((w) => w.isNotEmpty)) {
        final tryLine = line.isEmpty ? word : '$line $word';
        if (measure(tryLine) <= width) {
          line = tryLine;
          continue;
        }
        if (line.isNotEmpty) out.add(line);
        // A word longer than the line on its own is split.
        var rest = word;
        while (measure(rest) > width && rest.length > 1) {
          var n = rest.length - 1;
          while (n > 1 && measure(rest.substring(0, n)) > width) {
            n--;
          }
          out.add(rest.substring(0, n));
          rest = rest.substring(n);
        }
        line = rest;
      }
      if (line.isNotEmpty) out.add(line);
    }
    return out;
  }

  /// The size to write a one-line answer at so it fits [width]: [size], or smaller (not under half).
  static double oneLine(String text, double width, double size, double Function(String) measureAt1) {
    final w = measureAt1(text) * size;
    if (w <= width || w == 0) return size;
    return math.max(size * 0.5, size * width / w);
  }

  /// A long answer on [rows] ruled lines of [width]: the size (from [size] down to 55% of it) and the lines.
  /// If it still doesn't fit, there are more lines than rows (they carry on below).
  static (double, List<String>) rows(String text, double width, int rows, double size,
      double Function(String) measureAt1) {
    var s = size;
    while (true) {
      final lines = wrap(text, width, (t) => measureAt1(t) * s);
      if (lines.length <= math.max(1, rows) || s <= size * 0.55) return (s, lines);
      s *= 0.92;
    }
  }
}

