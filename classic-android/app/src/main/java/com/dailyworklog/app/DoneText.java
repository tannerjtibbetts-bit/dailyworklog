package com.dailyworklog.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * "Text when completed": when a field is marked done, texts the person chosen on its field page
 * that it's finished. Sent straight from the phone after 5 seconds (with Undo); without
 * permission to send texts, Messages opens with the text ready.
 */
final class DoneText {
    private final AppCompatActivity activity;
    private final ActivityResultLauncher<String> permission;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable afterPermission;

    /** Must be created in onCreate (registers the permission request). */
    DoneText(AppCompatActivity a) {
        activity = a;
        permission = a.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            Runnable r = afterPermission;
            afterPermission = null;
            if (r != null) r.run();
        });
    }

    static boolean wanted(Models.Task t) {
        return SmsHelper.digits(t.doneTextPhone).replace("+", "").length() >= 7;
    }

    /** Call right after a field is marked done. */
    void fieldDone(View anchor, Models.Project p, Models.DailyLog l, Models.Task t) {
        if (!t.done || !wanted(t)) return;
        Store store = Store.get(activity);
        String name = t.doneTextName.isEmpty() ? SmsHelper.formatPhone(t.doneTextPhone) : t.doneTextName;
        String phone = t.doneTextPhone;
        String body = SmsHelper.doneText(p, l, t, store.me(), store.contactPhone);
        if (activity.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            afterPermission = () -> {
                if (activity.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    fieldDone(anchor, p, l, t);
                } else {
                    compose(phone, body);
                }
            };
            permission.launch(Manifest.permission.SEND_SMS);
            return;
        }
        final boolean[] cancelled = {false};
        Runnable send = () -> {
            if (cancelled[0]) return;
            boolean ok = SmsHelper.sendDirect(activity, phone, body);
            if (ok) Toast.makeText(activity.getApplicationContext(), "Texted " + name, Toast.LENGTH_SHORT).show();
            else compose(phone, body);
        };
        handler.postDelayed(send, 5000);
        Snackbar.make(anchor, "Texting " + name + " that it's done…", 5000)
                .setAction("Undo", v -> {
                    cancelled[0] = true;
                    handler.removeCallbacks(send);
                    Toast.makeText(activity, "Text to " + name + " cancelled", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void compose(String phone, String body) {
        List<String> to = new ArrayList<>();
        to.add(phone);
        SmsHelper.send(activity, to, body, null);
    }
}
