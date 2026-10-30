package com.dailyworklog.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Overtime tab: weekend sign-up table with Name / Fri / Sat / Sun dropdowns. */
final class OvertimePane {
    private OvertimePane() {}

    private static final String PICK = "Select\u2026", BLANK = "\u2013";
    private static final float NAME_W = 2.2f;

    static void render(Activity a, Store store, Models.Project p, LinearLayout box) {
        // Redrawing must not jump the screen back to the top.
        View parent = (View) box.getParent();
        final int scrollY = parent.getScrollY();
        View focused = box.findFocus();
        if (focused != null) parent.requestFocus();  // park focus on the scroller, not the first Hours box
        box.removeAllViews();
        parent.post(() -> parent.scrollTo(0, scrollY));
        Models.Overtime ot = p.overtime;
        // Rows are only made when there are none, so a removed row stays gone.
        ot.prepare(p.crew.size(), Fmt.upcomingFriday());

        LinearLayout card = card(a);
        box.addView(card);

        // Title, with an Export button for sending just this sheet as PDF or Excel
        LinearLayout titleRow = new LinearLayout(a);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(a, "Weekend overtime", 22, R.color.navy, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView export = text(a, "Export", 15, R.color.deep_blue, true);
        export.setBackgroundResource(R.drawable.bg_chip_sky);
        export.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
        export.setContentDescription("Export overtime to PDF or Excel");
        export.setOnClickListener(v -> {
            if (a instanceof LogsActivity) ((LogsActivity) a).exportOvertime();
        });
        titleRow.addView(export);
        card.addView(titleRow);

        CheckBox include = new CheckBox(a);
        include.setText("Include in my reports (PDF, Word and email)");
        include.setTextSize(16);
        include.setChecked(ot.include);
        include.setOnCheckedChangeListener((b, on) -> {
            ot.include = on;
            store.save();
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(-Ui.dp(a, 6), Ui.dp(a, 8), 0, Ui.dp(a, 6));
        card.addView(include, ilp);

        // Header row
        LinearLayout head = row(a);
        head.addView(cellLabel(a, "Name"), weight(a, NAME_W));
        for (String d : new String[]{"Fri", "Sat", "Sun"}) head.addView(cellLabel(a, d), weight(a, 1f));
        head.addView(cellLabel(a, "Hours"), weight(a, 1f));
        head.addView(new View(a), removeSize(a));
        card.addView(head);

        List<String> names = new ArrayList<>();
        names.add(PICK);
        names.addAll(p.crew);
        for (Models.Overtime.OtRow r : ot.rows) if (!r.name.isEmpty() && !names.contains(r.name)) names.add(r.name);

        // Totals sit under the table and update as answers change.
        TextView[] totalViews = new TextView[3];
        Runnable refreshTotals = () -> {
            for (int d = 0; d < 3; d++) totalViews[d].setText(String.valueOf(ot.yesCount(d)));
        };

        LinearLayout table = new LinearLayout(a);
        table.setOrientation(LinearLayout.VERTICAL);
        card.addView(table);
        for (Models.Overtime.OtRow r : ot.rows) table.addView(line(a, store, ot, table, r, names, refreshTotals));

        LinearLayout totals = row(a);
        totals.addView(cellLabel(a, "Yes total"), weight(a, NAME_W));
        for (int d = 0; d < 3; d++) {
            TextView t = cellLabel(a, "");
            t.setTextColor(a.getColor(R.color.green));
            totalViews[d] = t;
            totals.addView(t, weight(a, 1f));
        }
        totals.addView(cellLabel(a, ""), weight(a, 1f));
        totals.addView(new View(a), removeSize(a));
        card.addView(totals);
        refreshTotals.run();

        if (p.crew.isEmpty()) {
            TextView hint = text(a, "Add your crew first: tap \u22EF at the top, then Manage crew.", 15, R.color.grey, false);
            hint.setPadding(0, Ui.dp(a, 10), 0, 0);
            card.addView(hint);
        }

        // Row actions
        TextView addRow = action(a, "+  Add a row", R.color.blue);
        addRow.setOnClickListener(v -> addRows(a, store, ot, table, names, refreshTotals, 1));
        box.addView(addRow);
        TextView addFive = action(a, "+  Add 5 rows", R.color.blue);
        addFive.setOnClickListener(v -> addRows(a, store, ot, table, names, refreshTotals, 5));
        box.addView(addFive);
        TextView trim = action(a, "Remove empty rows", R.color.deep_blue);
        trim.setOnClickListener(v -> {
            int before = ot.rows.size();
            ot.removeEmptyRows();
            if (ot.rows.size() == before) {
                android.widget.Toast.makeText(a, "No empty rows to remove", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            store.save();
            render(a, store, p, box);
        });
        box.addView(trim);

        // Notes for the weekend, printed under the table in reports
        LinearLayout notesCard = card(a);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(a, 18);
        box.addView(notesCard, nlp);
        notesCard.addView(text(a, "Notes", 20, R.color.navy, true));
        EditText notes = new EditText(a);
        notes.setHint("Add notes about this weekend\u2019s overtime\u2026");
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
        notes.setText(ot.notes);
        notes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                ot.notes = s.toString();
                store.save();
            }
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Ui.dp(a, 10);
        notesCard.addView(notes, elp);

        TextView newWeek = action(a, "Clear answers", R.color.deep_blue);
        newWeek.setOnClickListener(v -> Ui.confirm(a, "Clear the Yes/No answers?",
                "Names and notes stay in place, ready for the next weekend.", "Clear", () -> {
                    ot.startWeekend(Fmt.upcomingFriday());
                    store.save();
                    render(a, store, p, box);
                }));
        box.addView(newWeek);
    }

    /** Adds rows to the end of the table without redrawing it, then scrolls to the last one. */
    private static void addRows(Activity a, Store store, Models.Overtime ot, LinearLayout table,
                                List<String> names, Runnable refreshTotals, int count) {
        View last = null;
        for (int i = 0; i < count; i++) {
            Models.Overtime.OtRow r = new Models.Overtime.OtRow();
            ot.rows.add(r);
            last = line(a, store, ot, table, r, names, refreshTotals);
            table.addView(last);
        }
        store.save();
        final View target = last;
        target.post(() -> target.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), false));
    }

    /** One table row: Name, Fri, Sat, Sun dropdowns, the Hours box and the remove button. */
    private static LinearLayout line(Activity a, Store store, Models.Overtime ot, LinearLayout table,
                                     Models.Overtime.OtRow r, List<String> names, Runnable refreshTotals) {
        LinearLayout line = row(a);
        line.addView(dropdown(a, names, r.name.isEmpty() ? PICK : r.name, (sp, v) -> {
            r.name = PICK.equals(v) ? "" : v;
            store.save();
        }, false), weight(a, NAME_W));
        for (int d = 0; d < 3; d++) {
            final int day = d;
            String cur = day == 0 ? r.fri : day == 1 ? r.sat : r.sun;
            List<String> yn = new ArrayList<>();
            yn.add(BLANK);
            yn.add(Models.Overtime.YES);
            yn.add(Models.Overtime.NO);
            line.addView(dropdown(a, yn, cur.isEmpty() ? BLANK : cur, (sp, v) -> {
                String val = BLANK.equals(v) ? "" : v;
                if (day == 0) r.fri = val;
                else if (day == 1) r.sat = val;
                else r.sun = val;
                store.save();
                sp.setBackgroundResource(cellBg(val));
                refreshTotals.run();
            }, true, false), weight(a, 1f));
        }
        line.addView(hoursBox(a, store, r), weight(a, 1f));
        line.addView(removeButton(a, () -> r.name, () -> Models.Overtime.isBlank(r), () -> {
            ot.rows.remove(r);
            table.removeView(line);
            store.save();
            refreshTotals.run();
        }), removeSize(a));
        return line;
    }

    /** Grey "×" at the end of a row. A blank row goes at once; otherwise it asks first. */
    static ImageView removeButton(Activity a, Supplier<String> name, BooleanSupplier blank, Runnable remove) {
        ImageView b = new ImageView(a);
        b.setImageResource(R.drawable.ic_close);
        b.setColorFilter(a.getColor(R.color.grey));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 20dp icon inside a 32 x 40dp tap area
        b.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
        b.setBackgroundResource(android.R.drawable.list_selector_background);
        b.setContentDescription("Remove row");
        b.setOnClickListener(v -> {
            if (blank.getAsBoolean()) {
                remove.run();
                return;
            }
            String who = name.get().isEmpty() ? "This row" : name.get();
            Ui.confirm(a, "Remove this row?", who + " comes off this sheet. Your crew list doesn't change.",
                    "Remove", remove);
        });
        return b;
    }

    static LinearLayout.LayoutParams removeSize(Activity a) {
        return new LinearLayout.LayoutParams(Ui.dp(a, 32), Ui.dp(a, 40));
    }

    private static boolean isRed(String v) {
        return Models.Overtime.NO.equals(v) || Models.Attendance.AWOL.equals(v);
    }

    /** Box colour: Yes green, No and AWOL red, anything else the normal box. */
    static int cellBg(String v) {
        return Models.Overtime.YES.equals(v) ? R.drawable.bg_cell_yes
                : isRed(v) ? R.drawable.bg_cell_no : R.drawable.bg_cell;
    }

    static LinearLayout card(Activity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(a, 16);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    /** Number box for hours willing to work: accepts 1 to 10. */
    private static EditText hoursBox(Activity a, Store store, Models.Overtime.OtRow r) {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        e.setGravity(Gravity.CENTER);
        e.setTextSize(16);
        e.setTypeface(null, Typeface.BOLD);
        e.setTextColor(a.getColor(R.color.deep_blue));
        e.setHint(BLANK);
        e.setHintTextColor(a.getColor(R.color.grey_light));
        e.setBackgroundResource(R.drawable.bg_cell);
        e.setMinHeight(Ui.dp(a, 44));
        e.setPadding(Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8));
        e.setSelectAllOnFocus(true);
        e.setText(r.hours);
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String v = s.toString().trim();
                if (v.isEmpty()) {
                    r.hours = "";
                    store.save();
                    return;
                }
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException ex) {
                    n = -1;
                }
                if (n < 1 || n > 10) {
                    e.setError("1 to 10");
                    return;
                }
                e.setError(null);
                r.hours = String.valueOf(n);
                store.save();
            }
        });
        return e;
    }

    interface Pick {
        void onPick(Spinner sp, String value);
    }

    static Spinner dropdown(Activity a, List<String> items, String current, Pick pick, boolean yesNo) {
        return dropdown(a, items, current, pick, yesNo, true);
    }

    /** arrow false hides the dropdown arrow so short answers fit narrow cells. */
    static Spinner dropdown(Activity a, List<String> items, String current, Pick pick, boolean yesNo, boolean arrow) {
        Spinner sp = new Spinner(a, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                String v = getItem(position);
                t.setTextSize(yesNo ? 16 : 15);
                t.setSingleLine(true);
                t.setGravity(yesNo ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
                t.setPadding(Ui.dp(a, !arrow ? 2 : yesNo ? 4 : 10), Ui.dp(a, 10), Ui.dp(a, arrow ? 4 : 2), Ui.dp(a, 10));
                t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, arrow ? R.drawable.ic_drop : 0, 0);
                t.setTypeface(null, Models.Overtime.YES.equals(v) || isRed(v) ? Typeface.BOLD : Typeface.NORMAL);
                int col = Models.Overtime.YES.equals(v) ? 0xFF148A3A : isRed(v) ? 0xFFC9353A
                        : (PICK.equals(v) || BLANK.equals(v)) ? a.getColor(R.color.grey_light) : a.getColor(R.color.text);
                t.setTextColor(col);
                return t;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setBackgroundResource(cellBg(current));
        int idx = Math.max(0, items.indexOf(current));
        sp.setSelection(idx, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int last = idx;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == last) return;  // ignore the initial selection
                last = position;
                pick.onPick(sp, items.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        return sp;
    }

    static LinearLayout row(Activity a) {
        // The dropdowns ask the screen to stop scrolling as soon as a finger rests on them,
        // which made the table impossible to scroll. Ignoring that request lets a swipe
        // that starts on a dropdown scroll the screen, while a tap still opens it.
        LinearLayout r = new LinearLayout(a) {
            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallow) {}
        };
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        return r;
    }

    static LinearLayout.LayoutParams weight(Activity a, float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.setMarginEnd(Ui.dp(a, 4));
        return lp;
    }

    static TextView cellLabel(Activity a, String s) {
        TextView t = text(a, s, 14, R.color.grey, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static TextView text(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(a.getColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static TextView action(Activity a, String s, int color) {
        TextView t = text(a, s, 18, color, false);
        t.setPadding(Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 4));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        return t;
    }
}
