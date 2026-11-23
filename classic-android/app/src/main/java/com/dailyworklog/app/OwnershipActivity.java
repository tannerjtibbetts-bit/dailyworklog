package com.dailyworklog.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.PublicKey;

/**
 * Support > Ownership proof: the copyright notice, and a signed proof that the owner's phone holds the
 * app's owner key (make one on the owner's phone; anyone can check one).
 */
public class OwnershipActivity extends FormScreen {
    private EditText paste;
    private LinearLayout result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitles("Ownership proof", null);
        PublicKey key = Access.builtInOwnerKey(this);
        boolean owner = Access.isOwner(this);

        TextView notice = text(Ownership.NOTICE, 18, R.color.navy, true);
        body.addView(notice);
        para2("Daily Work Log was created by " + Ownership.OWNER + ". Its code, screens, manual and pictures are his "
                + "work and may not be copied.");
        if (key != null) {
            TextView fp = para2("This app’s owner key fingerprint: " + OwnershipProof.fingerprint(key));
            fp.setTextIsSelectable(true);
        }

        heading("Make an ownership proof");
        para2(owner
                ? "This phone holds the app’s owner key, so it can sign a proof that you own Daily Work Log. "
                + "Keep a copy somewhere safe, and send it to anyone who needs to see it."
                : "Only the owner’s phone can make one.");
        if (owner) {
            EditText name = box("Your name", false);
            name.setText(store.userName);
            name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            button("Make ownership proof", true, v -> make(name.getText().toString().trim()));
        }

        heading("Check an ownership proof");
        para2("Paste a proof you were sent, then tap Check.");
        paste = box("Paste the ownership proof here", true);
        button("Check", false, v -> check());
        result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        body.addView(result);
    }

    private TextView para2(String s) {
        TextView t = text(s, 16, R.color.text, false);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        body.addView(t);
        return t;
    }

    private EditText box(String hint, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setBackgroundResource(R.drawable.bg_input);
        int p = Ui.dp(this, 12);
        e.setPadding(p, p, p, p);
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setSingleLine(false);
            e.setMinLines(3);
            e.setMaxLines(8);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        body.addView(e, lp);
        return e;
    }

    private void make(String name) {
        if (name.isEmpty()) {
            Toast.makeText(this, "Type your name first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String text = Access.ownershipProof(this, name);
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Ownership proof", text));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "Daily Work Log ownership proof");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Send ownership proof"));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't make the proof: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** The result card's title and text for a checked proof (null: nothing found). */
    static String[] verdict(OwnershipProof.Result r) {
        if (r == null) return new String[]{"No proof found",
                "Paste the whole ownership proof, including the line that starts with " + OwnershipProof.PREFIX};
        if (!r.genuine) return new String[]{"Not genuine", "This proof was changed, or it wasn’t signed with an owner key."};
        String match = r.sameAsThisApp == null
                ? "This copy of the app has no owner key built in, so compare the fingerprint with the owner’s."
                : r.sameAsThisApp ? "It is the owner key built into this copy of the app."
                : "It is NOT the owner key built into this copy of the app.";
        return new String[]{"Genuine", "Signed by " + r.name + " on " + r.at + " with owner key " + r.fingerprint + ". " + match};
    }

    private void check() {
        OwnershipProof.Result r = OwnershipProof.check(paste.getText().toString(), Access.builtInOwnerKey(this));
        String[] v = verdict(r);
        boolean good = r != null && r.genuine && !Boolean.FALSE.equals(r.sameAsThisApp);
        int color = getColor(good ? R.color.green : R.color.red);
        result.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 16));
        bg.setColor((color & 0x00FFFFFF) | 0x1A000000);
        bg.setStroke(Ui.dp(this, 2), color);
        card.setBackground(bg);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        TextView t = text(v[0], 18, good ? R.color.green : R.color.red, true);
        TextView b = text(v[1], 16, R.color.text, false);
        b.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(t);
        card.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        result.addView(card, lp);
        scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN));
    }
}
