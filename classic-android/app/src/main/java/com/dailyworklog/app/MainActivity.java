package com.dailyworklog.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** Home screen: the list of projects (e.g. "B44 Zone 2 Tool Readiness"). */
public class MainActivity extends AppCompatActivity {
    private Store store;
    private ProjectAdapter adapter;
    private TextView empty;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Access.need(this) != Access.Need.NONE) {  // e.g. reopened after being signed out
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_projects);
        Ui.applyInsets(findViewById(R.id.root));
        store = Store.get(this);

        empty = findViewById(R.id.empty);
        subtitle = findViewById(R.id.subtitle);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.fab).setOnClickListener(v -> editProject(null));
        findViewById(R.id.profile).setOnClickListener(v -> askName());
        if (store.userName.isEmpty() && savedInstanceState == null) list.post(this::askName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.notifyDataSetChanged();
        empty.setVisibility(store.projects.isEmpty() ? View.VISIBLE : View.GONE);
        subtitle.setText(store.userName.isEmpty()
                ? "Tap the person icon to set your name"
                : "Signed in as " + store.userName
                        + (store.contactPhone.isEmpty() ? "" : "  \u00B7  " + SmsHelper.formatPhone(store.contactPhone)));
    }

    /** Your name, plus the number crew should reach you on (added to texts sent from this phone). */
    private void askName() {
        EditText name = new EditText(this);
        name.setHint("Your name");
        name.setText(store.userName);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setSingleLine(true);
        EditText phone = new EditText(this);
        phone.setHint("Number crew should call or text you on");
        phone.setText(store.contactPhone);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phone.setSingleLine(true);
        TextView hint = new TextView(this);
        hint.setText("Your name is shown on completed fields and photos. The number is added under your name in texts.");
        hint.setTextColor(getColor(R.color.grey));
        hint.setTextSize(14);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(name);
        box.addView(phone);
        box.addView(hint);
        // Profile: which task colours on the zone sheet are yours (photo imports pre-tick these)
        TextView colTitle = new TextView(this);
        colTitle.setText("My task colours on the zone sheet");
        colTitle.setTextColor(getColor(R.color.navy));
        colTitle.setTextSize(16);
        colTitle.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 2));
        box.addView(colTitle);
        String[][] colours = {{ZoneSheet.BLUE, "Dark blue"}, {ZoneSheet.ORANGE, "Orange (maintenance)"},
                {ZoneSheet.PINK, "Pink (electrical)"}};
        android.widget.CheckBox[] colourBoxes = new android.widget.CheckBox[colours.length];
        for (int i = 0; i < colours.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(colours[i][1]);
            cb.setChecked(store.myColours.contains(colours[i][0]));
            colourBoxes[i] = cb;
            box.addView(cb);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);
        androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Your profile").setView(scroll)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null)
                .setNeutralButton("More\u2026", (x, w) -> profileMore()).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Enter your name");
                return;
            }
            d.dismiss();
            store.userName = n;
            store.contactPhone = phone.getText().toString().trim();
            store.myColours.clear();
            for (int i = 0; i < colours.length; i++) if (colourBoxes[i].isChecked()) store.myColours.add(colours[i][0]);
            store.save();
            refresh();
        }));
        d.show();
        name.requestFocus();
    }

    /** Approvals (app owner), the user manual and Sign out. */
    private void profileMore() {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_person_add, Access.isOwner(this) ? "Approvals / hand over" : "App owner / Approvals",
                () -> startActivity(new Intent(this, ApprovalsActivity.class))));
        items.add(new Ui.SheetItem(R.drawable.ic_reorder, "Show or hide tabs", () -> LogsActivity.chooseTabs(this, null)));
        items.add(new Ui.SheetItem(R.drawable.ic_template, "User manual", () -> ManualActivity.open(this)));
        if (Access.hasPin(this)) {
            items.add(new Ui.SheetItem(R.drawable.ic_back, "Sign out", () -> {
                Access.signOut(this);
                startActivity(new Intent(this, SignInActivity.class));
                finish();
            }));
        }
        Ui.menu(this, null, items);
    }

    private void editProject(Models.Project existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_project, null);
        EditText name = v.findViewById(R.id.name);
        EditText loc = v.findViewById(R.id.location);
        if (existing != null) {
            name.setText(existing.name);
            loc.setText(existing.location);
            name.setSelection(name.getText().length());
        }
        final AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "New project" : "Edit project")
                .setView(v)
                .setPositiveButton(existing == null ? "Create" : "Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Enter a project name");
                return;
            }
            Models.Project p = existing;
            if (p == null) {
                p = new Models.Project();
                store.projects.add(0, p);
            }
            p.name = n;
            p.location = loc.getText().toString().trim();
            store.save();
            d.dismiss();
            refresh();
            if (existing == null) open(p);
        }));
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        name.requestFocus();
    }

    private void open(Models.Project p) {
        startActivity(new Intent(this, LogsActivity.class).putExtra("project", p.id));
    }

    private void projectMenu(Models.Project p) {
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Edit name and location", () -> editProject(p)));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete project", () ->
                Ui.confirm(this, "Delete " + p.name + "?",
                        "This removes all of its daily logs and photos. It can't be undone.", "Delete", () -> {
                            store.deletePhotos(p);
                            store.projects.remove(p);
                            store.save();
                            refresh();
                        }), true));
        Ui.menu(this, p.name, items);
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final TextView name, location, meta;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                location = v.findViewById(R.id.location);
                meta = v.findViewById(R.id.meta);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Models.Project p = store.projects.get(position);
            h.name.setText(p.name);
            h.location.setText(p.location);
            h.location.setVisibility(p.location.isEmpty() ? View.GONE : View.VISIBLE);
            Models.DailyLog latest = p.latestLog();
            h.meta.setText(latest == null ? "No daily logs yet"
                    : Fmt.plural(p.logs.size(), "daily log", "daily logs") + ", latest " + Fmt.logDate(latest.date));
            h.itemView.setOnClickListener(v -> open(p));
            h.itemView.setOnLongClickListener(v -> {
                projectMenu(p);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return store.projects.size();
        }
    }
}
