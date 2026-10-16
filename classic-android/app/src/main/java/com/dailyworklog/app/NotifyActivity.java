package com.dailyworklog.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
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

/**
 * Notify: pick crew, write a note, add photos. Everyone picked gets it, whether or not they're
 * ticked for assignment texts. A note without photos is texted straight from the app to each
 * person; with photos, Messages opens with it all attached.
 */
public class NotifyActivity extends AppCompatActivity {
    private Store store;
    private Models.Project project;
    private final ArrayList<String> to = new ArrayList<>();
    private final ArrayList<String> photoFiles = new ArrayList<>();
    private boolean sent;
    private String pendingFile;

    private LinearLayout recipients, photos;
    private EditText note;
    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<String> pickImages;
    private ActivityResultLauncher<Intent> dictate;
    private ActivityResultLauncher<String> smsPermission;
    private Runnable afterSmsPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_notify);
        Ui.applyInsets(findViewById(R.id.root));
        store = Store.get(this);
        project = store.project(getIntent().getStringExtra("project"));
        if (project == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            ArrayList<String> t = savedInstanceState.getStringArrayList("to");
            ArrayList<String> ph = savedInstanceState.getStringArrayList("photos");
            if (t != null) to.addAll(t);
            if (ph != null) photoFiles.addAll(ph);
            pendingFile = savedInstanceState.getString("pendingFile");
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
                boolean good = PhotoUtil.normalize(f);
                runOnUiThread(() -> {
                    if (good) {
                        photoFiles.add(f.getName());
                        bindPhotos();
                    }
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
                runOnUiThread(() -> {
                    photoFiles.addAll(done);
                    bindPhotos();
                });
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
        });

        smsPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            Runnable r = afterSmsPermission;
            afterSmsPermission = null;
            if (r != null) r.run();
        });

        recipients = findViewById(R.id.recipients);
        photos = findViewById(R.id.photos);
        note = findViewById(R.id.note);
        ((TextView) findViewById(R.id.topProject)).setText(project.name);

        findViewById(R.id.back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.chooseTo).setOnClickListener(v -> chooseTo());
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
        findViewById(R.id.send).setOnClickListener(v -> send());

        bindRecipients();
        bindPhotos();
        if (savedInstanceState == null) findViewById(R.id.chooseTo).post(this::chooseTo);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList("to", to);
        out.putStringArrayList("photos", photoFiles);
        out.putString("pendingFile", pendingFile);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Drafts that were never sent shouldn't leave photos behind.
        if (isFinishing() && !sent && store != null) {
            for (String f : photoFiles) {
                File file = store.photoFile(f);
                ImageLoader.evict(file);
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private void chooseTo() {
        AssignSheet.show(this, project, to, "Send to", sel -> {
            to.clear();
            to.addAll(sel);
            bindRecipients();
        });
    }

    private void bindRecipients() {
        recipients.removeAllViews();
        for (String name : to) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, Ui.dp(this, 14));
            TextView badge = Ui.badge(this, name, 44);
            ((LinearLayout.LayoutParams) badge.getLayoutParams()).setMarginStart(0);
            row.addView(badge);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(this, 14), 0, 0, 0);
            TextView n = new TextView(this);
            n.setText(name);
            n.setTextSize(19);
            n.setTextColor(getColor(R.color.text));
            col.addView(n);
            TextView ph = new TextView(this);
            String number = project.phoneOf(name);
            ph.setText(number.isEmpty() ? "+ Add phone number" : number);
            ph.setTextSize(14);
            ph.setTextColor(getColor(number.isEmpty() ? R.color.blue : R.color.grey));
            ph.setOnClickListener(v -> Ui.promptPhone(this, "Phone number for " + name, number, num -> {
                project.phones.put(name, num);
                store.save();
                bindRecipients();
            }));
            col.addView(ph);
            row.addView(col);
            row.setOnClickListener(v -> chooseTo());
            recipients.addView(row);
        }
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
        for (String name : new ArrayList<>(photoFiles)) {
            File f = store.photoFile(name);
            FrameLayout cell = new FrameLayout(this);
            cell.setPadding(0, pad, pad, 0);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            ShapeableImageView iv = new ShapeableImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(Ui.dp(this, 12)).build());
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
            remove.setOnClickListener(v -> {
                photoFiles.remove(name);
                ImageLoader.evict(f);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                bindPhotos();
            });
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

    private void send() {
        String text = note.getText().toString().trim();
        if (to.isEmpty()) {
            Toast.makeText(this, "Choose who to send it to", Toast.LENGTH_SHORT).show();
            chooseTo();
            return;
        }
        if (text.isEmpty() && photoFiles.isEmpty()) {
            note.setError("Write a note or add a photo");
            note.requestFocus();
            return;
        }
        ensurePhones(0, () -> {
            List<String> numbers = new ArrayList<>();
            for (String n : to) numbers.add(project.phoneOf(n));
            List<File> files = new ArrayList<>();
            for (String f : photoFiles) files.add(store.photoFile(f));

            Models.Notice n = new Models.Notice();
            n.to = new ArrayList<>(to);
            n.note = text;
            n.sentAt = System.currentTimeMillis();
            for (String f : photoFiles) {
                Models.Photo p = new Models.Photo();
                p.file = f;
                p.addedBy = store.me();
                p.addedAt = n.sentAt;
                n.photos.add(p);
            }
            project.notices.add(0, n);
            project.record(null, Fmt.todayIso(), Models.Extra.NOTIFY, n.id, Fmt.join(n.to), n.note,
                    n.photos, store.me(), n.sentAt);
            store.save();
            sent = true;

            String body = SmsHelper.noticeText(project, text, store.me(), store.contactPhone);
            if (!files.isEmpty()) {
                SmsHelper.send(this, numbers, body, files);
                finish();
            } else if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                textEach(numbers, body);
            } else {
                afterSmsPermission = () -> {
                    if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        textEach(numbers, body);
                    } else {
                        // No permission: open Messages with everyone and the text ready.
                        SmsHelper.send(this, numbers, body, null);
                        finish();
                    }
                };
                smsPermission.launch(Manifest.permission.SEND_SMS);
            }
        });
    }

    /** Texts each person separately from this phone; anyone it couldn't reach goes to Messages. */
    private void textEach(List<String> numbers, String body) {
        List<String> sentTo = new ArrayList<>(), failed = new ArrayList<>();
        for (int i = 0; i < to.size(); i++) {
            if (SmsHelper.sendDirect(this, numbers.get(i), body)) sentTo.add(to.get(i));
            else failed.add(numbers.get(i));
        }
        if (!sentTo.isEmpty()) Toast.makeText(this, "Texted " + Fmt.join(sentTo), Toast.LENGTH_LONG).show();
        if (!failed.isEmpty()) SmsHelper.send(this, failed, body, null);
        finish();
    }

    /** Makes sure every recipient has a number, asking for any that are missing. */
    private void ensurePhones(int i, Runnable then) {
        if (i >= to.size()) {
            then.run();
            return;
        }
        SmsHelper.withPhone(this, project, to.get(i), () -> {
            bindRecipients();
            ensurePhones(i + 1, then);
        });
    }
}
