package com.dailyworklog.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One daily work log: header with progress, filter chips, and the list of fields. */
public class LogActivity extends AppCompatActivity {
    private static final int TYPE_HEADER = 0, TYPE_TASK = 1, TYPE_FOOTER = 2;
    private static final Object PAYLOAD_CHECK = new Object();
    private static final Object PAYLOAD_PROGRESS = new Object();
    private static final int F_NONE = 0, F_ME = 1, F_INCOMPLETE = 2, F_COMPLETE = 3, F_UNASSIGNED = 4;

    private Store store;
    private Models.Project project;
    private Models.DailyLog log;
    private int filter = F_NONE;
    private final List<Models.Task> visible = new ArrayList<>();

    private RecyclerView list;
    private LinearLayoutManager lm;
    private Adapter adapter;
    private View topTitle, topDivider, mic;
    private TextView topLog;
    private boolean topShown;

    private String pendingTaskId, pendingFile, pendingScan;
    private ActivityResultLauncher<Uri> takePicture, scanCamera;
    private ActivityResultLauncher<String> scanGallery, scanFile, scanText;
    private ActivityResultLauncher<Intent> voice;
    private ExportHelper exporter;
    private DoneText doneText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_log);
        Ui.applyInsets(findViewById(R.id.root));
        store = Store.get(this);
        project = store.project(getIntent().getStringExtra("project"));
        log = project == null ? null : project.findLog(getIntent().getStringExtra("log"));
        if (log == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            pendingTaskId = savedInstanceState.getString("pendingTask");
            pendingFile = savedInstanceState.getString("pendingFile");
            pendingScan = savedInstanceState.getString("pendingScan");
            filter = savedInstanceState.getInt("filter", F_NONE);
        }
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);
        voice = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
            ArrayList<String> res = r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) addTask(Fmt.capitalize(res.get(0)));
        });
        scanCamera = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (pendingScan == null) return;
            File f = store.photoFile(pendingScan);
            pendingScan = null;
            if (!Boolean.TRUE.equals(ok)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                return;
            }
            readPhoto(Uri.fromFile(f), f);
        });
        scanGallery = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) readPhoto(uri, null);
        });
        scanFile = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) readFile(uri);
        });
        scanText = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) readTextShot(uri);
        });
        exporter = new ExportHelper(this);
        doneText = new DoneText(this);

        topTitle = findViewById(R.id.topTitle);
        topDivider = findViewById(R.id.topDivider);
        topLog = findViewById(R.id.topLog);
        ((TextView) findViewById(R.id.topProject)).setText(project.name);
        mic = findViewById(R.id.mic);

        list = findViewById(R.id.list);
        lm = new LinearLayoutManager(this);
        list.setLayoutManager(lm);
        DefaultItemAnimator anim = new DefaultItemAnimator();
        anim.setSupportsChangeAnimations(false);  // no flicker when a field is checked
        list.setItemAnimator(anim);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateTopBar();
            }
        });
        new ItemTouchHelper(new DragCallback()).attachToRecyclerView(list);

        findViewById(R.id.back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.more).setOnClickListener(v -> menu());
        mic.setOnClickListener(v -> startVoice());
        findViewById(R.id.scan).setOnClickListener(v -> scanMenu());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("pendingTask", pendingTaskId);
        out.putString("pendingFile", pendingFile);
        out.putString("pendingScan", pendingScan);
        out.putInt("filter", filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (log == null) return;
        if (project.findLog(log.id) == null) {
            finish();
            return;
        }
        topLog.setText(log.displayTitle());
        rebuildVisible();
        adapter.notifyDataSetChanged();
    }

    // ----- filtering -----

    private boolean matches(Models.Task t) {
        switch (filter) {
            case F_ME: return t.assignees.contains(store.me());
            case F_INCOMPLETE: return !t.done;
            case F_COMPLETE: return t.done;
            case F_UNASSIGNED: return t.assignees.isEmpty();
            default: return true;
        }
    }

    private void rebuildVisible() {
        visible.clear();
        for (Models.Task t : log.tasks) if (matches(t)) visible.add(t);
        visible.sort((a, b) -> Integer.compare(areaRank(a), areaRank(b)));  // keeps order within an area
    }

    /** South first, then North, then Unzoned, then fields with no area (same order as the report). */
    private static int areaRank(Models.Task t) {
        return Models.Task.areaRank(t.side);
    }

    private void setFilter(int f) {
        filter = (filter == f) ? F_NONE : f;
        rebuildVisible();
        adapter.notifyDataSetChanged();
    }

    private void updateTopBar() {
        View header = lm.findViewByPosition(0);
        boolean show = header == null || header.getBottom() < Ui.dp(this, 120);
        if (show == topShown) return;
        topShown = show;
        topTitle.animate().alpha(show ? 1f : 0f).setDuration(160).start();
        topDivider.animate().alpha(show ? 1f : 0f).setDuration(160).start();
    }

    // ----- actions -----

    private void toggle(Models.Task t) {
        if (!t.done && t.photosRequired && t.photos.isEmpty()) {
            list.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Snackbar.make(list, "This field needs a photo before it can be completed", Snackbar.LENGTH_LONG)
                    .setAnchorView(mic)
                    .setAction("Take photo", v -> startCamera(t))
                    .show();
            return;
        }
        t.done = !t.done;
        if (t.done) {
            t.completedBy = store.me();
            t.completedAt = System.currentTimeMillis();
        } else {
            t.completedBy = "";
            t.completedAt = 0;
        }
        store.save();
        list.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        doneText.fieldDone(list, project, log, t);
        int idx = visible.indexOf(t);
        if (idx >= 0 && !matches(t)) {
            visible.remove(idx);
            adapter.notifyItemRemoved(idx + 1);
            adapter.notifyItemChanged(adapter.getItemCount() - 1);
        } else if (idx >= 0) {
            adapter.notifyItemChanged(idx + 1, PAYLOAD_CHECK);
        }
        adapter.notifyItemChanged(0, PAYLOAD_PROGRESS);
    }

    private void addTask(String title) {
        addTask(title, "");
    }

    private void addTask(String title, String side) {
        Models.Task t = new Models.Task();
        t.title = title;
        t.side = side;
        t.createdBy = store.me();
        log.tasks.add(t);
        store.save();
        rebuildVisible();
        adapter.notifyDataSetChanged();
        if (matches(t)) {
            int pos = visible.indexOf(t) + 1;
            list.post(() -> list.smoothScrollToPosition(pos));
        } else {
            Toast.makeText(this, "Field added. Clear the filter to see it.", Toast.LENGTH_SHORT).show();
        }
    }

    // ----- add fields from a photo or spreadsheet -----

    private void scanMenu() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_camera, "Take a photo of a task list", () -> {
            File f = PhotoUtil.newFile(store);
            pendingScan = f.getName();
            try {
                scanCamera.launch(FileProvider.getUriForFile(this, getPackageName() + ".files", f));
            } catch (ActivityNotFoundException e) {
                pendingScan = null;
                Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show();
            }
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_gallery, "Choose a photo", () -> {
            try {
                scanGallery.launch("image/*");
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_LONG).show();
            }
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_send, "Add a field from a text screenshot", () -> {
            try {
                scanText.launch("image/*");
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_LONG).show();
            }
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Import an Excel or CSV file", () -> {
            try {
                scanFile.launch("*/*");
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No file app found", Toast.LENGTH_LONG).show();
            }
        }));
        Ui.menu(this, "Add fields from a list. You'll check them before they're added.", items);
    }

    /** Reads the text in a photo, then opens the review screen. A camera photo is deleted afterwards. */
    private void readPhoto(Uri uri, File temp) {
        androidx.appcompat.app.AlertDialog busy = busy("Reading the photo\u2026");
        ScanReader.read(this, uri, new ScanReader.Result() {
            @Override
            public void onRows(List<List<String>> rows) {
                busy.dismiss();
                deleteTemp(temp);
                if (rows.isEmpty()) {
                    Toast.makeText(LogActivity.this, "No text found. Try a closer, straighter photo in good light.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ImportActivity.start(LogActivity.this, project.id, log.id, rows, "the photo", false);
            }

            @Override
            public void onZone(List<ZoneSheet.Task> tasks) {
                busy.dismiss();
                deleteTemp(temp);
                ImportActivity.startZone(LogActivity.this, project.id, log.id, tasks);
            }

            @Override
            public void onError(String message) {
                busy.dismiss();
                deleteTemp(temp);
                Toast.makeText(LogActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** A screenshot of an assignment text: finds "Assigned to you: …" and offers it as a field. */
    private void readTextShot(Uri uri) {
        androidx.appcompat.app.AlertDialog busy = busy("Reading the screenshot\u2026");
        ScanReader.readLines(this, uri, new ScanReader.LinesResult() {
            @Override
            public void onLines(List<String> lines) {
                busy.dismiss();
                List<AssignmentText> found = AssignmentText.parse(lines);
                if (found.isEmpty()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(LogActivity.this)
                            .setTitle("Add from text")
                            .setMessage("Couldn't find an assignment text in that picture. Make sure the whole text, "
                                    + "starting at \"Assigned to you\", is in the screenshot.")
                            .setPositiveButton("Read it as a list", (d, w) -> readPhoto(uri, null))
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
                addFromText(found);
            }

            @Override
            public void onError(String message) {
                busy.dismiss();
                Toast.makeText(LogActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Review what was read: fix the title, keep or drop each one, and whether to text the sender back. */
    private void addFromText(List<AssignmentText> found) {
        int pad = Ui.dp(this, 22);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 4), pad, 0);
        List<android.widget.CheckBox> keep = new ArrayList<>(), textBack = new ArrayList<>();
        List<android.widget.EditText> titles = new ArrayList<>();
        androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];
        Runnable label = () -> {
            int n = 0;
            for (android.widget.CheckBox c : keep) if (c.isChecked()) n++;
            if (dialog[0] != null) {
                android.widget.Button b = dialog[0].getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                b.setText(n == 1 ? "Add 1 field" : "Add " + n + " fields");
                b.setEnabled(n > 0);
            }
        };
        for (AssignmentText a : found) {
            android.widget.CheckBox add = new android.widget.CheckBox(this);
            add.setText(a.photosRequired ? "Add this field  \u00B7  Photos required" : "Add this field");
            add.setChecked(true);
            add.setTextSize(16);
            add.setOnCheckedChangeListener((b, on) -> label.run());
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.dp(this, 10);
            box.addView(add, alp);
            android.widget.EditText title = new android.widget.EditText(this);
            title.setText(a.title);
            title.setTextSize(17);
            title.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            box.addView(title);
            if (!a.from.isEmpty()) {
                TextView from = new TextView(this);
                from.setText("From " + a.from + (a.phone.isEmpty() ? "" : " \u00B7 " + SmsHelper.formatPhone(a.phone)));
                from.setTextColor(getColor(R.color.grey));
                from.setTextSize(14);
                box.addView(from);
            }
            android.widget.CheckBox back = new android.widget.CheckBox(this);
            back.setText("Text " + (a.from.isEmpty() ? "them" : a.from) + " when it's done");
            back.setChecked(!a.phone.isEmpty());
            back.setVisibility(a.phone.isEmpty() ? View.GONE : View.VISIBLE);
            box.addView(back);
            keep.add(add);
            titles.add(title);
            textBack.add(back);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Add from text").setView(scroll)
                .setPositiveButton("Add", null).setNegativeButton("Cancel", null).create();
        dialog[0] = d;
        d.setOnShowListener(x -> {
            label.run();
            d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int added = 0;
                for (int i = 0; i < found.size(); i++) {
                    String t = titles.get(i).getText().toString().trim();
                    if (!keep.get(i).isChecked() || t.isEmpty()) continue;
                    AssignmentText a = found.get(i);
                    Models.Task task = new Models.Task();
                    task.title = Fmt.capitalize(t);
                    task.photosRequired = a.photosRequired;
                    task.createdBy = store.me();
                    if (textBack.get(i).isChecked() && !a.phone.isEmpty()) {
                        task.doneTextName = a.from;
                        task.doneTextPhone = a.phone;
                    }
                    log.tasks.add(task);
                    added++;
                }
                store.save();
                rebuildVisible();
                adapter.notifyDataSetChanged();
                d.dismiss();
                Toast.makeText(this, added == 1 ? "Added 1 field" : "Added " + added + " fields", Toast.LENGTH_SHORT).show();
            });
        });
        d.show();
    }

    private void deleteTemp(File f) {
        //noinspection ResultOfMethodCallIgnored
        if (f != null) f.delete();
    }

    private void readFile(Uri uri) {
        androidx.appcompat.app.AlertDialog busy = busy("Opening the file\u2026");
        PhotoUtil.BG.execute(() -> {
            List<List<String>> rows = null;
            String error = null;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("open");
                rows = SheetImport.fromFile(in);
            } catch (java.io.IOException e) {
                error = "old-xls".equals(e.getMessage())
                        ? "That's an older Excel file (.xls). Save it as .xlsx or .csv and try again."
                        : "Couldn't read that file. It needs to be an Excel (.xlsx) or CSV file.";
            } catch (OutOfMemoryError e) {
                error = "That file is too big to open here.";
            }
            final List<List<String>> got = rows;
            final String err = error;
            runOnUiThread(() -> {
                busy.dismiss();
                if (err != null) {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                } else if (got == null || got.isEmpty()) {
                    Toast.makeText(this, "That file looks empty", Toast.LENGTH_LONG).show();
                } else {
                    ImportActivity.start(this, project.id, log.id, got, "the spreadsheet", true);
                }
            });
        });
    }

    private androidx.appcompat.app.AlertDialog busy(String message) {
        androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(message).setCancelable(false).create();
        d.show();
        return d;
    }

    private void newFieldPrompt() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("e.g. Pack up sea can 317503 and send out of zone 2");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        android.widget.CheckBox south = new android.widget.CheckBox(this);
        south.setText("South");
        android.widget.CheckBox north = new android.widget.CheckBox(this);
        north.setText("North");
        android.widget.CheckBox unzoned = new android.widget.CheckBox(this);
        unzoned.setText("Unzoned");
        // One of them (or none)
        south.setOnCheckedChangeListener((b, on) -> { if (on) { north.setChecked(false); unzoned.setChecked(false); } });
        north.setOnCheckedChangeListener((b, on) -> { if (on) { south.setChecked(false); unzoned.setChecked(false); } });
        unzoned.setOnCheckedChangeListener((b, on) -> { if (on) { south.setChecked(false); north.setChecked(false); } });
        LinearLayout sides = new LinearLayout(this);
        sides.addView(south);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMarginStart(Ui.dp(this, 14));
        sides.addView(north, nlp);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.setMarginStart(Ui.dp(this, 14));
        sides.addView(unzoned, ulp);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(et);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 8);
        box.addView(sides, slp);
        androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("New field").setView(box)
                .setPositiveButton("Add", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            if (s.isEmpty()) {
                et.setError("Required");
                return;
            }
            d.dismiss();
            addTask(s, south.isChecked() ? Models.Task.SOUTH : north.isChecked() ? Models.Task.NORTH
                    : unzoned.isChecked() ? Models.Task.UNZONED : "");
        }));
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        et.requestFocus();
    }

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the new field");
        try {
            voice.launch(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice input isn't available on this phone", Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera(Models.Task t) {
        File f = PhotoUtil.newFile(store);
        pendingTaskId = t.id;
        pendingFile = f.getName();
        Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
        try {
            takePicture.launch(u);
        } catch (ActivityNotFoundException e) {
            pendingFile = null;
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show();
        }
    }

    private void onPhotoTaken(Boolean ok) {
        if (pendingFile == null) return;
        File f = store.photoFile(pendingFile);
        String taskId = pendingTaskId;
        pendingFile = null;
        pendingTaskId = null;
        if (!Boolean.TRUE.equals(ok)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return;
        }
        PhotoUtil.BG.execute(() -> {
            boolean good = PhotoUtil.normalize(f);
            runOnUiThread(() -> {
                Models.Task t = log.findTask(taskId);
                if (!good || t == null) {
                    if (!good) Toast.makeText(this, "Couldn't save that photo", Toast.LENGTH_SHORT).show();
                    return;
                }
                Models.Photo p = new Models.Photo();
                p.file = f.getName();
                p.addedBy = store.me();
                p.addedAt = System.currentTimeMillis();
                t.photos.add(p);
                store.save();
                int idx = visible.indexOf(t);
                if (idx >= 0) adapter.notifyItemChanged(idx + 1);
            });
        });
    }

    private void menu() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Rename Checklist", () ->
                Ui.prompt(this, "Rename checklist", "Checklist name", log.displayTitle(), "Save", n -> {
                    log.name = n;
                    store.save();
                    topLog.setText(log.displayTitle());
                    adapter.notifyItemChanged(0);
                })));
        items.add(new Ui.SheetItem(R.drawable.ic_add, "Add Field", this::newFieldPrompt));
        items.add(new Ui.SheetItem(R.drawable.ic_camera, "Add Fields from a Photo or Spreadsheet", this::scanMenu));
        items.add(new Ui.SheetItem(R.drawable.ic_reorder, "Reorder Fields", () -> {
            if (filter != F_NONE) setFilter(filter);
            Snackbar.make(list, "Press and hold a field, then drag it up or down", Snackbar.LENGTH_LONG)
                    .setAnchorView(mic).show();
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_send, "Email report", () -> exporter.email(project, log)));
        items.add(new Ui.SheetItem(R.drawable.ic_pdf, "Export to PDF", () -> exporter.export(project, log, false)));
        items.add(new Ui.SheetItem(R.drawable.ic_word, "Export to Word", () -> exporter.export(project, log, true)));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "Save Checklist as Template", () ->
                Ui.prompt(this, "Save as template", "Template name", "Daily fields", "Save", n -> {
                    Models.Template t = new Models.Template();
                    t.name = n;
                    for (Models.Task x : log.tasks) t.tasks.add(x.freshCopy());
                    project.templates.add(t);
                    store.save();
                    Snackbar.make(list, "Template saved. Use it from the + button.", Snackbar.LENGTH_SHORT)
                            .setAnchorView(mic).show();
                })));
        items.add(new Ui.SheetItem(R.drawable.ic_copy, "Duplicate Checklist", () -> {
            Models.DailyLog copy = project.duplicate(log, Fmt.todayIso(), store.me());
            store.save();
            startActivity(new Intent(this, LogActivity.class)
                    .putExtra("project", project.id).putExtra("log", copy.id));
            finish();
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete Checklist", () ->
                Ui.confirm(this, "Delete this checklist?",
                        log.displayTitle() + " and its photos will be removed. This can't be undone.", "Delete", () -> {
                            store.deletePhotos(log);
                            project.logs.remove(log);
                            store.save();
                            finish();
                        }), true));
        Ui.menu(this, null, items);
    }

    private void openTask(Models.Task t) {
        startActivity(new Intent(this, TaskActivity.class)
                .putExtra("project", project.id)
                .putExtra("log", log.id)
                .putExtra("task", t.id));
    }

    // ----- drag to reorder -----

    private class DragCallback extends ItemTouchHelper.Callback {
        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            if (!(vh instanceof TaskVH) || filter != F_NONE) return 0;
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                              @NonNull RecyclerView.ViewHolder to) {
            if (!(to instanceof TaskVH)) return false;
            int a = from.getBindingAdapterPosition() - 1;
            int b = to.getBindingAdapterPosition() - 1;
            if (a < 0 || b < 0 || a >= visible.size() || b >= visible.size()) return false;
            // Fields stay inside their area (South / North / none) when dragged.
            if (areaRank(visible.get(a)) != areaRank(visible.get(b))) return false;
            Collections.swap(visible, a, b);
            adapter.notifyItemMoved(a + 1, b + 1);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
            super.onSelectedChanged(vh, state);
            if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                vh.itemView.animate().scaleX(1.02f).scaleY(1.02f).translationZ(Ui.dp(LogActivity.this, 8))
                        .setDuration(120).start();
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(120).start();
            // Dragging only happens with no filter, so the screen shows every field: keep that order.
            if (filter == F_NONE && visible.size() == log.tasks.size()) {
                log.tasks.clear();
                log.tasks.addAll(visible);
            }
            store.save();
        }
    }

    // ----- adapter -----

    private class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title, location, count;
        final ProgressRing ring;
        final LinearLayout crew;
        final TextView[] chips;

        HeaderVH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            location = v.findViewById(R.id.location);
            count = v.findViewById(R.id.count);
            ring = v.findViewById(R.id.ring);
            ring.setFilledWhenComplete(false);
            crew = v.findViewById(R.id.crew);
            chips = new TextView[]{v.findViewById(R.id.chipMe), v.findViewById(R.id.chipIncomplete),
                    v.findViewById(R.id.chipComplete), v.findViewById(R.id.chipUnassigned)};
            int[] codes = {F_ME, F_INCOMPLETE, F_COMPLETE, F_UNASSIGNED};
            for (int i = 0; i < chips.length; i++) {
                int code = codes[i];
                chips[i].setOnClickListener(x -> setFilter(code));
            }
            View.OnClickListener assign = x -> AssignSheet.show(LogActivity.this, project, log.crew,
                    "Checklist Crew", sel -> {
                        log.crew = sel;
                        store.save();
                        adapter.notifyItemChanged(0);
                    });
            v.findViewById(R.id.addCrew).setOnClickListener(assign);
            crew.setOnClickListener(assign);
        }

        void bindProgress(boolean animate) {
            int done = log.doneCount(), total = log.tasks.size();
            count.setText(done + "/" + total + " fields completed");
            ring.setProgress(total == 0 ? 0f : done / (float) total, animate);
        }

        void bind() {
            title.setText(log.displayTitle());
            location.setText(project.location);
            location.setVisibility(project.location.isEmpty() ? View.GONE : View.VISIBLE);
            bindProgress(false);
            Ui.fillBadges(crew, log.crew, 52, 4);
            int[] codes = {F_ME, F_INCOMPLETE, F_COMPLETE, F_UNASSIGNED};
            for (int i = 0; i < chips.length; i++) {
                boolean on = filter == codes[i];
                chips[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
                chips[i].setTextColor(on ? 0xFFFFFFFF : getColor(R.color.grey));
            }
        }
    }

    private class TaskVH extends RecyclerView.ViewHolder {
        final View accent;
        final CheckCircle check;
        final TextView title, stamp, assigned, notes, required, secured, nextShift, side;
        final ImageButton open, camera;
        final HorizontalScrollView photoScroll;
        final LinearLayout photos;
        Models.Task task;

        TaskVH(View v) {
            super(v);
            accent = v.findViewById(R.id.accent);
            check = v.findViewById(R.id.check);
            title = v.findViewById(R.id.title);
            stamp = v.findViewById(R.id.stamp);
            assigned = v.findViewById(R.id.assigned);
            notes = v.findViewById(R.id.notes);
            required = v.findViewById(R.id.required);
            secured = v.findViewById(R.id.secured);
            nextShift = v.findViewById(R.id.nextShiftChip);
            side = v.findViewById(R.id.sideChip);
            open = v.findViewById(R.id.open);
            camera = v.findViewById(R.id.camera);
            photoScroll = v.findViewById(R.id.photoScroll);
            photos = v.findViewById(R.id.photos);
            v.setClipToOutline(true);  // keeps the colored side bar inside the rounded card
            check.setOnClickListener(x -> { if (task != null) toggle(task); });
            open.setOnClickListener(x -> { if (task != null) openTask(task); });
            camera.setOnClickListener(x -> { if (task != null) startCamera(task); });
            v.setOnClickListener(x -> { if (task != null) openTask(task); });
        }

        void bind(Models.Task t) {
            task = t;
            // North = pale blue, South = pale pink, Unzoned = pale yellow. Done adds a green border all
            // round, the green line and tick.
            boolean north = Models.Task.NORTH.equals(t.side), south = Models.Task.SOUTH.equals(t.side);
            boolean unzoned = Models.Task.UNZONED.equals(t.side);
            accent.setBackgroundColor(getColor(t.done ? R.color.green
                    : north ? R.color.north_accent : south ? R.color.south_accent
                    : unzoned ? R.color.unzoned_accent : R.color.sky));
            itemView.setBackgroundResource(north ? (t.done ? R.drawable.bg_card_north_done : R.drawable.bg_card_north)
                    : south ? (t.done ? R.drawable.bg_card_south_done : R.drawable.bg_card_south)
                    : unzoned ? (t.done ? R.drawable.bg_card_unzoned_done : R.drawable.bg_card_unzoned)
                    : t.done ? R.drawable.bg_card_done : R.drawable.bg_card);
            check.setChecked(t.done, false);
            title.setText(t.title);
            if (t.done) {
                stamp.setVisibility(View.VISIBLE);
                stamp.setText(t.completedBy + ", " + Fmt.stamp(t.completedAt));
            } else {
                stamp.setVisibility(View.GONE);
            }
            assigned.setVisibility(t.assignees.isEmpty() ? View.GONE : View.VISIBLE);
            assigned.setText(Fmt.join(t.assignees));
            notes.setVisibility(t.notes.trim().isEmpty() ? View.GONE : View.VISIBLE);
            notes.setText(t.notes.trim());
            secured.setVisibility(t.loadSecured ? View.VISIBLE : View.GONE);
            nextShift.setVisibility(t.nextShift ? View.VISIBLE : View.GONE);
            side.setVisibility(t.side.isEmpty() ? View.GONE : View.VISIBLE);
            side.setText(Models.Task.sideLabel(t.side));
            required.setVisibility(t.photosRequired ? View.VISIBLE : View.GONE);
            boolean missing = t.photosRequired && t.photos.isEmpty() && !t.done;
            int reqColor = missing ? getColor(R.color.red) : getColor(R.color.text);
            required.setTextColor(reqColor);
            required.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(reqColor));
            bindPhotos(t);
        }

        void bindPhotos(Models.Task t) {
            photoScroll.setVisibility(t.photos.isEmpty() ? View.GONE : View.VISIBLE);
            int size = Ui.dp(LogActivity.this, 100);
            int gap = Ui.dp(LogActivity.this, 10);
            // Reuse thumbnails where possible so rebinding doesn't flash.
            while (photos.getChildCount() > t.photos.size()) photos.removeViewAt(photos.getChildCount() - 1);
            for (int i = 0; i < t.photos.size(); i++) {
                ShapeableImageView iv;
                if (i < photos.getChildCount()) {
                    iv = (ShapeableImageView) photos.getChildAt(i);
                } else {
                    iv = new ShapeableImageView(LogActivity.this);
                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    iv.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                            .setAllCornerSizes(Ui.dp(LogActivity.this, 10)).build());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.setMarginEnd(gap);
                    photos.addView(iv, lp);
                }
                Models.Photo p = t.photos.get(i);
                File f = store.photoFile(p.file);
                ImageLoader.load(iv, f, 300);
                iv.setOnClickListener(x -> Ui.showPhoto(LogActivity.this, p, f));
            }
        }
    }

    private class FooterVH extends RecyclerView.ViewHolder {
        final TextView emptyMsg;
        final View extras;
        final LinearLayout extraRows;

        FooterVH(View v) {
            super(v);
            emptyMsg = v.findViewById(R.id.emptyMsg);
            extras = v.findViewById(R.id.extras);
            extraRows = v.findViewById(R.id.extraRows);
            v.findViewById(R.id.newField).setOnClickListener(x -> newFieldPrompt());
        }

        void bindExtras() {
            extraRows.removeAllViews();
            extras.setVisibility(log.extras.isEmpty() ? View.GONE : View.VISIBLE);
            List<Models.Extra> list = new ArrayList<>(log.extras);
            list.sort((x, y) -> Long.compare(x.at, y.at));
            for (Models.Extra e : list) {
                android.widget.CheckBox cb = new android.widget.CheckBox(LogActivity.this);
                String time = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(new java.util.Date(e.at));
                String text = time + "  " + e.label();
                if ((Models.Extra.NOTIFY.equals(e.type) || Models.Extra.NOTE.equals(e.type)) && !e.detail.isEmpty()) text += "\n" + e.detail;
                if (!e.photos.isEmpty()) text += "\n" + Fmt.plural(e.photos.size(), "photo", "photos");
                cb.setText(text);
                cb.setTextSize(16);
                cb.setTextColor(getColor(R.color.text));
                cb.setPadding(Ui.dp(LogActivity.this, 6), Ui.dp(LogActivity.this, 8), 0, Ui.dp(LogActivity.this, 8));
                cb.setChecked(e.include);
                cb.setOnCheckedChangeListener((b, on) -> {
                    e.include = on;
                    store.save();
                });
                Runnable remove = () -> Ui.confirm(LogActivity.this, "Remove from this checklist?",
                        "It's taken off the Also today list. The link, note or template itself stays.", "Remove", () -> {
                            log.extras.remove(e);
                            store.save();
                            bindExtras();
                        });
                cb.setOnLongClickListener(x -> {
                    remove.run();
                    return true;
                });
                ImageButton del = new ImageButton(LogActivity.this);
                del.setImageResource(R.drawable.ic_delete);
                del.setColorFilter(getColor(R.color.grey));
                del.setBackgroundResource(android.R.color.transparent);
                del.setContentDescription("Remove from this checklist");
                del.setOnClickListener(x -> remove.run());
                LinearLayout line = new LinearLayout(LogActivity.this);
                line.setGravity(android.view.Gravity.CENTER_VERTICAL);
                line.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                line.addView(del, new LinearLayout.LayoutParams(Ui.dp(LogActivity.this, 48), Ui.dp(LogActivity.this, 48)));
                extraRows.addView(line);
            }
        }

        void bind() {
            bindExtras();
            if (!visible.isEmpty()) {
                emptyMsg.setVisibility(View.GONE);
                return;
            }
            emptyMsg.setVisibility(View.VISIBLE);
            emptyMsg.setText(log.tasks.isEmpty()
                    ? "No fields yet. Tap New Field below, the blue mic to say one, or the camera at the top to add them from a list."
                    : "No fields match this filter.");
        }
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            if (position == 0) return TYPE_HEADER;
            if (position == visible.size() + 1) return TYPE_FOOTER;
            return TYPE_TASK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) return new HeaderVH(inf.inflate(R.layout.item_log_header, parent, false));
            if (viewType == TYPE_FOOTER) return new FooterVH(inf.inflate(R.layout.item_footer, parent, false));
            return new TaskVH(inf.inflate(R.layout.item_task, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            if (h instanceof HeaderVH) ((HeaderVH) h).bind();
            else if (h instanceof FooterVH) ((FooterVH) h).bind();
            else ((TaskVH) h).bind(visible.get(position - 1));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty()) {
                onBindViewHolder(h, position);
                return;
            }
            if (h instanceof HeaderVH && payloads.contains(PAYLOAD_PROGRESS)) {
                ((HeaderVH) h).bindProgress(true);
            } else if (h instanceof TaskVH && payloads.contains(PAYLOAD_CHECK)) {
                TaskVH t = (TaskVH) h;
                t.bind(visible.get(position - 1));
                t.check.setChecked(t.task.done, true);
            } else {
                onBindViewHolder(h, position);
            }
        }

        @Override
        public int getItemCount() {
            return visible.size() + 2;
        }
    }
}
