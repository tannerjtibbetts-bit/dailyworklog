package com.dailyworklog.app;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Load securement photos by email: the Gmail setup, the To address, and sending in the background. */
final class LoadEmail {
    private LoadEmail() {}

    /** "Load securement - Pack up sea can 317503" */
    static String defaultSubject(Models.Task t) {
        return "Load securement - " + title(t);
    }

    private static String title(Models.Task t) {
        return t.title.trim().isEmpty() ? "(Untitled field)" : t.title.trim();
    }

    static String body(Models.Project p, Models.DailyLog log, Models.Task t, String me, long at, int count) {
        return "Load securement photos\n"
                + "Field: " + title(t) + "\n"
                + "Checklist: " + log.displayTitle() + (p.name.isEmpty() ? "" : " (" + p.name + ")") + "\n"
                + "Secured by " + me + ", " + Fmt.stamp(at) + "\n"
                + Fmt.plural(count, "photo", "photos") + " attached.\n"
                + "\n"
                + "Sent from Daily Work Log";
    }

    /** Sends the photos, split into several emails if they're large. Background thread. */
    static void sendNow(Store store, Models.Project p, Models.DailyLog log, Models.Task t, List<Models.Photo> photos,
                        String subject, String to) throws Mailer.MailException {
        String from = store.mailFrom.trim();
        String pass = store.mailPassword();
        if (from.isEmpty() || pass.isEmpty()) throw new Mailer.MailException("Set up Email sending first.");
        List<Mailer.Attachment> files = new ArrayList<>();
        for (Models.Photo ph : photos) {
            try {
                files.add(new Mailer.Attachment(ph.file, Files.readAllBytes(store.photoFile(ph.file).toPath())));
            } catch (Exception e) {
                // skip a photo that can't be read
            }
        }
        if (files.isEmpty()) throw new Mailer.MailException("The photos couldn't be read.");
        String me = store.me();
        long at = System.currentTimeMillis();
        List<List<Mailer.Attachment>> groups = Mailer.groups(files, Mailer.MAX_GROUP_BYTES);
        List<String> recipients = Mailer.addresses(to);
        for (int g = 0; g < groups.size(); g++) {
            String s = groups.size() == 1 ? subject : subject + " (" + (g + 1) + " of " + groups.size() + ")";
            String message = Mailer.buildMessage(me, from, recipients, s, body(p, log, t, me, at, groups.get(g).size()),
                    groups.get(g), null, null);
            Mailer.send(from, pass, recipients, message);
        }
    }

    /** Sends in the background with toasts; on failure a dialog with Try again. */
    static void send(Activity a, Models.Project p, Models.DailyLog log, Models.Task t, List<Models.Photo> photos,
                     String subject, String to) {
        Store store = Store.get(a);
        android.content.Context app = a.getApplicationContext();
        int n = photos.size();
        Toast.makeText(app, "Sending " + Fmt.plural(n, "photo", "photos") + " to " + to + "…", Toast.LENGTH_SHORT).show();
        PhotoUtil.BG.execute(() -> {
            String error = null;
            try {
                sendNow(store, p, log, t, photos, subject, to);
            } catch (Mailer.MailException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = Mailer.OFFLINE;
            }
            final String err = error;
            a.runOnUiThread(() -> {
                if (err == null) {
                    Toast.makeText(app, "Sent " + Fmt.plural(n, "photo", "photos") + " to " + to, Toast.LENGTH_LONG).show();
                } else if (a.isFinishing() || a.isDestroyed()) {
                    Toast.makeText(app, "Couldn't send the email. " + err, Toast.LENGTH_LONG).show();
                } else {
                    new MaterialAlertDialogBuilder(a)
                            .setTitle("Couldn't send the email")
                            .setMessage(err)
                            .setPositiveButton("Try again", (d, w) -> send(a, p, log, t, photos, subject, to))
                            .setNegativeButton("Close", null)
                            .show();
                }
            });
        });
    }

