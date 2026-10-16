package com.dailyworklog.app;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a Word (.docx) report by hand: plain Java, no libraries.
 * Layout: header, progress summary, then each field with its photos two per row.
 */
final class ReportDocx {

    static final class Encoded {
        final byte[] jpeg;
        final int width, height;

        Encoded(byte[] jpeg, int width, int height) {
            this.jpeg = jpeg;
            this.width = width;
            this.height = height;
        }
    }

    interface ImageEncoder {
        Encoded encode(File f);
    }

    private static final long EMU_PER_INCH = 914400L;
    private static final String NAVY = "14233A", GREY = "6B7685", GREEN = "1DB04B", RED = "D93B3F", TEXT = "1B2533";

    private final StringBuilder body = new StringBuilder();
    private final StringBuilder rels = new StringBuilder();
    private final ZipOutputStream zip;
    private final ImageEncoder encoder;
    private int imageCount = 0;
    /** The log's date, so photo captions only show a date when it's a different day. */
    private String logIso = "";

    private ReportDocx(ZipOutputStream zip, ImageEncoder encoder) {
        this.zip = zip;
        this.encoder = encoder;
    }

    static void write(Models.Project p, Models.DailyLog log, String generatedBy, long generatedAt,
                      File photoDir, ImageEncoder encoder, OutputStream out) throws IOException {
        ZipOutputStream z = new ZipOutputStream(out);
        ReportDocx r = new ReportDocx(z, encoder);
        r.render(p, log, generatedBy, generatedAt, photoDir);
        r.finish();
        z.finish();
        z.flush();
    }

    private void render(Models.Project p, Models.DailyLog log, String by, long at, File photoDir) throws IOException {
        logIso = log.date;
        int done = log.doneCount(), total = log.tasks.size();

        if (!p.name.isEmpty()) para(p.name.toUpperCase(java.util.Locale.ROOT), false, 10, GREY, 40, false, null);
        para(log.displayTitle(), true, 22, NAVY, 60, false, null);
        if (!p.location.isEmpty()) para(p.location, false, 11, GREY, 20, false, null);
        para("Generated " + Fmt.longStamp(at) + " by " + by, false, 10, GREY, 120, false, null);

        String summary = done + "/" + total + " fields completed";
        body.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"8\" w:color=\"D5D9DE\"/></w:pBdr>")
                .append("<w:spacing w:before=\"0\" w:after=\"240\"/></w:pPr>");
        run(summary, true, 12, done == total && total > 0 ? GREEN : NAVY, null);
        List<String> people = log.people();
        if (!people.isEmpty()) run("     Crew: " + Fmt.join(people), false, 11, GREY, null);
        body.append("</w:p>");

        for (java.util.Map.Entry<String, List<Models.Task>> g : Models.Task.reportGroups(log.tasks)) {
            if (!g.getKey().isEmpty()) {
                body.append("<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/></w:pPr>");
                run(g.getKey() + " (" + g.getValue().size() + ")", true, 14, NAVY, null);
                body.append("</w:p>");
            }
            for (Models.Task t : g.getValue()) renderTask(t, photoDir);
        }

        renderOvertime(p);
        renderSeaCans(p, false);

