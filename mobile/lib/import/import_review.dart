// The review list's state: which column has the tasks, what's ticked, fixed words and combined rows.
// Kept apart from the screen so it can be tested.
import '../data/fmt.dart';
import '../data/models.dart';
import 'sheet_import.dart';
import 'zone_sheet.dart';

class ImportReview {
  final List<List<String>> rows;

  /// True when the rows are whole tasks read from the zone sheet: [title, notes, side, colour].
  final bool zone;

  /// "the photo", "the spreadsheet", "the North sheet"...
  final String source;
  int column;
  bool notesFromOthers = true;
  final Set<int> unticked = {};
  final Map<String, String> edits = {};

  ImportReview._(this.rows, this.zone, this.source, this.column);

  /// Rows from a photo or a spreadsheet. A spreadsheet's first row ([header]) starts unticked.
  factory ImportReview.rows(List<List<String>> rows, String source, {bool header = false}) {
    final r = ImportReview._(rows, false, source, SheetImport.likelyTaskColumn(rows));
    r._untickNonTasks();
    // A spreadsheet's first row is almost always its headings.
    if (header && rows.length > 1) r.unticked.add(0);
    return r;
  }

  /// Tasks read from a photo of the zone sheet; only tasks in [myColours] start ticked.
  factory ImportReview.zone(List<ZoneTask> tasks, Set<String> myColours) {
    final rows = [
      for (final t in tasks) [t.title(), t.notes(), t.side, t.colour]
    ];
    final first = tasks.isEmpty ? '' : tasks[0].side;
    final where = first == Task.north
        ? 'the North sheet'
        : first == Task.south
            ? 'the South sheet'
            : 'the zone sheet';
    final r = ImportReview._(rows, true, where, 0);
    r._untickNonTasks();
    for (var i = 0; i < rows.length; i++) {
      if (!myColours.contains(rows[i][3])) r.unticked.add(i);
    }
    return r;
  }

  int get width => rows.isEmpty ? 0 : rows[0].length;

  /// Rows that are only numbers or symbols are rarely tasks; start them unticked.
  void _untickNonTasks() {
    for (var i = 0; i < rows.length; i++) {
      if (!SheetImport.hasLetters(rows[i][column])) unticked.add(i);
    }
  }

  void setColumn(int c) {
    if (c == column) return;
    column = c;
    unticked.clear();
    _untickNonTasks();
  }

  String intro() => zone
      ? 'Found ${Fmt.plural(rows.length, 'task', 'tasks')} on $source. Tasks in your colours (set with the person icon '
          'on the Projects screen) are ticked; tick any others you want too. Tap a task to fix a word.'
      : 'Found ${Fmt.plural(rows.length, 'row', 'rows')} in $source. Untick anything that isn\'t a task, and tap a line '
          'to fix a word. If one task is split over several rows, tick just those and tap Combine ticked.';

  /// "Column 2: Pack up sea can…"
  String columnOption(int c) => 'Column ${c + 1}: ${_sample(c)}';

  String _sample(int c) {
    for (final r in rows) {
      final s = r[c];
      if (s.isNotEmpty) return s.length > 30 ? '${s.substring(0, 30)}…' : s;
    }
    return '(empty)';
  }

  String title(int row) => edits['$row:$column'] ?? rows[row][column].trim();

  bool ticked(int row) => !unticked.contains(row);

  void tick(int row, bool on) => on ? unticked.remove(row) : unticked.add(row);

  void tickAll() => unticked.clear();

  void untickAll() {
    for (var i = 0; i < rows.length; i++) {
      unticked.add(i);
    }
  }

  void fix(int row, String text) {
    edits['$row:$column'] = text;
    unticked.remove(row);
  }

  String notes(int row) {
    if (zone) return rows[row][1].trim();
    if (!notesFromOthers) return '';
    final sb = StringBuffer();
    final r = rows[row];
    for (var c = 0; c < r.length; c++) {
      if (c == column || r[c].trim().isEmpty) continue;
      if (sb.isNotEmpty) sb.write('  ·  ');
      sb.write(r[c].trim());
    }
    return sb.toString();
  }

  /// The grey line under a zone task: its colour, then its notes.
  String subtitle(int row) {
    final n = notes(row);
    if (!zone) return n;
    return colourLabel(rows[row][3]) + (n.isEmpty ? '' : '\n$n');
  }

  static String colourLabel(String colour) {
    switch (colour.split(' ')[0]) {
      case ZoneSheet.blue:
        return '■ Dark blue';
      case ZoneSheet.orange:
        return '■ Orange (maintenance)';
      case ZoneSheet.pink:
        return '■ Pink (electrical)';
      case ZoneSheet.green:
        return '■ Green (completed)';
      case ZoneSheet.yellow:
        return '■ Yellow (ongoing)';
      default:
        return '□ No colour';
    }
  }

  /// Ticked rows that have a title, top to bottom.
  List<int> chosen() => [
        for (var i = 0; i < rows.length; i++)
          if (!unticked.contains(i) && title(i).isNotEmpty) i
      ];

  /// The ticked rows' words joined, for the Combine box.
  String joinedTitles(List<int> pick) => pick.map(title).join(' ');

  /// Replaces the rows in pick (ascending) with one row at the first one's place: the task column
  /// gets text, every other column joins the rows' values. Ticks and fixes on other rows are kept.
  void combine(List<int> pick, String text) {
    // Bake in any fixes so they move with their rows.
    edits.forEach((key, value) {
      final k = key.split(':');
      rows[int.parse(k[0])][int.parse(k[1])] = value;
    });
    edits.clear();
    final newUnticked = <int>{};
    var at = 0;
    final gone = pick.sublist(1).toSet();
    for (var i = 0; i < rows.length; i++) {
      if (gone.contains(i)) continue;
      if (unticked.contains(i)) newUnticked.add(at);
      at++;
    }
    SheetImport.combineRows(rows, pick, column, text);
    unticked
      ..clear()
      ..addAll(newUnticked);
  }

  /// The fields to add for the ticked rows.
  List<Task> toTasks(String me) {
    final out = <Task>[];
    for (final i in chosen()) {
      final t = Task()
        ..title = Fmt.capitalize(title(i))
        ..notes = notes(i)
        ..createdBy = me;
      if (zone) {
        final side = rows[i][2].trim().split(' ')[0];
        if (side == Task.north || side == Task.south) t.side = side;
      }
      out.add(t);
    }
    return out;
  }
}
