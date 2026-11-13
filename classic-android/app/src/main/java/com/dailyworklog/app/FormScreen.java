package com.dailyworklog.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** A simple scrolling page with a back arrow and a title, used by the report form screens. */
abstract class FormScreen extends AppCompatActivity {
    protected Store store;
    protected LinearLayout body;
    protected ScrollView scroll;
    private TextView titleView, subView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        store = Store.get(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(getColor(R.color.navy));
        back.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        back.setContentDescription("Back");
        back.setBackgroundResource(android.R.drawable.list_selector_background);
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = text("", 24, R.color.navy, true);
        subView = text("", 15, R.color.grey, false);
        titles.addView(titleView);
        titles.addView(subView);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        body.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 40));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Ui.applyInsets(root);
    }

    protected void setTitles(String title, String sub) {
        titleView.setText(title);
        subView.setText(sub);
        subView.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
    }

    protected TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    protected TextView heading(String s) {
        TextView t = text(s, 19, R.color.navy, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 8));
        body.addView(t);
        return t;
    }

    protected TextView para(String s) {
        TextView t = text(s, 16, R.color.grey, false);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8));
        body.addView(t);
        return t;
    }

    /** A white card with a bold first line and a grey second line. */
    protected LinearLayout card(String first, String second, View.OnClickListener tap, View.OnLongClickListener hold) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card);
        c.setForeground(getDrawable(android.R.drawable.list_selector_background));
        c.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        c.addView(text(first, 18, R.color.navy, true));
        if (second != null && !second.isEmpty()) c.addView(text(second, 15, R.color.grey, false));
        if (tap != null) c.setOnClickListener(tap);
        if (hold != null) c.setOnLongClickListener(hold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 10);
        body.addView(c, lp);
        return c;
    }

    /** A blue text row, like "+  Add a form". */
    protected TextView action(String label, View.OnClickListener tap) {
        TextView t = text(label, 18, R.color.blue, false);
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        t.setOnClickListener(tap);
        body.addView(t);
        return t;
    }

    /** A big button (filled blue, or light). */
    protected TextView button(String label, boolean filled, View.OnClickListener tap) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(18);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(filled ? 0xFFFFFFFF : getColor(R.color.deep_blue));
        b.setBackgroundResource(filled ? R.drawable.bg_pill_blue : R.drawable.bg_chip_sky);
        b.setOnClickListener(tap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, filled ? 56 : 48));
        lp.topMargin = Ui.dp(this, 12);
        body.addView(b, lp);
        return b;
    }

    protected AlertDialog busy(String message) {
        AlertDialog d = new MaterialAlertDialogBuilder(this).setMessage(message).setCancelable(false).create();
        d.show();
        return d;
    }

    /** The project with this id, or with this name, or null. */
    protected Models.Project project(String id, String name) {
        Models.Project p = id == null ? null : store.project(id);
        if (p != null) return p;
        for (Models.Project x : store.projects) if (x.name.equals(name)) return x;
        return null;
    }
}
