package com.dailyworklog.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** The round completion check: grey ring when open, filled green with a tick when done. */
public class CheckCircle extends View {
    private boolean checked;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public CheckCircle(Context c) {
        this(c, null);
    }

    public CheckCircle(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        fill.setColor(0xFF1DB04B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.2f * d);
        ring.setColor(0xFF9DB4CF);
        tick.setStyle(Paint.Style.STROKE);
        tick.setColor(0xFFFFFFFF);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
        setFocusable(true);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value, boolean animate) {
        checked = value;
        setContentDescription(value ? "Completed. Tap to reopen" : "Not completed. Tap to complete");
        invalidate();
        if (animate) {
            animate().cancel();
            setScaleX(0.6f);
            setScaleY(0.6f);
            animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (checked) {
            float r = size / 2f;
            canvas.drawCircle(cx, cy, r, fill);
            tick.setStrokeWidth(size * 0.09f);
            path.reset();
            path.moveTo(cx - r * 0.40f, cy + r * 0.02f);
            path.lineTo(cx - r * 0.10f, cy + r * 0.32f);
            path.lineTo(cx + r * 0.42f, cy - r * 0.28f);
            canvas.drawPath(path, tick);
        } else {
            canvas.drawCircle(cx, cy, size / 2f - ring.getStrokeWidth(), ring);
        }
    }
}
