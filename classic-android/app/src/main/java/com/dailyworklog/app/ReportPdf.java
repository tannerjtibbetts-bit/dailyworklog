package com.dailyworklog.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * PDF report drawn with Android's built-in PdfDocument (US Letter).
 * Each field's photos sit right under it, three per row, captioned once.
 */
final class ReportPdf {
    private static final int W = 612, H = 792, M = 44;
    private static final float INDENT = 24f;

    private final PdfDocument doc = new PdfDocument();
    private PdfDocument.Page page;
    private Canvas c;
    private float y;
    private int pageNo;
    private final String footerTitle;

    private final TextPaint caps = text(8.5f, 0xFF6B7685, false);
    private final TextPaint title = text(22f, 0xFF14233A, true);
    private final TextPaint meta = text(10f, 0xFF6B7685, false);
    private final TextPaint heading = text(13f, 0xFF14233A, true);
    private final TextPaint body = text(10.5f, 0xFF1B2533, false);
    private final TextPaint small = text(8.5f, 0xFF6B7685, false);
    private final TextPaint smallRed = text(8.5f, 0xFFD93B3F, false);
    private final TextPaint tiny = text(7.5f, 0xFF8A94A1, false);
    private final TextPaint smallGreen = text(8.5f, 0xFF148A3A, true);

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barTrack = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ReportPdf(String footerTitle) {
        this.footerTitle = footerTitle;
        caps.setLetterSpacing(0.08f);
        line.setColor(0xFFE2E6EB);
        line.setStrokeWidth(0.8f);
        green.setColor(0xFF1DB04B);
        ring.setColor(0xFFA3ACB7);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.4f);
        tick.setColor(0xFFFFFFFF);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(1.8f);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        barTrack.setColor(0xFFE4E7EC);
    }

    private static TextPaint text(float size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    static void write(Models.Project p, Models.DailyLog log, String generatedBy, long generatedAt,
                      File photoDir, OutputStream out) throws IOException {
        ReportPdf r = new ReportPdf(log.displayTitle());
        try {
            r.render(p, log, generatedBy, generatedAt, photoDir);
            r.doc.writeTo(out);
        } finally {
            r.doc.close();
        }
    }

    private void render(Models.Project p, Models.DailyLog log, String by, long at, File photoDir) {
        logIso = log.date;
        newPage();
        float w = W - 2 * M;
        if (!p.name.isEmpty()) text(p.name.toUpperCase(Locale.ROOT), caps, M, w, 4);
        text(log.displayTitle(), title, M, w, 6);
        if (!p.location.isEmpty()) text(p.location, meta, M, w, 2);
        text("Generated " + Fmt.longStamp(at) + " by " + by, meta, M, w, 14);

        int done = log.doneCount(), total = log.tasks.size();
        text(done + "/" + total + " fields completed", heading, M, w, 6);
        RectF bar = new RectF(M, y, M + w, y + 6);
        c.drawRoundRect(bar, 3, 3, barTrack);
        if (total > 0 && done > 0) {
            RectF fill = new RectF(M, y, M + w * done / (float) total, y + 6);
            c.drawRoundRect(fill, 3, 3, green);
        }
        y += 14;
        List<String> people = log.people();
        if (!people.isEmpty()) text("Crew: " + Fmt.join(people), meta, M, w, 4);
        y += 8;
        c.drawLine(M, y, W - M, y, line);
        y += 16;

        for (java.util.Map.Entry<String, List<Models.Task>> g : Models.Task.reportGroups(log.tasks)) {
            if (!g.getKey().isEmpty()) {
                ensure(70);
                text(g.getKey() + " (" + g.getValue().size() + ")", heading, M, W - 2 * M, 8);
            }
            for (Models.Task t : g.getValue()) drawTask(t, photoDir);
        }
        drawExtras(log, photoDir);
        drawOvertime(p, false);
        drawSeaCans(p, false);
        finishPage();
    }

    /** Just the Sea Can Movements tab as its own PDF. */
    static void writeSeaCans(Models.Project p, String generatedBy, long generatedAt, OutputStream out) throws IOException {
        ReportPdf r = new ReportPdf("Sea can movements" + (p.name.isEmpty() ? "" : " - " + p.name));
        try {
            r.newPage();
            float w = W - 2 * M;
            if (!p.name.isEmpty()) r.text(p.name.toUpperCase(Locale.ROOT), r.caps, M, w, 4);
            r.text("Sea can movements", r.title, M, w, 6);
            if (!p.location.isEmpty()) r.text(p.location, r.meta, M, w, 2);
            r.text("Generated " + Fmt.longStamp(generatedAt) + " by " + generatedBy, r.meta, M, w, 10);
            if (p.filledSeaCans().isEmpty()) r.text("No sea cans entered yet.", r.body, M, w, 4);
            else r.drawSeaCans(p, true);
            r.finishPage();
            r.doc.writeTo(out);
        } finally {
            r.doc.close();
        }
    }

    /** Sea can table. In the daily report only when ticked; force draws it regardless. */
    private void drawSeaCans(Models.Project p, boolean force) {
        List<Models.SeaCanMove> rows = p.filledSeaCans();
        if ((!force && !p.seaCanInclude) || rows.isEmpty()) return;
        float w = W - 2 * M, rowH = 20;
        float[] x = seaCanColumns(w);
        ensure(70 + rowH * 2);
        y += 6;
        if (!force) text("Sea can movements", heading, M, w, 8);
        text(Fmt.plural(rows.size(), "sea can", "sea cans"), meta, M, w, 6);
        seaCanHeader(w, rowH, x);
        for (int i = 0; i < rows.size(); i++) {
            Models.SeaCanMove m = rows.get(i);
            if (y + rowH > H - M - 18) {
                // Carry on over the page with the column headings again.
                finishPage();
                newPage();
                seaCanHeader(w, rowH, x);
            }
            c.drawLine(M, y, M + w, y, line);
            String[] v = {String.valueOf(i + 1), m.can, m.fromLocation(), m.toLocation(), m.weight.trim()};
            for (int d = 0; d < 5; d++) {
                String s = v[d].isEmpty() ? "-" : v[d];
                TextPaint tp = v[d].isEmpty() || d == 0 ? small : body;
                float cw = x[d + 1] - x[d];
                c.drawText(s, d == 1 ? x[d] + 6 : x[d] + (cw - tp.measureText(s)) / 2f, y + 13.5f, tp);
            }
            y += rowH;
        }
        c.drawLine(M, y, M + w, y, line);
        y += 14;
    }

    /** Column edges: Moves, Can #, From location, Current location, Weight. */
    private static float[] seaCanColumns(float w) {
        return new float[]{M, M + w * 0.11f, M + w * 0.40f, M + w * 0.63f, M + w * 0.86f, M + w};
    }

    /** The heading row. */
    private void seaCanHeader(float w, float rowH, float[] x) {
        TextPaint bold = text(9.5f, 0xFF14233A, true);
        c.drawRect(M, y, M + w, y + rowH, barTrack);
        String[] h = {"Moves", "Can #", "From location", "Current location", "Weight"};
        for (int d = 0; d < 5; d++) {
            float cw = x[d + 1] - x[d];
            c.drawText(h[d], d == 1 ? x[d] + 6 : x[d] + (cw - bold.measureText(h[d])) / 2f, y + 13.5f, bold);
        }
        y += rowH;
    }

    /** Weekend overtime table, when ticked for reports. */
    /** Just the Overtime tab as its own PDF, to send on its own. */
    static void writeOvertime(Models.Project p, String generatedBy, long generatedAt, OutputStream out) throws IOException {
        ReportPdf r = new ReportPdf("Weekend overtime" + (p.name.isEmpty() ? "" : " - " + p.name));
        try {
            Models.Overtime ot = p.overtime;
            r.newPage();
            float w = W - 2 * M;
            if (!p.name.isEmpty()) r.text(p.name.toUpperCase(Locale.ROOT), r.caps, M, w, 4);
            r.text("Weekend overtime", r.title, M, w, 6);
            r.text("Weekend of " + Fmt.weekendDay(ot.weekOf, 0) + " to " + Fmt.weekendDay(ot.weekOf, 2), r.meta, M, w, 2);
            if (!p.location.isEmpty()) r.text(p.location, r.meta, M, w, 2);
            r.text("Generated " + Fmt.longStamp(generatedAt) + " by " + generatedBy, r.meta, M, w, 10);
            if (ot.filled().isEmpty() && ot.notes.trim().isEmpty()) r.text("No one has signed up yet.", r.body, M, w, 4);
            else r.drawOvertime(p, true);
            r.finishPage();
            r.doc.writeTo(out);
        } finally {
            r.doc.close();
        }
    }

    /** Weekend overtime table. In the daily report only when ticked; force draws it regardless. */
    private void drawOvertime(Models.Project p, boolean force) {
        Models.Overtime ot = p.overtime;
        List<Models.Overtime.OtRow> rows = ot.filled();
        String notes = ot.notes.trim();
        if ((!force && !ot.include) || (rows.isEmpty() && notes.isEmpty())) return;
        float w = W - 2 * M, rowH = 20, nameW = w * 0.40f, dayW = (w - nameW) / 4f;
        ensure(70 + rowH * 2);
        y += 6;
        text("Weekend overtime", heading, M, w, 8);
        if (rows.isEmpty()) {
            drawOvertimeNotes(notes, w);
            return;
        }
        TextPaint bold = text(9.5f, 0xFF14233A, true);
        TextPaint yes = text(9.5f, 0xFF148A3A, true);
        TextPaint no = text(9.5f, 0xFFC9353A, true);
        // header
        c.drawRect(M, y, M + w, y + rowH, barTrack);
        c.drawText("Name", M + 6, y + 13.5f, bold);
        for (int d = 0; d < 4; d++) {
            String h = new String[]{"Fri", "Sat", "Sun", "Hours"}[d];
            c.drawText(h, M + nameW + d * dayW + (dayW - bold.measureText(h)) / 2f, y + 13.5f, bold);
        }
        y += rowH;
        for (Models.Overtime.OtRow r : rows) {
            ensure(rowH);
            c.drawLine(M, y, M + w, y, line);
            c.drawText(r.name, M + 6, y + 13.5f, body);
            String[] v = {r.fri, r.sat, r.sun, r.hours};
            for (int d = 0; d < 4; d++) {
                String s = v[d].isEmpty() ? "-" : v[d];
                TextPaint tp = Models.Overtime.YES.equals(v[d]) ? yes : Models.Overtime.NO.equals(v[d]) ? no : small;
                c.drawText(s, M + nameW + d * dayW + (dayW - tp.measureText(s)) / 2f, y + 13.5f, tp);
            }
            y += rowH;
        }
        c.drawLine(M, y, M + w, y, line);
        String tot = "Yes total";
        c.drawText(tot, M + 6, y + 13.5f, bold);
        for (int d = 0; d < 3; d++) {
            String s = String.valueOf(ot.yesCount(d));
            c.drawText(s, M + nameW + d * dayW + (dayW - yes.measureText(s)) / 2f, y + 13.5f, yes);
        }
        y += rowH + 10;
        drawOvertimeNotes(notes, w);
    }

    private void drawOvertimeNotes(String notes, float w) {
        if (notes.isEmpty()) return;
        text("Notes", text(9.5f, 0xFF14233A, true), M, w, 3);
        for (String para : notes.split("\n")) text(para.isEmpty() ? " " : para, body, M, w, 2);
        y += 8;
    }

    /** Links used, notes sent and templates used that were ticked for the report. */
    private void drawExtras(Models.DailyLog log, File photoDir) {
        List<Models.Extra> list = log.includedExtras();
        if (list.isEmpty()) return;
        float w = W - 2 * M;
        ensure(60);
        text("Also today", heading, M, w, 8);
        for (Models.Extra e : list) {
            float x = M + INDENT, iw = W - M - x;
            String time = new java.text.SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date(e.at));
            ensure(30);
            text(time + "   " + e.label(), body, x, iw, 2);
            if (Models.Extra.LINK.equals(e.type) && !e.detail.isEmpty()) text(e.detail, small, x, iw, 2);
            if ((Models.Extra.NOTIFY.equals(e.type) || Models.Extra.NOTE.equals(e.type)) && !e.detail.trim().isEmpty()) text(e.detail.trim(), small, x, iw, 2);
            if (!e.photos.isEmpty()) {
                y += 6;
                drawPhotoGrid(e.photos, photoDir, x, iw);
            }
            y += 8;
        }
    }

    private void drawTask(Models.Task t, File photoDir) {
        float x = M + INDENT;
        float w = W - M - x;
        StaticLayout head = layout(t.title.isEmpty() ? "(Untitled field)" : t.title, heading, w);
        // Keep the title with its details and, when it has photos, the first row of them.
        float keep = head.getHeight() + 40;
        if (!t.photos.isEmpty()) {
            List<List<PhotoLayout.Cell>> rows = photoRows(t.photos, photoDir, w);
            keep += 30 + PhotoLayout.height(rows.get(0)) + CAPTION_H;
        }
        ensure(Math.min(keep, H - 2 * M - 40));

        float cy = y + head.getLineBaseline(0) - heading.getTextSize() * 0.35f;
        float cx = M + 8;
        if (t.done) {
            c.drawCircle(cx, cy, 7.5f, green);
            Path p = new Path();
            p.moveTo(cx - 3.4f, cy + 0.2f);
            p.lineTo(cx - 0.9f, cy + 2.7f);
            p.lineTo(cx + 3.6f, cy - 2.4f);
            c.drawPath(p, tick);
        } else {
            c.drawCircle(cx, cy, 6.8f, ring);
        }
        draw(head, x);
        y += 3;

        if (t.done) text("Completed by " + t.completedBy + ", " + Fmt.stamp(t.completedAt), small, x, w, 2);
        else text("Not completed", smallRed, x, w, 2);
        if (t.loadSecured) {
            String who = t.securedBy.isEmpty() ? "" : " by " + t.securedBy + ", " + Fmt.stamp(t.securedAt);
            text("\u2713 Load securement completed" + who, smallGreen, x, w, 2);
        }
        if (t.nextShift) text("Carries over to next shift", text(8.5f, 0xFF174A8B, true), x, w, 2);
        if (!t.assignees.isEmpty()) text("Assigned: " + Fmt.join(t.assignees), small, x, w, 2);
        if (!t.notes.trim().isEmpty()) {
            y += 4;
            text(t.notes.trim(), body, x, w, 2);
        }
        if (t.photosRequired && t.photos.isEmpty()) text("Photos required: none attached", smallRed, x, w, 2);

        if (!t.photos.isEmpty()) {
            y += 6;
            drawPhotoGrid(t.photos, photoDir, x, w);
        }
        y += 10;
        ensure(12);
        c.drawLine(M, y, W - M, y, line);
        y += 16;
    }

    /** Photos sit side by side at this height (about 0.9 inch, half the old size) and wrap to a new row. */
    private static final float PHOTO_H = PhotoLayout.REPORT_H, PHOTO_GAP = 8f, CAPTION_H = 12f;
    /** Print sharpness for report photos: 220 dpi is crisp on paper and screen, and keeps files emailable. */
    private static final float PHOTO_DPI = 220f;
    /** The log's date, so photo captions only show a date when it's a different day. */
    private String logIso = "";

    private List<List<PhotoLayout.Cell>> photoRows(List<Models.Photo> photos, File photoDir, float width) {
        List<float[]> sizes = new java.util.ArrayList<>();
        for (Models.Photo p : photos) sizes.add(photoSize(new File(photoDir, p.file)));
        return PhotoLayout.rows(sizes, width, PHOTO_H, PHOTO_GAP, 90f, PhotoLayout.REPORT_SLOT);
    }

    /** Draws photos in rows that are only as tall as they need to be. */
    private void drawPhotoGrid(List<Models.Photo> photos, File photoDir, float x, float width) {
        for (List<PhotoLayout.Cell> row : photoRows(photos, photoDir, width)) {
            float rowH = PhotoLayout.height(row);
            ensure(rowH + CAPTION_H);
            for (PhotoLayout.Cell cell : row) {
                Models.Photo p = photos.get(cell.index);
                drawPhoto(p, new File(photoDir, p.file), x + cell.x, cell.w, cell.h);
            }
            y += rowH + CAPTION_H + 4;
        }
    }

    /** Width and height of a saved photo without loading it, or null if it's missing. */
    private static float[] photoSize(File f) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        return new float[]{o.outWidth, o.outHeight};
    }

    private void drawPhoto(Models.Photo p, File f, float left, float dw, float dh) {
        Bitmap bm = ImageLoader.decode(f, 1600);
        if (bm == null) {
            c.drawText("(Photo missing)", left, y + 12, smallRed);
            return;
        }
        // Resize the photo ourselves to PHOTO_DPI for its printed size, then place it with a plain
        // scale and no paint. Drawing a big bitmap into a rectangle with a smoothing paint made the
        // PDF store a copy at only 72 dpi (about one pixel per point), which is why photos looked soft.
        int pw = Math.max(1, Math.min(bm.getWidth(), Math.round(dw * PHOTO_DPI / 72f)));
        int ph = Math.max(1, Math.min(bm.getHeight(), Math.round(dh * PHOTO_DPI / 72f)));
        Bitmap sized = (pw == bm.getWidth() && ph == bm.getHeight()) ? bm : Bitmap.createScaledBitmap(bm, pw, ph, true);
        c.save();
        c.translate(left, y);
        c.scale(dw / sized.getWidth(), dh / sized.getHeight());
        c.drawBitmap(sized, 0, 0, null);
        c.restore();
        if (sized != bm) sized.recycle();
        bm.recycle();
        String cap = PhotoLayout.caption(p, logIso);
        float room = Math.max(dw, PhotoLayout.REPORT_SLOT) + PHOTO_GAP - 2;
        c.drawText(TextUtils.ellipsize(cap, tiny, room, TextUtils.TruncateAt.END).toString(), left, y + dh + 9, tiny);
    }

    // ----- layout helpers -----

    private static StaticLayout layout(String s, TextPaint p, float width) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build();
    }

    private void text(String s, TextPaint p, float x, float width, float after) {
        StaticLayout l = layout(s, p, width);
        ensure(Math.min(l.getHeight(), H - 2 * M - 40));
        draw(l, x);
        y += after;
    }

    private void draw(StaticLayout l, float x) {
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
        y += l.getHeight();
    }

    private void ensure(float height) {
        if (y + height > H - M - 18) {
            finishPage();
            newPage();
        }
    }

    private void newPage() {
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        c = page.getCanvas();
        y = M;
    }

    private void finishPage() {
        if (page == null) return;
        float fy = H - 24;
        c.drawText(footerTitle, M, fy, tiny);
        String n = "Page " + pageNo;
        c.drawText(n, W - M - tiny.measureText(n), fy, tiny);
        doc.finishPage(page);
        page = null;
    }
}
