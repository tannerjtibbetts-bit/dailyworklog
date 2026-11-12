package com.dailyworklog.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** One crew member's box: name, phone, group, level, trade, union local, and whether they're texted when assigned. */
final class CrewDialog {
    private CrewDialog() {}

    /** What the box holds. */
    static final class Person {
        String name = "", phone = "", group = "", level = "", trade = "", local = "";
        boolean texts;

        /** Someone already on the project. */
        static Person of(Models.Project p, String name) {
            Person x = new Person();
            x.name = name;
            x.phone = p.phoneOf(name);
            x.group = p.roleOf(name);
            x.level = p.levelOf(name);
            x.trade = p.tradeOf(name);
            x.local = p.localOf(name);
            x.texts = p.assignTexts.contains(name);
            return x;
        }

        /** Stores group, level, trade, union local, phone and texting under name (blank ones are removed). */
        void saveTo(Models.Project p, String as) {
            Models.Project.putOrRemove(p.phones, as, phone);
            p.roles.put(as, group.isEmpty() ? p.groups.get(0).id : group);
            Models.Project.putOrRemove(p.levels, as, level);
            Models.Project.putOrRemove(p.trades, as, trade);
            Models.Project.putOrRemove(p.unionLocals, as, local);
            if (texts) p.assignTexts.add(as);
            else p.assignTexts.remove(as);
        }
    }

    interface Done {
        void onDone(Person p);
    }

    /** "Add crew member" with the name typed so far. */
    static void add(Context c, Models.Project project, String prefillName, Done done) {
        Person p = new Person();
        p.name = prefillName == null ? "" : prefillName;
        p.group = project.groups.get(0).id;
        show(c, project, "Add crew member", "Add", p, null, done);
    }

    /** onRemove adds a Remove button when not null. */
    static void show(Context c, Models.Project project, String title, String positive, Person start,
                     Runnable onRemove, Done done) {
        Store store = Store.get(c);
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_crew, null);
        EditText name = v.findViewById(R.id.name);
        EditText phone = v.findViewById(R.id.phone);
        name.setText(start.name);
        name.setSelection(name.getText().length());
        phone.setText(start.phone);
        ((CheckBox) v.findViewById(R.id.texts)).setChecked(start.texts);

        // Group
        Spinner group = v.findViewById(R.id.group);
        List<String> groupNames = new ArrayList<>();
        int at = 0;
        for (int i = 0; i < project.groups.size(); i++) {
            groupNames.add(project.groups.get(i).name);
            if (project.groups.get(i).id.equals(start.group)) at = i;
        }
        group.setAdapter(adapter(c, groupNames));
        group.setSelection(at);

        // Level: tap one to pick it, tap it again to clear
        String[] level = {start.level};
        TextView appr = v.findViewById(R.id.levelApprentice), journ = v.findViewById(R.id.levelJourneyman);
        Runnable showLevel = () -> {
            chip(c, appr, Models.Project.APPRENTICE.equals(level[0]));
            chip(c, journ, Models.Project.JOURNEYMAN.equals(level[0]));
        };
        appr.setOnClickListener(x -> {
            level[0] = Models.Project.APPRENTICE.equals(level[0]) ? "" : Models.Project.APPRENTICE;
            showLevel.run();
        });
        journ.setOnClickListener(x -> {
            level[0] = Models.Project.JOURNEYMAN.equals(level[0]) ? "" : Models.Project.JOURNEYMAN;
            showLevel.run();
        });
        showLevel.run();

        String[] trade = {start.trade}, local = {start.local};
        options(c, store, v.findViewById(R.id.trade), store.tradeOptions, trade,
                "Add a trade…", "Add a trade", "e.g. Ironworker", "Edit trades…", "Trades");
        options(c, store, v.findViewById(R.id.local), store.localOptions, local,
                "Add a union local…", "Add a union local", "e.g. Local 128", "Edit union locals…", "Union locals");

