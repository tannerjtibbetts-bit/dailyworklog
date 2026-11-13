package com.dailyworklog.app;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.StyleSpan;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** A filled-in report form as a PDF (US Letter, 0.6 inch margins). */
final class FormPdf {
    private static final float W = 612, H = 792, M = 43.2f, BODY_W = W - 2 * M;

    private final PdfDocument doc = new PdfDocument();
    private PdfDocument.Page page;
    private Canvas c;
    private float y;
    private int pageNo;
    private final String footer;

    private final TextPaint title = paint(18, 0xFF14233A, true);
    private final TextPaint meta = paint(10, 0xFF1B2533, false);
    private final TextPaint body = paint(10.5f, 0xFF1B2533, false);
    private final TextPaint bold = paint(10.5f, 0xFF14233A, true);
    private final TextPaint small = paint(8.5f, 0xFF6B7685, false);
    private final TextPaint sig = paint(9, 0xFF1B2533, false);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();

    private FormPdf(String footer) {
        this.footer = footer;
        line.setColor(0xFFB8C0CA);
        line.setStrokeWidth(0.8f);
        shade.setColor(0xFFEEF2F7);
    }

    private static TextPaint paint(float size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    static void write(Models.ReportForm f, Models.FilledReport r, long now, OutputStream out) throws IOException {
        FormPdf pdf = new FormPdf("Filled in with Daily Work Log on " + ReportFill.day(now));
        try {
            pdf.render(f, r);
            pdf.doc.writeTo(out);
        } finally {
            pdf.doc.close();
        }
    }

    private static String answer(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }

    private void render(Models.ReportForm f, Models.FilledReport r) {
        newPage();
        text(f.name.isEmpty() ? r.formName : f.name, title, M, BODY_W, 6);
        String who = Models.ReportForm.DISCIPLINE.equals(f.group) ? "Employee" : "Apprentice";
        text(who + ": " + r.person + "   Project: " + r.project + "   Date: " + ReportFill.day(r.createdAt), meta, M, BODY_W, 14);

        boolean hasAverage = false;
        for (Models.FormQuestion q : f.questions) if (Models.FormQuestion.AVERAGE.equals(q.kind)) hasAverage = true;
        boolean tableDone = false;
        int ratings = 0;
        for (Models.FormQuestion q : f.questions) if (Models.FormQuestion.RATING.equals(q.kind)) ratings++;
        for (Models.FormQuestion q : f.questions) {
            switch (q.kind) {
                case Models.FormQuestion.RATING:
                    if (!tableDone) ratingTable(f, r, hasAverage);
                    tableDone = true;
                    break;
                case Models.FormQuestion.AVERAGE:
                    if (ratings == 0) labelled(q.label, answer(ReportFill.average(f, r)));
                    break;
                case Models.FormQuestion.LONG:
                    ensure(40);
                    text(q.label, bold, M, BODY_W, 2);
                    text(answer(r.answer(q.id)), body, M, BODY_W, 10);
                    break;
                default:
                    labelled(q.label, answer(r.answer(q.id)));
            }
        }
        signatures(f.signatures);
        finishPage();
    }

    /** "Label: answer" with the label in bold. */
    private void labelled(String label, String value) {
        SpannableStringBuilder b = new SpannableStringBuilder(label + ": ");
        b.setSpan(new StyleSpan(Typeface.BOLD), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append(value);
        text(b, body, M, BODY_W, 6);
    }

    private void ratingTable(Models.ReportForm f, Models.FilledReport r, boolean hasAverage) {
        float left = BODY_W * 0.62f, pad = 6;
        y += 4;
        int n = 0;
        List<Models.Choice> scale = new ArrayList<>();
        for (Models.FormQuestion q : f.questions) {
            if (!Models.FormQuestion.RATING.equals(q.kind)) continue;
            n++;
            if (scale.isEmpty()) scale = q.choices;
            String a = r.answer(q.id).trim();
            String shown = "—";
            if (!a.isEmpty()) {
                shown = a;
                for (Models.Choice ch : q.choices) if (ch.label.equalsIgnoreCase(a)) shown = ch.display();
            }
            row(n + ". " + q.label, shown, left, pad, false);
        }
        if (hasAverage) row("Overall average", answer(ReportFill.average(f, r)), left, pad, true);
        c.drawLine(M, y, M + BODY_W, y, line);
        y += 4;
        if (!scale.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Models.Choice ch : scale) parts.add(ch.value.isEmpty() ? ch.label : ch.label + " " + ch.value);
            text("Point system: " + String.join(" · ", parts), small, M, BODY_W, 4);
        }
        y += 8;
    }

    private void row(String label, String value, float left, float pad, boolean strong) {
        StaticLayout l1 = layout(label, strong ? bold : body, left - 2 * pad);
        StaticLayout l2 = layout(value, strong ? bold : body, BODY_W - left - 2 * pad);
        float h = Math.max(l1.getHeight(), l2.getHeight()) + 2 * pad;
        ensure(h);
        if (strong) c.drawRect(M, y, M + BODY_W, y + h, shade);
        c.drawLine(M, y, M + BODY_W, y, line);
        c.drawLine(M + left, y, M + left, y + h, line);
        c.save();
        c.translate(M + pad, y + pad);
        l1.draw(c);
        c.restore();
        c.save();
        c.translate(M + left + pad, y + pad);
        l2.draw(c);
        c.restore();
        y += h;
    }

    /** Two per row: a line, the label under it, then "Date: ______". Kept together on one page. */
    private void signatures(List<String> labels) {
        if (labels.isEmpty()) return;
        float rowH = 36 + 14 + 26, colW = BODY_W * 0.45f, gap = BODY_W - 2 * colW;
        int rows = (labels.size() + 1) / 2;
        ensure(16 + rows * rowH + (rows - 1) * 36f);
        y += 16;
        for (int i = 0; i < labels.size(); i += 2) {
            for (int k = 0; k < 2 && i + k < labels.size(); k++) {
                float x = M + k * (colW + gap);
                float lineY = y + 36;
                c.drawLine(x, lineY, x + colW, lineY, line);
                c.drawText(labels.get(i + k), x, lineY + 12, sig);
                c.drawText("Date: ________________", x, lineY + 34, sig);
            }
            y += rowH + 36;
        }
    }

    // ----- layout -----

    private static StaticLayout layout(CharSequence s, TextPaint p, float width) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build();
    }

    private void text(CharSequence s, TextPaint p, float x, float width, float after) {
        StaticLayout l = layout(s, p, width);
        ensure(Math.min(l.getHeight(), H - 2 * M - 40));
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
        y += l.getHeight() + after;
    }

    private void ensure(float height) {
        if (y + height > H - M - 18) {
            finishPage();
            newPage();
        }
    }

    private void newPage() {
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder((int) W, (int) H, pageNo).create());
        c = page.getCanvas();
        y = M;
    }

    private void finishPage() {
        if (page == null) return;
        c.drawText(footer, M, H - 22, small);
        String n = "Page " + pageNo;
        c.drawText(n, W - M - small.measureText(n), H - 22, small);
        doc.finishPage(page);
        page = null;
    }
}
