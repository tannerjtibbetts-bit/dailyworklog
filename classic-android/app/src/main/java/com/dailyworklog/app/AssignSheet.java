package com.dailyworklog.app;

import android.content.ClipData;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
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
        show(c, project, current, title, done, false, false);
    }

    /** The Notify picker: the same sheet with a "Select all" row on top. */
    static void showWithSelectAll(Context c, Models.Project project, List<String> current, String title, Done done) {
        show(c, project, current, title, done, false, true);
    }

    /**
     * Manage crew: every group (rename, delete, add; drag people between them), each person's details,
     * and who gets assignment texts. Saves as you go.
     */
    static void manage(Context c, Models.Project project) {
        show(c, project, new ArrayList<>(), "Manage Crew", sel -> {}, true, false);
    }

    private static void show(Context c, Models.Project project, List<String> current, String title, Done done,
                             boolean manage, boolean selectAll) {
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
            // "Select all": everyone shown (after the search), ticked when they're all selected.
            List<String> shown = new ArrayList<>();
            for (String name : project.crew) if (ql.isEmpty() || name.toLowerCase(Locale.ROOT).contains(ql)) shown.add(name);
            // Everything that shows a "Select all" tick, kept in step when someone is ticked.
            List<Runnable> syncs = new ArrayList<>();
            Runnable syncAll = () -> {
                for (Runnable x : syncs) x.run();
            };
            if (selectAll && !shown.isEmpty()) {
                View all = inf.inflate(R.layout.item_user_check, rows, false);
                CheckBox allBox = all.findViewById(R.id.box);
                all.findViewById(R.id.badge).setVisibility(View.GONE);
                all.findViewById(R.id.phone).setVisibility(View.GONE);
                TextView label = all.findViewById(R.id.name);
                label.setText("Select all");
                label.setTypeface(null, android.graphics.Typeface.BOLD);
                syncs.add(() -> allBox.setChecked(selected.containsAll(shown)));
                all.setOnClickListener(x -> {
                    if (selected.containsAll(shown)) selected.removeAll(shown);
                    else selected.addAll(shown);
                    render[0].run();
                });
                rows.addView(all);
            }
            for (Models.Project.Group g : project.groups) {
                List<String> group = new ArrayList<>();
                for (String name : project.crew) {
                    if (!g.id.equals(project.roleOf(name))) continue;
                    if (!ql.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(ql)) continue;
                    group.add(name);
                }
                // Manage Crew shows every group (so people can be dragged into an empty one); pickers hide empty ones.
                if (group.isEmpty() && !manage) continue;
                LinearLayout section = new LinearLayout(c);
                section.setOrientation(LinearLayout.VERTICAL);
                LinearLayout head = heading(c, g.name);
                section.addView(head);
                if (manage) groupTools(c, store, project, g, head, render[0]);
                if (selectAll && !group.isEmpty()) {
                    CheckBox groupBox = new CheckBox(c);
                    groupBox.setText("Select all");
                    groupBox.setTextSize(13);
                    groupBox.setTextColor(c.getColor(R.color.grey));
                    groupBox.setOnClickListener(x -> {
                        if (selected.containsAll(group)) selected.removeAll(group);
                        else selected.addAll(group);
                        render[0].run();
                    });
                    syncs.add(() -> groupBox.setChecked(selected.containsAll(group)));
                    head.addView(groupBox);
                }
                for (String name : group) {
                    View r = row(c, inf, section, store, project, name, selected, manage, render[0], syncAll);
                    if (name.equals(justAdded[0])) added = r;
                    section.addView(r);
                }
                if (manage) dropTarget(c, store, project, g, section, render[0]);
                rows.addView(section);
            }
            syncAll.run();
            if (manage) {
                TextView addGroup = new TextView(c);
                addGroup.setText("+  Add group");
                addGroup.setTextColor(c.getColor(R.color.blue));
                addGroup.setTextSize(19);
                addGroup.setPadding(Ui.dp(c, 24), Ui.dp(c, 18), Ui.dp(c, 24), Ui.dp(c, 18));
                addGroup.setBackgroundResource(android.R.drawable.list_selector_background);
                addGroup.setOnClickListener(x -> Ui.prompt(c, "New group", "e.g. Night shift", "", "Add",
                        n -> !n.isEmpty() && !project.groupNameTaken(n, ""), "Enter a new group name", n -> {
                            project.addGroup(n);
                            store.save();
                            render[0].run();
                        }));
                rows.addView(addGroup);
                if (q.isEmpty()) {
                    TextView paste = new TextView(c);
                    paste.setText("Paste crew numbers");
                    paste.setTextColor(c.getColor(R.color.blue));
                    paste.setTextSize(19);
                    paste.setPadding(Ui.dp(c, 24), Ui.dp(c, 6), Ui.dp(c, 24), Ui.dp(c, 18));
                    paste.setBackgroundResource(android.R.drawable.list_selector_background);
                    paste.setOnClickListener(x -> pasteNumbers(c, store, project, render[0]));
                    rows.addView(paste);
                }
            }
            if (!exact) {
                TextView add = new TextView(c);
                add.setText(q.isEmpty() ? "+  Add crew member" : "+  Add \u201C" + q + "\u201D to crew");
                add.setTextColor(c.getColor(R.color.blue));
                add.setTextSize(19);
                add.setPadding(Ui.dp(c, 24), Ui.dp(c, 18), Ui.dp(c, 24), Ui.dp(c, 18));
                add.setBackgroundResource(android.R.drawable.list_selector_background);
                add.setOnClickListener(x -> CrewDialog.add(c, project, Fmt.capitalize(q), person -> {
                    String name = person.name;
                    addCrew(store, project, manage ? new LinkedHashSet<>() : selected, name);
                    person.saveTo(project, name);
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

    /** A group's heading row: its name in grey capitals; tools or a "Select all" tick go on the right. */
    private static LinearLayout heading(Context c, String label) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, Ui.dp(c, 12), 0);
        TextView t = new TextView(c);
        t.setText(label.toUpperCase(Locale.ROOT));
        t.setTextColor(c.getColor(R.color.grey));
        t.setTextSize(13);
        t.setLetterSpacing(0.06f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 4));
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Manage Crew: pencil (rename) and, when there's more than one group, trash (delete) on a group heading. */
    private static void groupTools(Context c, Store store, Models.Project project, Models.Project.Group g,
                                   LinearLayout head, Runnable render) {
        head.addView(icon(c, R.drawable.ic_edit, "Rename group", x -> Ui.prompt(c, "Rename group", "Group name", g.name, "Save",
                n -> !n.isEmpty() && !project.groupNameTaken(n, g.id), "Enter a new group name", n -> {
                    g.name = n;
                    store.save();
                    render.run();
                })));
        if (project.groups.size() > 1) {
            head.addView(icon(c, R.drawable.ic_delete, "Delete group", x -> {
                Models.Project.Group to = null;
                for (Models.Project.Group o : project.groups) if (o != g) { to = o; break; }
                Ui.confirm(c, "Delete the " + g.name + " group?", "Everyone in it moves to " + to.name + ".", "Delete", () -> {
                    project.deleteGroup(g.id);
                    store.save();
                    render.run();
                });
            }));
        }
    }

    private static ImageView icon(Context c, int res, String tip, View.OnClickListener click) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setColorFilter(c.getColor(R.color.grey));
        i.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        i.setBackgroundResource(android.R.drawable.list_selector_background);
        i.setContentDescription(tip);
        if (android.os.Build.VERSION.SDK_INT >= 26) i.setTooltipText(tip);
        i.setOnClickListener(click);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        return i;
    }

    /** Manage Crew: a person dragged by their handle onto a group (heading or people) joins it. */
    private static void dropTarget(Context c, Store store, Models.Project project, Models.Project.Group g,
                                   LinearLayout section, Runnable render) {
        section.setOnDragListener((sv, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return e.getLocalState() instanceof String;
                case DragEvent.ACTION_DRAG_ENTERED:
                    sv.setBackgroundColor(c.getColor(R.color.sky));
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    sv.setBackground(null);
                    return true;
                case DragEvent.ACTION_DROP:
                    sv.setBackground(null);
                    String who = (String) e.getLocalState();
                    if (!g.id.equals(project.roleOf(who))) {
                        project.roles.put(who, g.id);
                        store.save();
                        sv.post(render);  // redraw after the drag has finished
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private static View row(Context c, LayoutInflater inf, LinearLayout parent, Store store, Models.Project project,
                            String name, Set<String> selected, boolean manage, Runnable render, Runnable onToggle) {
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
            for (String details : new String[]{project.personLine(name), project.numbersLine(name)}) {
                if (details.isEmpty()) continue;
                TextView more = new TextView(c);
                more.setText(details);
                more.setTextColor(c.getColor(R.color.grey));
                more.setTextSize(14);
                ((ViewGroup) phone.getParent()).addView(more);
            }
            r.setOnClickListener(x -> editPerson(c, store, project, name, render));
            // Drag handle: hold and move to drop this person on another group.
            ImageView handle = new ImageView(c);
            handle.setImageResource(R.drawable.ic_drag);
            handle.setColorFilter(c.getColor(R.color.grey_light));
            handle.setPadding(Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12));
            handle.setContentDescription("Drag to another group");
            if (android.os.Build.VERSION.SDK_INT >= 26) handle.setTooltipText("Drag to another group");
            handle.setOnTouchListener((hv, ev) -> {
                if (ev.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
                r.startDragAndDrop(ClipData.newPlainText("crew", name), new View.DragShadowBuilder(r), name, 0);
                return true;
            });
            ((ViewGroup) r).addView(handle, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 48)));
        } else {
            box.setChecked(selected.contains(name));
            r.setOnClickListener(x -> {
                if (!selected.remove(name)) selected.add(name);
                box.setChecked(selected.contains(name));
                onToggle.run();  // keeps "Select all" in step
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

    /** Paste rows copied from a crew list; numbers go on the matching people. */
    private static void pasteNumbers(Context c, Store store, Models.Project project, Runnable render) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 22), Ui.dp(c, 4), Ui.dp(c, 22), 0);
        TextView help = new TextView(c);
        help.setText("Copy the rows from your crew list (for example in Excel) and paste them here. Each row needs the "
                + "person's name and their numbers. The first row can be the headings.");
        help.setTextColor(c.getColor(R.color.grey));
        help.setTextSize(15);
        box.addView(help);
        EditText et = new EditText(c);
        et.setHint("Name | Employee # | BP # | DISN #");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setSingleLine(false);
        et.setMinLines(6);
        et.setMaxLines(12);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setTextSize(14);
        box.addView(et);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                .setTitle("Paste crew numbers").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add numbers", (x, w) -> {
                    List<CrewNumbers.Row> rowsIn = CrewNumbers.parse(et.getText().toString());
                    CrewNumbers.Applied done = CrewNumbers.apply(project, rowsIn);
                    store.save();
                    render.run();
                    com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
                            .setMessage(CrewNumbers.summary(done)).setPositiveButton("OK", null);
                    if (!done.missing.isEmpty()) {
                        b.setNeutralButton("Add them to the crew", (y, z) -> {
                            CrewNumbers.addMissing(project, done.missing);
                            store.save();
                            render.run();
                        });
                    }
                    b.show();
                })
                .show();
    }

    private static void editPerson(Context c, Store store, Models.Project project, String name, Runnable render) {
        Runnable remove = () -> Ui.confirm(c, "Remove " + name + " from the crew?",
                "They'll stay on fields they're already assigned to.", "Remove", () -> {
                    project.crew.remove(name);
                    store.save();
                    render.run();
                });
        CrewDialog.show(c, project, name, "Save", CrewDialog.Person.of(project, name), remove, person -> {
                    String newName = person.name;
                    if (!newName.equals(name) && project.crew.contains(newName)) {
                        android.widget.Toast.makeText(c, newName + " is already on the crew", android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    project.renamePerson(name, newName);
                    person.saveTo(project, newName);
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
