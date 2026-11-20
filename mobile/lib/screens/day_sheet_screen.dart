// A day's Attendance or Overtime sheet, opened from that day's checklist. Each day keeps its own
// sheets, so a report for an old day shows what was filled in on that day.
import 'package:flutter/material.dart';

import '../data/models.dart';
import '../data/store.dart';
import '../export/export.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'attendance_pane.dart';
import 'overtime_pane.dart';

enum DaySheet { attendance, overtime }

/// One line under a day's Attendance / Overtime button: what's filled in and whether it's on the report.
String daySheetSummary(Project p, DailyLog l, DaySheet kind) {
  if (kind == DaySheet.attendance) {
    final a = l.attendance;
    if (a == null) return 'Not filled in';
    final s = a.summary();
    return [s.isEmpty ? 'No answers yet' : s, if (a.include) 'On report'].join(' · ');
  }
  final ot = l.overtime;
  if (ot == null) return 'Not filled in';
  final n = ot.confirmedCount();
  return [n == 0 ? 'None confirmed' : '$n confirmed', if (ot.include) 'On report'].join(' · ');
}

class DaySheetScreen extends StatefulWidget {
  final String projectId, logId;
  final DaySheet kind;

  const DaySheetScreen({super.key, required this.projectId, required this.logId, required this.kind});

  @override
  State<DaySheetScreen> createState() => _DaySheetScreenState();
}

class _DaySheetScreenState extends State<DaySheetScreen> {
  var _closing = false;

  Store get store => Store.instance;

  @override
  void initState() {
    super.initState();
    // Make and save the day's sheet now, so it keeps the names it started with.
    final p = store.project(widget.projectId);
    final l = p?.findLog(widget.logId);
    if (p == null || l == null) return;
    final fresh = widget.kind == DaySheet.attendance ? l.attendance == null : l.overtime == null;
    if (!fresh) return;
    if (widget.kind == DaySheet.attendance) {
      p.attendanceFor(l);
    } else {
      p.overtimeFor(l);
    }
    // After this frame: saving tells every screen to redraw, which can't happen mid-build.
    WidgetsBinding.instance.addPostFrameCallback((_) => store.save());
  }

  void _export(Project p, DailyLog l) {
    final att = widget.kind == DaySheet.attendance;
    final what = att ? 'attendance' : 'overtime';
    menu(context, 'Send just this day\'s $what sheet. The day\'s report still includes it when ticked.', [
      SheetItem(
        Icons.picture_as_pdf_outlined,
        'Export $what to PDF',
        () => att ? Export.attendance(context, p, l, excel: false) : Export.overtime(context, p, l, excel: false),
      ),
      SheetItem(
        Icons.grid_on,
        'Export $what to Excel',
        () => att ? Export.attendance(context, p, l, excel: true) : Export.overtime(context, p, l, excel: true),
      ),
    ]);
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) {
      final p = store.project(widget.projectId);
      final l = p?.findLog(widget.logId);
      if (p == null || l == null) {
        if (!_closing) {
          _closing = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) Navigator.of(context).maybePop();
          });
        }
        return const Scaffold();
      }
      final att = widget.kind == DaySheet.attendance;
      return Scaffold(
        body: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SizedBox(
                height: 64,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  child: Row(
                    children: [
                      IconButton(
                        iconSize: 28,
                        tooltip: 'Back',
                        icon: const Icon(Icons.arrow_back, color: C.text),
                        onPressed: () => Navigator.of(context).maybePop(),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              att ? 'Attendance' : 'Overtime',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(color: C.text, fontSize: 24),
                            ),
                            Text(
                              l.displayTitle(),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(color: C.grey, fontSize: 14),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Expanded(
                child: att
                    ? AttendancePane(project: p, log: l, onExport: () => _export(p, l))
                    : OvertimePane(project: p, log: l, onExport: () => _export(p, l)),
              ),
            ],
          ),
        ),
      );
    },
  );
}
