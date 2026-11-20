package com.dailyworklog.app;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Documents": photos and files kept for every project (open, rename, share, delete), with + to add one. */
public class DocumentsActivity extends FormScreen {
    private ActivityResultLauncher<Uri> takePhoto;
    private ActivityResultLauncher<String[]> pickFile;
    private String pendingPhoto;
    private String query = "";
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) pendingPhoto = savedInstanceState.getString("pendingPhoto");
        Models.Project project = store.project(getIntent().getStringExtra("project"));
        setTitles("Documents", project == null ? "" : project.name);

        takePhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            String name = pendingPhoto;
            pendingPhoto = null;
            if (name == null) return;
            File f = store.photoFile(name);
            if (!Boolean.TRUE.equals(ok) || !f.exists() || f.length() == 0) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                return;
            }
            PhotoUtil.BG.execute(() -> {
                PhotoUtil.normalize(f);
                runOnUiThread(() -> CropView.show(this, f, () -> LinksExtras.nameAndSave(this, name, "", this::renderList)));
            });
        });
        pickFile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            String original = Docs.displayName(this, uri);
            PhotoUtil.BG.execute(() -> {
                String name = Docs.copyIn(this, store, uri, original);
                runOnUiThread(() -> {
                    if (name == null) {
                        Toast.makeText(this, "Couldn't copy that file", Toast.LENGTH_LONG).show();
                        return;
                    }
                    LinksExtras.nameAndSave(this, name, Docs.withoutExtension(original), this::renderList);
                });
            });
        });

        // Search box
        EditText search = new EditText(this);
        search.setHint("Search documents");
        search.setSingleLine(true);
        search.setBackgroundResource(R.drawable.bg_search);
        search.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                renderList();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = Ui.dp(this, 12);
        body.addView(search, slp);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        body.setPadding(body.getPaddingLeft(), body.getPaddingTop(), body.getPaddingRight(), Ui.dp(this, 110));

        // The big "+" at the bottom, over the list
        ViewGroup parent = (ViewGroup) scroll.getParent();
        int at = parent.indexOfChild(scroll);
        parent.removeView(scroll);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_add);
        fab.setColorFilter(0xFFFFFFFF);
        fab.setBackgroundResource(R.drawable.bg_pill_dark);
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setElevation(Ui.dp(this, 6));
        fab.setContentDescription("Add a document");
        fab.setOnClickListener(v -> addMenu());
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this, 156), Ui.dp(this, 72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        flp.bottomMargin = Ui.dp(this, 20);
        frame.addView(fab, flp);
        parent.addView(frame, at, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        renderList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) renderList();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("pendingPhoto", pendingPhoto);
    }

    private void renderList() {
        list.removeAllViews();
        List<Models.Document> docs = LinksExtras.documents(store, query);
        if (docs.isEmpty()) {
            TextView t = text(query.isEmpty()
                    ? "No documents yet.\nTap + to take a photo of a document or upload a file.\nDocuments show up in every project."
                    : "Nothing matches “" + query + "”", 16, R.color.grey, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 40), 0, 0);
            list.addView(t);
            return;
        }
        for (Models.Document d : docs) {
            View card = LinksExtras.docCard(this);
            LinksExtras.bindDoc(this, card, d, this::renderList);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(this, 12);
            card.setLayoutParams(lp);
            list.addView(card);
        }
    }

    private void addMenu() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_camera, "Take a photo of a document", () -> {
            pendingPhoto = Store.newDocFile("jpg");
            try {
                takePhoto.launch(FileProvider.getUriForFile(this, getPackageName() + ".files", store.photoFile(pendingPhoto)));
            } catch (ActivityNotFoundException e) {
                pendingPhoto = null;
                Toast.makeText(this, "No camera app on this phone", Toast.LENGTH_LONG).show();
            }
        }));
        items.add(new Ui.SheetItem(R.drawable.ic_folder, "Upload a file", () -> pickFile.launch(new String[]{"*/*"})));
        Ui.menu(this, null, items);
    }
}
