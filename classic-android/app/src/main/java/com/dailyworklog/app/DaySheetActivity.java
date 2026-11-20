package com.dailyworklog.app;

import android.os.Bundle;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** A day's Attendance or Overtime sheet, opened from that day's checklist. Each day keeps its own sheets,
 *  so a report for an old day shows what was filled in on that day. */
public class DaySheetActivity extends FormScreen {
    static final String KIND_ATTENDANCE = "attendance", KIND_OVERTIME = "overtime";

    private Models.Project project;
    private Models.DailyLog log;
    private boolean attendance;
    private ExportHelper exporter;

    /** One line under a day's Attendance / Overtime button: what's filled in and whether it's on the report. */
    static String summary(Models.DailyLog l, boolean attendance) {
        if (attendance) {
            Models.Attendance a = l.attendance;
            if (a == null) return "Not filled in";
            String s = a.summary();
            return (s.isEmpty() ? "No answers yet" : s) + (a.include ? " · On report" : "");
        }
        Models.Overtime ot = l.overtime;
        if (ot == null) return "Not filled in";
        int n = ot.confirmedCount();
        return (n == 0 ? "None confirmed" : n + " confirmed") + (ot.include ? " · On report" : "");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        project = store.project(getIntent().getStringExtra("project"));
        log = project == null ? null : project.findLog(getIntent().getStringExtra("log"));
        if (project == null || log == null) {
            finish();
            return;
        }
        attendance = KIND_ATTENDANCE.equals(getIntent().getStringExtra("kind"));
        exporter = new ExportHelper(this);
        setTitles(attendance ? "Attendance" : "Overtime", log.displayTitle());
        // Like the old tab: typing in a box doesn't jump the page, and nothing grabs focus at the start.
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scroll.setFocusableInTouchMode(true);
        boolean fresh = attendance ? log.attendance == null : log.overtime == null;
        if (attendance) project.attendanceFor(log);
        else project.overtimeFor(log);
        if (fresh) store.save();
        render();
    }

    private void render() {
        if (attendance) AttendancePane.render(this, store, project, log, body);
        else OvertimePane.render(this, store, project, log, body);
    }

    /** The sheet's Export button: just this day's sheet, as PDF or Excel. */
    void export() {
        String what = attendance ? "attendance" : "overtime";
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export " + what + " to PDF", () -> {
            if (attendance) exporter.exportAttendance(project, log, false);
            else exporter.exportOvertime(project, log, false);
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Export " + what + " to Excel", () -> {
            if (attendance) exporter.exportAttendance(project, log, true);
            else exporter.exportOvertime(project, log, true);
        }));
        Ui.menu(this, "Send just this day's " + what + " sheet. The day's report still includes it when ticked.", items);
    }
}