    /** "Send load securement photos to": one or more addresses, saved app-wide. */
    static void changeTo(Activity a, Runnable after) {
        Store store = Store.get(a);
        EditText et = new EditText(a);
        et.setHint("name@example.com");
        et.setText(store.loadEmailTo);
        et.setSelection(et.getText().length());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        et.setSingleLine(true);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), 0);
        box.addView(et);
        TextView tip = new TextView(a);
        tip.setText("Several addresses can be separated with commas.");
        tip.setTextSize(14);
        tip.setTextColor(a.getColor(R.color.grey));
        box.addView(tip);
        AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle("Send load securement photos to")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            if (!Mailer.validList(s)) {
                et.setError("Enter a valid email address");
                return;
            }
            store.loadEmailTo = String.join(", ", Mailer.addresses(s));
            store.save();
            d.dismiss();
            if (after != null) after.run();
        }));
        d.show();
    }

    /** "Email sending": the Gmail address and app password used to send. */
    static void setup(Activity a, Runnable after) {
        Store store = Store.get(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 22);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);
        TextView msg = new TextView(a);
        msg.setText("The app sends load securement photos from your Gmail. You need a Gmail \"app password\" (not your normal "
                + "password). In your Google account: Security > 2-Step Verification (turn it on) > App passwords > make one named "
                + "Daily Work Log, then type the 16 letters here.");
        msg.setTextSize(15);
        msg.setTextColor(a.getColor(R.color.grey));
        box.addView(msg);
        EditText from = new EditText(a);
        from.setHint("Your Gmail address");
        from.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        from.setSingleLine(true);
        from.setText(store.mailFrom);
        box.addView(from);
        EditText pass = new EditText(a);
        pass.setHint("App password");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setSingleLine(true);
        pass.setText(store.mailPassword());
        box.addView(pass);
        // Who gets the photos: remembered until changed here or with Change in the pop-up.
        EditText sendTo = new EditText(a);
        sendTo.setHint("Send load securement photos to");
        sendTo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        sendTo.setSingleLine(true);
        sendTo.setText(store.loadEmailTo);
        box.addView(sendTo);
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(box);
        AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle("Email sending")
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNeutralButton("Send a test", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!check(from, pass)) return;
                String toText = sendTo.getText().toString().trim();
                if (!toText.isEmpty() && !Mailer.validList(toText)) {
                    sendTo.setError("Enter a valid email address");
                    return;
                }
                store.loadEmailTo = toText.isEmpty() ? "" : String.join(", ", Mailer.addresses(toText));
                store.mailFrom = from.getText().toString().trim();
                store.save();
                if (!store.setMailPassword(pass.getText().toString())) {
                    Toast.makeText(a, "Couldn't save the app password on this phone", Toast.LENGTH_LONG).show();
                    return;
                }
                d.dismiss();
                if (after != null) after.run();
            });
            View test = d.getButton(AlertDialog.BUTTON_NEUTRAL);
            test.setOnClickListener(v -> {
                if (!check(from, pass)) return;
                String f = from.getText().toString().trim();
                String pw = pass.getText().toString().replaceAll("\\s", "");
                String me = store.me();
                test.setEnabled(false);
                PhotoUtil.BG.execute(() -> {
                    String error = null;
                    try {
                        List<String> to = new ArrayList<>();
                        to.add(f);
                        Mailer.send(f, pw, to, Mailer.buildMessage(me, f, to, "Daily Work Log test", "Email sending works.",
                                null, null, null));
                    } catch (Mailer.MailException e) {
                        error = e.getMessage();
                    }
                    final String err = error;
                    a.runOnUiThread(() -> {
                        test.setEnabled(true);
                        if (a.isFinishing() || a.isDestroyed()) return;
                        if (err == null) Toast.makeText(a, "Test email sent", Toast.LENGTH_LONG).show();
                        else new MaterialAlertDialogBuilder(a).setTitle("Couldn't send the email").setMessage(err)
                                .setPositiveButton("Close", null).show();
                    });
                });
            });
        });
        d.show();
    }

    private static boolean check(EditText from, EditText pass) {
        if (!Mailer.validAddress(from.getText().toString().trim())) {
            from.setError("Enter a valid email address");
            return false;
        }
        if (pass.getText().toString().replaceAll("\\s", "").isEmpty()) {
            pass.setError("Required");
            return false;
        }
        return true;
    }

    /** Deletes photo files taken in a pop-up session that was cancelled. */
    static void deleteFiles(Store store, List<String> names) {
        for (String n : names) {
            File f = store.photoFile(n);
            ImageLoader.evict(f);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
