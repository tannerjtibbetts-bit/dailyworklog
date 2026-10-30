package com.dailyworklog.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** The daily report as an Excel (.xlsx) sheet laid out like the PDF: one row per field, then the extra sections. */
final class ReportXlsx {
    private ReportXlsx() {}

    /** Cell styles (cellXfs) in styles.xml. */
    private static final int NORMAL = 0, BOLD = 1, TITLE = 2, META = 3, HEAD = 4, CELL = 5, GREEN = 6, RED = 7,
            SECTION = 8, NUM = 9, AREA = 10;
    private static final String COLS = "ABCDEFGHIJ";
    private static final int[] WIDTHS = {12, 40, 11, 18, 20, 22, 26, 11, 40, 9};

    /** Builds the sheet's rows; r is the current row number. */
    private static final class Sheet {
        final StringBuilder sb = new StringBuilder();
        int r = 0;
        private boolean open;

        /** Starts the next row (closing the last one). */
        Sheet row() {
            return row(0);
        }

        Sheet row(int height) {
            end();
            r++;
            sb.append("<row r=\"").append(r).append("\"");
            if (height > 0) sb.append(" ht=\"").append(height).append("\" customHeight=\"1\"");
            sb.append(">");
            open = true;
            return this;
        }

        /** Leaves one row empty. */
        void blank() {
            end();
            r++;
        }

        Sheet text(int col, String text, int style) {
            String ref = COLS.charAt(col) + String.valueOf(r);
            if (text == null || text.isEmpty()) {
                if (style != NORMAL) sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"/>");
                return this;
            }
            sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
            if (style != NORMAL) sb.append(" s=\"").append(style).append("\"");
            sb.append("><is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>");
            return this;
        }

        Sheet num(int col, String number, int style) {
            sb.append("<c r=\"").append(COLS.charAt(col)).append(r).append("\"");
            if (style != NORMAL) sb.append(" s=\"").append(style).append("\"");
            sb.append("><v>").append(number).append("</v></c>");
            return this;
        }

        /** Header row of white-on-navy cells. */
        void header(String... labels) {
            row();
            for (int i = 0; i < labels.length; i++) text(i, labels[i], HEAD);
        }

