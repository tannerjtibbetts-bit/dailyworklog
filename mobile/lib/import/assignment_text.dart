// Reads assignment texts ("Assigned to you: ...") out of the lines of a screenshot, so a field
// someone texted you can be added to your checklist. Plain Dart, unit-tested; must match the
// classic app's rules.
import '../data/fmt.dart';
import '../data/models.dart';

class AssignmentText {
  final String title, logTitle, project, from, phone;
  final bool photosRequired;

  const AssignmentText({
    required this.title,
    this.logTitle = '',
    this.project = '',
    this.from = '',
    this.phone = '',
    this.photosRequired = false,
  });

  @override
  String toString() => 'AssignmentText($title | $logTitle | $project | $from | $phone | $photosRequired)';
}

final _start = RegExp(r'assign\w*\s+to\s+you\s*[:;.,]?\s*(.*)', caseSensitive: false);
final _photos = RegExp(r'photos?\s+required', caseSensitive: false);
final _photosLine = RegExp(r'^photos?\s+required', caseSensitive: false);
final _dashes = RegExp(r'^[-–—_=~.·•]{3,}');
final _fromStop = RegExp(r'^from\s*[:;]', caseSensitive: false);
final _from = RegExp(r'^from\s*[:;]\s*(.+)', caseSensitive: false);
final _time = RegExp(r'^\d{1,2}[:.]\d{2}\s*(am|pm)?$', caseSensitive: false);
final _date = RegExp(r'^(today|yesterday|mon|tue|wed|thu|fri|sat|sun)\b.*\d', caseSensitive: false);
final _at = RegExp(r'\bat\s*(.*)', caseSensitive: false);

/// True for a line that can't be part of a wrapped title.
bool _stops(String line) {
  final s = line.trim();
  final low = s.toLowerCase();
  return s.isEmpty ||
      _photosLine.hasMatch(s) ||
      _dashes.hasMatch(s) ||
      _fromStop.hasMatch(s) ||
      low.contains('call or text') ||
      _time.hasMatch(s) ||
      _start.hasMatch(s) ||
      low.startsWith('daily work log') ||
      _date.hasMatch(s);
}

/// Every assignment text found in [lines] (top to bottom), in order.
List<AssignmentText> parseAssignmentTexts(List<String> lines) {
  final out = <AssignmentText>[];
  for (var i = 0; i < lines.length; i++) {
    final m = _start.firstMatch(lines[i]);
    if (m == null) continue;

    // The title: the rest of the start line, plus any lines it wrapped onto.
    final words = [m.group(1) ?? ''];
    for (var j = i + 1, n = 0; j < lines.length && n < 4 && !_stops(lines[j]); j++, n++) {
      words.add(lines[j]);
    }
    final title = words.join(' ').trim().replaceAll(RegExp(r'\s+'), ' ').replaceFirst(RegExp(r'[.,;:\s]+$'), '');
    if (title.isEmpty) continue;

    var end = lines.length;
    for (var k = i + 1; k < lines.length; k++) {
      if (_start.hasMatch(lines[k])) {
        end = k;
        break;
      }
    }

    var photos = false;
    for (var k = i + 1; k < end && k <= i + 8; k++) {
      if (_photos.hasMatch(lines[k])) photos = true;
    }

    var from = '', phone = '';
    for (var k = i + 1; k < end && k <= i + 10; k++) {
      final f = _from.firstMatch(lines[k].trim());
      if (f == null) continue;
      from = f.group(1)!.replaceFirst(RegExp(r'\s*\(.*\)\s*$'), '').trim();
      for (var q = k + 1; q < lines.length && q <= k + 3; q++) {
        final low = lines[q].toLowerCase();
        final c = low.indexOf('call or text');
        if (c < 0) continue;
        final a = _at.firstMatch(lines[q].substring(c + 'call or text'.length));
        final d = a == null ? '' : a.group(1)!.replaceAll(RegExp(r'[^0-9+]'), '');
        if (d.replaceAll('+', '').length >= 7) phone = d;
        break;
      }
      break;
    }

    var logTitle = '', project = '';
    for (var k = i - 1; k >= 0 && k >= i - 3; k--) {
      if (_start.hasMatch(lines[k])) break;
      final s = lines[k].trim();
      if (s.isEmpty) continue;
      final open = s.indexOf('(');
      if (s.endsWith(')') && open >= 0) {
        logTitle = s.substring(0, open).trim();
        project = s.substring(open + 1, s.length - 1).trim();
      } else {
        logTitle = s;
      }
      break;
    }

    out.add(
      AssignmentText(
        title: title,
        logTitle: logTitle,
        project: project,
        from: from,
        phone: phone,
        photosRequired: photos,
      ),
    );
  }
  return out;
}

/// The "Add from text" list: what's ticked, the (editable) titles, and who to text when done.
class AssignmentReview {
  final List<AssignmentText> texts;
  final List<String> titles;
  final List<bool> ticked, textWhenDone;

  AssignmentReview(this.texts)
    : titles = [for (final t in texts) t.title],
      ticked = [for (final _ in texts) true],
      textWhenDone = [for (final t in texts) t.phone.isNotEmpty];

  List<int> chosen() => [
    for (var i = 0; i < texts.length; i++)
      if (ticked[i] && titles[i].trim().isNotEmpty) i,
  ];

  /// New fields for the ticked texts.
  List<Task> toTasks(String me) => [
    for (final i in chosen())
      Task()
        ..title = Fmt.capitalize(titles[i].trim())
        ..photosRequired = texts[i].photosRequired
        ..createdBy = me
        ..notes = ''
        ..doneTextName = textWhenDone[i] && texts[i].phone.isNotEmpty ? texts[i].from : ''
        ..doneTextPhone = textWhenDone[i] && texts[i].phone.isNotEmpty ? texts[i].phone : '',
  ];
}
