package com.dailyworklog.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the phone's Messages app with a text (and photos) ready to send, or sends a plain text
 * straight from the app (assignment texts, and Notify notes without photos).
 */
final class SmsHelper {
    private SmsHelper() {}

    static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }

    /** numbers may be empty: Messages then asks who to send it to. */
    static void send(Activity a, List<String> numbers, String body, List<File> photos) {
        List<String> clean = new ArrayList<>();
        for (String n : numbers) {
            String d = digits(n);
            if (!d.isEmpty()) clean.add(d);
        }
        String smsApp = Telephony.Sms.getDefaultSmsPackage(a);
        // Samsung Messages wants commas between numbers; Google Messages and most others semicolons.
        String to = String.join(smsApp != null && smsApp.contains("samsung") ? "," : ";", clean);
        try {
            if (photos == null || photos.isEmpty()) {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + to));
                i.putExtra("sms_body", body);
                a.startActivity(i);
                return;
            }
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : photos) {
                if (f.exists()) uris.add(FileProvider.getUriForFile(a, a.getPackageName() + ".files", f));
            }
            Intent i;
            if (uris.size() == 1) {
                i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_TEXT, body);
            i.putExtra("sms_body", body);
            if (!to.isEmpty()) i.putExtra("address", to);
            if (!uris.isEmpty()) {
                ClipData clip = ClipData.newRawUri("", uris.get(0));
                for (int k = 1; k < uris.size(); k++) clip.addItem(new ClipData.Item(uris.get(k)));
                i.setClipData(clip);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (smsApp != null) {
                i.setPackage(smsApp);  // straight into Messages, no app picker
                try {
                    a.startActivity(i);
                    return;
                } catch (ActivityNotFoundException ignored) {
                    i.setPackage(null);
                }
            }
            a.startActivity(Intent.createChooser(i, "Send with"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No messaging app found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    /** Sends a plain text straight from this phone (needs SEND_SMS). Returns false if it couldn't. */
    static boolean sendDirect(Activity a, String phone, String body) {
        try {
            SmsManager sms = Build.VERSION.SDK_INT >= 31
                    ? a.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            if (sms == null) return false;
            ArrayList<String> parts = sms.divideMessage(body);
            sms.sendMultipartTextMessage(digits(phone), null, parts, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Text for a field assignment, matching the web version. */
    static String taskText(Models.Project p, Models.DailyLog l, Models.Task t, String me, String contact) {
        StringBuilder s = new StringBuilder();
        s.append(l.displayTitle());
        if (!p.name.isEmpty()) s.append(" (").append(p.name).append(")");
        s.append("\n\nAssigned to you: ").append(t.title);  // blank line under the title
        // Field notes stay in the app; texts only carry the photo reminder.
        if (t.photosRequired) s.append("\n\nPhotos required.");
        s.append(signOff(me, contact));
        return s.toString();
    }

    /** Text to the person who wanted to know when a field is done. */
    static String doneText(Models.Project p, Models.DailyLog l, Models.Task t, String me, String contact) {
        StringBuilder s = new StringBuilder();
        s.append(l.displayTitle());
        if (!p.name.isEmpty()) s.append(" (").append(p.name).append(")");
        s.append("\n\nCompleted: ").append(t.title);
        s.append("\nDone by ").append(me).append(", ").append(Fmt.stamp(t.completedAt));
        s.append(signOff(me, contact));
        return s.toString();
    }

    static String noticeText(Models.Project p, String note, String me, String contact) {
        return note.trim() + signOff(me + (p.name.isEmpty() ? "" : " (" + p.name + ")"), contact);
    }

    /**
     * A divider line, then who sent it and how to reach them, so the message and the
     * contact details are clearly separate. Plain hyphens keep it a normal-length text.
     */
    static String signOff(String from, String contact) {
        return "\n\n--------------------\nFrom: " + from + contactLine(contact);
    }

    /** "\nCall or text me at 1-226-555-0123", or nothing when no number is set. */
    static String contactLine(String contact) {
        if (contact == null || contact.trim().isEmpty()) return "";
        return "\nCall or text me at " + formatPhone(contact);
    }

    /** 12265550123 -> 1-226-555-0123; 2265550123 -> 226-555-0123; anything else as typed. */
    static String formatPhone(String phone) {
        String d = phone.replaceAll("[^0-9]", "");
        if (d.length() == 11 && d.startsWith("1")) {
            return "1-" + d.substring(1, 4) + "-" + d.substring(4, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
        return phone.trim();
    }

    /** Asks for a number when none is saved, then continues. */
    static void withPhone(Activity a, Models.Project p, String name, Runnable then) {
        if (!p.phoneOf(name).isEmpty()) {
            then.run();
            return;
        }
        Ui.promptPhone(a, "Phone number for " + name, "", number -> {
            p.phones.put(name, number);
            Store.get(a).save();
            then.run();
        });
    }
}
