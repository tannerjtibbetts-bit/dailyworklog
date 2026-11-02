package com.dailyworklog.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

/** Shared dialogs, bottom sheets and small view helpers. */
final class Ui {
    private Ui() {}

    interface TextCallback {
        void onText(String text);
    }

    interface CrewCallback {
        void onCrew(String name, String phone, String role, boolean texts);
    }

    static final class SheetItem {
        final int icon;
        final String label;
        final Runnable action;
        final boolean danger;

        SheetItem(int icon, String label, Runnable action) {
            this(icon, label, action, false);
        }

        SheetItem(int icon, String label, Runnable action, boolean danger) {
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.danger = danger;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Pads the root for the status bar, nav bar and keyboard (edge-to-edge on Android 15+). */
    static void applyInsets(View root) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop();
        final int r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    static void prompt(Context c, String title, String hint, String prefill, String positive, TextCallback cb) {
        EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(prefill == null ? "" : prefill);
        et.setSelection(et.getText().length());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        FrameLayout box = new FrameLayout(c);
        box.setPadding(dp(c, 22), dp(c, 8), dp(c, 22), 0);
        box.addView(et);
        final AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(positive, null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> {
            View ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String s = et.getText().toString().trim();
                if (s.isEmpty()) {
                    et.setError("Required");
                    return;
                }
                d.dismiss();
                cb.onText(s);
            });
            et.setOnEditorActionListener((v, actionId, e) -> {
                ok.performClick();
                return true;
            });
            et.requestFocus();
        });
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }

    /** Phone number prompt with the phone keypad. */
    static void promptPhone(Context c, String title, String prefill, TextCallback cb) {
        EditText et = new EditText(c);
        et.setHint("e.g. 519-555-0123");
        et.setText(prefill == null ? "" : prefill);
        et.setSelection(et.getText().length());
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setSingleLine(true);
        FrameLayout box = new FrameLayout(c);
        box.setPadding(dp(c, 22), dp(c, 8), dp(c, 22), 0);
        box.addView(et);
        final AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle(title).setView(box)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            if (SmsHelper.digits(s).length() < 7) {
                et.setError("Enter a phone number");
                return;
            }
            d.dismiss();
            cb.onText(s);
        }));
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        et.requestFocus();
    }

    /** Name + optional phone number for a new crew member. */
    static void promptCrew(Context c, String prefillName, CrewCallback cb) {
        crewDialog(c, "Add crew member", "Add", prefillName, "", Models.Project.CREW, false, null, cb);
    }

    /**
     * One crew member's details: name, phone, group (Crew / Tech / Supervision) and whether
     * they're texted when assigned. onRemove adds a Remove button when not null.
     */
    static void crewDialog(Context c, String title, String positive, String prefillName, String prefillPhone,
                           String role, boolean texts, Runnable onRemove, CrewCallback cb) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_crew, null);
        EditText name = v.findViewById(R.id.name);
        EditText phone = v.findViewById(R.id.phone);
        name.setText(prefillName == null ? "" : prefillName);
        name.setSelection(name.getText().length());
        phone.setText(prefillPhone == null ? "" : prefillPhone);
        ((android.widget.RadioGroup) v.findViewById(R.id.role)).check(
                Models.Project.TECH.equals(role) ? R.id.roleTech : Models.Project.MGMT.equals(role) ? R.id.roleMgmt : R.id.roleCrew);
        ((android.widget.CheckBox) v.findViewById(R.id.texts)).setChecked(texts);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(c)
                .setTitle(title).setView(v)
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
            int r = ((android.widget.RadioGroup) v.findViewById(R.id.role)).getCheckedRadioButtonId();
            String group = r == R.id.roleTech ? Models.Project.TECH : r == R.id.roleMgmt ? Models.Project.MGMT : Models.Project.CREW;
            boolean textOn = ((android.widget.CheckBox) v.findViewById(R.id.texts)).isChecked();
            cb.onCrew(Fmt.capitalize(n), phone.getText().toString().trim(), group, textOn);
        }));
        if (onRemove == null && d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        if (onRemove == null) name.requestFocus();
    }

    static void confirm(Context c, String title, String message, String positive, Runnable onYes) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Bottom sheet with large icon tiles, styled like the old app's menu. */
    static BottomSheetDialog menu(Context c, String title, List<SheetItem> items) {
        BottomSheetDialog d = new BottomSheetDialog(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 22), dp(c, 10), dp(c, 22), dp(c, 28));

        View handle = new View(c);
        handle.setBackgroundResource(R.drawable.bg_handle);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(c, 44), dp(c, 5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(c, 14);
        col.addView(handle, hp);

        if (title != null) {
            TextView t = new TextView(c);
            t.setText(title);
            t.setTextColor(c.getColor(R.color.grey));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t.setPadding(0, 0, 0, dp(c, 8));
            col.addView(t);
        }
        LayoutInflater inf = LayoutInflater.from(c);
        for (SheetItem it : items) {
            View row = inf.inflate(R.layout.item_sheet_row, col, false);
            ImageView icon = row.findViewById(R.id.icon);
            TextView label = row.findViewById(R.id.label);
            icon.setImageResource(it.icon);
            label.setText(it.label);
            if (it.danger) {
                int red = c.getColor(R.color.red);
                label.setTextColor(red);
                icon.setColorFilter(red);
            }
            row.setOnClickListener(v -> {
                d.dismiss();
                it.action.run();
            });
            col.addView(row);
        }
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.addView(col);
        d.setContentView(scroll);
        d.getBehavior().setSkipCollapsed(true);
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.show();
        return d;
    }

    /** Round initials badge, like "JM" and "SH" in the old app. */
    static TextView badge(Context c, String name, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(Fmt.initials(name));
        t.setGravity(Gravity.CENTER);
        t.setTextColor(c.getColor(R.color.deep_blue));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.32f);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(c.getColor(R.color.sky));
        bg.setStroke(dp(c, 2), Color.WHITE);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.setMarginStart(-dp(c, 6));
        t.setLayoutParams(lp);
        return t;
    }

    static void fillBadges(LinearLayout row, List<String> people, int sizeDp, int max) {
        row.removeAllViews();
        Context c = row.getContext();
        int shown = Math.min(max, people.size());
        for (int i = 0; i < shown; i++) row.addView(badge(c, people.get(i), sizeDp));
        if (people.size() > shown) {
            TextView more = badge(c, "+", sizeDp);
            more.setText("+" + (people.size() - shown));
            row.addView(more);
        }
        if (row.getChildCount() > 0) {
            LinearLayout.LayoutParams first = (LinearLayout.LayoutParams) row.getChildAt(0).getLayoutParams();
            first.setMarginStart(dp(c, 6));
        }
    }

    /** Full-screen photo with who took it and when. Tap anywhere to close. */
    static void showPhoto(Context c, Models.Photo p, File f) {
        Dialog d = new Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout fl = new FrameLayout(c);
        fl.setBackgroundColor(Color.BLACK);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fl.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView cap = new TextView(c);
        cap.setTextColor(Color.WHITE);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cap.setText(p.addedBy + ", " + Fmt.stamp(p.addedAt));
        cap.setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 56));
        cap.setBackgroundColor(0x99000000);
        fl.addView(cap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        ImageLoader.load(iv, f, 2048);
        fl.setOnClickListener(v -> d.dismiss());
        d.setContentView(fl);
        d.show();
    }
}
