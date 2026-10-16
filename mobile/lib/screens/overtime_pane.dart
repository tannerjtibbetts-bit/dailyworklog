// Overtime tab: weekend sign-up table with Name / Fri / Sat / Sun dropdowns.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

const _pick = 'Select…', _blank = '–';
const _nameFlex = 19, _cellFlex = 10;

/// Hours box text to the saved value: '' when blank, the number for 1 to 10, null if not allowed.
String? otHours(String typed) {
  final v = typed.trim();
  if (v.isEmpty) return '';
  final n = int.tryParse(v) ?? -1;
  if (n < 1 || n > 10) return null;
  return '$n';
}

/// Fills in the week and makes one row per crew member (at least one).
void prepareOvertime(Project p) {
  final ot = p.overtime;
  if (ot.weekOf.isEmpty) ot.weekOf = Fmt.upcomingFriday();
  final want = p.crew.isEmpty ? 1 : p.crew.length;
  while (ot.rows.length < want) {
    ot.rows.add(OtRow());
  }
}

/// Names offered in the Name dropdown: crew, plus any name already on a row.
List<String> overtimeNames(Project p) {
  final names = <String>[_pick, ...p.crew];
  for (final r in p.overtime.rows) {
    if (r.name.isNotEmpty && !names.contains(r.name)) names.add(r.name);
  }
  return names;
}

class OvertimePane extends StatefulWidget {
  final Project project;

  /// The Export button: just this sheet, as PDF or Excel.
  final VoidCallback onExport;

  const OvertimePane({super.key, required this.project, required this.onExport});

  @override
  State<OvertimePane> createState() => _OvertimePaneState();
}

class _OvertimePaneState extends State<OvertimePane> {
  final Map<OtRow, TextEditingController> _hours = {};
  final Map<OtRow, String?> _hourErrors = {};
  late final TextEditingController _notes = TextEditingController(text: widget.project.overtime.notes);
  final _lastRow = GlobalKey();

  Store get store => Store.instance;
  Overtime get ot => widget.project.overtime;

  @override
  void dispose() {
    for (final c in _hours.values) {
      c.dispose();
    }
    _notes.dispose();
    super.dispose();
  }

  void _resetHours() {
    for (final c in _hours.values) {
      c.dispose();
    }
    _hours.clear();
    _hourErrors.clear();
  }

  TextEditingController _hoursFor(OtRow r) => _hours.putIfAbsent(r, () => TextEditingController(text: r.hours));

