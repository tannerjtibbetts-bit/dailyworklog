// A daily log's own Overtime or Attendance sheet. Each day keeps its own copy (made from the day before
// when the day is started), so an old day's report shows what was entered on that day.
import 'package:flutter/material.dart';

import '../data/models.dart';
import '../data/store.dart';
import '../export/export.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'attendance_pane.dart';
import 'note_screen.dart' show SmallTopBar;
import 'overtime_pane.dart';

class DaySheetScreen extends StatefulWidget {
  final String projectId, logId;

  /// true: Overtime; false: Attendance.
  final bool overtime;

  const DaySheetScreen({super.key, required this.projectId, required this.logId, required this.overtime});

  @override
  State<DaySheetScreen> createState() => _DaySheetScreenState();
}

class _DaySheetScreenState extends State<DaySheetScreen> {
  Store get store => Store.instance;

  @override
  void initState() {
    super.initState();
    final p = store.project(widget.projectId);
    final l = p?.logs.where((x) => x.id == widget.logId).firstOrNull;
    if (p != null && l != null && (l.overtime == null || l.attendance == null)) {
      p.ensureSheets(l);
      // Saved after this frame: saving tells every screen to redraw, which can't happen mid-build.
      WidgetsBinding.instance.addPostFrameCallback((_) => store.save());
    }
  }

  void _export(Project p, DailyLog l) {
    final what = widget.overtime ? 'overtime' : 'attendance';
    Future<void> go(bool excel) => widget.overtime
        ? Export.overtime(context, p, l, excel: excel)
        : Export.attendance(context, p, l, excel: excel);
    menu(context, "Send just this day's $what sheet. The day's report still includes it when ticked.", [
      SheetItem(Icons.picture_as_pdf_outlined, 'Export $what to PDF', () => go(false)),
      SheetItem(Icons.grid_on, 'Export $what to Excel', () => go(true)),
    ]);
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) {
      final p = store.project(widget.projectId);
      final l = p?.logs.where((x) => x.id == widget.logId).firstOrNull;
      if (p == null || l == null || l.overtime == null || l.attendance == null) return const Scaffold();
      return Scaffold(
        backgroundColor: C.bg,
        body: SafeArea(
          child: Column(children: [
            SmallTopBar(l.displayTitle(), widget.overtime ? 'Overtime' : 'Attendance'),
            Expanded(
              child: widget.overtime
                  ? OvertimePane(project: p, sheet: l.overtime!, onExport: () => _export(p, l))
                  : AttendancePane(project: p, sheet: l.attendance!, day: l.date, onExport: () => _export(p, l)),
            ),
          ]),
        ),
      );
    },
  );
}
