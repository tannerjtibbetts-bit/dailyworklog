package com.dailyworklog.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Decodes thumbnails off the main thread and caches them, so lists scroll smoothly. */
final class ImageLoader {
    private ImageLoader() {}

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void load(ImageView view, File file, int maxPx) {
        final String key = file.getAbsolutePath() + "@" + maxPx;
        view.setTag(R.id.tag_image_key, key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            final Bitmap bm = decode(file, maxPx);
            if (bm != null) CACHE.put(key, bm);
            MAIN.post(() -> {
                if (bm == null || !key.equals(view.getTag(R.id.tag_image_key))) return;
                view.setAlpha(0f);
                view.setImageBitmap(bm);
                view.animate().alpha(1f).setDuration(160).start();
            });
        });
    }

    /** Decode so the longest side is at least maxPx (power-of-two sampling). */
    static Bitmap decode(File f, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }

    static void evict(File f) {
        String prefix = f.getAbsolutePath() + "@";
        List<String> keys = new ArrayList<>(CACHE.snapshot().keySet());
        for (String k : keys) if (k.startsWith(prefix)) CACHE.remove(k);
    }
}
