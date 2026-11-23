package com.dailyworklog.app;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * A daily log's own Overtime or Attendance sheet. Each day keeps its own copy (made from the day before
 * when the day is started), so an old day's report shows what was entered on that day.
 */
public class DaySheetActivity extends FormScreen {
    private Models.Project project;
    private Models.DailyLog log;
    private boolean overtime;
    private ExportHelper exporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exporter = new ExportHelper(this);
        project = store.project(getIntent().getStringExtra("project"));
        log = project == null ? null : project.findLog(getIntent().getStringExtra("log"));
        if (log == null) {
            finish();
            return;
        }
        overtime = getIntent().getBooleanExtra("overtime", true);
        if (log.overtime == null || log.attendance == null) {
            project.ensureSheets(log);  // a day from before sheets moved into the day
            store.save();
        }
        setTitlesSmallFirst(log.displayTitle(), overtime ? "Overtime" : "Attendance");
        // Let the pane park focus on the scroller when it redraws (like the old tab).
        scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scroll.setFocusableInTouchMode(true);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 120));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (log != null && project.findLog(log.id) == null) finish();
    }

    private void render() {
        if (overtime) OvertimePane.render(this, store, project, log, body);
        else AttendancePane.render(this, store, project, log, body);
    }

    /** The pane's Export button: just this day's sheet, as PDF or Excel. */
    void export() {
        String what = overtime ? "overtime" : "attendance";
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export " + what + " to PDF", () -> go(false)));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Export " + what + " to Excel", () -> go(true)));
        Ui.menu(this, "Send just this day’s " + what + " sheet. The day’s report still includes it when ticked.", items);
    }

    private void go(boolean excel) {
        if (overtime) exporter.exportOvertime(project, log, excel);
        else exporter.exportAttendance(project, log, excel);
    }

}
