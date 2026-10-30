package com.dailyworklog.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Reads the text in a photo on the phone itself (no internet) and lays it out as table rows. */
final class ScanReader {
    private ScanReader() {}

    /** Big enough for small print, small enough not to run out of memory. */
    private static final int MAX_SIDE = 3000;

    interface Result {
        void onRows(List<List<String>> rows);

        /** The photo was the zone task sheet: whole tasks, already put together. */
        void onZone(List<ZoneSheet.Task> tasks);

        void onError(String message);
    }

    static void read(Context c, Uri uri, Result result) {
        Context app = c.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        PhotoUtil.BG.execute(() -> {
            Bitmap bmp = load(app, uri);
            main.post(() -> {
                if (bmp == null) {
                    result.onError("Couldn't open that photo");
                    return;
                }
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener(text -> {
                            recognizer.close();
                            List<SheetImport.Box> found = boxes(text);
                            List<ZoneSheet.Task> zone = ZoneSheet.parse(found, pixels(bmp));
                            if (zone != null && !zone.isEmpty()) result.onZone(zone);
                            else result.onRows(SheetImport.fromBoxes(found));
                        })
                        .addOnFailureListener(e -> {
                            recognizer.close();
                            result.onError("Couldn't read the text in that photo");
                        });
            });
        });
    }

    interface LinesResult {
        /** Every line of text found, in reading order. */
        void onLines(List<String> lines);

        void onError(String message);
    }

    /** Just the lines of text in a picture, e.g. a screenshot of a text message. */
    static void readLines(Context c, Uri uri, LinesResult result) {
        Context app = c.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        PhotoUtil.BG.execute(() -> {
            Bitmap bmp = load(app, uri);
            main.post(() -> {
                if (bmp == null) {
                    result.onError("Couldn't open that picture");
                    return;
                }
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener(text -> {
                            recognizer.close();
                            List<String> lines = new ArrayList<>();
                            for (Text.TextBlock block : text.getTextBlocks()) {
                                for (Text.Line line : block.getLines()) lines.add(line.getText());
                            }
                            result.onLines(lines);
                        })
                        .addOnFailureListener(e -> {
                            recognizer.close();
                            result.onError("Couldn't read the text in that picture");
                        });
            });
        });
    }

    /** The photo's pixels, for reading cell colours. */
    private static ZoneSheet.Pixels pixels(Bitmap bmp) {
        return new ZoneSheet.Pixels() {
            @Override
            public int width() {
                return bmp.getWidth();
            }

            @Override
            public int height() {
                return bmp.getHeight();
            }

            @Override
            public int rgb(int x, int y) {
                return bmp.getPixel(x, y) & 0xFFFFFF;
            }
        };
    }

    private static List<SheetImport.Box> boxes(Text text) {
        List<SheetImport.Box> out = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect r = line.getBoundingBox();
                if (r == null) continue;
                out.add(new SheetImport.Box(line.getText(), r.left, r.top, r.right, r.bottom));
            }
        }
        return out;
    }

    /** Decodes the photo upright (camera photos are often stored sideways) and not too large. */
    private static Bitmap load(Context c, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, opts);
            }
            if (b == null) return null;
            int deg = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (o == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
                }
            } catch (Exception ignored) {
                // no orientation info; use as is
            }
            float scale = Math.min(1f, (float) MAX_SIDE / Math.max(b.getWidth(), b.getHeight()));
            if (deg == 0 && scale >= 1f) return b;
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(deg);
            Bitmap out = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (out != b) b.recycle();
            return out;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
