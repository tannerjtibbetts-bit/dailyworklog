package com.dailyworklog.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Review screen for fields read from a photo or spreadsheet: pick the task column,
 * untick rows that aren't tasks, fix any misread words, then add them to the checklist.
 */
public class ImportActivity extends AppCompatActivity {
    /** Rows handed over from the checklist screen (kept out of the Intent; they can be large). */
    private static List<List<String>> pending;

    /** True when the rows are whole tasks read from the zone sheet: [title, notes, side, colour]. */
    private static boolean pendingZone;

    /** Tasks read from a photo of the zone sheet; your (dark blue) tasks start ticked. */
    static void startZone(Context c, String projectId, String logId, List<ZoneSheet.Task> tasks) {
        List<List<String>> rows = new ArrayList<>();
        for (ZoneSheet.Task t : tasks) {
            List<String> r = new ArrayList<>();
            r.add(t.title());
            r.add(t.notes());
            r.add(t.side);
            r.add(t.colour);
            rows.add(r);
        }
        String where = Models.Task.NORTH.equals(tasks.get(0).side) ? "the North sheet"
                : Models.Task.SOUTH.equals(tasks.get(0).side) ? "the South sheet" : "the zone sheet";
        start(c, projectId, logId, rows, where, false);
        pendingZone = true;
    }

    static void start(Context c, String projectId, String logId, List<List<String>> rows, String source, boolean header) {
        pending = rows;
        pendingZone = false;
        c.startActivity(new Intent(c, ImportActivity.class)
                .putExtra("project", projectId).putExtra("log", logId)
                .putExtra("source", source).putExtra("header", header));
    }

    private Store store;
    private Models.DailyLog log;
    private List<List<String>> rows;
    private boolean zone;
    private int column;
    private boolean notesFromOthers = true;
    private final Set<Integer> unticked = new HashSet<>();
    private final Map<String, String> edits = new HashMap<>();

