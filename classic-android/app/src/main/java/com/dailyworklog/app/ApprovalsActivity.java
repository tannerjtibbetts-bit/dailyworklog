package com.dailyworklog.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * App owner: set this phone up as the owner, approve people's access requests, and hand the
 * owner role to another phone. Other phones: take over as owner from a handover text.
 */
public class ApprovalsActivity extends AppCompatActivity {
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        body.setPadding(pad, Ui.dp(this, 32), pad, pad);
        scroll.addView(body);
        setContentView(scroll);
        Ui.applyInsets(scroll);
        show();
    }

    /** Text shared or passed in (a request or handover), used once to fill in the paste box. */
    private String passed() {
        String t = getIntent().getStringExtra("text");
        getIntent().removeExtra("text");
        return t == null ? "" : t;
    }

    private void show() {
        body.removeAllViews();
        boolean built = Access.builtInOwnerKey(this) != null;
        if (Access.isOwner(this)) {
            showApprovals();
            showHandover();
        } else if (built) showTakeOver();
        else showSetup();
    }

    // ----- one-time setup -----

    private void showSetup() {
        title("Become the app owner");
        if (!Access.hasOwnerKey()) {
            para("This makes this phone the only one that can approve people. A secret key is created inside "
                    + "the phone's secure storage; it can't be copied out. Its public half is saved to Downloads as "
                    + Access.KEY_FILE + " so your next Termux build includes it.");
            button("Set up this phone as the owner", true, this::setUp);
        } else {
            para("This phone has the owner key, but the app hasn't been rebuilt with it yet.");
            button("Save the key to Downloads again", false, this::setUp);
        }
        heading("Then, in Termux:");
        para("bash ~/DailyWorkLog/build-termux.sh\n\nInstall the new DailyWorkLog.apk from Downloads. From then on, "
                + "other phones using that app need your approval, and this page lets you approve them. Keep "
                + Access.KEY_FILE + ": every future build needs it.");
    }

    private void setUp() {
        try {
            Access.becomeOwner(this);
            Toast.makeText(this, "Saved " + Access.KEY_FILE + " to Downloads", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't set up the owner key: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        show();
    }

    // ----- approving people -----

    private void showApprovals() {
        title("Approvals");
        para("When someone texts you an access request, copy the whole text, then tap Paste and Approve.");
        EditText req = new EditText(this);
        req.setHint("Paste the request here");
        req.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        req.setSingleLine(false);
        req.setMinLines(3);
        String in = passed();
        if (AccessCodes.parseRequest(in) != null) req.setText(in);
        body.addView(req, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button("Paste", false, () -> req.setText(clipboard()));
        button("Approve", true, () -> {
            AccessCodes.Request r = AccessCodes.parseRequest(req.getText().toString());
            if (r == null) {
                Toast.makeText(this, "That doesn't look like an access request", Toast.LENGTH_LONG).show();
                return;
            }
            Ui.confirm(this, "Approve " + r.name + "?", "They can use the app on that phone for one year.", "Approve", () -> {
                try {
                    String code = Access.approve(this, r);
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Approval", code));
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Your Daily Work Log approval. Copy this whole text, then paste it on the sign-in screen:\n" + code);
                    startActivity(Intent.createChooser(send, "Send the approval to " + r.name));
                    show();
                } catch (Exception e) {
                    Toast.makeText(this, "Couldn't approve: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

        heading("Approved");
        JSONArray list = Access.approvedList(this);
        if (list.length() == 0) para("No one yet.");
        for (int i = list.length() - 1; i >= 0; i--) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            para(o.optString("name") + "\nApproved " + Fmt.longStamp(o.optLong("at")) + " · until " + o.optString("expiry"));
        }
    }

    // ----- handing over to another phone -----

    private void showHandover() {
        heading("Hand over ownership");
        para("Moving to another phone, like a work phone? Install this same app there first. Then type that "
                + "phone's number and tap Send. You'll get a 6-digit code to type on that phone. Everyone you've "
                + "approved stays approved, and no rebuild is needed.");
        EditText phone = new EditText(this);
        phone.setHint("New phone's number");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setTextSize(18);
        body.addView(phone, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button("Send handover text", true, () -> {
            String number = phone.getText().toString().replaceAll("[^0-9+]", "");
            if (number.replace("+", "").length() < 7) {
                phone.setError("Enter the phone number");
                return;
            }
            Ui.confirm(this, "Hand over ownership?", "Once you send it, this phone stops approving people. "
                    + "It keeps its own access and everything saved on it.", "Hand over", () -> handOver(number));
        });
    }

    private void handOver(String number) {
        String code = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1000000));
        String myName = Store.get(this).userName;
        Toast.makeText(this, "Preparing…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String text = Access.handOver(this, code, myName);
                Access.rememberHandoverCode(this, code);
                runOnUiThread(() -> sendHandover(number, code, text));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Couldn't hand over: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void sendHandover(String number, String code, String text) {
        show();
        String msg = "Daily Work Log owner handover. On your new phone, copy this whole text, open the app and tap "
                + "\"Take over as the app owner\":\n" + text;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Handover", msg));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Your code: " + code)
                .setMessage("Type this code on the new phone after pasting the text. Don't text the code; "
                        + "it's also shown on this page until the handover is done.")
                .setPositiveButton("OK", null)
                .show();
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number)).putExtra("sms_body", msg));
        } catch (ActivityNotFoundException e) {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, msg), "Send the handover to your new phone"));
        }
    }

    private void showTakeOver() {
        if (Access.handedOver(this)) {
            title("Ownership handed over");
            para("On " + Fmt.longStamp(Access.handedOverAt(this)) + " you sent the owner role to another phone. "
                    + "This phone can't approve people any more. Everything else works as before, and everyone "
                    + "you approved stays approved.");
            String code = Access.handoverCode(this);
            if (!code.isEmpty()) para("Code for the new phone: " + code);
            button("Undo: keep this phone as the owner", false, () -> Ui.confirm(this, "Keep this phone as the owner?",
                    "Only do this if the new phone didn't take over. If it did, you'd have two owner phones.",
                    "Keep", () -> {
                        Access.undoHandover(this);
                        show();
                    }));
            heading("Take it back later");
            para("To make this phone the owner again, send a handover from the current owner phone and enter it below.");
        } else {
            title("Take over as the app owner");
            para("Only the app owner's phone can approve people. To make this phone the owner: on the owner phone, "
                    + "open Approvals and use Hand over ownership. Copy the whole text it sends to this phone, paste it "
                    + "here, then type the 6-digit code shown on the owner phone.");
        }
        EditText text = new EditText(this);
        text.setHint("Paste the handover text here");
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setSingleLine(false);
        text.setMinLines(3);
        String in = passed();
        if (AccessCodes.looksLikeHandover(in)) text.setText(in);
        body.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button("Paste", false, () -> text.setText(clipboard()));
        EditText code = new EditText(this);
        code.setHint("6-digit code");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setTextSize(18);
        body.addView(code, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        button("Take over", true, () -> {
            String t = text.getText().toString(), c = code.getText().toString().trim();
            if (!c.matches("\\d{6}")) {
                code.setError("Enter the 6 digits");
                return;
            }
            Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String err = Access.acceptHandover(this, t, c);
                runOnUiThread(() -> {
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, "This phone is now the app owner", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, SignInActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                });
            }).start();
        });
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