        ScrollView scroll = new ScrollView(c);
        scroll.addView(v);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(c)
                .setTitle(title).setView(scroll)
                .setPositiveButton(positive, null).setNegativeButton("Cancel", null);
        if (onRemove != null) builder.setNeutralButton("Remove", (x, w) -> onRemove.run());
        final AlertDialog d = builder.create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Enter a name");
                return;
            }
            d.dismiss();
            Person out = new Person();
            out.name = Fmt.capitalize(n);
            out.phone = phone.getText().toString().trim();
            int g = group.getSelectedItemPosition();
            out.group = project.groups.get(g >= 0 && g < project.groups.size() ? g : 0).id;
            out.level = level[0];
            out.trade = trade[0];
            out.local = local[0];
            out.texts = ((CheckBox) v.findViewById(R.id.texts)).isChecked();
            done.onDone(out);
        }));
        if (onRemove == null && d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        if (onRemove == null) name.requestFocus();
    }

    private static ArrayAdapter<String> adapter(Context c, List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(c, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private static void chip(Context c, TextView t, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(c, 22));
        bg.setColor(on ? c.getColor(R.color.deep_blue) : c.getColor(R.color.chip));
        t.setBackground(bg);
        t.setTextColor(on ? 0xFFFFFFFF : c.getColor(R.color.text));
        t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        t.setSelected(on);
    }

    /**
     * A drop-down of "None", the options (plus the person's own value if it was removed from the list),
     * then "Add …" and "Edit …". value[0] holds the pick.
     */
    private static void options(Context c, Store store, Spinner sp, List<String> options, String[] value,
                                String addItem, String addTitle, String addHint, String editItem, String editTitle) {
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            List<String> items = new ArrayList<>();
            items.add("None");
            items.addAll(options);
            if (!value[0].isEmpty() && !options.contains(value[0])) items.add(value[0]);
            items.add(addItem);
            items.add(editItem);
            sp.setOnItemSelectedListener(null);
            sp.setAdapter(adapter(c, items));
            int at = value[0].isEmpty() ? 0 : items.indexOf(value[0]);
            sp.setSelection(Math.max(0, at), false);
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                int last = Math.max(0, at);

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == last) return;  // the initial selection
                    String pick = items.get(position);
                    if (pick.equals(addItem)) {
                        sp.setSelection(last, false);
                        Ui.prompt(c, addTitle, addHint, "", "Add", t -> {
                            value[0] = addOption(options, t);
                            store.save();
                            fill[0].run();
                        });
                    } else if (pick.equals(editItem)) {
                        sp.setSelection(last, false);
                        editOptions(c, store, options, editTitle, fill[0]);
                    } else {
                        last = position;
                        value[0] = position == 0 ? "" : pick;
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        };
        fill[0].run();
    }

    /** Adds to the list unless it's there already (any case); returns the entry to pick. */
    static String addOption(List<String> options, String text) {
        String t = text.trim();
        for (String o : options) if (o.equalsIgnoreCase(t)) return o;
        options.add(t);
        return t;
    }

    /** The list with a red × on each entry and an Add box at the bottom. Removing doesn't touch people's values. */
    private static void editOptions(Context c, Store store, List<String> options, String title, Runnable after) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 24), Ui.dp(c, 8), Ui.dp(c, 16), 0);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(list);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            if (options.isEmpty()) {
                TextView none = new TextView(c);
                none.setText("Nothing in the list yet.");
                none.setTextColor(c.getColor(R.color.grey));
                none.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
                list.addView(none);
            }
            for (String o : new ArrayList<>(options)) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView t = new TextView(c);
                t.setText(o);
                t.setTextSize(17);
                t.setTextColor(c.getColor(R.color.text));
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                ImageView x = new ImageView(c);
                x.setImageResource(R.drawable.ic_close);
                x.setColorFilter(0xFFD93B3F);
                x.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
                x.setContentDescription("Remove " + o);
                x.setBackgroundResource(android.R.drawable.list_selector_background);
                x.setOnClickListener(y -> {
                    options.remove(o);
                    store.save();
                    render[0].run();
                });
                row.addView(x, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
                list.addView(row);
            }
        };
        render[0].run();
        LinearLayout addRow = new LinearLayout(c);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText et = new EditText(c);
        et.setHint("Add");
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        addRow.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = new TextView(c);
        add.setText("Add");
        add.setTextColor(c.getColor(R.color.blue));
        add.setTextSize(17);
        add.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        add.setOnClickListener(y -> {
            String t = et.getText().toString().trim();
            if (t.isEmpty()) return;
            addOption(options, t);
            store.save();
            et.setText("");
            render[0].run();
        });
        addRow.addView(add);
        box.addView(addRow);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        new MaterialAlertDialogBuilder(c)
                .setTitle(title).setView(scroll)
                .setPositiveButton("Done", null)
                .setOnDismissListener(x -> after.run())
                .show();
    }
}
