package com.dailyworklog.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** The Organization Contacts tab: contact cards, their Call / Text / Email / Open link buttons, and the edit box. */
final class ContactsUi {
    private ContactsUi() {}

    /** Fills a contact card (item_contact). changed runs after an edit or delete. */
    static void bind(Activity a, View card, Models.Contact c, Runnable changed) {
        ((TextView) card.findViewById(R.id.name)).setText(c.name);
        line(card.findViewById(R.id.role), c.role());
        line(card.findViewById(R.id.phone), c.phone);
        line(card.findViewById(R.id.email), c.email);
        LinearLayout actions = card.findViewById(R.id.actions);
        actions.removeAllViews();
        if (!c.phone.isEmpty()) actions.addView(button(a, R.drawable.ic_call, "Call", () -> call(a, c)));
        if (!c.phone.isEmpty()) actions.addView(button(a, R.drawable.ic_sms, "Text", () -> text(a, c)));
        if (!c.email.isEmpty()) actions.addView(button(a, R.drawable.ic_mail, "Email", () -> email(a, c)));
        if (!c.link.isEmpty()) actions.addView(button(a, R.drawable.ic_open, "Open link", () -> open(a, c)));
        actions.setVisibility(actions.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        card.setOnClickListener(v -> edit(a, c, changed));
        card.setOnLongClickListener(v -> {
            menu(a, c, changed);
            return true;
        });
    }

    private static void line(TextView t, String s) {
        t.setText(s);
        t.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static ImageView button(Activity a, int icon, String tip, Runnable run) {
        ImageView b = new ImageView(a);
        b.setImageResource(icon);
        b.setColorFilter(a.getColor(R.color.deep_blue));
        b.setBackgroundResource(R.drawable.bg_icon_soft);
        int pad = Ui.dp(a, 10);
        b.setPadding(pad, pad, pad, pad);
        b.setContentDescription(tip);
        if (android.os.Build.VERSION.SDK_INT >= 26) b.setTooltipText(tip);
        b.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
        lp.setMarginEnd(Ui.dp(a, 10));
        b.setLayoutParams(lp);
        return b;
    }

    private static void start(Activity a, Intent i) {
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No app on this phone can do that", Toast.LENGTH_LONG).show();
        }
    }

    static void call(Activity a, Models.Contact c) {
        start(a, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.phone))));
    }

    static void text(Activity a, Models.Contact c) {
        start(a, new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.phone))));
    }

    static void email(Activity a, Models.Contact c) {
        start(a, new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + c.email)));
    }

    static void open(Activity a, Models.Contact c) {
        start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(c.link)));
    }

    /** Press and hold: what's set of Call / Text / Email / Open link, then Edit and Delete. */
    private static void menu(Activity a, Models.Contact c, Runnable changed) {
        List<Ui.SheetItem> items = new ArrayList<>();
        if (!c.phone.isEmpty()) items.add(new Ui.SheetItem(R.drawable.ic_call, "Call", () -> call(a, c)));
        if (!c.phone.isEmpty()) items.add(new Ui.SheetItem(R.drawable.ic_sms, "Text", () -> text(a, c)));
        if (!c.email.isEmpty()) items.add(new Ui.SheetItem(R.drawable.ic_mail, "Email", () -> email(a, c)));
        if (!c.link.isEmpty()) items.add(new Ui.SheetItem(R.drawable.ic_open, "Open link", () -> open(a, c)));
        items.add(new Ui.SheetItem(R.drawable.ic_edit, "Edit contact", () -> edit(a, c, changed)));
        items.add(new Ui.SheetItem(R.drawable.ic_delete, "Delete contact", () -> Ui.confirm(a, "Delete this contact?",
                c.name + " will be removed from every project.", "Delete", () -> {
                    Store s = Store.get(a);
                    s.contacts.remove(c);
                    s.save();
                    changed.run();
                }), true));
        Ui.menu(a, c.name, items);
    }

    /** "Add contact" (existing null) or "Edit contact". */
    static void edit(Activity a, Models.Contact existing, Runnable changed) {
        Models.Contact c = existing == null ? new Models.Contact() : existing;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 4), Ui.dp(a, 22), 0);
        int words = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        EditText name = field(a, box, "Name", "", c.name, words);
        EditText company = field(a, box, "Company", "", c.company, words);
        EditText title = field(a, box, "Job title", "e.g. Safety coordinator", c.title, words);
        EditText phone = field(a, box, "Phone number", "", c.phone, InputType.TYPE_CLASS_PHONE);
        EditText email = field(a, box, "Email", "", c.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText link = field(a, box, "Web link (optional)", "", c.link, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText notes = field(a, box, "Notes", "", c.notes,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setSingleLine(false);
        notes.setMinLines(2);
        notes.setGravity(Gravity.TOP | Gravity.START);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle(existing == null ? "Add contact" : "Edit contact")
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Enter a name");
                name.requestFocus();
                return;
            }
            String mail = email.getText().toString().trim();
            if (!mail.isEmpty() && !Fmt.isEmail(mail)) {
                email.setError("Enter a valid email address");
                email.requestFocus();
                return;
            }
            String web = link.getText().toString().trim();
            String url = "";
            if (!web.isEmpty()) {
                url = Fmt.webLink(web);
                if (url == null) {
                    link.setError("Enter a web address");
                    link.requestFocus();
                    return;
                }
            }
            c.name = n;
            c.company = company.getText().toString().trim();
            c.title = title.getText().toString().trim();
            c.phone = phone.getText().toString().trim();
            c.email = mail;
            c.link = url;
            c.notes = notes.getText().toString().trim();
            Store s = Store.get(a);
            if (existing == null) s.contacts.add(c);
            s.save();
            d.dismiss();
            changed.run();
        }));
        if (existing == null && d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        if (existing == null) name.requestFocus();
    }

    /** A grey label with its text box under it. */
    private static EditText field(Activity a, LinearLayout box, String label, String hint, String value, int type) {
        TextView l = new TextView(a);
        l.setText(label);
        l.setTextColor(a.getColor(R.color.grey));
        l.setTextSize(14);
        l.setPadding(0, Ui.dp(a, 12), 0, 0);
        box.addView(l);
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setText(value);
        box.addView(e);
        return e;
    }
}
