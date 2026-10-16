package com.dailyworklog.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Sea Can Movements tab: one line per sea can. Type its identifier, then pick the letter (A to Z),
 * row (1 to 40) and level (1 to 3) it's coming from and the ones it's at now.
 */
final class SeaCanPane {
    private SeaCanPane() {}

    static final int ROWS = 40, LEVELS = 3;
    /** Lines shown to start with; more can be added (there's no limit). */
    static final int START_LINES = 20;
    private static final String BLANK = "–";
    /** Widths on a line: identifier, then letter, row, level on each side. */
    private static final float CAN_W = 2.2f, LETTER_W = 0.7f, ROW_W = 0.9f, LEVEL_W = 0.7f;

    static void render(Activity a, Store store, Models.Project p, LinearLayout box) {
        // Redrawing must not jump the screen back to the top.
        View parent = (View) box.getParent();
        final int scrollY = parent.getScrollY();
        if (box.findFocus() != null) parent.requestFocus();
        box.removeAllViews();
        parent.post(() -> parent.scrollTo(0, scrollY));
        while (p.seaCans.size() < START_LINES) p.seaCans.add(new Models.SeaCanMove());

        LinearLayout card = card(a);
        box.addView(card);
        // Title, with an Export button for sending just this list as PDF, Excel, CSV or Word
        LinearLayout titleRow = new LinearLayout(a);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text(a, "Sea can movements", 22, R.color.navy, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView export = text(a, "Export", 15, R.color.deep_blue, true);
        export.setBackgroundResource(R.drawable.bg_chip_sky);
        export.setPadding(Ui.dp(a, 16), Ui.dp(a, 9), Ui.dp(a, 16), Ui.dp(a, 9));
        export.setContentDescription("Export sea cans to PDF, Excel, CSV or Word");
        export.setOnClickListener(v -> {
            if (a instanceof LogsActivity) ((LogsActivity) a).exportSeaCans();
        });
        titleRow.addView(export);
        card.addView(titleRow);

        CheckBox include = new CheckBox(a);
        include.setText("Include in my reports (PDF, Word and email)");
        include.setTextSize(16);
        include.setChecked(p.seaCanInclude);
        include.setOnCheckedChangeListener((b, on) -> {
            p.seaCanInclude = on;
            store.save();
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(-Ui.dp(a, 6), Ui.dp(a, 8), 0, 0);
        card.addView(include, ilp);
        TextView count = text(a, "", 15, R.color.grey, false);
        count.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        card.addView(count);
        Runnable refreshCount = () -> {
            int n = 0;
            for (Models.SeaCanMove m : p.seaCans) if (!m.can.trim().isEmpty()) n++;
            count.setText(n == 0 ? "Type a sea can number, then pick where it came from and where it is now."
                    : Fmt.plural(n, "sea can", "sea cans") + " entered");
        };
        refreshCount.run();

        // Two header lines: the three columns, then Letter / Row / Level under each location
        float side = LETTER_W + ROW_W + LEVEL_W;
        LinearLayout head = row(a);
        head.addView(cellLabel(a, "Sea can"), weight(a, CAN_W));
        head.addView(cellLabel(a, "From location"), weight(a, side));
        head.addView(new View(a), arrowLp(a));
        head.addView(cellLabel(a, "Current location"), weight(a, side));
        card.addView(head);
        LinearLayout sub = row(a);
        sub.addView(cellLabel(a, "Identifier"), weight(a, CAN_W));
        for (int i = 0; i < 2; i++) {
            if (i == 1) sub.addView(new View(a), arrowLp(a));
            sub.addView(cellLabel(a, "Letter"), weight(a, LETTER_W));
            sub.addView(cellLabel(a, "Row"), weight(a, ROW_W));
            sub.addView(cellLabel(a, "Level"), weight(a, LEVEL_W));
        }
        card.addView(sub);

        LinearLayout table = new LinearLayout(a);
        table.setOrientation(LinearLayout.VERTICAL);
        card.addView(table);
        for (Models.SeaCanMove m : p.seaCans) table.addView(line(a, store, m, refreshCount));

        TextView addTen = action(a, "+  Add 10 rows", R.color.blue);
        addTen.setOnClickListener(v -> {
            View last = null;
            for (int i = 0; i < 10; i++) {
                Models.SeaCanMove m = new Models.SeaCanMove();
                p.seaCans.add(m);
                last = line(a, store, m, refreshCount);
                table.addView(last);
            }
            store.save();
            final View target = last;
            target.post(() -> target.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), false));
        });
        box.addView(addTen);
        TextView trim = action(a, "Remove empty rows", R.color.deep_blue);
        trim.setOnClickListener(v -> {
            int before = p.seaCans.size();
            p.seaCans.removeIf(Models.SeaCanMove::isEmpty);
            if (p.seaCans.size() == before) {
                android.widget.Toast.makeText(a, "No empty rows to remove", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            store.save();
            render(a, store, p, box);
        });
        box.addView(trim);
        TextView clear = action(a, "Clear all", R.color.red);
        clear.setOnClickListener(v -> Ui.confirm(a, "Clear all sea cans?",
                "Every sea can number and location on this tab is cleared. This can't be undone.", "Clear all", () -> {
                    p.seaCans.clear();
                    store.save();
                    render(a, store, p, box);
                }));
        box.addView(clear);
    }

    /** One sea can: identifier box, then From and Current letter / row / level dropdowns. */
    private static LinearLayout line(Activity a, Store store, Models.SeaCanMove m, Runnable refreshCount) {
        LinearLayout line = row(a);
        EditText can = new EditText(a);
        can.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // A long number wraps onto a second line instead of being cut off.
        can.setMaxLines(2);
        can.setHorizontallyScrolling(false);
        can.setTextSize(13);
        can.setTypeface(null, Typeface.BOLD);
        can.setTextColor(a.getColor(R.color.text));
        can.setHint("Sea can #");
        can.setHintTextColor(a.getColor(R.color.grey_light));
        can.setBackgroundResource(R.drawable.bg_cell);
        can.setMinHeight(Ui.dp(a, 44));
        can.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8));
        can.setText(m.can);
        can.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                m.can = s.toString().trim();
                store.save();
                refreshCount.run();
            }
        });
        line.addView(can, weight(a, CAN_W));
        line.addView(dropdown(a, letters(), m.fromLetter, v -> { m.fromLetter = v; store.save(); }), weight(a, LETTER_W));
        line.addView(dropdown(a, numbers(ROWS), m.fromRow, v -> { m.fromRow = v; store.save(); }), weight(a, ROW_W));
        line.addView(dropdown(a, numbers(LEVELS), m.fromLevel, v -> { m.fromLevel = v; store.save(); }), weight(a, LEVEL_W));
        line.addView(arrow(a), arrowLp(a));
        line.addView(dropdown(a, letters(), m.toLetter, v -> { m.toLetter = v; store.save(); }), weight(a, LETTER_W));
        line.addView(dropdown(a, numbers(ROWS), m.toRow, v -> { m.toRow = v; store.save(); }), weight(a, ROW_W));
        line.addView(dropdown(a, numbers(LEVELS), m.toLevel, v -> { m.toLevel = v; store.save(); }), weight(a, LEVEL_W));
        return line;
    }

    private static List<String> numbers(int max) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= max; i++) items.add(String.valueOf(i));
        return items;
    }

    private static List<String> letters() {
        List<String> items = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) items.add(String.valueOf(c));
        return items;
    }

    /** The little arrow between From and Current location: "came from here, went to here". */
    private static View arrow(Activity a) {
        TextView t = new TextView(a);
        t.setText("\u2192");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(13);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{a.getColor(R.color.blue), a.getColor(R.color.deep_blue)});
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        t.setBackground(bg);
        t.setContentDescription("moved to");
        LinearLayout box = new LinearLayout(a);
        box.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        return box;
    }

    private static LinearLayout.LayoutParams arrowLp(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 20), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(a, 4));
        return lp;
    }

    interface Pick {
        void onPick(String value);
    }

    /** Dropdown of the given choices, with a dash first for not picked yet. */
    private static Spinner dropdown(Activity a, List<String> choices, String current, Pick pick) {
        List<String> items = new ArrayList<>();
        items.add(BLANK);
        items.addAll(choices);
        Spinner sp = new Spinner(a, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                boolean blank = position == 0;
                t.setTextSize(15);
                t.setSingleLine(true);
                t.setGravity(Gravity.CENTER);
                t.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
                t.setTypeface(null, blank ? Typeface.NORMAL : Typeface.BOLD);
                t.setTextColor(blank ? a.getColor(R.color.grey_light) : a.getColor(R.color.deep_blue));
                return t;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setBackgroundResource(R.drawable.bg_cell);
        int idx = Math.max(0, items.indexOf(current));
        sp.setSelection(idx, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int last = idx;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == last) return;  // ignore the initial selection
                last = position;
                pick.onPick(position == 0 ? "" : items.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        return sp;
    }

    private static LinearLayout card(Activity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(a, 12);
        card.setPadding(pad, Ui.dp(a, 16), pad, pad);
        return card;
    }

    private static LinearLayout row(Activity a) {
        // Let a swipe that starts on a dropdown scroll the page (as on the Overtime tab).
        LinearLayout r = new LinearLayout(a) {
            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallow) {}
        };
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3));
        return r;
    }

    private static LinearLayout.LayoutParams weight(Activity a, float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.setMarginEnd(Ui.dp(a, 4));
        return lp;
    }

    private static TextView cellLabel(Activity a, String s) {
        TextView t = text(a, s, 11, R.color.grey, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        return t;
    }

    private static TextView text(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(a.getColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private static TextView action(Activity a, String s, int color) {
        TextView t = text(a, s, 18, color, false);
        t.setPadding(Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 4));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        return t;
    }
}