  /// Adds rows to the end of the table, then scrolls so the last one shows.
  void _addRows(int count) {
    for (var i = 0; i < count; i++) {
      ot.rows.add(OtRow());
    }
    store.save();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final c = _lastRow.currentContext;
      if (c != null && c.mounted) Scrollable.ensureVisible(c, duration: const Duration(milliseconds: 250));
    });
  }

  static BoxDecoration _cellBg(String v) => BoxDecoration(
        color: v == Overtime.yes
            ? const Color(0xFFE3F6EA)
            : v == Overtime.no
                ? const Color(0xFFFDECEC)
                : const Color(0xFFF2F7FD),
        border: Border.all(
            color: v == Overtime.yes
                ? const Color(0xFFB5E3C5)
                : v == Overtime.no
                    ? const Color(0xFFF4C4C5)
                    : C.cardStroke),
        borderRadius: BorderRadius.circular(10),
      );

  static Color _valueColour(String v) => v == Overtime.yes
      ? const Color(0xFF148A3A)
      : v == Overtime.no
          ? const Color(0xFFC9353A)
          : (v == _pick || v == _blank)
              ? C.greyLight
              : C.text;

  Widget _dropdown(List<String> items, String current, ValueChanged<String> onPick, {required bool yesNo}) {
    return Container(
      constraints: const BoxConstraints(minHeight: 44),
      decoration: _cellBg(current),
      padding: EdgeInsets.only(left: yesNo ? 4 : 10, right: 2),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          value: items.contains(current) ? current : items.first,
          isExpanded: true,
          isDense: true,
          iconSize: 18,
          icon: const Icon(Icons.arrow_drop_down, color: C.grey),
          items: [
            for (final v in items)
              DropdownMenuItem(
                value: v,
                child: Text(
                  v,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: yesNo ? TextAlign.center : TextAlign.start,
                  style: TextStyle(
                    fontSize: yesNo ? 16 : 15,
                    color: _valueColour(v),
                    fontWeight: v == Overtime.yes || v == Overtime.no ? FontWeight.bold : FontWeight.normal,
                  ),
                ),
              ),
          ],
          selectedItemBuilder: (_) => [
            for (final v in items)
              Container(
                alignment: yesNo ? Alignment.center : Alignment.centerLeft,
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Text(
                  v,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: yesNo ? 16 : 15,
                    color: _valueColour(v),
                    fontWeight: v == Overtime.yes || v == Overtime.no ? FontWeight.bold : FontWeight.normal,
                  ),
                ),
              ),
          ],
          onChanged: (v) {
            if (v != null && v != current) onPick(v);
          },
        ),
      ),
    );
  }

  /// Number box for hours willing to work: accepts 1 to 10.
  Widget _hoursBox(OtRow r) => TextField(
        controller: _hoursFor(r),
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(2)],
        textAlign: TextAlign.center,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: C.deepBlue),
        onTap: () {
          final c = _hoursFor(r);
          c.selection = TextSelection(baseOffset: 0, extentOffset: c.text.length);
        },
        decoration: InputDecoration(
          hintText: _blank,
          hintStyle: const TextStyle(color: C.greyLight),
          isDense: true,
          filled: true,
          fillColor: const Color(0xFFF2F7FD),
          contentPadding: const EdgeInsets.symmetric(horizontal: 2, vertical: 12),
          errorText: _hourErrors[r],
          errorStyle: const TextStyle(fontSize: 10),
          enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: C.cardStroke)),
          focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: C.blue)),
          errorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: C.red)),
          focusedErrorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10), borderSide: const BorderSide(color: C.red)),
        ),
        onChanged: (s) {
          final v = otHours(s);
          if (v == null) {
            setState(() => _hourErrors[r] = '1 to 10');
            return;
          }
          _hourErrors[r] = null;
          r.hours = v;
          store.save();
        },
      );

  Widget _row(List<Widget> cells) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
          for (var i = 0; i < cells.length; i++)
            Expanded(
              flex: i == 0 ? _nameFlex : _cellFlex,
              child: Padding(padding: const EdgeInsets.only(right: 6), child: cells[i]),
            ),
        ]),
      );

  static Widget _label(String s, {Color color = C.grey}) => Text(s,
      textAlign: TextAlign.center, style: TextStyle(fontSize: 14, color: color, fontWeight: FontWeight.bold));

  /// One table row: Name, Fri, Sat, Sun dropdowns and the Hours box.
  Widget _line(OtRow r, List<String> names, {Key? key}) => KeyedSubtree(
        key: key,
        child: _row([
          _dropdown(names, r.name.isEmpty ? _pick : r.name, (v) {
            r.name = v == _pick ? '' : v;
            store.save();
          }, yesNo: false),
          for (var d = 0; d < 3; d++)
            _dropdown(const [_blank, Overtime.yes, Overtime.no], r.day(d).isEmpty ? _blank : r.day(d), (v) {
              r.setDay(d, v == _blank ? '' : v);
              store.save();
            }, yesNo: true),
          _hoursBox(r),
        ]),
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
    prepareOvertime(p);
    // Rows removed elsewhere drop their boxes.
    _hours.removeWhere((r, c) {
      if (ot.rows.contains(r)) return false;
      c.dispose();
      return true;
    });
    final names = overtimeNames(p);
    final card = BoxDecoration(
        color: Colors.white, borderRadius: BorderRadius.circular(22), border: Border.all(color: C.cardStroke));
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 120),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(
          decoration: card,
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            // Title, with an Export button for sending just this sheet as PDF or Excel
            Row(children: [
              const Expanded(
                child: Text('Weekend overtime',
                    style: TextStyle(fontSize: 22, color: C.navy, fontWeight: FontWeight.bold)),
              ),
              Semantics(
                label: 'Export overtime to PDF or Excel',
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
            Padding(
              padding: const EdgeInsets.only(top: 8, bottom: 6),
              child: InkWell(
                onTap: () {
                  ot.include = !ot.include;
                  store.save();
                },
                child: Row(children: [
                  Checkbox(
                    value: ot.include,
                    onChanged: (v) {
                      ot.include = v ?? false;
                      store.save();
                    },
                  ),
                  const Expanded(
                    child: Text('Include in my reports (PDF, Word and email)', style: TextStyle(fontSize: 16)),
                  ),
                ]),
              ),
            ),
            _row([_label('Name'), _label('Fri'), _label('Sat'), _label('Sun'), _label('Hours')]),
            for (final r in ot.rows) _line(r, names, key: ObjectKey(r)),
            // Totals sit under the table; after adding rows the screen scrolls here.
            KeyedSubtree(key: _lastRow, child: _row([
              _label('Yes total'),
              for (var d = 0; d < 3; d++) _label('${ot.yesCount(d)}', color: C.green),
              const SizedBox(),
            ])),
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
            final before = ot.rows.length;
            ot.removeEmptyRows();
            if (ot.rows.length == before) {
              toast(context, 'No empty rows to remove');
              return;
            }
            store.save();
          }),
        ),
        // Notes for the weekend, printed under the table in reports
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
                hintText: 'Add notes about this weekend’s overtime…',
                hintStyle: const TextStyle(color: C.greyLight),
                contentPadding: const EdgeInsets.all(12),
                enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.cardStroke)),
                focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.blue)),
              ),
              onChanged: (s) {
                ot.notes = s;
                store.save();
              },
            ),
          ]),
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: _action('Clear answers', C.deepBlue, () {
            confirm(context, 'Clear the Yes/No answers?', 'Names and notes stay in place, ready for the next weekend.',
                'Clear', () {
              ot.startWeekend(Fmt.upcomingFriday());
              _resetHours();
              store.save();
            });
          }),
        ),
      ]),
    );
  }
}
