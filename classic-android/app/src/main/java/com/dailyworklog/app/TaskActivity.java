package com.dailyworklog.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** "Edit Field": title, notes, assignees, photos, photos-required switch. Saves automatically. */
public class TaskActivity extends AppCompatActivity {
    private Store store;
    private Models.Project project;
    private Models.DailyLog log;
    private Models.Task task;

    private CheckCircle check;
    private EditText title, notes;
    private TextView stamp, created;
    private LinearLayout assignees, photos;
    private MaterialSwitch required;
    private CheckBox secured, nextShift;
    private TextView securedStamp;
    private View root;

    private String pendingFile;
    /** Load securement pop-up: photos taken in this session (file names), its subject, and the dialog. */
    private ArrayList<String> loadSession;
    private String loadSubject;
    private androidx.appcompat.app.AlertDialog loadDialog;
    private LinearLayout loadThumbs;
    private boolean pendingForLoad;
    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<String> pickImages;
    private ActivityResultLauncher<Intent> dictate;
    private ActivityResultLauncher<String> smsPermission;
    private DoneText doneText;
    private Runnable afterSmsPermission;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_task);
        root = findViewById(R.id.root);
        Ui.applyInsets(root);
        store = Store.get(this);
        project = store.project(getIntent().getStringExtra("project"));
        log = project == null ? null : project.findLog(getIntent().getStringExtra("log"));
        task = log == null ? null : log.findTask(getIntent().getStringExtra("task"));
        if (task == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            pendingFile = savedInstanceState.getString("pendingFile");
            pendingForLoad = savedInstanceState.getBoolean("pendingForLoad");
            loadSession = savedInstanceState.getStringArrayList("loadSession");
            loadSubject = savedInstanceState.getString("loadSubject");
        }

        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);
        pickImages = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), this::onPicked);
        dictate = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
            ArrayList<String> res = r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res == null || res.isEmpty()) return;
            String cur = notes.getText().toString().trim();
            String add = Fmt.capitalize(res.get(0));
            notes.setText(cur.isEmpty() ? add : cur + " " + add);
            notes.setSelection(notes.getText().length());
            saveText();
        });

        doneText = new DoneText(this);
        smsPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            Runnable r = afterSmsPermission;
            afterSmsPermission = null;
            if (r != null) r.run();
        });

        check = findViewById(R.id.check);
        title = findViewById(R.id.title);
        notes = findViewById(R.id.notes);
        stamp = findViewById(R.id.stamp);
        created = findViewById(R.id.created);
        assignees = findViewById(R.id.assignees);
        photos = findViewById(R.id.photos);
        required = findViewById(R.id.required);
        secured = findViewById(R.id.secured);
        securedStamp = findViewById(R.id.securedStamp);
        nextShift = findViewById(R.id.nextShift);
        nextShift.setChecked(task.nextShift);
        bindSide(findViewById(R.id.sideSouth), findViewById(R.id.sideNorth), findViewById(R.id.sideUnzoned));
        bindDoneText();
        findViewById(R.id.doneTextRow).setOnClickListener(v -> chooseDoneText());

        ((TextView) findViewById(R.id.topLog)).setText(log.displayTitle());
        title.setText(task.title);
        notes.setText(task.notes);
        check.setChecked(task.done, false);
        required.setChecked(task.photosRequired);
        secured.setChecked(task.loadSecured);
        String by = task.createdBy.isEmpty() ? "" : " by " + task.createdBy;
        created.setText(task.createdAt > 0 ? "Added" + by + ", " + Fmt.stamp(task.createdAt) : "");

        findViewById(R.id.back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.more).setOnClickListener(v -> {
            List<Ui.SheetItem> items = new ArrayList<>();
            items.add(new Ui.SheetItem(R.drawable.ic_camera, "Take photo", this::startCamera));
            items.add(new Ui.SheetItem(R.drawable.ic_gallery, "Add from gallery", this::startGallery));
            items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete field", this::confirmDelete, true));
            Ui.menu(this, null, items);
        });
        check.setOnClickListener(v -> toggle());
        findViewById(R.id.notesMic).setOnClickListener(v -> startDictation());
        findViewById(R.id.assignRow).setOnClickListener(v -> assign());
        findViewById(R.id.addPhotos).setOnClickListener(v -> photoSource());
        findViewById(R.id.delete).setOnClickListener(v -> confirmDelete());
        required.setOnCheckedChangeListener((b, on) -> {
            task.photosRequired = on;
            store.save();
        });

        findViewById(R.id.securedRow).setOnClickListener(v -> openLoadPopup());
        findViewById(R.id.nextShiftRow).setOnClickListener(v -> {
            task.nextShift = !task.nextShift;
            nextShift.setChecked(task.nextShift);
            store.save();
            root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        });

        bindStamp();
        bindSecured();
        bindAssignees();
        bindPhotos();
        if (loadSession != null) openLoadPopup();  // came back after the phone rotated
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("pendingFile", pendingFile);
        out.putBoolean("pendingForLoad", pendingForLoad);
        if (loadSession != null) {
            out.putStringArrayList("loadSession", loadSession);
            out.putString("loadSubject", loadSubject);
        }
    }

    @Override
    protected void onDestroy() {
        if (loadDialog != null) loadDialog.dismiss();
        loadDialog = null;
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (task != null) saveText();
    }

    private void saveText() {
        String t = title.getText().toString().trim();
        if (!t.isEmpty()) task.title = t;
        task.notes = notes.getText().toString().trim();
        store.save();
    }

    // ----- completion -----

    private void toggle() {
        if (!task.done && task.photosRequired && task.photos.isEmpty()) {
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Snackbar.make(root, "This field needs a photo before it can be completed", Snackbar.LENGTH_LONG)
                    .setAction("Take photo", v -> startCamera())
                    .show();
            return;
        }
        task.done = !task.done;
        task.completedBy = task.done ? store.me() : "";
        task.completedAt = task.done ? System.currentTimeMillis() : 0;
        store.save();
        root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        check.setChecked(task.done, true);
        bindStamp();
        doneText.fieldDone(root, project, log, task);
    }

    private void bindStamp() {
        stamp.setVisibility(task.done ? View.VISIBLE : View.GONE);
        if (task.done) stamp.setText(task.completedBy + ", " + Fmt.stamp(task.completedAt));
    }

    // ----- text when completed -----

    private void bindDoneText() {
        TextView sub = findViewById(R.id.doneTextSub);
        if (DoneText.wanted(task)) {
            sub.setText((task.doneTextName.isEmpty() ? "" : task.doneTextName + " \u00B7 ") + SmsHelper.formatPhone(task.doneTextPhone));
            sub.setTextColor(getColor(R.color.deep_blue));
        } else {
            sub.setText("Off \u2014 tap to choose who gets a text when this is done");
            sub.setTextColor(getColor(R.color.grey));
        }
    }

    /** Pick a crew member with a number, or type someone else's name and number. */
    private void chooseDoneText() {
        int pad = Ui.dp(this, 22);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        TextView msg = new TextView(this);
        msg.setText("When this field is marked complete, the app texts this person that it's done.");
        msg.setTextSize(15);
        msg.setTextColor(getColor(R.color.grey));
        box.addView(msg);
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        List<String> withPhones = new ArrayList<>();
        for (String n : project.crew) if (SmsHelper.digits(project.phoneOf(n)).length() >= 7) withPhones.add(n);
        int checked = -1;
        for (int i = 0; i < withPhones.size(); i++) {
            String n = withPhones.get(i);
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(n + "  \u00B7  " + SmsHelper.formatPhone(project.phoneOf(n)));
            rb.setTextSize(16);
            group.addView(rb);
            if (n.equals(task.doneTextName) && SmsHelper.digits(project.phoneOf(n)).equals(SmsHelper.digits(task.doneTextPhone))) checked = rb.getId();
        }
        android.widget.RadioButton other = new android.widget.RadioButton(this);
        other.setId(999);
        other.setText("Someone else");
        other.setTextSize(16);
        group.addView(other);
        box.addView(group);
        android.widget.EditText name = new android.widget.EditText(this);
        name.setHint("Name");
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        android.widget.EditText phone = new android.widget.EditText(this);
        phone.setHint("Phone number");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        box.addView(name);
        box.addView(phone);
        if (checked < 0 && DoneText.wanted(task)) {
            checked = 999;
            name.setText(task.doneTextName);
            phone.setText(task.doneTextPhone);
        }
        if (checked < 0) checked = withPhones.isEmpty() ? 999 : 1000;
        if (checked >= 0) group.check(checked);
        Runnable showOther = () -> {
            int vis = group.getCheckedRadioButtonId() == 999 ? View.VISIBLE : View.GONE;
            name.setVisibility(vis);
            phone.setVisibility(vis);
        };
        group.setOnCheckedChangeListener((g, id) -> showOther.run());
        showOther.run();
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Text when completed").setView(scroll)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null);
        if (DoneText.wanted(task)) {
            b.setNeutralButton("Turn off", (d, w) -> {
                task.doneTextName = "";
                task.doneTextPhone = "";
                store.save();
                bindDoneText();
            });
        }
        androidx.appcompat.app.AlertDialog d = b.create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int id = group.getCheckedRadioButtonId();
            if (id == 999) {
                String n = name.getText().toString().trim();
                String ph = phone.getText().toString().trim();
                if (n.isEmpty()) {
                    name.setError("Enter a name");
                    return;
                }
                if (SmsHelper.digits(ph).replace("+", "").length() < 7) {
                    phone.setError("Enter a phone number");
                    return;
                }
                task.doneTextName = Fmt.capitalize(n);
                task.doneTextPhone = ph;
            } else if (id >= 1000) {
                String n = withPhones.get(id - 1000);
                task.doneTextName = n;
                task.doneTextPhone = project.phoneOf(n);
            } else {
                return;  // nothing picked
            }
            store.save();
            bindDoneText();
            d.dismiss();
        }));
        d.show();
    }

    // ----- south / north -----

    /** Three ticks that act as one choice: South, North, Unzoned, or none. */
    private void bindSide(CheckBox south, CheckBox north, CheckBox unzoned) {
        south.setOnCheckedChangeListener(null);
        north.setOnCheckedChangeListener(null);
        unzoned.setOnCheckedChangeListener(null);
        south.setChecked(Models.Task.SOUTH.equals(task.side));
        north.setChecked(Models.Task.NORTH.equals(task.side));
        unzoned.setChecked(Models.Task.UNZONED.equals(task.side));
        south.setOnCheckedChangeListener((b, on) -> setSide(on ? Models.Task.SOUTH : "", south, north, unzoned));
        north.setOnCheckedChangeListener((b, on) -> setSide(on ? Models.Task.NORTH : "", south, north, unzoned));
        unzoned.setOnCheckedChangeListener((b, on) -> setSide(on ? Models.Task.UNZONED : "", south, north, unzoned));
    }

    private void setSide(String side, CheckBox south, CheckBox north, CheckBox unzoned) {
        task.side = side;
        store.save();
        bindSide(south, north, unzoned);
    }

    // ----- load securement -----

    private void bindSecured() {
        secured.setChecked(task.loadSecured);
        securedStamp.setVisibility(task.loadSecured ? View.VISIBLE : View.GONE);
        if (task.loadSecured) securedStamp.setText(task.securedBy + ", " + Fmt.stamp(task.securedAt));
        bindSecuredPhotos();
    }

    /** Under the Load securement row: the photos sent, and whether they go in the report. */
    private void bindSecuredPhotos() {
        LinearLayout box = findViewById(R.id.securedPhotosBox);
        box.removeAllViews();
        if (task.securedPhotos.isEmpty()) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        TextView head = new TextView(this);
        head.setText("Load securement photos (" + task.securedPhotos.size() + ")");
        head.setTextSize(16);
        head.setTextColor(getColor(R.color.navy));
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(head);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row);
        box.addView(hs);
        for (Models.Photo p : task.securedPhotos) {
            row.addView(photoCell(p, Ui.dp(this, 140), () -> Ui.confirm(this, "Remove this photo?",
                    "It will be deleted from this field.", "Remove", () -> {
                        task.securedPhotos.remove(p);
                        store.deletePhoto(p);
                        store.save();
                        bindSecuredPhotos();
                    })), cellLp());
        }
        CheckBox inReport = new CheckBox(this);
        inReport.setText("Put load securement photos in the report");
        inReport.setTextSize(16);
        inReport.setChecked(task.securedPhotosInReport);
        inReport.setOnCheckedChangeListener((b, on) -> {
            task.securedPhotosInReport = on;
            store.save();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(-Ui.dp(this, 6));
        lp.topMargin = Ui.dp(this, 6);
        box.addView(inReport, lp);
    }

    // ----- load securement pop-up -----

    /** Take photos, type a subject, Done: ticks Load secured and emails the photos. */
    private void openLoadPopup() {
        if (loadSession == null) {
            loadSession = new ArrayList<>();
            loadSubject = LoadEmail.defaultSubject(task);
        }
        if (loadDialog != null && loadDialog.isShowing()) {
            refreshLoadThumbs();
            return;
        }
        int pad = Ui.dp(this, 22);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        TextView msg = new TextView(this);
        msg.setText("Take photos of the load, type a subject, then tap Done. The photos are emailed straight away.");
        msg.setTextSize(15);
        msg.setTextColor(getColor(R.color.grey));
        box.addView(msg);

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        loadThumbs = new LinearLayout(this);
        loadThumbs.setOrientation(LinearLayout.HORIZONTAL);
        loadThumbs.setClipChildren(false);
        hs.addView(loadThumbs);
        box.addView(hs);
        refreshLoadThumbs();

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView take = chip("Take photo", R.drawable.ic_camera);
        take.setOnClickListener(v -> startCamera(true));
        buttons.addView(take, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));
        TextView gallery = chip("From gallery", R.drawable.ic_gallery);
        gallery.setOnClickListener(v -> startGallery(true));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f);
        glp.setMarginStart(Ui.dp(this, 8));
        buttons.addView(gallery, glp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 10);
        box.addView(buttons, blp);

        TextView toLine = new TextView(this);
        Runnable bindTo = () -> {
            boolean set = !store.loadEmailTo.isEmpty();
            toLine.setText("To: " + (set ? store.loadEmailTo : "Not set"));
            toLine.setTextColor(getColor(set ? R.color.text : R.color.grey));
        };
        bindTo.run();
        box.addView(linkRow(toLine, "Change", v -> LoadEmail.changeTo(this, bindTo)), blp);

        EditText subject = new EditText(this);
        subject.setHint("Subject");
        subject.setText(loadSubject);
        subject.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        subject.setSingleLine(true);
        subject.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                loadSubject = s.toString();
            }
        });
        box.addView(subject);

        TextView fromLine = new TextView(this);
        fromLine.setTextSize(14);
        fromLine.setTextColor(getColor(R.color.grey));
        TextView fromLink = new TextView(this);
        Runnable bindFrom = () -> {
            fromLine.setText("From: " + (store.mailFrom.isEmpty() ? "Not set" : store.mailFrom));
            fromLink.setText(store.hasMailSetup() ? "Change" : "Set up");
        };
        LinearLayout fromRow = new LinearLayout(this);
        fromRow.setGravity(Gravity.CENTER_VERTICAL);
        fromRow.addView(fromLine, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        styleLink(fromLink);
        fromLink.setOnClickListener(v -> LoadEmail.setup(this, bindFrom));
        fromRow.addView(fromLink);
        bindFrom.run();
        box.addView(fromRow);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Load securement")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .setNegativeButton("Cancel", (d, w) -> cancelLoadPopup());
        if (task.loadSecured) {
            b.setNeutralButton("Mark not secured", (d, w) -> {
                task.loadSecured = false;
                task.securedBy = "";
                task.securedAt = 0;
                store.save();
                bindSecured();
                cancelLoadPopup();
            });
        }
        loadDialog = b.create();
        loadDialog.setCanceledOnTouchOutside(false);
        loadDialog.setOnCancelListener(d -> cancelLoadPopup());
        loadDialog.setOnShowListener(x -> loadDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> loadDone(bindTo, bindFrom)));
        loadDialog.show();
    }

    private void loadDone(Runnable bindTo, Runnable bindFrom) {
        if (loadSession.isEmpty()) {
            if (!task.loadSecured) markSecured();
            closeLoadPopup();
            Toast.makeText(this, "Load secured", Toast.LENGTH_SHORT).show();
            return;
        }
        if (store.loadEmailTo.isEmpty()) {
            LoadEmail.changeTo(this, () -> {
                bindTo.run();
                if (!store.loadEmailTo.isEmpty()) loadDone(bindTo, bindFrom);
            });
            return;
        }
        if (!store.hasMailSetup()) {
            LoadEmail.setup(this, () -> {
                bindFrom.run();
                if (store.hasMailSetup()) loadDone(bindTo, bindFrom);
            });
            return;
        }
        if (!task.loadSecured) markSecured();
        List<Models.Photo> sent = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String name : loadSession) {
            Models.Photo p = new Models.Photo();
            p.file = name;
            p.addedBy = store.me();
            p.addedAt = now;
            sent.add(p);
        }
        task.securedPhotos.addAll(sent);
        store.save();
        String subject = loadSubject.trim().isEmpty() ? LoadEmail.defaultSubject(task) : loadSubject.trim();
        String to = store.loadEmailTo;
        closeLoadPopup();
        bindSecured();
        LoadEmail.send(this, project, log, task, sent, subject, to);
    }

    private void markSecured() {
        task.loadSecured = true;
        task.securedBy = store.me();
        task.securedAt = System.currentTimeMillis();
        store.save();
        root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        bindSecured();
    }

    /** Cancel: the photos taken in this session are deleted. */
    private void cancelLoadPopup() {
        if (loadSession != null) LoadEmail.deleteFiles(store, loadSession);
        closeLoadPopup();
    }

    private void closeLoadPopup() {
        loadSession = null;
        loadSubject = null;
        loadThumbs = null;
        if (loadDialog != null) {
            loadDialog.setOnCancelListener(null);
            loadDialog.dismiss();
        }
        loadDialog = null;
    }

    private void refreshLoadThumbs() {
        if (loadThumbs == null || loadSession == null) return;
        loadThumbs.removeAllViews();
        for (String name : new ArrayList<>(loadSession)) {
            Models.Photo p = new Models.Photo();
            p.file = name;
            p.addedBy = store.me();
            loadThumbs.addView(photoCell(p, Ui.dp(this, 96), () -> {
                loadSession.remove(name);
                LoadEmail.deleteFiles(store, java.util.Collections.singletonList(name));
                refreshLoadThumbs();
            }), cellLp());
        }
        loadThumbs.setVisibility(loadSession.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addLoadPhoto(File f) {
        if (loadSession == null) {
            // The pop-up was closed meanwhile: keep the photo with the field's photos instead of losing it.
            addPhoto(f);
            return;
        }
        loadSession.add(f.getName());
        openLoadPopup();
    }

    private TextView chip(String label, int icon) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(getColor(R.color.deep_blue));
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_chip_sky);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        t.setCompoundDrawablePadding(Ui.dp(this, 6));
        t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.deep_blue)));
        t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        return t;
    }

    private void styleLink(TextView t) {
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(getColor(R.color.blue));
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), 0, Ui.dp(this, 10));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
    }

    /** A line of text with a small text button on the right. */
    private LinearLayout linkRow(TextView text, String link, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        text.setTextSize(16);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView l = new TextView(this);
        l.setText(link);
        styleLink(l);
        l.setOnClickListener(click);
        row.addView(l);
        return row;
    }

    // ----- assignees -----

    private void assign() {
        AssignSheet.show(this, project, task.assignees, "Assign Users", sel -> {
            List<String> added = new ArrayList<>();
            for (String n : sel) if (!task.assignees.contains(n)) added.add(n);
            task.assignees = sel;
            store.save();
            bindAssignees();
            if (!added.isEmpty()) autoText(added, 0);
        });
    }

    /** Texts each newly assigned person in turn, asking for any missing numbers first. */
    private void autoText(List<String> people, int i) {
        if (i >= people.size()) return;
        String name = people.get(i);
        // Only people ticked for assignment texts in Manage crew get one; never yourself.
        if (store.me().equals(name) || !project.assignTexts.contains(name)) {
            autoText(people, i + 1);
            return;
        }
        SmsHelper.withPhone(this, project, name, () -> {
            bindAssignees();
            sendWithUndo(name);
            autoText(people, i + 1);
        });
    }

    private void sendWithUndo(String name) {
        String body = SmsHelper.taskText(project, log, task, store.me(), store.contactPhone);
        String phone = project.phoneOf(name);
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            afterSmsPermission = () -> {
                if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    sendWithUndo(name);
                } else {
                    // No permission: open Messages with the text ready, one tap to send.
                    List<String> to = new ArrayList<>();
                    to.add(phone);
                    SmsHelper.send(this, to, body, null);
                }
            };
            smsPermission.launch(Manifest.permission.SEND_SMS);
            return;
        }
        final boolean[] cancelled = {false};
        Runnable send = () -> {
            if (cancelled[0]) return;
            boolean ok = SmsHelper.sendDirect(this, phone, body);
            Toast.makeText(getApplicationContext(), ok ? "Texted " + name : "Couldn't text " + name + ". Use Text it to try again.",
                    Toast.LENGTH_SHORT).show();
        };
        handler.postDelayed(send, 5000);
        Snackbar.make(root, "Texting " + name + "\u2026", 5000)
                .setAction("Undo", v -> {
                    cancelled[0] = true;
                    handler.removeCallbacks(send);
                    Toast.makeText(this, "Text to " + name + " cancelled", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Manual resend: opens Messages with the text ready. */
    private void textIt(String name) {
        SmsHelper.withPhone(this, project, name, () -> {
            bindAssignees();
            List<String> to = new ArrayList<>();
            to.add(project.phoneOf(name));
            SmsHelper.send(this, to, SmsHelper.taskText(project, log, task, store.me(), store.contactPhone), null);
        });
    }

    private void bindAssignees() {
        assignees.removeAllViews();
        for (String name : task.assignees) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, Ui.dp(this, 16));
            TextView badge = Ui.badge(this, name, 44);
            ((LinearLayout.LayoutParams) badge.getLayoutParams()).setMarginStart(0);
            row.addView(badge);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
            TextView n = new TextView(this);
            n.setText(name);
            n.setTextSize(19);
            n.setTextColor(getColor(R.color.text));
            col.addView(n);
            TextView ph = new TextView(this);
            String number = project.phoneOf(name);
            ph.setText(number.isEmpty() ? "No phone number yet" : number);
            ph.setTextSize(14);
            ph.setTextColor(getColor(R.color.grey));
            col.addView(ph);
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView text = new TextView(this);
            text.setText("Text it");
            text.setTextSize(15);
            text.setTextColor(getColor(R.color.deep_blue));
            text.setBackgroundResource(R.drawable.bg_chip_sky);
            text.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
            text.setOnClickListener(v -> textIt(name));
            row.addView(text);
            // Remove this person from the field right here (handy after carrying a field over).
            android.widget.ImageButton remove = new android.widget.ImageButton(this);
            remove.setImageResource(R.drawable.ic_remove);
            remove.setColorFilter(0xFFFFFFFF);
            remove.setBackgroundResource(R.drawable.bg_red_circle);
            int rp = Ui.dp(this, 6);
            remove.setPadding(rp, rp, rp, rp);
            remove.setContentDescription("Remove " + name + " from this field");
            remove.setOnClickListener(v -> Ui.confirm(this, "Remove " + name + " from this field?",
                    "They stay on the crew. No text is sent.", "Remove", () -> {
                        task.assignees.remove(name);
                        store.save();
                        bindAssignees();
                    }));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
            rlp.setMarginStart(Ui.dp(this, 12));
            row.addView(remove, rlp);
            row.setOnClickListener(v -> assign());
            assignees.addView(row);
        }
    }

    // ----- photos -----

    private void photoSource() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_camera, "Take photo", this::startCamera));
        items.add(new Ui.SheetItem(R.drawable.ic_gallery, "Choose from gallery", this::startGallery));
        Ui.menu(this, null, items);
    }

    private void startCamera() {
        startCamera(false);
    }

    /** forLoad: the photo goes to the Load securement pop-up instead of the field's photos. */
    private void startCamera(boolean forLoad) {
        File f = PhotoUtil.newFile(store);
        pendingFile = f.getName();
        pendingForLoad = forLoad;
        Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
        try {
            takePicture.launch(u);
        } catch (ActivityNotFoundException e) {
            pendingFile = null;
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show();
        }
    }

    private void startGallery() {
        startGallery(false);
    }

    private void startGallery(boolean forLoad) {
        pendingForLoad = forLoad;
        try {
            pickImages.launch("image/*");
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_LONG).show();
        }
    }

    private void onPhotoTaken(Boolean ok) {
        if (pendingFile == null) return;
        File f = store.photoFile(pendingFile);
        pendingFile = null;
        boolean forLoad = pendingForLoad;
        pendingForLoad = false;
        if (!Boolean.TRUE.equals(ok)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return;
        }
        PhotoUtil.BG.execute(() -> {
            boolean good = PhotoUtil.fromCamera(this, f);
            runOnUiThread(() -> {
                if (good && forLoad) addLoadPhoto(f);
                else if (good) addPhoto(f);
                else Toast.makeText(this, "Couldn't save that photo", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void onPicked(List<Uri> uris) {
        boolean forLoad = pendingForLoad;
        pendingForLoad = false;
        if (uris == null || uris.isEmpty()) return;
        PhotoUtil.BG.execute(() -> {
            List<File> done = new ArrayList<>();
            for (Uri u : uris) {
                File f = PhotoUtil.newFile(store);
                if (PhotoUtil.copyFrom(this, u, f)) done.add(f);
                //noinspection ResultOfMethodCallIgnored
                else f.delete();
            }
            runOnUiThread(() -> {
                for (File f : done) {
                    if (forLoad) addLoadPhoto(f);
                    else addPhoto(f);
                }
                if (done.size() < uris.size()) {
                    Toast.makeText(this, "Some photos couldn't be added", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void addPhoto(File f) {
        Models.Photo p = new Models.Photo();
        p.file = f.getName();
        p.addedBy = store.me();
        p.addedAt = System.currentTimeMillis();
        task.photos.add(p);
        store.save();
        bindPhotos();
    }

    private void bindPhotos() {
        photos.removeAllViews();
        int size = Ui.dp(this, 140);
        int pad = Ui.dp(this, 10);
        for (Models.Photo p : task.photos) {
            photos.addView(photoCell(p, size, () -> Ui.confirm(this, "Remove this photo?",
                    "It will be deleted from this field.", "Remove", () -> {
                        task.photos.remove(p);
                        store.deletePhoto(p);
                        store.save();
                        bindPhotos();
                    })), cellLp());
        }

        // Dashed "add" tile at the end.
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.bg_add_tile);
        ImageView cam = new ImageView(this);
        cam.setImageResource(R.drawable.ic_camera);
        cam.setColorFilter(getColor(R.color.blue));
        tile.addView(cam, new FrameLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40), Gravity.CENTER));
        tile.setContentDescription("Add photo");
        tile.setOnClickListener(v -> photoSource());
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(size, size);
        tl.topMargin = pad;
        photos.addView(tile, tl);
        photos.setClipChildren(false);
    }

    private LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 14));
        return lp;
    }

    /** A rounded thumbnail (tap to view) with a red remove badge on its corner. */
    private FrameLayout photoCell(Models.Photo p, int size, Runnable onRemove) {
        int pad = Ui.dp(this, 10);
        File f = store.photoFile(p.file);
        FrameLayout cell = new FrameLayout(this);
        cell.setPadding(0, pad, pad, 0);  // room for the remove badge

        ShapeableImageView iv = new ShapeableImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(Ui.dp(this, 12)).build());
        cell.addView(iv, new FrameLayout.LayoutParams(size, size));
        ImageLoader.load(iv, f, 420);
        iv.setOnClickListener(v -> Ui.showPhoto(this, p, f));

        ImageView remove = new ImageView(this);
        remove.setImageResource(R.drawable.ic_remove);
        remove.setColorFilter(0xFFFFFFFF);
        remove.setBackgroundResource(R.drawable.bg_red_circle);
        int r = Ui.dp(this, 5);
        remove.setPadding(r, r, r, r);
        remove.setContentDescription("Remove photo");
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34), Gravity.END | Gravity.TOP);
        rl.topMargin = -pad;
        rl.rightMargin = -pad;
        cell.addView(remove, rl);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        remove.setOnClickListener(v -> onRemove.run());
        return cell;
    }

    // ----- delete -----

    private void confirmDelete() {
        Ui.confirm(this, "Delete this field?", "Its notes and photos will be removed.", "Delete", () -> {
            store.deletePhotos(task);
            log.tasks.remove(task);
            store.save();
            task = null;
            finish();
        });
    }

    private void startDictation() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your notes");
        try {
            dictate.launch(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice input isn't available on this phone", Toast.LENGTH_LONG).show();
        }
    }
}
