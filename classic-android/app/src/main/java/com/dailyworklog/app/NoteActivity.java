package com.dailyworklog.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
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
import com.google.android.material.shape.ShapeAppearanceModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Notes tab: write a note, add photos, and tick it into today's report. Saves automatically. */
public class NoteActivity extends AppCompatActivity {
    private Store store;
    private Models.Project project;
    private Models.Note n;
    /** True until the note has something in it and is added to the project. */
    private boolean isNew;
    private String pendingFile;

    private LinearLayout photos;
    private EditText note;
    private CheckBox include;
    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<String> pickImages;
    private ActivityResultLauncher<Intent> dictate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_note);
        Ui.applyInsets(findViewById(R.id.root));
        store = Store.get(this);
        project = store.project(getIntent().getStringExtra("project"));
        if (project == null) {
            finish();
            return;
        }
        String id = getIntent().getStringExtra("note");
        if (savedInstanceState != null) {
            if (savedInstanceState.getString("note") != null) id = savedInstanceState.getString("note");
            pendingFile = savedInstanceState.getString("pendingFile");
        }
        n = project.findNote(id);
        isNew = n == null;
        if (isNew) {
            n = new Models.Note();
            n.by = store.me();
        }

        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (pendingFile == null) return;
            File f = store.photoFile(pendingFile);
            pendingFile = null;
            if (!Boolean.TRUE.equals(ok)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                return;
            }
            PhotoUtil.BG.execute(() -> {
                boolean good = PhotoUtil.fromCamera(this, f);
                runOnUiThread(() -> {
                    if (good) addPhotos(java.util.Collections.singletonList(f.getName()));
                });
            });
        });
        pickImages = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            PhotoUtil.BG.execute(() -> {
                List<String> done = new ArrayList<>();
                for (Uri u : uris) {
                    File f = PhotoUtil.newFile(store);
                    if (PhotoUtil.copyFrom(this, u, f)) done.add(f.getName());
                }
                runOnUiThread(() -> addPhotos(done));
            });
        });
        dictate = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
            ArrayList<String> res = r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res == null || res.isEmpty()) return;
            String cur = note.getText().toString().trim();
            String add = Fmt.capitalize(res.get(0));
            note.setText(cur.isEmpty() ? add : cur + " " + add);
            note.setSelection(note.getText().length());
            persist();
        });

        photos = findViewById(R.id.photos);
        note = findViewById(R.id.note);
        include = findViewById(R.id.include);
        ((TextView) findViewById(R.id.topProject)).setText(project.name);
        if (savedInstanceState == null) note.setText(n.text);

        findViewById(R.id.back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.addPhotos).setOnClickListener(v -> photoSource());
        findViewById(R.id.noteMic).setOnClickListener(v -> {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note");
            try {
                dictate.launch(i);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Voice input isn't available on this phone", Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.delete).setOnClickListener(v -> Ui.confirm(this, "Delete this note?",
                "The note and its photos are deleted and taken off any report.", "Delete", () -> {
                    store.deletePhotos(n);
                    if (!isNew) {
                        project.notes.remove(n);
                        project.forgetExtras(n.id);
                        store.save();
                    }
                    n = null;  // nothing left to save
                    isNew = true;
                    finish();
                }));
        findViewById(R.id.done).setOnClickListener(v -> {
            persist();
            finish();
        });

        bindInclude();
        bindPhotos();
        if (isNew && savedInstanceState == null) note.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persist();
        // A note that was emptied out is removed when you leave it.
        if (isFinishing() && !isNew && n != null && n.isEmpty()) {
            project.notes.remove(n);
            project.forgetExtras(n.id);
            store.save();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (!isNew && n != null) out.putString("note", n.id);
        out.putString("pendingFile", pendingFile);
    }

    /** Saves the note; a brand-new note is only kept once it has text or a photo. */
    private void persist() {
        if (n == null || note == null) return;
        String text = note.getText().toString().trim();
        boolean changed = !text.equals(n.text);
        n.text = text;
        if (isNew) {
            if (n.isEmpty()) return;
            project.notes.add(0, n);
            isNew = false;
            changed = true;
        }
        if (!changed) return;
        n.updatedAt = System.currentTimeMillis();
        project.syncNote(n);
        store.save();
    }

    // ----- include in today's report -----

    private void bindInclude() {
        String today = Fmt.todayIso();
        Models.DailyLog target = project.todayLog(today);
        include.setOnCheckedChangeListener(null);
        if (target == null) {
            include.setVisibility(View.GONE);
            findViewById(R.id.includeHint).setVisibility(View.VISIBLE);
            return;
        }
        include.setVisibility(View.VISIBLE);
        findViewById(R.id.includeHint).setVisibility(View.GONE);
        include.setText(target.date.equals(today) ? "Include in today's report"
                : "Include in " + Fmt.logDate(target.date) + " report");
        Models.Extra e = target.findExtra(n.id);
        include.setChecked(e != null && e.include);
        include.setOnCheckedChangeListener((b, on) -> {
            persist();
            if (isNew) {
                if (on) Toast.makeText(this, "Write a note or add a photo first", Toast.LENGTH_SHORT).show();
                bindInclude();
                return;
            }
            Models.Extra x = target.findExtra(n.id);
            if (x == null) {
                x = project.record(target, today, Models.Extra.NOTE, n.id, n.by, n.text, n.photos,
                        store.me(), System.currentTimeMillis());
            }
            x.include = on;
            store.save();
            b.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        });
    }

    // ----- photos -----

    private void addPhotos(List<String> files) {
        if (files.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (String f : files) {
            Models.Photo p = new Models.Photo();
            p.file = f;
            p.addedBy = store.me();
            p.addedAt = now;
            n.photos.add(p);
        }
        boolean wasNew = isNew;
        persist();
        if (!wasNew) {  // persist() only saves on text changes once the note exists
            n.updatedAt = now;
            project.syncNote(n);
            store.save();
        }
        bindPhotos();
    }

    private void photoSource() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_camera, "Take photo", this::startCamera));
        items.add(new Ui.SheetItem(R.drawable.ic_gallery, "Choose from gallery", () -> {
            try {
                pickImages.launch("image/*");
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_LONG).show();
            }
        }));
        Ui.menu(this, null, items);
    }

    private void startCamera() {
        File f = PhotoUtil.newFile(store);
        pendingFile = f.getName();
        try {
            takePicture.launch(FileProvider.getUriForFile(this, getPackageName() + ".files", f));
        } catch (ActivityNotFoundException e) {
            pendingFile = null;
            Toast.makeText(this, "No camera app found", Toast.LENGTH_LONG).show();
        }
    }

    private void bindPhotos() {
        photos.removeAllViews();
        int size = Ui.dp(this, 120);
        int pad = Ui.dp(this, 10);
        for (Models.Photo p : new ArrayList<>(n.photos)) {
            File f = store.photoFile(p.file);
            FrameLayout cell = new FrameLayout(this);
            cell.setPadding(0, pad, pad, 0);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            ShapeableImageView iv = new ShapeableImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(Ui.dp(this, 12)).build());
            iv.setOnClickListener(v -> Ui.showPhoto(this, p, f));
            cell.addView(iv, new FrameLayout.LayoutParams(size, size));
            ImageLoader.load(iv, f, 360);
            ImageView remove = new ImageView(this);
            remove.setImageResource(R.drawable.ic_remove);
            remove.setColorFilter(0xFFFFFFFF);
            remove.setBackgroundResource(R.drawable.bg_red_circle);
            int r = Ui.dp(this, 5);
            remove.setPadding(r, r, r, r);
            remove.setContentDescription("Remove photo");
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32), Gravity.END | Gravity.TOP);
            rl.topMargin = -pad;
            rl.rightMargin = -pad;
            cell.addView(remove, rl);
            remove.setOnClickListener(v -> Ui.confirm(this, "Delete this photo?",
                    "It's removed from this note and any report it's in.", "Delete", () -> {
                        n.photos.remove(p);
                        store.deletePhoto(p);
                        n.updatedAt = System.currentTimeMillis();
                        if (!isNew) {
                            project.syncNote(n);
                            store.save();
                        }
                        bindPhotos();
                    }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(this, 12));
            photos.addView(cell, lp);
        }
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.bg_add_tile);
        ImageView cam = new ImageView(this);
        cam.setImageResource(R.drawable.ic_camera);
        cam.setColorFilter(getColor(R.color.blue));
        tile.addView(cam, new FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36), Gravity.CENTER));
        tile.setContentDescription("Add photo");
        tile.setOnClickListener(v -> photoSource());
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(size, size);
        tl.topMargin = pad;
        photos.addView(tile, tl);
        photos.setClipChildren(false);
    }
}
