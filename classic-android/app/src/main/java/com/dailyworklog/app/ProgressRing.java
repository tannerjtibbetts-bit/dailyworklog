package com.dailyworklog.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Percentage ring like the old app; turns into a green check at 100%. */
public class ProgressRing extends View {
    private float shown;
    private boolean filledWhenComplete = true;
    private ValueAnimator animator;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path path = new Path();

    public ProgressRing(Context c) {
        this(c, null);
    }

    public ProgressRing(Context c, AttributeSet a) {
        super(c, a);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0xFFD3E1F1);
        arc.setStyle(Paint.Style.STROKE);
        arc.setColor(0xFF1268D6);
        arc.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(0xFF0F1A2B);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        green.setColor(0xFF1DB04B);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
    }

    /** true: solid green disc (list cards). false: green outline (checklist header). */
    public void setFilledWhenComplete(boolean filled) {
        filledWhenComplete = filled;
        invalidate();
    }

    public void setProgress(float progress, boolean animate) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (animator != null) animator.cancel();
        if (!animate || !isLaidOut()) {
            shown = progress;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(shown, progress);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float stroke = size * 0.1f;
        if (shown >= 0.999f) {
            float r = size / 2f;
            path.reset();
            path.moveTo(cx - r * 0.36f, cy + r * 0.02f);
            path.lineTo(cx - r * 0.09f, cy + r * 0.29f);
            path.lineTo(cx + r * 0.38f, cy - r * 0.25f);
            if (filledWhenComplete) {
                canvas.drawCircle(cx, cy, r, green);
                tick.setColor(0xFFFFFFFF);
                tick.setStrokeWidth(size * 0.085f);
            } else {
                green.setStyle(Paint.Style.STROKE);
                green.setStrokeWidth(stroke * 0.8f);
                canvas.drawCircle(cx, cy, r - stroke * 0.4f, green);
                green.setStyle(Paint.Style.FILL);
                tick.setColor(0xFF1DB04B);
                tick.setStrokeWidth(size * 0.07f);
            }
            canvas.drawPath(path, tick);
            return;
        }
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        oval.set(cx - size / 2f + stroke / 2f, cy - size / 2f + stroke / 2f,
                cx + size / 2f - stroke / 2f, cy + size / 2f - stroke / 2f);
        canvas.drawArc(oval, 0, 360, false, track);
        if (shown > 0f) canvas.drawArc(oval, -90, 360 * shown, false, arc);
        text.setTextSize(size * 0.25f);
        String label = Math.round(shown * 100) + "%";
        canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text);
    }
}
