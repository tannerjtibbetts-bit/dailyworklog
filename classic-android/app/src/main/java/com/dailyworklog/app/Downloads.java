package com.dailyworklog.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Saves a copy of a file into the phone's Downloads folder. */
final class Downloads {
    private Downloads() {}

    static void save(Context c, String name, String mime, File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            save(c, name, mime, in);
        }
    }

    static void save(Context c, String name, String mime, byte[] data) throws IOException {
        save(c, name, mime, new ByteArrayInputStream(data));
    }

    /** Android 10+: a new Downloads entry (the system adds " (1)" if the name is taken). Older: the public Downloads folder. */
    static void save(Context c, String name, String mime, InputStream in) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri u = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("couldn't save to Downloads");
            try (OutputStream out = r.openOutputStream(u)) {
                if (out == null) throw new IOException("couldn't save to Downloads");
                Backup.copy(in, out);
            } catch (IOException e) {
                r.delete(u, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            r.update(u, v, null, null);
            return;
        }
        // Android 9 and older need the storage permission.
        if (c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw new IOException("no permission to save to Downloads");
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File dest = new File(dir, name);
        try (OutputStream out = new FileOutputStream(dest)) {
            Backup.copy(in, out);
        }
        MediaScannerConnection.scanFile(c, new String[]{dest.getAbsolutePath()}, new String[]{mime}, null);
    }
}
