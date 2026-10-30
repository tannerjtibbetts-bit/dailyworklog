package com.dailyworklog.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Attendance tab: Name and Here? dropdowns, one row per person. Shares Overtime's look. */
final class AttendancePane {
    private AttendancePane() {}

    private static final String PICK = "Select…", BLANK = "–";
    private static final float NAME_W = 1.9f, STATUS_W = 1.2f, COMMENT_W = 1.8f;
    /** Narrower screens scroll the table sideways instead of squashing it. */
    private static final int MIN_WIDTH_DP = 470;

    static void render(Activity a, Store store, Models.Project p, LinearLayout box) {
        // Redrawing must not jump the screen back to the top.
        View parent = (View) box.getParent();
        final int scrollY = parent.getScrollY();
        View focused = box.findFocus();
        if (focused != null) parent.requestFocus();
        box.removeAllViews();
        parent.post(() -> parent.scrollTo(0, scrollY));
        Models.Attendance at = p.attendance;
        // Rows (with crew names) are only made when there are none, so a removed row stays gone.
        at.prepare(p.crew, Fmt.todayIso());

        LinearLayout card = OvertimePane.card(a);
        box.addView(card);

        // Title, with an Export button for sending just this sheet as PDF or Excel
        LinearLayout titleRow = new LinearLayout(a);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = OvertimePane.text(a, "Attendance", 22, R.color.navy, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView export = OvertimePane.text(a, "Export", 15, R.color.deep_blue, true);
        export.setBackgroundResource(R.drawable.bg_chip_sky);
        export.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
        export.setContentDescription("Export attendance to PDF or Excel");
        export.setOnClickListener(v -> {
            if (a instanceof LogsActivity) ((LogsActivity) a).exportAttendance();
        });
        titleRow.addView(export);
        card.addView(titleRow);

        // Date line with a Change button
        LinearLayout dateRow = new LinearLayout(a);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView date = OvertimePane.text(a, "Date: " + Fmt.longDay(at.date), 16, R.color.text, false);
        dateRow.addView(date, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView change = OvertimePane.text(a, "Change", 16, R.color.blue, true);
        change.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10));
        change.setBackgroundResource(android.R.drawable.list_selector_background);
        change.setContentDescription("Change the attendance date");
        change.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            String[] parts = at.date.split("-");
            int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
            if (parts.length == 3) {
                try {
                    y = Integer.parseInt(parts[0]);
                    m = Integer.parseInt(parts[1]) - 1;
                    d = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    // keep today
                }
            }
            new DatePickerDialog(a, (view, yy, mm, dd) -> {
                at.date = Fmt.iso(yy, mm, dd);
                store.save();
                date.setText("Date: " + Fmt.longDay(at.date));
            }, y, m, d).show();
        });
        dateRow.addView(change);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(a, 4);
        card.addView(dateRow, dlp);

        CheckBox include = new CheckBox(a);
        include.setText("Include in my reports (PDF, Word and email)");
        include.setTextSize(16);
        include.setChecked(at.include);
        include.setOnCheckedChangeListener((b, on) -> {
            at.include = on;
            store.save();
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(-Ui.dp(a, 6), Ui.dp(a, 4), 0, Ui.dp(a, 6));
        card.addView(include, ilp);

        // Header row
        LinearLayout head = OvertimePane.row(a);
        head.addView(OvertimePane.cellLabel(a, "Name"), OvertimePane.weight(a, NAME_W));
        head.addView(OvertimePane.cellLabel(a, "Here?"), OvertimePane.weight(a, STATUS_W));
        head.addView(OvertimePane.cellLabel(a, "Comment"), OvertimePane.weight(a, COMMENT_W));
        head.addView(new View(a), OvertimePane.removeSize(a));
        // The headings and rows scroll sideways together on narrow screens.
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumWidth(Ui.dp(a, MIN_WIDTH_DP));
        android.widget.HorizontalScrollView sideways = new android.widget.HorizontalScrollView(a);
        sideways.setFillViewport(true);
        sideways.addView(grid, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(sideways);
        grid.addView(head);

        List<String> names = new ArrayList<>();
        names.add(PICK);
        names.addAll(p.crew);
        for (Models.Attendance.AttRow r : at.rows) if (!r.name.isEmpty() && !names.contains(r.name)) names.add(r.name);

        // Summary under the table, updated as answers change.
        TextView summary = OvertimePane.text(a, "", 15, R.color.text, true);
        summary.setPadding(0, Ui.dp(a, 10), 0, 0);
        Runnable refresh = () -> {
            String s = at.summary();
            summary.setText(s.isEmpty() ? "Nobody marked yet" : s);
            summary.setTextColor(a.getColor(s.isEmpty() ? R.color.grey : R.color.text));
        };

        LinearLayout table = new LinearLayout(a);
        table.setOrientation(LinearLayout.VERTICAL);
        grid.addView(table);
        for (Models.Attendance.AttRow r : at.rows) table.addView(line(a, store, at, table, r, names, refresh));
        card.addView(summary);
        refresh.run();

        if (p.crew.isEmpty()) {
            TextView hint = OvertimePane.text(a, "Add your crew first: tap ⋯ at the top, then Manage crew.", 15, R.color.grey, false);
            hint.setPadding(0, Ui.dp(a, 10), 0, 0);
            card.addView(hint);
        }

        // Row actions
        TextView addRow = OvertimePane.action(a, "+  Add a row", R.color.blue);
        addRow.setOnClickListener(v -> addRows(a, store, at, table, names, refresh, 1));
        box.addView(addRow);
        TextView addFive = OvertimePane.action(a, "+  Add 5 rows", R.color.blue);
        addFive.setOnClickListener(v -> addRows(a, store, at, table, names, refresh, 5));
        box.addView(addFive);
        TextView trim = OvertimePane.action(a, "Remove empty rows", R.color.deep_blue);
        trim.setOnClickListener(v -> {
            int before = at.rows.size();
            at.removeEmptyRows();
            if (at.rows.size() == before) {
                android.widget.Toast.makeText(a, "No empty rows to remove", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            store.save();
            render(a, store, p, box);
        });
        box.addView(trim);

        // Notes, printed under the table in reports
        LinearLayout notesCard = OvertimePane.card(a);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(a, 18);
        box.addView(notesCard, nlp);
        notesCard.addView(OvertimePane.text(a, "Notes", 20, R.color.navy, true));
        EditText notes = new EditText(a);
        notes.setHint("Add notes about today’s attendance…");
        notes.setHintTextColor(a.getColor(R.color.grey_light));
        notes.setTextColor(a.getColor(R.color.text));
        notes.setTextSize(17);
        notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setSingleLine(false);
        notes.setMinLines(4);
        notes.setGravity(Gravity.TOP | Gravity.START);
        notes.setBackgroundResource(R.drawable.bg_input);
        int np = Ui.dp(a, 12);
        notes.setPadding(np, np, np, np);
        notes.setText(at.notes);
        notes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                at.notes = s.toString();
                store.save();
            }
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Ui.dp(a, 10);
        notesCard.addView(notes, elp);

        TextView clear = OvertimePane.action(a, "Clear answers", R.color.deep_blue);
        clear.setOnClickListener(v -> Ui.confirm(a, "Clear the attendance answers?",
                "Answers and comments are cleared and the date changes to today. Names and notes stay.", "Clear", () -> {
                    at.startDay(Fmt.todayIso());
                    store.save();
                    render(a, store, p, box);
                }));
        box.addView(clear);
    }

    /** Adds rows to the end of the table without redrawing it, then scrolls to the last one. */
    private static void addRows(Activity a, Store store, Models.Attendance at, LinearLayout table,
                                List<String> names, Runnable refresh, int count) {
        View last = null;
        for (int i = 0; i < count; i++) {
            Models.Attendance.AttRow r = new Models.Attendance.AttRow();
            at.rows.add(r);
            last = line(a, store, at, table, r, names, refresh);
            table.addView(last);
        }
        store.save();
        final View target = last;
        target.post(() -> target.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), false));
    }

    /** One table row: Name and Here? dropdowns, the Comment box and the remove button. */
    private static LinearLayout line(Activity a, Store store, Models.Attendance at, LinearLayout table,
                                     Models.Attendance.AttRow r, List<String> names, Runnable refresh) {
        LinearLayout line = OvertimePane.row(a);
        line.addView(OvertimePane.dropdown(a, names, r.name.isEmpty() ? PICK : r.name, (sp, v) -> {
            r.name = PICK.equals(v) ? "" : v;
            store.save();
            refresh.run();
        }, false), OvertimePane.weight(a, NAME_W));
        List<String> statuses = new ArrayList<>();
        statuses.add(BLANK);
        java.util.Collections.addAll(statuses, Models.Attendance.STATUSES);
        line.addView(OvertimePane.dropdown(a, statuses, r.status.isEmpty() ? BLANK : r.status, (sp, v) -> {
            r.status = BLANK.equals(v) ? "" : v;
            store.save();
            sp.setBackgroundResource(OvertimePane.cellBg(r.status));
            refresh.run();
        }, true), OvertimePane.weight(a, STATUS_W));
        line.addView(commentBox(a, store, r), OvertimePane.weight(a, COMMENT_W));
        line.addView(OvertimePane.removeButton(a, () -> r.name, r::isBlank, () -> {
            at.rows.remove(r);
            table.removeView(line);
            store.save();
            refresh.run();
        }), OvertimePane.removeSize(a));
        return line;
    }

    private static EditText commentBox(Activity a, Store store, Models.Attendance.AttRow r) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setTextSize(15);
        e.setTextColor(a.getColor(R.color.text));
        e.setHint("Comment");
        e.setHintTextColor(a.getColor(R.color.grey_light));
        e.setBackgroundResource(R.drawable.bg_cell);
        e.setMinHeight(Ui.dp(a, 44));
        e.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        e.setText(r.comment);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                r.comment = s.toString();
                store.save();
            }
        });
        return e;
    }
}
