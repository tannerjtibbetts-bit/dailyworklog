package com.dailyworklog.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-resolution camera photos, straightened and resized to a sensible 1600px. */
final class PhotoUtil {
    private PhotoUtil() {}

    static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final int MAX_SIDE = 1600;
    private static final Random RANDOM = new Random();

    static File newFile(Store s) {
        String name = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + "_" + Integer.toHexString(RANDOM.nextInt(0xFFFFF)) + ".jpg";
        return s.photoFile(name);
    }

    /** Applies EXIF rotation and shrinks the photo in place. Run on a background thread. */
    static boolean normalize(File f) {
        try {
            int orientation = new ExifInterface(f.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Bitmap b = ImageLoader.decode(f, MAX_SIDE);
            if (b == null) return false;
            float scale = Math.min(1f, (float) MAX_SIDE / Math.max(b.getWidth(), b.getHeight()));
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            int deg = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
            m.postRotate(deg);
            Bitmap out = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            try (FileOutputStream o = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.JPEG, 85, o);
            }
            if (out != b) b.recycle();
            out.recycle();
            return true;
        } catch (Exception e) {
            return f.exists() && f.length() > 0;
        }
    }

    /** Copies a gallery image into app storage, then normalizes it. Background thread. */
    static boolean copyFrom(Context c, Uri uri, File dest) {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return false;
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            return false;
        }
        return normalize(dest);
    }

    /** JPEG bytes for reports, longest side about maxPx. */
    static ReportDocx.Encoded encode(File f, int maxPx) {
        Bitmap b = ImageLoader.decode(f, maxPx);
        if (b == null) return null;
        float scale = Math.min(1f, (float) maxPx / Math.max(b.getWidth(), b.getHeight()));
        Bitmap s = scale < 1f
                ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true)
                : b;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        s.compress(Bitmap.CompressFormat.JPEG, 82, bos);
        ReportDocx.Encoded e = new ReportDocx.Encoded(bos.toByteArray(), s.getWidth(), s.getHeight());
        if (s != b) s.recycle();
        b.recycle();
        return e;
    }
}
