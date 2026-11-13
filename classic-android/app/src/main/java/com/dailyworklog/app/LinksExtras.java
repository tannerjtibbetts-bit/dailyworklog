package com.dailyworklog.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Links tab's extras: the Apprentice reports / Disciplinary action cards and documents. */
final class LinksExtras {
    private LinksExtras() {}

    /** Row markers for the list. */
    static final Object REPORT_CARDS = new Object();

    static final class Heading {
        final String text;

        Heading(String text) {
            this.text = text;
        }
    }

    /** Documents matching the (lower-case) query, newest first. */
    static List<Models.Document> documents(Store store, String query) {
        List<Models.Document> out = new ArrayList<>();
        for (Models.Document d : store.documents) {
            if (query.isEmpty() || d.title.toLowerCase(Locale.ROOT).contains(query)) out.add(d);
        }
        out.sort((a, b) -> Long.compare(b.addedAt, a.addedAt));
        return out;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** The two big cards side by side. */
    static View reportCards(Activity a, String projectId) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(bigCard(a, R.drawable.ic_school, "Apprentice reports", projectId, Models.ReportForm.APPRENTICE));
        View gap = new View(a);
        row.addView(gap, new LinearLayout.LayoutParams(Ui.dp(a, 12), 1));
        row.addView(bigCard(a, R.drawable.ic_gavel, "Disciplinary action", projectId, Models.ReportForm.DISCIPLINE));
        return row;
    }

    private static View bigCard(Activity a, int icon, String label, String projectId, String group) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setForeground(a.getDrawable(android.R.drawable.list_selector_background));
        int pad = Ui.dp(a, 16);
        card.setPadding(pad, pad, pad, pad);
        card.setMinimumHeight(Ui.dp(a, 120));
        ImageView iv = new ImageView(a);
        iv.setImageResource(icon);
        iv.setColorFilter(a.getColor(R.color.deep_blue));
        iv.setBackgroundResource(R.drawable.bg_icon_soft);
        iv.setPadding(Ui.dp(a, 11), Ui.dp(a, 11), Ui.dp(a, 11), Ui.dp(a, 11));
        card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48)));
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextColor(a.getColor(R.color.navy));
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, Ui.dp(a, 10), 0, 0);
        card.addView(t);
        card.setOnClickListener(v -> a.startActivity(new Intent(a, ReportsActivity.class)
                .putExtra("project", projectId).putExtra("group", group)));
        card.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    static View heading(Activity a) {
        TextView t = new TextView(a);
        t.setTextColor(a.getColor(R.color.navy));
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, Ui.dp(a, 10));
        t.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** A document card: icon, title, "Photo · Nov 13, 2026". */
    static View docCard(Activity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setForeground(a.getDrawable(android.R.drawable.list_selector_background));
        card.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16));
        card.setMinimumHeight(Ui.dp(a, 88));
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        ImageView iv = new ImageView(a);
        iv.setId(R.id.icon);
        iv.setColorFilter(a.getColor(R.color.deep_blue));
        iv.setBackgroundResource(R.drawable.bg_icon_soft);
        iv.setPadding(Ui.dp(a, 13), Ui.dp(a, 13), Ui.dp(a, 13), Ui.dp(a, 13));
        card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(a, 16), 0, 0, 0);
        TextView title = new TextView(a);
        title.setId(R.id.title);
        title.setTextColor(a.getColor(R.color.navy));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        texts.addView(title, wrap());
        TextView sub = new TextView(a);
        sub.setId(R.id.sub);
        sub.setTextColor(a.getColor(R.color.grey));
        sub.setTextSize(15);
        texts.addView(sub, wrap());
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView more = new ImageView(a);
        more.setId(R.id.more);
        more.setImageResource(R.drawable.ic_more);
        more.setColorFilter(a.getColor(R.color.grey));
        more.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        more.setBackgroundResource(android.R.drawable.list_selector_background);
        more.setContentDescription("More");
        if (android.os.Build.VERSION.SDK_INT >= 26) more.setTooltipText("More");
        card.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        return card;
    }

    static void bindDoc(Activity a, View card, Models.Document d, Runnable changed) {
        ((ImageView) card.findViewById(R.id.icon)).setImageResource(Docs.icon(d.kind));
        ((TextView) card.findViewById(R.id.title)).setText(d.title);
        ((TextView) card.findViewById(R.id.sub)).setText(Models.Document.kindLabel(d.kind) + " · " + ReportFill.day(d.addedAt));
        card.setOnClickListener(v -> open(a, d));
        card.setOnLongClickListener(v -> {
            menu(a, d, changed);
            return true;
        });
        card.findViewById(R.id.more).setOnClickListener(v -> menu(a, d, changed));
    }

    static void open(Activity a, Models.Document d) {
        Docs.open(a, Store.get(a).photoFile(d.file), d.kind, d.title);
    }

    private static void menu(Activity a, Models.Document d, Runnable changed) {
        Store store = Store.get(a);
        File f = store.photoFile(d.file);
        List<Ui.SheetItem> items = new ArrayList<>();
        items.add(new Ui.SheetItem(R.drawable.ic_open, "Open", () -> open(a, d)));
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Rename", () -> Ui.prompt(a, "Rename", "Name this document", d.title, "Save", n -> {
            d.title = n;
            store.save();
            changed.run();
        })));
        items.add(new Ui.SheetItem(R.drawable.ic_share, "Share", () -> Docs.share(a, f, d.title)));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete document", () -> Ui.confirm(a, "Delete this document?",
                d.title + " will be removed from every project.", "Delete", () -> {
                    store.documents.remove(d);
                    Docs.deleteFiles(store, java.util.Collections.singletonList(d.file));
                    store.save();
                    changed.run();
                }), true));
        Ui.menu(a, d.title, items);
    }

    /** Names a new document and saves it (the file is already in the photos folder). */
    static void nameAndSave(Activity a, String file, String suggested, Runnable changed) {
        Store store = Store.get(a);
        Ui.prompt(a, "Name this document", "e.g. Lifting plan", suggested, "Save", n -> {
            Models.Document d = new Models.Document();
            d.title = n;
            d.file = file;
            d.kind = Models.Document.kindOf(file);
            store.documents.add(d);
            store.save();
            changed.run();
        });
    }
}
