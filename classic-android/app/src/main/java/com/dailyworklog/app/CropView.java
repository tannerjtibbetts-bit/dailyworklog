package com.dailyworklog.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/** "Crop the page": the photo with a rectangle whose 4 corners can be dragged. */
final class CropView extends View {
    private Bitmap bitmap;
    /** The crop as fractions of the photo. */
    final RectF crop = new RectF(0.03f, 0.03f, 0.97f, 0.97f);
    private final RectF shown = new RectF();
    private final Paint dim = new Paint(), edge = new Paint(Paint.ANTI_ALIAS_FLAG), handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dragging = -1;

    CropView(Context c) {
        super(c);
        dim.setColor(0x99000000);
        edge.setColor(Color.WHITE);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 2));
        handle.setColor(0xFF1268D6);
    }

    void setBitmap(Bitmap b) {
        bitmap = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        if (bitmap == null) return;
        float s = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        float w = bitmap.getWidth() * s, h = bitmap.getHeight() * s;
        shown.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
        c.drawBitmap(bitmap, null, shown, null);
        float l = shown.left + crop.left * w, t = shown.top + crop.top * h, r = shown.left + crop.right * w, b = shown.top + crop.bottom * h;
        c.drawRect(shown.left, shown.top, shown.right, t, dim);
        c.drawRect(shown.left, b, shown.right, shown.bottom, dim);
        c.drawRect(shown.left, t, l, b, dim);
        c.drawRect(r, t, shown.right, b, dim);
        c.drawRect(l, t, r, b, edge);
        float rad = Ui.dp(getContext(), 9);
        c.drawCircle(l, t, rad, handle);
        c.drawCircle(r, t, rad, handle);
        c.drawCircle(l, b, rad, handle);
        c.drawCircle(r, b, rad, handle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null || shown.width() <= 0) return false;
        float fx = (e.getX() - shown.left) / shown.width(), fy = (e.getY() - shown.top) / shown.height();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float[][] corners = {{crop.left, crop.top}, {crop.right, crop.top}, {crop.left, crop.bottom}, {crop.right, crop.bottom}};
                float best = Ui.dp(getContext(), 48);
                dragging = -1;
                for (int i = 0; i < 4; i++) {
                    float dx = (corners[i][0] - fx) * shown.width(), dy = (corners[i][1] - fy) * shown.height();
                    float d = (float) Math.hypot(dx, dy);
                    if (d < best) {
                        best = d;
                        dragging = i;
                    }
                }
                return dragging >= 0;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragging < 0) return false;
                float x = Math.max(0, Math.min(1, fx)), y = Math.max(0, Math.min(1, fy)), min = 0.08f;
                if (dragging == 0 || dragging == 2) crop.left = Math.min(x, crop.right - min);
                else crop.right = Math.max(x, crop.left + min);
                if (dragging == 0 || dragging == 1) crop.top = Math.min(y, crop.bottom - min);
                else crop.bottom = Math.max(y, crop.top + min);
                invalidate();
                return true;
            }
            default:
                dragging = -1;
                return true;
        }
    }

    /** Shows "Crop the page" for a photo just taken; then runs after (on the UI thread). */
    static void show(Activity a, File f, Runnable after) {
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.setCancelable(false);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        TextView title = new TextView(a);
        title.setText("Crop the page");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(Ui.dp(a, 20), Ui.dp(a, 36), Ui.dp(a, 20), Ui.dp(a, 8));
        root.addView(title);
        CropView view = new CropView(a);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 28));
        TextView whole = button(a, "Use whole photo", false);
        TextView crop = button(a, "Crop", true);
        buttons.addView(whole, new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 52), 1f);
        lp.setMarginStart(Ui.dp(a, 12));
        buttons.addView(crop, lp);
        root.addView(buttons);
        d.setContentView(root);
        d.show();
        PhotoUtil.BG.execute(() -> {
            Bitmap b = ImageLoader.decode(f, 1600);
            a.runOnUiThread(() -> view.setBitmap(b));
        });
        whole.setOnClickListener(v -> {
            d.dismiss();
            after.run();
        });
        crop.setOnClickListener(v -> {
            RectF r = new RectF(view.crop);
            d.dismiss();
            PhotoUtil.BG.execute(() -> {
                boolean ok = cropFile(f, r);
                a.runOnUiThread(() -> {
                    if (!ok) Toast.makeText(a, "Couldn't crop the photo; the whole photo is kept", Toast.LENGTH_LONG).show();
                    after.run();
                });
            });
        });
    }

    /** Overwrites the photo with the cropped part (JPEG). Background thread. */
    static boolean cropFile(File f, RectF r) {
        try {
            Bitmap b = ImageLoader.decode(f, 4000);
            if (b == null) return false;
            int x = Math.round(r.left * b.getWidth()), y = Math.round(r.top * b.getHeight());
            int w = Math.max(1, Math.min(b.getWidth() - x, Math.round(r.width() * b.getWidth())));
            int h = Math.max(1, Math.min(b.getHeight() - y, Math.round(r.height() * b.getHeight())));
            Bitmap out = Bitmap.createBitmap(b, x, y, w, h);
            try (FileOutputStream o = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.JPEG, 90, o);
            }
            if (out != b) out.recycle();
            b.recycle();
            return true;
        } catch (Exception | OutOfMemoryError e) {
            return false;
        }
    }

    private static TextView button(Activity a, String label, boolean filled) {
        TextView b = new TextView(a);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(17);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(filled ? Color.WHITE : a.getColor(R.color.deep_blue));
        b.setBackgroundResource(filled ? R.drawable.bg_pill_blue : R.drawable.bg_chip_sky);
        return b;
    }
}