        void end() {
            if (open) sb.append("</row>");
            open = false;
        }
    }

    static void write(Models.Project p, Models.DailyLog log, String me, long at, OutputStream out) throws IOException {
        Sheet s = new Sheet();
        s.row(24).text(0, log.displayTitle(), TITLE);
        String place = p.name.isEmpty() || p.location.isEmpty() ? p.name + p.location : p.name + " · " + p.location;
        s.row();
        if (!place.isEmpty()) s.text(0, place, META);
        s.row().text(0, "Generated " + Fmt.longStamp(at) + " by " + me, META);
        s.row().text(0, log.doneCount() + "/" + log.tasks.size() + " fields completed", BOLD);
        List<String> people = log.people();
        s.row();
        if (!people.isEmpty()) s.text(0, "Crew: " + Fmt.join(people), META);
        s.blank();
        s.header("Area", "Field", "Status", "Completed by", "Completed at", "Assigned", "Load secured", "Next shift", "Notes", "Photos");
        if (log.tasks.isEmpty()) {
            s.row();
            for (int i = 0; i < 10; i++) s.text(i, i == 1 ? "No fields" : "", CELL);
        }
        for (Map.Entry<String, List<Models.Task>> g : Models.Task.reportGroups(log.tasks)) {
            for (Models.Task t : g.getValue()) {
                s.row();
                s.text(0, g.getKey(), AREA);
                s.text(1, t.title, CELL);
                s.text(2, t.done ? "Done" : "Not done", t.done ? GREEN : RED);
                s.text(3, t.done ? t.completedBy : "", CELL);
                s.text(4, t.done && t.completedAt > 0 ? Fmt.stamp(t.completedAt) : "", CELL);
                s.text(5, Fmt.join(t.assignees), CELL);
                String secured = "";
                if (t.loadSecured) {
                    secured = "Yes";
                    if (!t.securedBy.isEmpty()) secured += ", " + t.securedBy;
                    if (t.securedAt > 0) secured += ", " + Fmt.stamp(t.securedAt);
                }
                if (t.loadSecured && !t.securedPhotos.isEmpty()) {
                    secured += " \u00B7 " + Fmt.plural(t.securedPhotos.size(), "photo", "photos");
                }
                s.text(6, secured, CELL);
                s.text(7, t.nextShift ? "Yes" : "", NUM);
                s.text(8, t.notes.trim(), CELL);
                if (!t.photos.isEmpty()) s.num(9, String.valueOf(t.photos.size()), NUM);
                else if (t.photosRequired) s.text(9, "Required, none", RED);
                else s.text(9, "", NUM);
            }
        }

        List<Models.Extra> extras = log.includedExtras();
        if (!extras.isEmpty()) {
            s.blank();
            s.row().text(0, "Also today", SECTION);
            s.header("Time", "Item", "Details");
            for (Models.Extra e : extras) {
                s.row();
                s.text(0, new java.text.SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date(e.at)), CELL);
                s.text(1, e.label(), CELL);
                s.text(2, Models.Extra.TEMPLATE.equals(e.type) ? "" : e.detail.trim(), CELL);
            }
        }

        Models.Overtime ot = p.overtime;
        if (ot.include && !ot.filled().isEmpty()) {
            s.blank();
            s.row().text(0, "Weekend overtime", SECTION);
            s.header("Name", "Fri", "Sat", "Sun", "Hours");
            for (Models.Overtime.OtRow o : ot.filled()) {
                s.row();
                s.text(0, o.name, CELL);
                String[] days = {o.fri, o.sat, o.sun};
                for (int d = 0; d < 3; d++) s.text(1 + d, days[d], yesNoStyle(days[d]));
                if (o.hours.matches("\\d+")) s.num(4, o.hours, NUM);
                else s.text(4, o.hours, NUM);
            }
            s.row().text(0, "Yes total", BOLD);
            for (int d = 0; d < 3; d++) s.num(1 + d, String.valueOf(ot.yesCount(d)), NUM);
            if (!ot.notes.trim().isEmpty()) s.row().text(0, "Notes: " + ot.notes.trim(), NORMAL);
        }

        Models.Attendance att = p.attendance;
        if (att.include && !att.filled().isEmpty()) {
            s.blank();
            s.row().text(0, "Attendance " + Fmt.longDay(att.date), SECTION);
            s.header("Name", "Here?", "Comment");
            for (Models.Attendance.AttRow a : att.filled()) {
                s.row();
                s.text(0, a.name, CELL);
                s.text(1, a.status, Models.Attendance.AWOL.equals(a.status) ? RED : yesNoStyle(a.status));
                s.text(2, a.comment.trim(), CELL);
            }
            if (!att.summary().isEmpty()) s.row().text(0, att.summary(), BOLD);
            if (!att.notes.trim().isEmpty()) s.row().text(0, "Notes: " + att.notes.trim(), NORMAL);
        }

        List<Models.SeaCanMove> cans = p.filledSeaCans();
        if (p.seaCanInclude && !cans.isEmpty()) {
            s.blank();
            s.row().text(0, "Sea can movements", SECTION);
            s.header("Moves", "Can #", "From location", "Current location", "Weight");
            for (int i = 0; i < cans.size(); i++) {
                Models.SeaCanMove m = cans.get(i);
                s.row();
                s.num(0, String.valueOf(i + 1), NUM);
                s.text(1, m.can.trim(), CELL);
                s.text(2, m.fromLocation().isEmpty() ? "–" : m.fromLocation(), NUM);
                s.text(3, m.toLocation().isEmpty() ? "–" : m.toLocation(), NUM);
                String w = m.weight.trim();
                if (w.matches("\\d+(\\.\\d+)?")) s.num(4, w, NUM);
                else s.text(4, w, NUM);
            }
        }
        s.end();

        StringBuilder cols = new StringBuilder("<cols>");
        for (int i = 0; i < WIDTHS.length; i++) {
            cols.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                    .append(WIDTHS[i]).append("\" customWidth=\"1\"/>");
        }
        cols.append("</cols>");
        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + cols + "<sheetData>" + s.sb + "</sheetData></worksheet>";

        String border = "<border><left style=\"thin\"><color rgb=\"FFD5D9DE\"/></left><right style=\"thin\"><color rgb=\"FFD5D9DE\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFD5D9DE\"/></top><bottom style=\"thin\"><color rgb=\"FFD5D9DE\"/></bottom><diagonal/></border>";
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"8\">"
                + font(false, 11, null) + font(true, 11, null) + font(true, 16, "FF14233A") + font(false, 11, "FF6B7685")
                + font(true, 11, "FFFFFFFF") + font(true, 11, "FF1DB04B") + font(true, 11, "FFD93B3F") + font(true, 13, "FF14233A")
                + "</fonts>"
                + "<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF14233A\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEEF2F7\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" + border + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"11\">"
                + xf(0, 0, 0, null, "top", true)       // 0 normal
                + xf(1, 0, 0, null, null, false)       // 1 bold
                + xf(2, 0, 0, null, null, false)       // 2 title
                + xf(3, 0, 0, null, null, false)       // 3 meta
                + xf(4, 2, 1, null, "center", true)    // 4 table header
                + xf(0, 0, 1, null, "top", true)       // 5 table cell
                + xf(5, 0, 1, "center", "top", false)  // 6 green
                + xf(6, 0, 1, "center", "top", false)  // 7 red
                + xf(7, 0, 0, null, null, false)       // 8 section heading
                + xf(0, 0, 1, "center", "top", false)  // 9 number cell
                + xf(1, 3, 1, null, "top", false)      // 10 area cell
                + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Daily report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/styles.xml", styles);
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        }
    }

    private static int yesNoStyle(String v) {
        return Models.Overtime.YES.equals(v) ? GREEN : Models.Overtime.NO.equals(v) ? RED : NUM;
    }

    private static String font(boolean bold, int size, String color) {
        return "<font>" + (bold ? "<b/>" : "") + "<sz val=\"" + size + "\"/>"
                + (color == null ? "" : "<color rgb=\"" + color + "\"/>") + "<name val=\"Calibri\"/></font>";
    }

    private static String xf(int font, int fill, int border, String horizontal, String vertical, boolean wrap) {
        StringBuilder b = new StringBuilder("<xf numFmtId=\"0\" fontId=\"").append(font).append("\" fillId=\"").append(fill)
                .append("\" borderId=\"").append(border).append("\" xfId=\"0\"");
        if (font != 0) b.append(" applyFont=\"1\"");
        if (fill != 0) b.append(" applyFill=\"1\"");
        if (border != 0) b.append(" applyBorder=\"1\"");
        if (horizontal == null && vertical == null && !wrap) return b.append("/>").toString();
        b.append(" applyAlignment=\"1\"><alignment");
        if (horizontal != null) b.append(" horizontal=\"").append(horizontal).append("\"");
        if (vertical != null) b.append(" vertical=\"").append(vertical).append("\"");
        if (wrap) b.append(" wrapText=\"1\"");
        return b.append("/></xf>").toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
