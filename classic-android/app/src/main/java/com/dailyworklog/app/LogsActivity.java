package com.dailyworklog.app;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** A project's "To-Dos": daily work logs with progress rings, plus saved templates. */
public class LogsActivity extends AppCompatActivity {
    private Store store;
    private Models.Project project;
    private static final int MODE_LOGS = 0, MODE_TEMPLATES = 1, MODE_LINKS = 2, MODE_NOTIFY = 3, MODE_OVERTIME = 4, MODE_NOTES = 5, MODE_SEACANS = 6;
    private static final int TYPE_CARD = 0, TYPE_LINK = 1, TYPE_NOTICE = 2, TYPE_NOTE = 3;
    private int mode = MODE_LOGS;
    private String query = "";
    private final List<Object> rows = new ArrayList<>();
    private RowAdapter adapter;
    private TextView empty, title, location, pillLogs, pillOvertime, pillLinks, pillNotify, pillNotes, pillSeaCans;
    private EditText search;
    private ExportHelper exporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_logs);
        Ui.applyInsets(findViewById(R.id.root));
        store = Store.get(this);
        project = store.project(getIntent().getStringExtra("project"));
        if (project == null) {
            finish();
            return;
        }
        exporter = new ExportHelper(this);

        title = findViewById(R.id.title);
        location = findViewById(R.id.location);
        empty = findViewById(R.id.empty);
        pillLogs = findViewById(R.id.pillLogs);
        pillOvertime = findViewById(R.id.pillOvertime);
        pillSeaCans = findViewById(R.id.pillSeaCans);
        pillLinks = findViewById(R.id.pillLinks);
        pillNotify = findViewById(R.id.pillNotify);
        pillNotes = findViewById(R.id.pillNotes);
        if (savedInstanceState != null) mode = savedInstanceState.getInt("mode", MODE_LOGS);
        if (mode == MODE_TEMPLATES) mode = MODE_LOGS;  // the Templates tab was retired
        search = findViewById(R.id.search);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.more).setOnClickListener(v -> projectMenu());
        findViewById(R.id.fab).setOnClickListener(v -> {
            if (mode == MODE_NOTIFY) startActivity(new Intent(this, NotifyActivity.class).putExtra("project", project.id));
            else if (mode == MODE_NOTES) openNote(null);
            else if (mode == MODE_LINKS) editLink(null);
            else pickDate(null);
        });
        pillLogs.setOnClickListener(v -> setMode(MODE_LOGS));
        pillOvertime.setOnClickListener(v -> setMode(MODE_OVERTIME));
        pillSeaCans.setOnClickListener(v -> setMode(MODE_SEACANS));
        pillLinks.setOnClickListener(v -> setMode(MODE_LINKS));
        pillNotify.setOnClickListener(v -> setMode(MODE_NOTIFY));
        pillNotes.setOnClickListener(v -> setMode(MODE_NOTES));
        findViewById(R.id.clearAll).setOnClickListener(v -> clearNotes());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                rebuild();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (project == null || store.project(project.id) == null) {
            finish();
            return;
        }
        rebuild();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("mode", mode);
    }

    /** Tab ids for "Show or hide tabs"; Checklists can't be hidden. */
    static final String[] TAB_IDS = {"notify", "links", "overtime", "notes", "seacans"};
    static final String[] TAB_NAMES = {"Notify", "Links", "Overtime", "Notes", "Sea Can Movements"};
    private static final int[] TAB_MODES = {MODE_NOTIFY, MODE_LINKS, MODE_OVERTIME, MODE_NOTES, MODE_SEACANS};

    /** Puts the tabs that aren't hidden into rows of three, in their usual order. */
    private void layoutTabs() {
        LinearLayout row1 = findViewById(R.id.tabRow1), row2 = findViewById(R.id.tabRow2);
        TextView[] pills = {pillNotify, pillLinks, pillOvertime, pillNotes, pillSeaCans};
        row1.removeAllViews();
        row2.removeAllViews();
        List<TextView> shown = new ArrayList<>();
        shown.add(pillLogs);
        for (int i = 0; i < TAB_IDS.length; i++) {
            boolean hidden = store.hiddenTabs.contains(TAB_IDS[i]);
            if (!hidden) shown.add(pills[i]);
            if (hidden && mode == TAB_MODES[i]) mode = MODE_LOGS;
        }
        for (int i = 0; i < shown.size(); i++) {
            TextView pill = shown.get(i);
            LinearLayout row = i < 3 ? row1 : row2;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), pill == pillSeaCans ? 1.6f : 1f);
            if (row.getChildCount() > 0) lp.setMarginStart(Ui.dp(this, 4));
            row.addView(pill, lp);
        }
        row2.setVisibility(row2.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    /** Tick the tabs to show; unticked ones disappear from every project until ticked again. */
    static void chooseTabs(android.app.Activity a, Runnable after) {
        Store store = Store.get(a);
        boolean[] on = new boolean[TAB_IDS.length];
        for (int i = 0; i < TAB_IDS.length; i++) on[i] = !store.hiddenTabs.contains(TAB_IDS[i]);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                .setTitle("Show or hide tabs")
                .setMultiChoiceItems(TAB_NAMES, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton("Save", (d, w) -> {
                    store.hiddenTabs.clear();
                    for (int i = 0; i < TAB_IDS.length; i++) if (!on[i]) store.hiddenTabs.add(TAB_IDS[i]);
                    store.save();
                    if (after != null) after.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setMode(int m) {
        if (mode == m) return;
        mode = m;
        rebuild();
    }

    private void stylePill(TextView pill, boolean on) {
        pill.setBackgroundResource(on ? R.drawable.bg_pill_on : R.drawable.bg_pill_off);
        pill.setTextColor(on ? 0xFFFFFFFF : getColor(R.color.text));
    }

    private void rebuild() {
        layoutTabs();
        title.setText(project.name);
        location.setText(project.location);
        location.setVisibility(project.location.isEmpty() ? View.GONE : View.VISIBLE);
        stylePill(pillLogs, mode == MODE_LOGS);
        stylePill(pillOvertime, mode == MODE_OVERTIME);
        stylePill(pillSeaCans, mode == MODE_SEACANS);
        stylePill(pillLinks, mode == MODE_LINKS);
        stylePill(pillNotify, mode == MODE_NOTIFY);
        stylePill(pillNotes, mode == MODE_NOTES);
        // Overtime and Sea Can Movements are full pages in the same scroller, not lists.
        boolean ot = mode == MODE_OVERTIME || mode == MODE_SEACANS;
        findViewById(R.id.overtimePane).setVisibility(ot ? View.VISIBLE : View.GONE);
        findViewById(R.id.list).setVisibility(ot ? View.GONE : View.VISIBLE);
        findViewById(R.id.searchBox).setVisibility(ot ? View.GONE : View.VISIBLE);
        findViewById(R.id.fab).setVisibility(ot ? View.GONE : View.VISIBLE);
        findViewById(R.id.clearAll).setVisibility(mode == MODE_NOTES && !project.notes.isEmpty() ? View.VISIBLE : View.GONE);
        if (ot) {
            empty.setVisibility(View.GONE);
            if (mode == MODE_SEACANS) SeaCanPane.render(this, store, project, findViewById(R.id.overtimeContent));
            else OvertimePane.render(this, store, project, findViewById(R.id.overtimeContent));
            return;
        }
        search.setHint(mode == MODE_NOTIFY ? "Search sent notes" : mode == MODE_NOTES ? "Search notes" : mode == MODE_LINKS ? "Search links"
                : mode == MODE_TEMPLATES ? "Search templates" : "Search checklists");
        findViewById(R.id.fab).setContentDescription(mode == MODE_NOTIFY ? "New note to crew"
                : mode == MODE_NOTES ? "New note"
                : mode == MODE_LINKS ? "Add link" : "New daily log");
        rows.clear();
        if (mode == MODE_NOTIFY) {
            for (Models.Notice n : project.notices) {
                if (query.isEmpty() || n.note.toLowerCase(Locale.ROOT).contains(query)
                        || Fmt.join(n.to).toLowerCase(Locale.ROOT).contains(query)) rows.add(n);
            }
        } else if (mode == MODE_NOTES) {
            for (Models.Note n : project.notes) {
                if (query.isEmpty() || n.text.toLowerCase(Locale.ROOT).contains(query)) rows.add(n);
            }
        } else if (mode == MODE_LINKS) {
            for (Models.Link l : store.links) {
                if (query.isEmpty() || l.title.toLowerCase(Locale.ROOT).contains(query)
                        || l.url.toLowerCase(Locale.ROOT).contains(query)) rows.add(l);
            }
        } else if (mode == MODE_TEMPLATES) {
            for (Models.Template t : project.templates) {
                if (query.isEmpty() || t.name.toLowerCase(Locale.ROOT).contains(query)) rows.add(t);
            }
        } else {
            List<Models.DailyLog> logs = new ArrayList<>(project.logs);
            logs.sort((a, b) -> {
                int c = b.date.compareTo(a.date);
                return c != 0 ? c : Long.compare(b.createdAt, a.createdAt);
            });
            for (Models.DailyLog l : logs) if (matches(l)) rows.add(l);
        }
        adapter.notifyDataSetChanged();
        if (rows.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            if (!query.isEmpty()) empty.setText("Nothing matches \u201C" + search.getText().toString().trim() + "\u201D");
            else if (mode == MODE_NOTIFY) empty.setText("No notes sent yet.\nTap + to send a crew member a note or photo.\nTexts send straight away; with photos,\nMessages opens and you tap Send.");
            else if (mode == MODE_NOTES) empty.setText("No notes yet.\nTap + to write a note or add photos.\nTick a note to put it in the day's report.");
            else if (mode == MODE_LINKS) empty.setText("No links yet.\nTap + to save a web link to a tool or procedure.\nLinks show up in every project.");
            else if (mode == MODE_TEMPLATES) empty.setText("No templates yet.\nOpen a checklist, tap \u22EF and choose Save Checklist as Template.");
            else empty.setText("No daily logs yet.\nTap + to start today's.");
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private boolean matches(Models.DailyLog l) {
        if (query.isEmpty()) return true;
        if (l.displayTitle().toLowerCase(Locale.ROOT).contains(query)) return true;
        for (Models.Task t : l.tasks) {
            if (t.title.toLowerCase(Locale.ROOT).contains(query)
                    || t.notes.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    // ----- creating logs -----

    private void pickDate(Models.Template template) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            String iso = Fmt.iso(y, m, d);
            if (template != null) create(iso, template.tasks, false, template);
            else startOptions(iso);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void startOptions(String iso) {
        final Models.DailyLog latest = project.latestLog();
        if (latest == null && project.templates.isEmpty()) {
            create(iso, null, false, null);
            return;
        }
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_add, "Blank checklist", () -> create(iso, null, false, null)));
        if (latest != null) {
            int open = latest.tasks.size() - latest.doneCount();
            String from = Fmt.logDate(latest.date);
            List<Models.Task> marked = new ArrayList<>();
            for (Models.Task t : latest.tasks) if (t.nextShift) marked.add(t);
            if (!marked.isEmpty()) {
                items.add(new Ui.SheetItem(R.drawable.ic_carry,
                        "Carry over " + Fmt.plural(marked.size(), "field", "fields") + " marked for next shift",
                        () -> carryMarked(iso, marked)));
            }
            if (open > 0) {
                items.add(new Ui.SheetItem(R.drawable.ic_carry,
                        "Carry over " + Fmt.plural(open, "unfinished field", "unfinished fields") + " from " + from,
                        () -> create(iso, latest.tasks, true, null)));
            }
            if (!latest.tasks.isEmpty()) {
                items.add(new Ui.SheetItem(R.drawable.ic_copy, "Copy all fields from " + from,
                        () -> create(iso, latest.tasks, false, null)));
            }
        }
        for (Models.Template t : project.templates) {
            items.add(new Ui.SheetItem(R.drawable.ic_template, "Template: " + t.name,
                    () -> create(iso, t.tasks, false, t)));
        }
        Ui.menu(this, "Start the " + Fmt.logDate(iso) + " log with", items);
    }

    private void create(String iso, List<Models.Task> source, boolean unfinishedOnly, Models.Template tmpl) {
        Models.DailyLog latest = project.latestLog();
        Models.DailyLog l = new Models.DailyLog();
        l.date = iso;
        if (latest != null) l.crew = new ArrayList<>(latest.crew);
        if (source != null) {
            for (Models.Task t : source) {
                if (unfinishedOnly && t.done) continue;
                Models.Task c = unfinishedOnly ? t.carryCopy() : t.freshCopy();
                c.createdBy = store.me();
                l.tasks.add(c);
            }
        }
        project.logs.add(l);
        if (tmpl != null) {
            project.record(l, Fmt.todayIso(), Models.Extra.TEMPLATE, tmpl.id, tmpl.name,
                    Fmt.plural(tmpl.tasks.size(), "field", "fields"), null, store.me(), System.currentTimeMillis());
        }
        store.save();
        open(l);
    }

    /** New checklist with just the fields marked "Carry over to next shift" (notes kept). */
    private void carryMarked(String iso, List<Models.Task> marked) {
        Models.DailyLog latest = project.latestLog();
        Models.DailyLog l = new Models.DailyLog();
        l.date = iso;
        if (latest != null) l.crew = new ArrayList<>(latest.crew);
        for (Models.Task t : marked) {
            Models.Task c = t.carryCopy();
            c.createdBy = store.me();
            l.tasks.add(c);
        }
        project.logs.add(l);
        store.save();
        open(l);
    }

    private void open(Models.DailyLog l) {
        startActivity(new Intent(this, LogActivity.class)
                .putExtra("project", project.id)
                .putExtra("log", l.id));
    }

    // ----- menus -----

    private void projectMenu() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Rename project", () ->
                Ui.prompt(this, "Rename project", "Project name", project.name, "Save", n -> {
                    project.name = n;
                    store.save();
                    rebuild();
                })));
        items.add(new Ui.SheetItem(R.drawable.ic_pin_small, "Set location", () ->
                Ui.prompt(this, "Location", "e.g. Kincardine, ON", project.location, "Save", n -> {
                    project.location = n;
                    store.save();
                    rebuild();
                })));
        items.add(new Ui.SheetItem(R.drawable.ic_send, "Report email list", () ->
                ExportHelper.editEmailList(this, project, null)));
        items.add(new Ui.SheetItem(R.drawable.ic_person_add, "Manage crew", () ->
                AssignSheet.manage(this, project)));
        items.add(new Ui.SheetItem(R.drawable.ic_reorder, "Show or hide tabs", () -> chooseTabs(this, this::rebuild)));
        Ui.menu(this, project.name, items);
    }

    private void logMenu(Models.DailyLog l) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_send, "Email report", () -> exporter.email(project, l)));
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export to PDF", () -> exporter.export(project, l, false)));
        items.add(new Ui.SheetItem(R.drawable.ic_word, "Export to Word", () -> exporter.export(project, l, true)));
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Rename Checklist", () ->
                Ui.prompt(this, "Rename checklist", "Checklist name", l.displayTitle(), "Save", n -> {
                    l.name = n;
                    store.save();
                    rebuild();
                })));
        items.add(new Ui.SheetItem(R.drawable.ic_copy, "Duplicate Checklist", () -> {
            Models.DailyLog copy = project.duplicate(l, Fmt.todayIso(), store.me());
            store.save();
            open(copy);
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete Checklist", () ->
                Ui.confirm(this, "Delete this checklist?",
                        l.displayTitle() + " and its photos will be removed. This can't be undone.", "Delete", () -> {
                            store.deletePhotos(l);
                            project.logs.remove(l);
                            store.save();
                            rebuild();
                        }), true));
        Ui.menu(this, l.displayTitle(), items);
    }

    private void templateMenu(Models.Template t) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_add, "New checklist from this template", () -> pickDate(t)));
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Rename template", () ->
                Ui.prompt(this, "Rename template", "Template name", t.name, "Save", n -> {
                    t.name = n;
                    store.save();
                    rebuild();
                })));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete template", () ->
                Ui.confirm(this, "Delete template?", t.name + " will be removed. Checklists made from it stay.",
                        "Delete", () -> {
                            project.templates.remove(t);
                            store.save();
                            rebuild();
                        }), true));
        Ui.menu(this, t.name, items);
    }

    // ----- links -----

    private static String normalizeUrl(String raw) {
        String u = raw.trim().replace(" ", "");
        if (u.isEmpty()) return u;
        if (!u.contains("://") && !u.matches("(?i)^(mailto|tel):.*")) u = "https://" + u;
        return u;
    }

    private static String hostOf(String url) {
        String h = Uri.parse(url).getHost();
        if (h == null) return url;
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private void openLink(Models.Link l) {
        // Observation form: open it with your usual answers filled in.
        if (FormPrefill.isFormstack(l.url)) {
            if (!store.formDetails.isEmpty()) {
                openFormFilled(l);
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Fill in your usual answers?")
                        .setMessage("Save your project, location, position and so on once, and this form will open with them filled in.")
                        .setPositiveButton("Set up", (x, w) -> editFormDetails(() -> openLink(l)))
                        .setNegativeButton("Just open", (x, w) -> openPlain(l, l.url))
                        .show();
            }
            return;
        }
        openPlain(l, l.url);
    }

    private void openPlain(Models.Link l, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            // Note the use on today's checklist; it only goes in the report if ticked.
            if (project.record(null, Fmt.todayIso(), Models.Extra.LINK, l.id, l.title, l.url, null,
                    store.me(), System.currentTimeMillis()) != null) {
                store.save();
                rebuild();
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app on this phone can open that link", Toast.LENGTH_LONG).show();
        }
    }

    private void editLink(Models.Link existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_link, null);
        EditText url = v.findViewById(R.id.url);
        EditText name = v.findViewById(R.id.name);
        if (existing != null) {
            url.setText(existing.url);
            name.setText(existing.title);
        } else {
            // Handy: if a web address was just copied, fill it in.
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                String text = clip.getItemAt(0).getText().toString().trim();
                if (text.matches("(?i)^https?://\\S+$")) url.setText(text);
            }
        }
        final AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add link" : "Edit link")
                .setView(v)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String u = normalizeUrl(url.getText().toString());
            if (u.isEmpty() || Uri.parse(u).getHost() == null) {
                url.setError("Enter a web address");
                return;
            }
            String n = name.getText().toString().trim();
            Models.Link l = existing;
            if (l == null) {
                l = new Models.Link();
                store.links.add(l);
            }
            l.url = u;
            l.title = n.isEmpty() ? hostOf(u) : n;
            store.save();
            d.dismiss();
            rebuild();
        }));
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        (url.getText().length() == 0 ? url : name).requestFocus();
    }

    // ----- Formstack form: fill in your usual answers -----

    private void openFormFilled(Models.Link l) {
        FormPrefill.Details d = formDetails();
        AlertDialog busy = new MaterialAlertDialogBuilder(this)
                .setMessage("Filling in your details\u2026").setCancelable(false).create();
        busy.show();
        PhotoUtil.BG.execute(() -> {
            FormPrefill.Result r;
            try {
                r = FormPrefill.build(l.url, FormPrefill.fetch(l.url), d, new java.util.Date());
            } catch (Exception e) {
                r = null;
            }
            final FormPrefill.Result result = r;
            runOnUiThread(() -> {
                busy.dismiss();
                if (result != null && !result.filled.isEmpty() && result.missed.isEmpty()) {
                    openPlain(l, result.url);
                    Toast.makeText(this, "Filled in " + Fmt.plural(result.filled.size(), "answer", "answers")
                            + ". Check them, then Submit.", Toast.LENGTH_LONG).show();
                    return;
                }
                String msg = result == null
                        ? "Couldn't read the form (no signal?). Open it and paste your details in:"
                        : result.filled.isEmpty()
                        ? "Couldn't find the boxes on this form. Open it and paste your details in:"
                        : "Filled in " + Fmt.plural(result.filled.size(), "answer", "answers")
                        + ". Couldn't fill " + Fmt.join(result.missed) + ". Copy and paste these:";
                copyDetails(l, result == null ? l.url : result.url, msg);
            });
        });
    }

    /** Your saved answers plus your name from the Projects screen. */
    private FormPrefill.Details formDetails() {
        FormPrefill.Details d = new FormPrefill.Details();
        d.answers.putAll(store.formDetails.answers);
        d.name = store.userName;
        return d;
    }

    /** Your details, each with a Copy button, plus a button to open the form. */
    private void copyDetails(Models.Link l, String url, String message) {
        FormPrefill.Details d = formDetails();
        List<String[]> list = new ArrayList<>();
        for (FormPrefill.Question q : FormPrefill.QUESTIONS) {
            list.add(new String[]{q.label, d.get(q.id)});
            if (q.id.equals("workGroup")) {
                list.add(new String[]{"Observation Date",
                        new java.text.SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new java.util.Date())});
                list.add(new String[]{"Name of Observer 1", d.name});
            }
        }
        String[][] rows = list.toArray(new String[0][]);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 4), Ui.dp(this, 16), 0);
        TextView intro = new TextView(this);
        intro.setText(message);
        intro.setTextSize(15);
        intro.setTextColor(getColor(R.color.grey));
        intro.setPadding(0, 0, 0, Ui.dp(this, 8));
        box.addView(intro);
        for (String[] row : rows) {
            if (row[1] == null || row[1].trim().isEmpty()) continue;
            LinearLayout line = new LinearLayout(this);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView t = new TextView(this);
            t.setText(row[0] + "\n" + row[1]);
            t.setTextSize(15);
            t.setTextColor(getColor(R.color.text));
            line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView copy = new TextView(this);
            copy.setText("Copy");
            copy.setTextColor(getColor(R.color.deep_blue));
            copy.setBackgroundResource(R.drawable.bg_chip_sky);
            copy.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(row[0], row[1]));
                Toast.makeText(this, "Copied " + row[0], Toast.LENGTH_SHORT).show();
            });
            line.addView(copy);
            box.addView(line);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Your form details")
                .setView(scroll)
                .setPositiveButton("Open form", (x, w) -> openPlain(l, url))
                .setNegativeButton("Close", null)
                .show();
    }

    /** Your usual answers for the form, saved on this phone only. */
    private void editFormDetails(Runnable after) {
        FormPrefill.Details d = store.formDetails;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 4), Ui.dp(this, 22), 0);
        TextView top = new TextView(this);
        top.setText("Leave any blank that changes each time. Your name and today's date are filled in too. Saved on this phone only.");
        top.setTextSize(13);
        top.setTextColor(getColor(R.color.grey));
        box.addView(top);
        FormPrefill.Question[] qs = FormPrefill.QUESTIONS;
        EditText[] boxes = new EditText[qs.length];
        for (int i = 0; i < qs.length; i++) {
            TextView label = new TextView(this);
            label.setText(qs[i].label);
            label.setTextSize(14);
            label.setTextColor(getColor(R.color.grey));
            label.setPadding(0, Ui.dp(this, 10), 0, 0);
            box.addView(label);
            EditText e = new EditText(this);
            e.setHint(qs[i].hint);
            e.setText(d.get(qs[i].id));
            e.setSingleLine(true);
            e.setInputType(qs[i].id.equals("email")
                    ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            boxes[i] = e;
            box.addView(e);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        new MaterialAlertDialogBuilder(this)
                .setTitle("My form details")
                .setView(scroll)
                .setPositiveButton("Save", (x, w) -> {
                    for (int i = 0; i < qs.length; i++) d.answers.put(qs[i].id, boxes[i].getText().toString().trim());
                    store.save();
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    if (after != null) after.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void linkMenu(Models.Link l) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_open, "Open", () -> openLink(l)));
        if (FormPrefill.isFormstack(l.url)) {
            items.add(new Ui.SheetItem(R.drawable.ic_person, "My form details", () -> editFormDetails(null)));
            items.add(new Ui.SheetItem(R.drawable.ic_copy, "Copy my details", () ->
                    copyDetails(l, l.url, "Tap Copy, then paste into the form.")));
            items.add(new Ui.SheetItem(R.drawable.ic_open, "Open without filling in", () -> openPlain(l, l.url)));
        }
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Edit link", () -> editLink(l)));
        items.add(new Ui.SheetItem(R.drawable.ic_copy, "Copy link", () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(l.title, l.url));
            Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_share, "Share link", () -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, l.title)
                    .putExtra(Intent.EXTRA_TEXT, l.url);
            startActivity(Intent.createChooser(i, "Share link"));
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete link", () ->
                Ui.confirm(this, "Delete this link?", l.title + " will be removed from every project.", "Delete", () -> {
                    store.links.remove(l);
                    store.save();
                    rebuild();
                }), true));
        Ui.menu(this, l.title, items);
    }

    // ----- "include in today's report" checkboxes -----

    private void bindInclude(CheckBox cb, String type, String refId, String title, String detail,
                             List<Models.Photo> photos, long usedAt) {
        String today = Fmt.todayIso();
        Models.DailyLog target = project.todayLog(today);
        cb.setOnCheckedChangeListener(null);
        if (target == null) {
            cb.setVisibility(View.GONE);
            return;
        }
        cb.setVisibility(View.VISIBLE);
        cb.setText(target.date.equals(today) ? "Include in today's report"
                : "Include in " + Fmt.logDate(target.date) + " report");
        Models.Extra e = target.findExtra(refId);
        cb.setChecked(e != null && e.include);
        cb.setOnCheckedChangeListener((b, on) -> {
            Models.Extra x = target.findExtra(refId);
            if (x == null) {
                x = project.record(target, today, type, refId, title, detail, photos, store.me(),
                        usedAt > 0 ? usedAt : System.currentTimeMillis());
            }
            x.include = on;
            store.save();
            b.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        });
    }

    // ----- notify -----

    private void noticeMenu(Models.Notice n) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_send, "Send again", () -> {
            List<String> numbers = new ArrayList<>();
            for (String name : n.to) numbers.add(project.phoneOf(name));
            List<java.io.File> files = new ArrayList<>();
            for (Models.Photo p : n.photos) files.add(store.photoFile(p.file));
            SmsHelper.send(this, numbers, SmsHelper.noticeText(project, n.note, store.me(), store.contactPhone), files);
        }));
        if (!n.photos.isEmpty()) {
            items.add(new Ui.SheetItem(R.drawable.ic_gallery, "View photos", () ->
                    Ui.showPhoto(this, n.photos.get(0), store.photoFile(n.photos.get(0).file))));
        }
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete from list", () ->
                Ui.confirm(this, "Delete this note?", "It's removed from this list and its photos are deleted. Texts already sent stay on their phones.",
                        "Delete", () -> {
                            store.deletePhotos(n);
                            project.notices.remove(n);
                            store.save();
                            rebuild();
                        }), true));
        Ui.menu(this, "To " + Fmt.join(n.to), items);
    }

    // ----- overtime -----

    /** Overtime tab's Export button: just that sheet, as PDF or Excel. */
    void exportSeaCans() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export sea cans to PDF", () -> exporter.exportSeaCans(project, "pdf")));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Export sea cans to Excel", () -> exporter.exportSeaCans(project, "xlsx")));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Export sea cans to CSV", () -> exporter.exportSeaCans(project, "csv")));
        items.add(new Ui.SheetItem(R.drawable.ic_word, "Export sea cans to Word", () -> exporter.exportSeaCans(project, "docx")));
        Ui.menu(this, "Send just the sea can list. Your daily report still includes it when ticked.", items);
    }

    void exportOvertime() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export overtime to PDF", () -> exporter.exportOvertime(project, false)));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Export overtime to Excel", () -> exporter.exportOvertime(project, true)));
        Ui.menu(this, "Send just the overtime sheet. Your daily report still includes it when ticked.", items);
    }

    // ----- notes -----

    private void openNote(Models.Note n) {
        Intent i = new Intent(this, NoteActivity.class).putExtra("project", project.id);
        if (n != null) i.putExtra("note", n.id);
        startActivity(i);
    }

    private void noteMenu(Models.Note n) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Open note", () -> openNote(n)));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete note", () -> deleteNote(n), true));
        Ui.menu(this, "Note from " + Fmt.longStamp(n.createdAt), items);
    }

    private void deleteNote(Models.Note n) {
        Ui.confirm(this, "Delete this note?", "The note and its photos are deleted and taken off any report.",
                "Delete", () -> {
                    store.deletePhotos(n);
                    project.notes.remove(n);
                    project.forgetExtras(n.id);
                    store.save();
                    rebuild();
                });
    }

    /** Deletes every note in this project (all of them, not just the ones a search shows). */
    private void clearNotes() {
        int count = project.notes.size();
        if (count == 0) return;
        Ui.confirm(this, "Delete all " + Fmt.plural(count, "note", "notes") + "?",
                "Every note in " + project.name + " and its photos are deleted and taken off any report. This can't be undone.",
                "Delete all", () -> {
                    for (Models.Note n : new ArrayList<>(project.notes)) {
                        store.deletePhotos(n);
                        project.forgetExtras(n.id);
                    }
                    project.notes.clear();
                    store.save();
                    rebuild();
                });
    }

    // ----- list -----

    private class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        class NoticeVH extends RecyclerView.ViewHolder {
            final TextView title, note, when;
            final ImageView thumb, icon, del;
            final CheckBox include;

            NoticeVH(View v) {
                super(v);
                del = v.findViewById(R.id.del);
                title = v.findViewById(R.id.title);
                note = v.findViewById(R.id.note);
                when = v.findViewById(R.id.when);
                thumb = v.findViewById(R.id.thumb);
                icon = v.findViewById(R.id.icon);
                include = v.findViewById(R.id.include);
            }
        }

        class LinkVH extends RecyclerView.ViewHolder {
            final TextView title, url;
            final CheckBox include;

            LinkVH(View v) {
                super(v);
                title = v.findViewById(R.id.title);
                url = v.findViewById(R.id.url);
                include = v.findViewById(R.id.include);
            }
        }

        class VH extends RecyclerView.ViewHolder {
            final ProgressRing ring;
            final ImageView icon;
            final TextView title, sub;
            final CheckBox include;
            final LinearLayout badges;

            VH(View v) {
                super(v);
                ring = v.findViewById(R.id.ring);
                icon = v.findViewById(R.id.icon);
                title = v.findViewById(R.id.title);
                sub = v.findViewById(R.id.sub);
                include = v.findViewById(R.id.include);
                badges = v.findViewById(R.id.badges);
            }
        }

        @Override
        public int getItemViewType(int position) {
            Object r = rows.get(position);
            if (r instanceof Models.Notice) return TYPE_NOTICE;
            if (r instanceof Models.Note) return TYPE_NOTE;
            return r instanceof Models.Link ? TYPE_LINK : TYPE_CARD;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_LINK) return new LinkVH(inf.inflate(R.layout.item_link, parent, false));
            if (viewType == TYPE_NOTICE || viewType == TYPE_NOTE) return new NoticeVH(inf.inflate(R.layout.item_notice, parent, false));
            return new VH(inf.inflate(R.layout.item_log, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (row instanceof Models.Notice) {
                Models.Notice n = (Models.Notice) row;
                NoticeVH nh = (NoticeVH) holder;
                nh.icon.setImageResource(R.drawable.ic_send);
                nh.del.setVisibility(View.GONE);
                nh.title.setText("To " + Fmt.join(n.to));
                nh.note.setText(n.note.isEmpty() ? Fmt.plural(n.photos.size(), "photo", "photos") : n.note);
                String extra = n.photos.isEmpty() ? "" : ", " + Fmt.plural(n.photos.size(), "photo", "photos");
                nh.when.setText("Sent " + Fmt.longStamp(n.sentAt) + extra);
                bindInclude(nh.include, Models.Extra.NOTIFY, n.id, Fmt.join(n.to), n.note, n.photos, n.sentAt);
                if (n.photos.isEmpty()) {
                    nh.thumb.setVisibility(View.GONE);
                } else {
                    nh.thumb.setVisibility(View.VISIBLE);
                    ImageLoader.load(nh.thumb, store.photoFile(n.photos.get(0).file), 200);
                }
                nh.itemView.setOnClickListener(v -> noticeMenu(n));
                return;
            }
            if (row instanceof Models.Note) {
                Models.Note n = (Models.Note) row;
                NoticeVH nh = (NoticeVH) holder;
                nh.icon.setImageResource(R.drawable.ic_edit);
                nh.del.setVisibility(View.VISIBLE);
                nh.del.setOnClickListener(v -> deleteNote(n));
                nh.title.setText(Fmt.longStamp(n.createdAt));
                nh.note.setText(n.text.isEmpty() ? Fmt.plural(n.photos.size(), "photo", "photos") : n.text);
                String by = n.by.isEmpty() || "Me".equals(n.by) ? "" : "By " + n.by;
                String pics = n.photos.isEmpty() ? "" : Fmt.plural(n.photos.size(), "photo", "photos");
                nh.when.setText(by.isEmpty() || pics.isEmpty() ? by + pics : by + ", " + pics);
                nh.when.setVisibility(by.isEmpty() && pics.isEmpty() ? View.GONE : View.VISIBLE);
                bindInclude(nh.include, Models.Extra.NOTE, n.id, n.by, n.text, n.photos, 0);
                if (n.photos.isEmpty()) {
                    nh.thumb.setVisibility(View.GONE);
                } else {
                    nh.thumb.setVisibility(View.VISIBLE);
                    ImageLoader.load(nh.thumb, store.photoFile(n.photos.get(0).file), 200);
                }
                nh.itemView.setOnClickListener(v -> openNote(n));
                nh.itemView.setOnLongClickListener(v -> {
                    noteMenu(n);
                    return true;
                });
                return;
            }
            if (row instanceof Models.Link) {
                Models.Link l = (Models.Link) row;
                LinkVH lh = (LinkVH) holder;
                lh.title.setText(l.title);
                lh.url.setText(l.url.replaceFirst("(?i)^https?://(www\\.)?", ""));
                bindInclude(lh.include, Models.Extra.LINK, l.id, l.title, l.url, null, 0);
                lh.itemView.setOnClickListener(v -> openLink(l));
                lh.itemView.setOnLongClickListener(v -> {
                    linkMenu(l);
                    return true;
                });
                return;
            }
            VH h = (VH) holder;
            if (row instanceof Models.DailyLog) {
                Models.DailyLog l = (Models.DailyLog) row;
                int done = l.doneCount(), total = l.tasks.size();
                h.ring.setVisibility(View.VISIBLE);
                h.include.setVisibility(View.GONE);
                h.include.setOnCheckedChangeListener(null);
                h.icon.setVisibility(View.GONE);
                h.ring.setFilledWhenComplete(true);
                h.ring.setProgress(total == 0 ? 0f : done / (float) total, false);
                h.title.setText(l.displayTitle());
                h.sub.setText(done + "/" + total + " fields completed");
                Ui.fillBadges(h.badges, l.people(), 44, 2);
                h.itemView.setOnClickListener(v -> open(l));
                h.itemView.setOnLongClickListener(v -> {
                    logMenu(l);
                    return true;
                });
            } else {
                Models.Template t = (Models.Template) row;
                h.ring.setVisibility(View.GONE);
                h.icon.setVisibility(View.VISIBLE);
                h.title.setText(t.name);
                h.sub.setText(Fmt.plural(t.tasks.size(), "field", "fields"));
                bindInclude(h.include, Models.Extra.TEMPLATE, t.id, t.name,
                        Fmt.plural(t.tasks.size(), "field", "fields"), null, 0);
                h.badges.removeAllViews();
                h.itemView.setOnClickListener(v -> pickDate(t));
                h.itemView.setOnLongClickListener(v -> {
                    templateMenu(t);
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
