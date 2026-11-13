package com.dailyworklog.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** "Place the answers": the form's page pictures with a chip for each answer, dragged to where it's written. */
public class PlaceActivity extends FormScreen {
    private Models.ReportForm form;
    private Models.FilledReport report;
    private final List<Runnable> sizers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        report = store.filled(getIntent().getStringExtra("report"));
        form = report != null ? store.formFor(report) : store.form(getIntent().getStringExtra("form"));
        if (form == null) {
            finish();
            return;
        }
        setTitles("Place the answers", form.name);
        render();
    }

    /** What a chip shows: the label, or (from a report) its answer; a rating shows its value. */
    private String chipText(Models.FormQuestion q) {
        if (report == null) return q.label;
        String a = Models.FormQuestion.AVERAGE.equals(q.kind) ? ReportFill.average(form, report) : report.answer(q.id).trim();
        if (Models.FormQuestion.RATING.equals(q.kind)) {
            for (Models.Choice c : q.choices) if (c.label.equalsIgnoreCase(a) && !c.value.isEmpty()) a = c.value;
        }
        if (Models.FormQuestion.LONG.equals(q.kind) && a.length() > 60) a = a.substring(0, 60) + "…";
        return a.isEmpty() ? q.label : a;
    }

    private void render() {
        body.removeAllViews();
        sizers.clear();
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView smaller = sizeButton("A-", "Smaller answers"), bigger = sizeButton("A+", "Bigger answers");
        TextView size = text("", 15, R.color.grey, false);
        size.setPadding(Ui.dp(this, 12), 0, 0, 0);
        Runnable showSize = () -> size.setText(Math.round(form.fontScale * 100) + "%");
        smaller.setOnClickListener(v -> scale(-0.1, showSize));
        bigger.setOnClickListener(v -> scale(0.1, showSize));
        sizeRow.addView(smaller);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMarginStart(Ui.dp(this, 10));
        sizeRow.addView(bigger, gap);
        sizeRow.addView(size);
        showSize.run();
        body.addView(sizeRow);
        para("Drag each answer to its blank on the form. They print at this size.");

        List<String> pictures = form.pictures();
        if (pictures.isEmpty()) para("This form has no page pictures, so the answers are printed as a list instead.");
        for (int p = 0; p < pictures.size(); p++) addPage(p, store.photoFile(pictures.get(p)));

        List<Models.FormQuestion> unplaced = new ArrayList<>();
        for (Models.FormQuestion q : form.questions) {
            if (q.place == null || (q.place.page >= pictures.size() && !pictures.isEmpty())) unplaced.add(q);
        }
        if (!unplaced.isEmpty() && !pictures.isEmpty()) {
            heading("Not on the form yet");
            for (Models.FormQuestion q : unplaced) {
                action(q.label, v -> {
                    q.place = new Models.Place(0, 0.5, 0.5);
                    store.save();
                    render();
                });
            }
        }
        button("Done", true, v -> finish());
    }

    private TextView sizeButton(String label, String tip) {
        TextView t = text(label, 18, R.color.deep_blue, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_chip_sky);
        t.setContentDescription(tip);
        t.setMinWidth(Ui.dp(this, 64));
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        return t;
    }

    private void scale(double by, Runnable showSize) {
        form.fontScale = Math.max(0.6, Math.min(2.0, Math.round((form.fontScale + by) * 10) / 10.0));
        store.save();
        showSize.run();
        for (Runnable r : sizers) r.run();
    }

    private void addPage(int page, File f) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFFFFFFFF);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        frame.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 400));
        lp.topMargin = Ui.dp(this, 10);
        body.addView(frame, lp);
        PhotoUtil.BG.execute(() -> {
            Bitmap b = ImageLoader.decode(f, 2000);
            runOnUiThread(() -> {
                if (b == null) return;
                iv.setImageBitmap(b);
                frame.post(() -> {
                    int w = frame.getWidth();
                    int h = Math.round((float) w * b.getHeight() / b.getWidth());
                    frame.getLayoutParams().height = h;
                    frame.requestLayout();
                    for (Models.FormQuestion q : form.questions) {
                        if (q.place != null && q.place.page == page) addChip(frame, q, w, h);
                    }
                });
            });
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addChip(FrameLayout frame, Models.FormQuestion q, int w, int h) {
        TextView chip = new TextView(this);
        chip.setText(chipText(q));
        chip.setTextColor(0xFF0B2E83);
        chip.setTypeface(Typeface.SANS_SERIF);
        chip.setSingleLine(true);
        chip.setIncludeFontPadding(false);
        chip.setBackgroundColor(0x55FFE45C);
        chip.setPadding(0, 0, 0, 0);
        frame.addView(chip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // The real size: the PDF's font size is textSize x page height x fontScale.
        Runnable size = () -> {
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (form.textSize * h * form.fontScale));
            Paint.FontMetrics fm = chip.getPaint().getFontMetrics();
            // Bottom-left of the text at the baseline: the chip's top sits one ascent above it.
            chip.setX((float) (q.place.x * w));
            chip.setY((float) (q.place.y * h) + fm.ascent);
        };
        size.run();
        sizers.add(size);
        float[] grab = new float[2];
        chip.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    grab[0] = e.getRawX() - v.getX();
                    grab[1] = e.getRawY() - v.getY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);  // don't scroll the page while dragging
                    return true;
                case MotionEvent.ACTION_MOVE:
                    v.setX(Math.max(0, Math.min(w - 10, e.getRawX() - grab[0])));
                    v.setY(Math.max(-v.getHeight() / 2f, Math.min(h - 4, e.getRawY() - grab[1])));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Paint.FontMetrics fm = chip.getPaint().getFontMetrics();
                    q.place = new Models.Place(q.place.page, Models.Place.clamp01(v.getX() / w),
                            Models.Place.clamp01((v.getY() - fm.ascent) / h));
                    store.save();
                    return true;
                default:
                    return false;
            }
        });
    }
}