        List<Models.Extra> extras = log.includedExtras();
        if (!extras.isEmpty()) {
            body.append("<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"360\" w:after=\"80\"/></w:pPr>");
            run("Also today", true, 13, NAVY, null);
            body.append("</w:p>");
            for (Models.Extra e : extras) {
                String time = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(new java.util.Date(e.at));
                para(time + "   " + e.label(), false, 11, TEXT, 30, true, null);
                if (!e.detail.trim().isEmpty() && !Models.Extra.TEMPLATE.equals(e.type)) {
                    para(e.detail.trim(), false, 9, GREY, 40, true, null);
                }
                if (!e.photos.isEmpty()) photoTable(e.photos, photoDir);
            }
        }
    }

    private void renderOvertime(Models.Project p) {
        Models.Overtime ot = p.overtime;
        List<Models.Overtime.OtRow> rows = ot.filled();
        String notes = ot.notes.trim();
        if (!ot.include || (rows.isEmpty() && notes.isEmpty())) return;
        body.append("<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"360\" w:after=\"100\"/></w:pPr>");
        run("Weekend overtime", true, 13, NAVY, null);
        body.append("</w:p>");
        if (rows.isEmpty()) {
            overtimeNotes(notes);
            return;
        }
        String b = "<w:top w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/><w:left w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/><w:right w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/>"
                + "<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/>";
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"10080\" w:type=\"dxa\"/><w:tblBorders>").append(b)
                .append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/></w:tblPr><w:tblGrid><w:gridCol w:w=\"4080\"/>")
                .append("<w:gridCol w:w=\"1500\"/><w:gridCol w:w=\"1500\"/><w:gridCol w:w=\"1500\"/><w:gridCol w:w=\"1500\"/></w:tblGrid>");
        String[] head = {"Name", "Fri", "Sat", "Sun", "Hours"};
        otRow(head, true, "E4E7EC", true);
        for (Models.Overtime.OtRow r : rows) {
            otRow(new String[]{r.name, dash(r.fri), dash(r.sat), dash(r.sun), dash(r.hours)}, false, null, true);
        }
        otRow(new String[]{"Yes total", String.valueOf(ot.yesCount(0)), String.valueOf(ot.yesCount(1)), String.valueOf(ot.yesCount(2)), ""}, true, null, false);
        body.append("</w:tbl><w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"120\"/></w:pPr></w:p>");
        overtimeNotes(notes);
    }

    /** Just the Sea Can Movements tab as its own Word document. */
    static void writeSeaCans(Models.Project p, String generatedBy, long generatedAt, OutputStream out) throws IOException {
        ZipOutputStream z = new ZipOutputStream(out);
        ReportDocx r = new ReportDocx(z, null);
        if (!p.name.isEmpty()) r.para(p.name.toUpperCase(java.util.Locale.ROOT), false, 10, GREY, 40, false, null);
        r.para("Sea can movements", true, 22, NAVY, 60, false, null);
        if (!p.location.isEmpty()) r.para(p.location, false, 11, GREY, 20, false, null);
        r.para("Generated " + Fmt.longStamp(generatedAt) + " by " + generatedBy, false, 10, GREY, 120, false, null);
        if (p.filledSeaCans().isEmpty()) r.para("No sea cans entered yet.", false, 11, TEXT, 40, false, null);
        else r.renderSeaCans(p, true);
        r.finish();
        z.finish();
        z.flush();
    }

    /** Sea can table. In the daily report only when ticked; force writes it regardless. */
    private void renderSeaCans(Models.Project p, boolean force) {
        List<Models.SeaCanMove> rows = p.filledSeaCans();
        if ((!force && !p.seaCanInclude) || rows.isEmpty()) return;
        if (!force) {
            body.append("<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"360\" w:after=\"100\"/></w:pPr>");
            run("Sea can movements", true, 13, NAVY, null);
            body.append("</w:p>");
        }
        para(Fmt.plural(rows.size(), "sea can", "sea cans"), false, 10, GREY, 80, false, null);
        String b = "<w:top w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/><w:left w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/><w:right w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/>"
                + "<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"D5D9DE\"/>";
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"10080\" w:type=\"dxa\"/><w:tblBorders>").append(b)
                .append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/></w:tblPr><w:tblGrid><w:gridCol w:w=\"1200\"/>")
                .append("<w:gridCol w:w=\"3280\"/><w:gridCol w:w=\"2800\"/><w:gridCol w:w=\"2800\"/></w:tblGrid>");
        // The heading row repeats at the top of each page.
        seaCanRow(new String[]{"Moves", "Can #", "From location", "Current location"}, true);
        for (int i = 0; i < rows.size(); i++) {
            Models.SeaCanMove m = rows.get(i);
            seaCanRow(new String[]{String.valueOf(i + 1), m.can, dash(m.fromLocation()), dash(m.toLocation())}, false);
        }
        body.append("</w:tbl><w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"120\"/></w:pPr></w:p>");
    }

    private void seaCanRow(String[] cells, boolean head) {
        int[] widths = {1200, 3280, 2800, 2800};
        body.append("<w:tr><w:trPr><w:cantSplit/>").append(head ? "<w:tblHeader/>" : "").append("</w:trPr>");
        for (int i = 0; i < cells.length; i++) {
            body.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(widths[i]).append("\" w:type=\"dxa\"/>");
            if (head) body.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E4E7EC\"/>");
            body.append("</w:tcPr><w:p><w:pPr><w:spacing w:before=\"40\" w:after=\"40\"/>")
                    .append(i == 1 ? "" : "<w:jc w:val=\"center\"/>").append("</w:pPr>");
            run(cells[i], head || i == 1, 10, TEXT, null);
            body.append("</w:p></w:tc>");
        }
        body.append("</w:tr>");
    }

    private void overtimeNotes(String notes) {
        if (notes.isEmpty()) return;
        para("Notes", true, 10, NAVY, 40, false, null);
        para(notes, false, 10, TEXT, 160, false, null);
    }

    private static String dash(String v) {
        return v.isEmpty() ? "-" : v;
    }

    private void otRow(String[] cells, boolean bold, String shade, boolean keep) {
        int[] widths = {4080, 1500, 1500, 1500, 1500};
        body.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>");
        for (int i = 0; i < cells.length; i++) {
            body.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(widths[i]).append("\" w:type=\"dxa\"/>");
            if (shade != null) body.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(shade).append("\"/>");
            body.append("</w:tcPr><w:p><w:pPr>").append(keep ? "<w:keepNext/>" : "").append("<w:spacing w:before=\"40\" w:after=\"40\"/>")
                    .append(i == 0 ? "" : "<w:jc w:val=\"center\"/>").append("</w:pPr>");
            String v = cells[i];
            String col = i > 0 && Models.Overtime.YES.equals(v) ? GREEN : i > 0 && Models.Overtime.NO.equals(v) ? RED : TEXT;
            run(v, bold || (i > 0 && (Models.Overtime.YES.equals(v) || Models.Overtime.NO.equals(v))), 10, col, null);
            body.append("</w:p></w:tc>");
        }
        body.append("</w:tr>");
    }

    private void renderTask(Models.Task t, File photoDir) throws IOException {
        // Title line with a status mark; keepNext holds it on the page with its details.
        body.append("<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"200\" w:after=\"40\"/><w:ind w:left=\"440\" w:hanging=\"440\"/></w:pPr>");
        run(t.done ? "\u2714  " : "\u25CB  ", true, 13, t.done ? GREEN : "A3ACB7", "Segoe UI Symbol");
        run(t.title.isEmpty() ? "(Untitled field)" : t.title, true, 13, NAVY, null);
        body.append("</w:p>");

        if (t.done) {
            para("Completed by " + t.completedBy + ", " + Fmt.stamp(t.completedAt), false, 9, GREY, 40, true, null);
        } else {
            para("Not completed", false, 9, RED, 40, true, null);
        }
        if (t.loadSecured) {
            body.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"40\"/><w:ind w:left=\"440\"/></w:pPr>");
            run("\u2714 ", true, 9, GREEN, "Segoe UI Symbol");
            run("Load securement completed", true, 9, GREEN, null);
            if (!t.securedBy.isEmpty()) run(" by " + t.securedBy + ", " + Fmt.stamp(t.securedAt), false, 9, GREY, null);
            body.append("</w:p>");
        }
        if (t.nextShift) para("Carries over to next shift", true, 9, "174A8B", 40, true, null);
        if (!t.assignees.isEmpty()) {
            para("Assigned: " + Fmt.join(t.assignees), false, 9, GREY, 40, true, null);
        }
        if (!t.notes.trim().isEmpty()) {
            para(t.notes.trim(), false, 11, TEXT, 80, true, null);
        }
        if (t.photosRequired && t.photos.isEmpty()) {
            para("Photos required: none attached", false, 9, RED, 40, true, null);
        }
        if (!t.photos.isEmpty()) photoTable(t.photos, photoDir);
    }

    /** Photo rows like the PDF: same height side by side, each row only as tall as it needs. */
    private void photoTable(List<Models.Photo> photos, File photoDir) throws IOException {
        List<Encoded> enc = new java.util.ArrayList<>();
        List<float[]> sizes = new java.util.ArrayList<>();
        for (Models.Photo p : photos) {
            Encoded e = encoder.encode(new File(photoDir, p.file));
            enc.add(e);
            sizes.add(e == null ? null : new float[]{e.width, e.height});
        }
        final float gapPt = 8f, widthPt = 9640 / 20f;  // 9640 twips of room after the indent
        for (List<PhotoLayout.Cell> row : PhotoLayout.rows(sizes, widthPt, 130f, gapPt, 90f)) {
            StringBuilder grid = new StringBuilder();
            int[] colTw = new int[row.size()];
            for (int k = 0; k < row.size(); k++) {
                float w = row.get(k).w + (k < row.size() - 1 ? gapPt : 0);
                colTw[k] = Math.round(w * 20);
                grid.append("<w:gridCol w:w=\"").append(colTw[k]).append("\"/>");
            }
            body.append("<w:tbl><w:tblPr><w:tblInd w:w=\"440\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>")
                    .append("<w:tblCellMar><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/></w:tblCellMar>")
                    .append("</w:tblPr><w:tblGrid>").append(grid).append("</w:tblGrid>")
                    .append("<w:tr><w:trPr><w:cantSplit/></w:trPr>");
            for (int k = 0; k < row.size(); k++) {
                PhotoLayout.Cell cell = row.get(k);
                body.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(colTw[k]).append("\" w:type=\"dxa\"/></w:tcPr>");
                photoCell(photos.get(cell.index), enc.get(cell.index), cell.w, cell.h);
                body.append("</w:tc>");
            }
            body.append("</w:tr></w:tbl>");
        }
        body.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"60\"/></w:pPr></w:p>");
    }

    private void photoCell(Models.Photo p, Encoded e, float wPt, float hPt) throws IOException {
        if (e == null) {
            para("(Photo missing)", false, 9, RED, 40, false, null);
            return;
        }
        imageCount++;
        String rid = "rIdImg" + imageCount;
        String name = "image" + imageCount + ".jpeg";
        zip.putNextEntry(new ZipEntry("word/media/" + name));
        zip.write(e.jpeg);
        zip.closeEntry();
        rels.append("<Relationship Id=\"").append(rid)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
                .append(name).append("\"/>");

        long cx = Math.round(wPt / 72.0 * EMU_PER_INCH);
        long cy = Math.round(hPt / 72.0 * EMU_PER_INCH);
        int id = imageCount;
        body.append("<w:p><w:pPr><w:keepNext/><w:spacing w:before=\"60\" w:after=\"0\"/></w:pPr><w:r><w:drawing>")
                .append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                .append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                .append("<wp:docPr id=\"").append(id).append("\" name=\"Photo ").append(id).append("\"/>")
                .append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
                .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                .append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"").append(id).append("\" name=\"").append(name).append("\"/>")
                .append("<pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"").append(rid).append("\"/>")
                .append("<a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/>")
                .append("<a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
                .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                .append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
        para(PhotoLayout.caption(p, logIso), false, 7, GREY, 60, false, null);
    }

    /** One paragraph. Line breaks in text become Word line breaks. */
    private void para(String text, boolean bold, int pt, String color, int afterTwips, boolean indent, String font) {
        body.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"").append(afterTwips).append("\"/>");
        if (indent) body.append("<w:ind w:left=\"440\"/>");
        body.append("</w:pPr>");
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) body.append("<w:r><w:br/></w:r>");
            run(lines[i], bold, pt, color, font);
        }
        body.append("</w:p>");
    }

    private void run(String text, boolean bold, int pt, String color, String font) {
        String f = font == null ? "Calibri" : font;
        body.append("<w:r><w:rPr><w:rFonts w:ascii=\"").append(f).append("\" w:hAnsi=\"").append(f)
                .append("\" w:cs=\"").append(f).append("\"/>");
        if (bold) body.append("<w:b/>");
        body.append("<w:color w:val=\"").append(color).append("\"/><w:sz w:val=\"").append(pt * 2)
                .append("\"/><w:szCs w:val=\"").append(pt * 2).append("\"/></w:rPr><w:t xml:space=\"preserve\">")
                .append(esc(text)).append("</w:t></w:r>");
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    // Drop control characters that aren't legal in XML.
                    if (c >= 0x20 || c == '\t') b.append(c);
            }
        }
        return b.toString();
    }

    private void entry(String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void finish() throws IOException {
        String head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
        entry("[Content_Types].xml", head
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>");
        entry("_rels/.rels", head
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>");
        entry("word/_rels/document.xml.rels", head
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + rels + "</Relationships>");
        entry("word/document.xml", head
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
                + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>"
                + body
                + "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>"
                + "<w:pgMar w:top=\"1080\" w:right=\"1080\" w:bottom=\"1080\" w:left=\"1080\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>"
                + "</w:sectPr></w:body></w:document>");
    }
}
