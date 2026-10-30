// Attendance tab: who's here today, with Name and Here? dropdowns (like the Overtime tab).
import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'overtime_pane.dart' show confirmRemoveRow, removeRowButton, sheetDropdown;

const _pick = 'Select…', _blank = '–';

/// Fills in today's date and, for an empty sheet, makes one row per crew member with their name.
void prepareAttendance(Project p) {
  final a = p.attendance;
  if (a.date.isEmpty) a.date = Fmt.todayIso();
  if (a.rows.isEmpty) {
    for (final n in p.crew) {
      a.rows.add(AttRow()..name = n);
    }
    if (a.rows.isEmpty) a.rows.add(AttRow());
  }
}

/// Names offered in the Name dropdown: crew, plus any name already on a row.
List<String> attendanceNames(Project p) {
  final names = <String>[_pick, ...p.crew];
  for (final r in p.attendance.rows) {
    if (r.name.isNotEmpty && !names.contains(r.name)) names.add(r.name);
  }
  return names;
}

class AttendancePane extends StatefulWidget {
  final Project project;

  /// The Export button: just this sheet, as PDF or Excel.
  final VoidCallback onExport;

  const AttendancePane({super.key, required this.project, required this.onExport});

  @override
  State<AttendancePane> createState() => _AttendancePaneState();
}

class _AttendancePaneState extends State<AttendancePane> {
  late final TextEditingController _notes = TextEditingController(text: widget.project.attendance.notes);
  final Map<AttRow, TextEditingController> _comments = {};
  final _lastRow = GlobalKey();

  Store get store => Store.instance;
  Attendance get att => widget.project.attendance;

  @override
  void dispose() {
    for (final c in _comments.values) {
      c.dispose();
    }
    _notes.dispose();
    super.dispose();
  }