    private LinearLayout list;
    private TextView add;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = Store.get(this);
        Models.Project project = store.project(getIntent().getStringExtra("project"));
        log = project == null ? null : project.findLog(getIntent().getStringExtra("log"));
        rows = pending;
        if (log == null || rows == null || rows.isEmpty()) {
            finish();
            return;
        }
        zone = pendingZone;
        column = zone ? 0 : SheetImport.likelyTaskColumn(rows);
        if (savedInstanceState != null) {
            column = savedInstanceState.getInt("column", column);
            notesFromOthers = savedInstanceState.getBoolean("notes", true);
            ArrayList<Integer> off = savedInstanceState.getIntegerArrayList("unticked");
            if (off != null) unticked.addAll(off);
            ArrayList<String> keys = savedInstanceState.getStringArrayList("editKeys");
            ArrayList<String> vals = savedInstanceState.getStringArrayList("editVals");
            if (keys != null && vals != null) for (int i = 0; i < keys.size(); i++) edits.put(keys.get(i), vals.get(i));
        } else {
            // Rows that are only numbers or symbols are rarely tasks; start them unticked.
            for (int i = 0; i < rows.size(); i++) if (!SheetImport.hasLetters(rows.get(i).get(column))) unticked.add(i);
            // A spreadsheet's first row is almost always its headings.
            if (getIntent().getBooleanExtra("header", false) && rows.size() > 1) unticked.add(0);
            // Zone sheet: only tasks in your profile's colours start ticked.
            if (zone) for (int i = 0; i < rows.size(); i++) if (!store.myColours.contains(rows.get(i).get(3))) unticked.add(i);
        }
        build(project.name);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("column", column);
        out.putBoolean("notes", notesFromOthers);
        out.putIntegerArrayList("unticked", new ArrayList<>(unticked));
        out.putStringArrayList("editKeys", new ArrayList<>(edits.keySet()));
        ArrayList<String> vals = new ArrayList<>();
        for (String k : edits.keySet()) vals.add(edits.get(k));
        out.putStringArrayList("editVals", vals);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            pending = null;
            pendingZone = false;
        }
    }

    // ----- layout -----

    private void build(String projectName) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        // Top bar
        FrameLayout top = new FrameLayout(this);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER);
        TextView proj = label(projectName.toUpperCase(), 13, R.color.grey, false);
        proj.setSingleLine(true);
        titles.addView(proj);
        titles.addView(label("Add fields", 21, R.color.navy, false));
        top.addView(titles, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 72)));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(getColor(R.color.navy));
        back.setBackgroundResource(android.R.color.transparent);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.START | Gravity.CENTER_VERTICAL);
        blp.leftMargin = Ui.dp(this, 10);
        top.addView(back, blp);
        root.addView(top);

        NestedScrollView scroll = new NestedScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        body.setPadding(pad, 0, pad, Ui.dp(this, 120));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        String source = getIntent().getStringExtra("source");
        TextView intro = zone ? label("Found " + Fmt.plural(rows.size(), "task", "tasks") + " on " + source
                + ". Tasks in your colours (set with the person icon on the Projects screen) are ticked; tick any others"
                + " you want too. Tap a task to fix a word.",
                16, R.color.text, false) : label("Found " + Fmt.plural(rows.size(), "row", "rows")
                + (source == null ? "" : " in " + source) + ". Untick anything that isn't a task, and tap a line to fix a word."
                + " If one task is split over several rows, tick just those and tap Combine ticked.",
                16, R.color.text, false);
        intro.setGravity(Gravity.START);
        body.addView(intro);

        int width = rows.get(0).size();
        if (width > 1 && !zone) {
            TextView colLabel = label("Which column has the tasks?", 16, R.color.navy, true);
            colLabel.setGravity(Gravity.START);
            colLabel.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
            body.addView(colLabel);
            List<String> options = new ArrayList<>();
            for (int c = 0; c < width; c++) options.add("Column " + (c + 1) + ": " + sample(c));
            Spinner pick = new Spinner(this, Spinner.MODE_DIALOG);
            ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            pick.setAdapter(ad);
            pick.setBackgroundResource(R.drawable.bg_input);
            pick.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            pick.setSelection(column, false);
            pick.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == column) return;
                    column = position;
                    unticked.clear();
                    for (int i = 0; i < rows.size(); i++) if (!SheetImport.hasLetters(rows.get(i).get(column))) unticked.add(i);
                    bindRows();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            body.addView(pick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            CheckBox notes = new CheckBox(this);
            notes.setText("Put the other columns in each field's notes");
            notes.setTextSize(16);
            notes.setChecked(notesFromOthers);
            notes.setOnCheckedChangeListener((b, on) -> {
                notesFromOthers = on;
                bindRows();
            });
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.setMargins(-Ui.dp(this, 6), Ui.dp(this, 8), 0, 0);
            body.addView(notes, nlp);
        }

        LinearLayout quick = new LinearLayout(this);
        quick.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        TextView all = link("Tick all");
        all.setOnClickListener(v -> {
            unticked.clear();
            bindRows();
        });
        TextView none = link("Untick all");
        none.setOnClickListener(v -> {
            for (int i = 0; i < rows.size(); i++) unticked.add(i);
            bindRows();
        });
        TextView combine = link("Combine ticked");
        combine.setOnClickListener(v -> combineTicked());
        quick.addView(all);
        quick.addView(none);
        quick.addView(combine);
        body.addView(quick);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(R.drawable.bg_card);
        list.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        body.addView(list);

        // Bottom button
        FrameLayout frame = new FrameLayout(this);
        frame.addView(root);
        add = new TextView(this);
        add.setBackgroundResource(R.drawable.bg_pill_blue);
        add.setTextColor(0xFFFFFFFF);
        add.setTextSize(20);
        add.setTypeface(null, Typeface.BOLD);
        add.setGravity(Gravity.CENTER);
        add.setElevation(Ui.dp(this, 6));
        add.setOnClickListener(v -> addFields());
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64), Gravity.BOTTOM);
        alp.setMargins(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 16));
        frame.addView(add, alp);
        setContentView(frame);
        Ui.applyInsets(frame);
        bindRows();
    }

    private String sample(int c) {
        for (List<String> r : rows) {
            String s = r.get(c);
            if (!s.isEmpty()) return s.length() > 30 ? s.substring(0, 30) + "…" : s;
        }
        return "(empty)";
    }

    private void bindRows() {
        list.removeAllViews();
        for (int i = 0; i < rows.size(); i++) {
            final int idx = i;
            String title = title(i);
            if (title.isEmpty()) continue;
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            CheckBox box = new CheckBox(this);
            box.setChecked(!unticked.contains(i));
            box.setOnCheckedChangeListener((b, on) -> {
                if (on) unticked.remove(idx);
                else unticked.add(idx);
                updateButton();
            });
            line.addView(box);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t = label(title, 17, R.color.text, false);
            t.setGravity(Gravity.START);
            texts.addView(t);
            String notes = notes(i);
            if (zone) notes = colourLabel(rows.get(i).get(3)) + (notes.isEmpty() ? "" : "\n" + notes);
            if (!notes.isEmpty()) {
                TextView n = label(notes, 14, R.color.grey, false);
                n.setGravity(Gravity.START);
                texts.addView(n);
            }
            texts.setBackgroundResource(android.R.drawable.list_selector_background);
            texts.setOnClickListener(v -> Ui.prompt(this, "Fix this field", "Field", title(idx), "Save", s -> {
                edits.put(idx + ":" + column, s);
                unticked.remove(idx);
                bindRows();
            }));
            line.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            list.addView(line);
        }
        updateButton();
    }

    private String title(int row) {
        String e = edits.get(row + ":" + column);
        return e != null ? e : rows.get(row).get(column).trim();
    }

    private static String colourLabel(String colour) {
        switch (colour.split(" ")[0]) {
            case ZoneSheet.BLUE: return "\u25A0 Dark blue";
            case ZoneSheet.ORANGE: return "\u25A0 Orange (maintenance)";
            case ZoneSheet.PINK: return "\u25A0 Pink (electrical)";
            case ZoneSheet.GREEN: return "\u25A0 Green (completed)";
            case ZoneSheet.YELLOW: return "\u25A0 Yellow (ongoing)";
            default: return "\u25A1 No colour";
        }
    }

    private String notes(int row) {
        if (zone) return rows.get(row).get(1).trim();
        if (!notesFromOthers) return "";
        StringBuilder sb = new StringBuilder();
        List<String> r = rows.get(row);
        for (int c = 0; c < r.size(); c++) {
            if (c == column || r.get(c).trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(r.get(c).trim());
        }
        return sb.toString();
    }

    // ----- combine -----

    /** Joins the ticked rows into one field (you can edit the wording first). */
    private void combineTicked() {
        List<Integer> pick = chosen();
        if (pick.size() < 2) {
            Toast.makeText(this, "Tick two or more rows to combine them", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (int i : pick) {
            if (joined.length() > 0) joined.append(' ');
            joined.append(title(i));
        }
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(joined.toString());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(2);
        et.setSelection(et.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(et);
        androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Combine " + pick.size() + " rows into one field")
                .setView(box)
                .setPositiveButton("Combine", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String text = et.getText().toString().trim().replaceAll("\\s*\\n\\s*", " ");
            if (text.isEmpty()) {
                et.setError("Required");
                return;
            }
            d.dismiss();
            combine(pick, text);
            Toast.makeText(this, "Combined " + pick.size() + " rows into one field", Toast.LENGTH_SHORT).show();
        }));
        d.show();
    }

    /**
     * Replaces the rows in pick (ascending) with one row at the first one's place: the task column
     * gets text, every other column joins the rows' values. Ticks and fixes on other rows are kept.
     */
    private void combine(List<Integer> pick, String text) {
        // Bake in any fixes so they move with their rows.
        for (Map.Entry<String, String> e : edits.entrySet()) {
            String[] k = e.getKey().split(":");
            rows.get(Integer.parseInt(k[0])).set(Integer.parseInt(k[1]), e.getValue());
        }
        edits.clear();
        Set<Integer> newUnticked = new HashSet<>();
        int at = 0;
        Set<Integer> gone = new HashSet<>(pick.subList(1, pick.size()));
        for (int i = 0; i < rows.size(); i++) {
            if (gone.contains(i)) continue;
            if (unticked.contains(i)) newUnticked.add(at);
            at++;
        }
        SheetImport.combineRows(rows, pick, column, text);  // same list the screen opened with, so it survives a rotation
        unticked.clear();
        unticked.addAll(newUnticked);
        bindRows();
    }

    private List<Integer> chosen() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) if (!unticked.contains(i) && !title(i).isEmpty()) out.add(i);
        return out;
    }

    private void updateButton() {
        int n = chosen().size();
        add.setText(n == 0 ? "Tick the fields to add" : "Add " + Fmt.plural(n, "field", "fields"));
        add.setAlpha(n == 0 ? 0.5f : 1f);
    }

    private void addFields() {
        List<Integer> pick = chosen();
        if (pick.isEmpty()) return;
        for (int i : pick) {
            Models.Task t = new Models.Task();
            t.title = Fmt.capitalize(title(i));
            t.notes = notes(i);
            if (zone) {
                String side = rows.get(i).get(2).trim().split(" ")[0];
                if (Models.Task.NORTH.equals(side) || Models.Task.SOUTH.equals(side)) t.side = side;
            }
            t.createdBy = store.me();
            log.tasks.add(t);
        }
        store.save();
        Toast.makeText(this, "Added " + Fmt.plural(pick.size(), "field", "fields"), Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView link(String s) {
        TextView t = label(s, 16, R.color.blue, false);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        return t;
    }
}
