// Search across every day of a project: fields, notes, names, "Also today", notes sent, the
// Notes tab and the sea can list. Used by the search box on the Checklists tab.
import 'fmt.dart';
import 'models.dart';

/// One day that matched, with a short line for each place the word was found.
class DayHit {
  final DailyLog log;
  final List<String> lines;

  DayHit(this.log, this.lines);
}

class SearchResult {
  final List<DayHit> days;

  /// Sea cans aren't tied to a day, so they're listed on their own.
  final List<String> seaCans;

  SearchResult(this.days, this.seaCans);

  bool get isEmpty => days.isEmpty && seaCans.isEmpty;
}

class ProjectSearch {
  ProjectSearch._();

  static SearchResult run(Project p, String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return SearchResult([], []);
    bool has(String s) => s.toLowerCase().contains(q);

    final logs = List.of(p.logs)
      ..sort((a, b) {
        final c = b.date.compareTo(a.date);
        return c != 0 ? c : b.createdAt.compareTo(a.createdAt);
      });
    final days = <DayHit>[];
    for (final l in logs) {
      final lines = <String>[];
      void add(String s) {
        if (!lines.contains(s)) lines.add(s);
      }

      if (has(l.displayTitle())) add('Checklist: ${l.displayTitle()}');
      for (final t in l.tasks) {
        final title = t.title.trim().isEmpty ? '(Untitled field)' : t.title.trim();
        if (has(t.title)) add('Field: $title');
        if (has(t.notes)) add('Notes on $title: ${snippet(t.notes, q)}');
        for (final n in [...t.assignees, t.completedBy, t.securedBy]) {
          if (n.isNotEmpty && has(n)) {
            add('$title: $n');
            break;
          }
        }
      }
      for (final e in l.extras) {
        if (has(e.title)) {
          add('Also today: ${e.label()}');
        } else if (has(e.detail)) {
          add('Also today: ${e.label()}: ${snippet(e.detail, q)}');
        }
      }
      for (final n in p.notices) {
        if (_day(n.sentAt) != l.date) continue;
        if (has(n.note) || n.to.any(has)) add('Sent note: ${snippet(n.note, q)}');
      }
      for (final n in p.notes) {
        if (_day(n.createdAt) == l.date && has(n.text)) add('Note: ${snippet(n.text, q)}');
      }
      if (lines.isNotEmpty) days.add(DayHit(l, lines));
    }

    final cans = <String>[];
    for (final m in p.filledSeaCans()) {
      final from = m.fromLocation(), to = m.toLocation(), w = m.weight.trim();
      if (has(m.can) || has(from) || has(to) || has(w)) {
        cans.add('${m.can.trim()} · ${from.isEmpty ? '–' : from} to ${to.isEmpty ? '–' : to}${w.isEmpty ? '' : ' · $w'}');
      }
    }
    return SearchResult(days, cans);
  }

  static String _day(int ms) => Fmt.isoOf(DateTime.fromMillisecondsSinceEpoch(ms));

  /// The text on one line; if it's long, about 60 characters around the first match.
  static String snippet(String text, String query) {
    final s = text.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (s.length <= 60) return s;
    final i = s.toLowerCase().indexOf(query.toLowerCase());
    final start = i < 0 ? 0 : (i - 20).clamp(0, s.length - 60);
    final end = start + 60;
    return '${start > 0 ? '…' : ''}${s.substring(start, end)}${end < s.length ? '…' : ''}';
  }
}
