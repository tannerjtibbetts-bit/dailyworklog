package com.dailyworklog.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/**
 * Sign in: ask the owner for access (request code by text, approval code back), choose a PIN,
 * or enter it. "Keep me signed in on this phone" skips the PIN next time.
 */
public class SignInActivity extends AppCompatActivity {
    private LinearLayout body;

    /** Opens the app if this phone may use it, otherwise the sign-in screen. */
    static void enter(AppCompatActivity from) {
        if (Access.need(from) == Access.Need.NONE) {
            from.startActivity(new Intent(from, MainActivity.class));
        } else {
            from.startActivity(new Intent(from, SignInActivity.class));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        body.setPadding(pad, Ui.dp(this, 40), pad, pad);
        scroll.addView(body);
        setContentView(scroll);
        Ui.applyInsets(scroll);
        show();
    }

    private void show() {
        body.removeAllViews();
        switch (Access.need(this)) {
            case APPROVAL: showRequest(); break;
            case NEW_PIN: showNewPin(); break;
            case PIN: showPin(); break;
            default: open();
                return;
        }
        credit();
    }

    /** Support (the user manual) and fine print at the bottom. */
    private void credit() {
        TextView support = text("Support", 16, R.color.deep_blue);
        support.setTypeface(null, android.graphics.Typeface.BOLD);
        support.setGravity(Gravity.CENTER);
        support.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 4));
        support.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        support.setOnClickListener(v -> ManualActivity.open(this));
        TextView t = text("App created by Tanner Tibbetts", 11, R.color.grey_light);
        t.setGravity(Gravity.CENTER);
        t.setAlpha(0.8f);
        t.setPadding(0, Ui.dp(this, 40), 0, 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void open() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ----- asking the owner for access -----

    private void showRequest() {
        title("Sign in");
        para(Access.hadApproval(this)
                ? "Your approval has ended or was reset. Ask the app owner for a new one. Everything you've saved on this phone is still here."
                : "This app needs approval from its owner before you can use it.");
        heading("1. Ask for access");
        EditText name = input("Your full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        String saved = Access.requestedName(this);
        name.setText(saved.isEmpty() ? Store.get(this).userName : saved);
        button("Request access", true, () -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Enter your name");
                return;
            }
            String code = Access.requestCode(this, n);
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Daily Work Log access request from " + n + ":\n" + code);
            startActivity(Intent.createChooser(send, "Send your request to the app owner"));
        });
        para("Send it to the app owner by text. They'll text back an approval code.");

        heading("2. Enter the approval code");
        EditText code = input("Paste the whole text you were sent", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        code.setMinLines(3);
        code.setSingleLine(false);
        String passed = getIntent().getStringExtra("text");
        if (AccessCodes.parseApproval(passed) != null) code.setText(passed);
        button("Paste", false, () -> code.setText(clipboard()));
        button("Sign in", true, () -> {
            String err = Access.acceptApproval(this, code.getText().toString());
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                return;
            }
            Store store = Store.get(this);
            if (store.userName.isEmpty()) {
                store.userName = Access.requestedName(this);
                store.save();
            }
            Toast.makeText(this, "Approved. Welcome!", Toast.LENGTH_SHORT).show();
            show();
        });

        heading("Taking over as the app owner?");
        para("If the owner sent this phone a handover text, use this instead.");
        button("Take over as the app owner", false, () -> startActivity(new Intent(this, ApprovalsActivity.class)));
    }

    // ----- PIN -----

    private void showNewPin() {
        title("Choose a PIN");
        para("You'll use it to open the app. 4 to 8 digits.");
        EditText pin = input("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText again = input("PIN again", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        CheckBox remember = remember();
        button("Save", true, () -> {
            String p = pin.getText().toString();
            if (!p.matches("\\d{4,8}")) {
                pin.setError("4 to 8 digits");
                return;
            }
            if (!p.equals(again.getText().toString())) {
                again.setError("The PINs don't match");
                return;
            }
            Access.setPin(this, p, remember.isChecked());
            open();
        });
    }

    private void showPin() {
        title("Sign in");
        String name = Store.get(this).userName;
        if (!name.isEmpty()) para("Welcome back, " + name + ".");
        EditText pin = input("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        CheckBox remember = remember();
        button("Sign in", true, () -> {
            if (Access.checkPin(this, pin.getText().toString(), remember.isChecked())) open();
            else pin.setError("Wrong PIN");
        });
        button("Forgot PIN", false, () -> Ui.confirm(this, "Reset your PIN?",
                "You'll need the app owner to approve this phone again, then you choose a new PIN. "
                        + "Your projects, checklists, photos and crew stay on this phone; nothing is deleted.",
                "Reset", () -> {
                    Access.forgotPin(this);
                    show();
                }));
    }

    private CheckBox remember() {
        CheckBox cb = new CheckBox(this);
        cb.setText("Keep me signed in on this phone");
        cb.setTextSize(16);
        cb.setChecked(true);
        body.addView(cb);
        return cb;
    }

    // ----- small helpers -----

    private String clipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        return clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null
                ? clip.getItemAt(0).getText().toString() : "";
    }

    private void title(String s) {
        TextView t = text(s, 28, R.color.navy);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
    }

    private void heading(String s) {
        TextView t = text(s, 18, R.color.navy);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 4));
    }

    private void para(String s) {
        text(s, 16, R.color.grey).setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        body.addView(t);
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextSize(18);
        body.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void button(String label, boolean filled, Runnable run) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(18);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(filled ? 0xFFFFFFFF : getColor(R.color.deep_blue));
        b.setBackgroundResource(filled ? R.drawable.bg_pill_blue : R.drawable.bg_chip_sky);
        b.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, filled ? 56 : 46));
        lp.topMargin = Ui.dp(this, 10);
        body.addView(b, lp);
    }
}
