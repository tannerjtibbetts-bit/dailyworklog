package com.dailyworklog.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rename areas: a project's own names for South, North and Unzoned (e.g. "Garage 1"), so the checklist
 * fits the work front. The colours stay the same; reports use the new names.
 */
final class AreaNames {
    private AreaNames() {}

    /** The card colour and border of each area, as on the checklist. */
    private static int fill(String side) {
        return Models.Task.SOUTH.equals(side) ? R.color.south_bg : Models.Task.NORTH.equals(side) ? R.color.north_bg : R.color.unzoned_bg;
    }

    private static int stroke(String side) {
        return Models.Task.SOUTH.equals(side) ? R.color.south_stroke
                : Models.Task.NORTH.equals(side) ? R.color.north_stroke : R.color.unzoned_stroke;
    }

    /** Opens the Rename areas box for p; changed (if given) runs after the names are saved. */
    static void rename(Context c, Models.Project p, Runnable changed) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 22), 0);
        TextView intro = new TextView(c);
        intro.setText("Give the areas names that fit your work, like Garage 1 or Location 2. "
                + "The colours stay the same. Leave a box empty to keep the usual name.");
        intro.setTextColor(c.getColor(R.color.grey));
        intro.setTextSize(15);
        box.addView(intro);
        List<String> areas = Models.Task.AREAS;
        Map<String, EditText> boxes = new LinkedHashMap<>();
        for (String a : areas) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            // A small swatch of the area's card colour
            View swatch = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setColor(c.getColor(fill(a)));
            g.setCornerRadius(Ui.dp(c, 8));
            g.setStroke(Ui.dp(c, 2), c.getColor(stroke(a)));
            swatch.setBackground(g);
            row.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(c);
            label.setText(Models.Task.sideLabel(a) + " area");
            label.setTextColor(c.getColor(R.color.navy));
            label.setTextSize(13);
            label.setPadding(Ui.dp(c, 4), 0, 0, 0);
            col.addView(label);
            EditText et = new EditText(c);
            et.setHint(Models.Task.sideLabel(a));
            String own = p.areaNames.get(a);
            et.setText(own == null ? "" : own);
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
            col.addView(et);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            clp.setMarginStart(Ui.dp(c, 12));
            row.addView(col, clp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(c, 12);
            box.addView(row, rlp);
            boxes.put(a, et);
        }
        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle("Rename areas")
                .setView(scroll)
                .setNeutralButton("Use usual names", null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        d.setOnShowListener(x -> {
            // Clears the boxes; the box stays open
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                for (EditText et : boxes.values()) et.setText("");
            });
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Map<String, String> names = new LinkedHashMap<>();
                for (String a : areas) {
                    String s = boxes.get(a).getText().toString().trim();
                    if (!s.isEmpty()) names.put(a, s);
                }
                p.areaNames = names;
                Store.get(c).save();
                d.dismiss();
                if (changed != null) changed.run();
            });
        });
        d.show();
    }

    /** A small "Rename areas" text button (pencil icon) for next to the area choices. */
    static TextView button(Context c, Models.Project p, Runnable changed) {
        TextView t = new TextView(c);
        t.setText("Rename areas");
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(c.getColor(R.color.blue));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_edit, 0, 0, 0);
        t.setCompoundDrawablePadding(Ui.dp(c, 6));
        t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.blue)));
        t.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        t.setOnClickListener(v -> rename(c, p, changed));
        return t;
    }
}