  /// Adds rows to the end of the table, then scrolls so the last one shows.
  void _addRows(int count) {
    for (var i = 0; i < count; i++) {
      att.rows.add(AttRow());
    }
    store.save();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final c = _lastRow.currentContext;
      if (c != null && c.mounted) Scrollable.ensureVisible(c, duration: const Duration(milliseconds: 250));
    });
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final cur = DateTime.tryParse(att.date) ?? now;
    final d = await showDatePicker(
      context: context,
      initialDate: cur,
      firstDate: DateTime(now.year - 5),
      lastDate: DateTime(now.year + 5),
    );
    if (d == null) return;
    att.date = Fmt.isoOf(d);
    store.save();
  }

  /// The table is at least this wide; on a narrow phone it slides sideways.
  static const _tableMinWidth = 470.0;

  Widget _row(Widget name, Widget status, Widget comment, {Widget? end}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(children: [
          Expanded(flex: 19, child: Padding(padding: const EdgeInsets.only(right: 4), child: name)),
          Expanded(flex: 12, child: Padding(padding: const EdgeInsets.only(right: 4), child: status)),
          Expanded(flex: 18, child: Padding(padding: const EdgeInsets.only(right: 4), child: comment)),
          end ?? const SizedBox(width: 32),
        ]),
      );

  TextEditingController _commentFor(AttRow r) =>
      _comments.putIfAbsent(r, () => TextEditingController(text: r.comment));

  /// Free-text comment for one person, e.g. why they left early.
  Widget _commentBox(AttRow r) {
    return TextField(
      controller: _commentFor(r),
      textCapitalization: TextCapitalization.sentences,
      textInputAction: TextInputAction.done,
      style: const TextStyle(fontSize: 15, color: C.text),
      decoration: InputDecoration(
        hintText: 'Comment',
        hintStyle: const TextStyle(color: C.greyLight),
        isDense: true,
        filled: true,
        fillColor: const Color(0xFFF2F7FD),
        contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 13),
        enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: C.cardStroke)),
        focusedBorder:
            OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: C.blue)),
      ),
      onChanged: (v) {
        r.comment = v;
        store.save();
      },
    );
  }

  static Widget _label(String s) => Text(s,
      textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: C.grey, fontWeight: FontWeight.bold));

  Widget _line(AttRow r, List<String> names) => KeyedSubtree(
        key: ObjectKey(r),
        child: _row(
          sheetDropdown(names, r.name.isEmpty ? _pick : r.name, (v) {
            r.name = v == _pick ? '' : v;
            store.save();
          }, yesNo: false),
          sheetDropdown(const [_blank, ...Attendance.statuses], r.status.isEmpty ? _blank : r.status, (v) {
            r.status = v == _blank ? '' : v;
            store.save();
          }, yesNo: true),
          _commentBox(r),
          end: removeRowButton(() {
            confirmRemoveRow(context, r.name, r.isEmpty, () {
              att.rows.remove(r);
              store.save();
            });
          }),
        ),
      );

  Widget _action(String s, Color color, VoidCallback onTap) => InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(8, 16, 8, 4),
          child: Text(s, style: TextStyle(fontSize: 18, color: color)),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final p = widget.project;
    prepareAttendance(p);
    final names = attendanceNames(p);
    // Rows removed elsewhere drop their comment boxes.
    _comments.removeWhere((r, c) {
      if (att.rows.contains(r)) return false;
      c.dispose();
      return true;
    });
    final summary = att.summary();
    final card = BoxDecoration(
        color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: C.cardStroke));
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 120),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(
          decoration: card,
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Row(children: [
              const Expanded(
                child: Text('Attendance', style: TextStyle(fontSize: 22, color: C.navy, fontWeight: FontWeight.bold)),
              ),
              Semantics(
                label: 'Export attendance to PDF or Excel',
                button: true,
                child: Material(
                  color: C.sky,
                  borderRadius: BorderRadius.circular(100),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(100),
                    onTap: widget.onExport,
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16, vertical: 9),
                      child: Text('Export',
                          style: TextStyle(fontSize: 15, color: C.deepBlue, fontWeight: FontWeight.bold)),
                    ),
                  ),
                ),
              ),
            ]),
            // The day this sheet is for; Change opens a calendar.
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(children: [
                Expanded(
                  child: Text('Date: ${Fmt.longDay(att.date)}', style: const TextStyle(fontSize: 16, color: C.text)),
                ),
                TextButton(onPressed: _pickDate, child: const Text('Change', style: TextStyle(fontSize: 16))),
              ]),
            ),
            InkWell(
              onTap: () {
                att.include = !att.include;
                store.save();
              },
              child: Row(children: [
                Checkbox(
                  value: att.include,
                  onChanged: (v) {
                    att.include = v ?? false;
                    store.save();
                  },
                ),
                const Expanded(
                  child: Text('Include in my reports (PDF, Word and email)', style: TextStyle(fontSize: 16)),
                ),
              ]),
            ),
            const SizedBox(height: 6),
            // Name, Here? and Comment on one line; slides sideways if the phone is narrow.
            LayoutBuilder(
              builder: (context, box) => SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: SizedBox(
                  width: box.maxWidth < _tableMinWidth ? _tableMinWidth : box.maxWidth,
                  child: Column(children: [
                    _row(_label('Name'), _label('Here?'), _label('Comment')),
                    for (final r in att.rows) _line(r, names),
                  ]),
                ),
              ),
            ),
            // The count of each answer sits under the table; after adding rows the screen scrolls here.
            KeyedSubtree(
              key: _lastRow,
              child: Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  summary.isEmpty ? 'Nobody marked yet' : summary,
                  style: TextStyle(
                    fontSize: 16,
                    color: summary.isEmpty ? C.grey : C.navy,
                    fontWeight: summary.isEmpty ? FontWeight.normal : FontWeight.bold,
                  ),
                ),
              ),
            ),
            if (p.crew.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 10),
                child: Text('Add your crew first: tap ⋯ at the top, then Manage crew.',
                    style: TextStyle(fontSize: 15, color: C.grey)),
              ),
          ]),
        ),
        Align(alignment: Alignment.centerLeft, child: _action('+  Add a row', C.blue, () => _addRows(1))),
        Align(alignment: Alignment.centerLeft, child: _action('+  Add 5 rows', C.blue, () => _addRows(5))),
        Align(
          alignment: Alignment.centerLeft,
          child: _action('Remove empty rows', C.deepBlue, () {
            final before = att.rows.length;
            att.removeEmptyRows();
            if (att.rows.length == before) {
              toast(context, 'No empty rows to remove');
              return;
            }
            store.save();
          }),
        ),
        Container(
          margin: const EdgeInsets.only(top: 18),
          decoration: card,
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const Text('Notes', style: TextStyle(fontSize: 20, color: C.navy, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            TextField(
              controller: _notes,
              minLines: 4,
              maxLines: null,
              keyboardType: TextInputType.multiline,
              textCapitalization: TextCapitalization.sentences,
              style: const TextStyle(fontSize: 17, color: C.text),
              decoration: InputDecoration(
                hintText: 'Add notes about today’s attendance…',
                hintStyle: const TextStyle(color: C.greyLight),
                contentPadding: const EdgeInsets.all(12),
                enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.cardStroke)),
                focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.blue)),
              ),
              onChanged: (s) {
                att.notes = s;
                store.save();
              },
            ),
          ]),
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: _action('Clear answers', C.deepBlue, () {
            confirm(context, 'Clear the attendance answers?', 'Answers and comments are cleared and the date changes to today. Names and notes stay.',
                'Clear', () {
              att.startDay(Fmt.todayIso());
              for (final c in _comments.values) {
                c.clear();
              }
              store.save();
            });
          }),
        ),
      ]),
    );
  }
}
