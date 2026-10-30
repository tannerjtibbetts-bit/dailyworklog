package com.dailyworklog.app;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Assign Users" sheet: search the project crew, tick people, add new crew members. Free. */
final class AssignSheet {
    private AssignSheet() {}

    interface Done {
        void onDone(List<String> selected);
    }

    static void show(Context c, Models.Project project, List<String> current, String title, Done done) {
        show(c, project, current, title, done, false);
    }

    /** Manage crew: groups (Crew / Techs / Management) and who gets assignment texts. Saves as you go. */
    static void manage(Context c, Models.Project project) {
        show(c, project, new ArrayList<>(), "Manage Crew", sel -> {}, true);
    }

    private static void show(Context c, Models.Project project, List<String> current, String title, Done done,
                             boolean manage) {
        Store store = Store.get(c);
        if (!store.userName.isEmpty() && !project.crew.contains(store.userName)) {
            project.crew.add(0, store.userName);
            store.save();
        }
        BottomSheetDialog d = new BottomSheetDialog(c);
        View v = LayoutInflater.from(c).inflate(R.layout.sheet_assign, null);
        LinearLayout rows = v.findViewById(R.id.rows);
        NestedScrollView scroll = v.findViewById(R.id.rowsScroll);
        EditText search = v.findViewById(R.id.search);
        ((TextView) v.findViewById(R.id.sheetTitle)).setText(title);
        Set<String> selected = new LinkedHashSet<>(current);
        LayoutInflater inf = LayoutInflater.from(c);
        final String[] justAdded = {null};

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            rows.removeAllViews();
            String q = search.getText().toString().trim();
            String ql = q.toLowerCase(Locale.ROOT);
            boolean exact = false;
            for (String name : project.crew) if (name.equalsIgnoreCase(q)) exact = true;
            View added = null;
            for (String role : Models.Project.ROLES) {
                List<String> group = new ArrayList<>();
                for (String name : project.crew) {
                    if (!role.equals(project.roleOf(name))) continue;
                    if (!ql.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(ql)) continue;
                    group.add(name);
                }
                if (group.isEmpty()) continue;
                rows.addView(heading(c, Models.Project.roleLabel(role)));
                for (String name : group) {
                    View r = row(c, inf, rows, store, project, name, selected, manage, render[0]);
                    if (name.equals(justAdded[0])) added = r;
                    rows.addView(r);
                }
            }
            if (!exact) {
                TextView add = new TextView(c);
                add.setText(q.isEmpty() ? "+  Add crew member" : "+  Add \u201C" + q + "\u201D to crew");
                add.setTextColor(c.getColor(R.color.blue));
                add.setTextSize(19);
                add.setPadding(Ui.dp(c, 24), Ui.dp(c, 18), Ui.dp(c, 24), Ui.dp(c, 18));
                add.setBackgroundResource(android.R.drawable.list_selector_background);
                add.setOnClickListener(x -> Ui.promptCrew(c, Fmt.capitalize(q), (name, number, role, texts) -> {
                    addCrew(store, project, manage ? new LinkedHashSet<>() : selected, name);
                    if (!number.isEmpty()) project.phones.put(name, number);
                    project.roles.put(name, role);
                    if (texts) project.assignTexts.add(name);
                    else project.assignTexts.remove(name);
                    store.save();
                    justAdded[0] = name;
                    if (search.getText().length() > 0) search.setText("");
                    else render[0].run();
                }));
                rows.addView(add);
            }
            // Bring a newly added person into view, wherever their group is.
            final View target = added;
            justAdded[0] = null;
            if (target != null) target.post(() -> target.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), false));
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int n) {}
            @Override public void afterTextChanged(Editable s) {
                render[0].run();
            }
        });
        v.findViewById(R.id.cancel).setOnClickListener(x -> d.dismiss());
        TextView ok = v.findViewById(R.id.assign);
        if (manage) {
            v.findViewById(R.id.cancel).setVisibility(View.INVISIBLE);
            ok.setText("Done");
        }
        ok.setOnClickListener(x -> {
            d.dismiss();
            done.onDone(new ArrayList<>(selected));
        });
        render[0].run();
        d.setContentView(v);
        d.getBehavior().setSkipCollapsed(true);
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.show();
    }

    private static TextView heading(Context c, String label) {
        TextView t = new TextView(c);
        t.setText(label.toUpperCase(Locale.ROOT));
        t.setTextColor(c.getColor(R.color.grey));
        t.setTextSize(13);
        t.setLetterSpacing(0.06f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 4));
        return t;
    }

    private static View row(Context c, LayoutInflater inf, LinearLayout parent, Store store, Models.Project project,
                            String name, Set<String> selected, boolean manage, Runnable render) {
        View r = inf.inflate(R.layout.item_user_check, parent, false);
        CheckBox box = r.findViewById(R.id.box);
        ((TextView) r.findViewById(R.id.badge)).setText(Fmt.initials(name));
        ((TextView) r.findViewById(R.id.name)).setText(name);
        TextView phone = r.findViewById(R.id.phone);
        String number = project.phoneOf(name);
        phone.setText(number.isEmpty() ? "+ Add phone number" : number + "  \u00B7  edit");
        phone.setTextColor(c.getColor(number.isEmpty() ? R.color.blue : R.color.grey));
        phone.setOnClickListener(x -> Ui.promptPhone(c, "Phone number for " + name, number, n -> {
            project.phones.put(name, n);
            store.save();
            render.run();
        }));
        if (manage) {
            // Tap a name for their details pop-up.
            box.setVisibility(View.GONE);
            phone.setClickable(false);
            phone.setBackground(null);
            phone.setText((number.isEmpty() ? "No phone number" : number)
                    + (project.assignTexts.contains(name) ? "  \u00B7  texted when assigned" : ""));
            phone.setTextColor(c.getColor(R.color.grey));
            r.setOnClickListener(x -> editPerson(c, store, project, name, render));
        } else {
            box.setChecked(selected.contains(name));
            r.setOnClickListener(x -> {
                if (!selected.remove(name)) selected.add(name);
                box.setChecked(selected.contains(name));
            });
        }
        r.setOnLongClickListener(x -> {
            Ui.confirm(c, "Remove " + name + " from the crew?",
                    "They'll stay on fields they're already assigned to.", "Remove", () -> {
                        project.crew.remove(name);
                        selected.remove(name);
                        store.save();
                        render.run();
                    });
            return true;
        });
        return r;
    }

    private static void editPerson(Context c, Store store, Models.Project project, String name, Runnable render) {
        Runnable remove = () -> Ui.confirm(c, "Remove " + name + " from the crew?",
                "They'll stay on fields they're already assigned to.", "Remove", () -> {
                    project.crew.remove(name);
                    store.save();
                    render.run();
                });
        Ui.crewDialog(c, name, "Save", name, project.phoneOf(name), project.roleOf(name),
                project.assignTexts.contains(name), remove, (newName, number, role, texts) -> {
                    if (!newName.equals(name) && project.crew.contains(newName)) {
                        android.widget.Toast.makeText(c, newName + " is already on the crew", android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    project.renamePerson(name, newName);
                    if (number.isEmpty()) project.phones.remove(newName);
                    else project.phones.put(newName, number);
                    project.roles.put(newName, role);
                    if (texts) project.assignTexts.add(newName);
                    else project.assignTexts.remove(newName);
                    if (Store.get(c).userName.equals(name)) Store.get(c).userName = newName;
                    store.save();
                    render.run();
                });
    }

    private static void addCrew(Store store, Models.Project p, Set<String> selected, String name) {
        if (!p.crew.contains(name)) p.crew.add(name);
        selected.add(name);
        store.save();
    }
}
