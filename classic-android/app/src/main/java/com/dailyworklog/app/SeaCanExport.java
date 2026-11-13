package com.dailyworklog.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The Sea Can Movements tab as an Excel (.xlsx) file laid out like the site's movement sheet
 * (Moves, Can #, From / To Location, Weight, then the Blue Bin columns), or as a CSV file.
 * The Blue Bin columns are left empty, ready to fill in or paste into.
 */
final class SeaCanExport {
    private SeaCanExport() {}

    static final String[] COLUMNS = {"Moves", "Can #", "From Location", "To Location", "Weight", "Bin #", "From Location", "To Location"};
    /** Empty numbered rows after the sea cans, for adding by hand. */
    static final int SPARE_ROWS = 5;

    /** Cell styles in styles.xml: 0 normal, 1 title/heading (peach), 2 divider (near black), 3 data, 4 move number. */
    private static final int HEAD = 1, DIVIDER = 2, DATA = 3, MOVE = 4;
    private static final String COLS = "ABCDEFGH";

    static void writeXlsx(Models.Project p, String by, long at, OutputStream out) throws IOException {
        StringBuilder rows = new StringBuilder();
        // Row 1: section titles; row 2: headings; row 3: dark divider bar
        rows.append("<row r=\"1\" ht=\"24\" customHeight=\"1\">");
        cell(rows, "A1", "Sea Can Movements", HEAD);
        for (char c : "BCDE".toCharArray()) blank(rows, c + "1", HEAD);
        cell(rows, "F1", "Blue Bin Movements", HEAD);
        blank(rows, "G1", HEAD);
        blank(rows, "H1", HEAD);
        rows.append("</row><row r=\"2\" ht=\"42\" customHeight=\"1\">");
        for (int i = 0; i < 8; i++) cell(rows, COLS.charAt(i) + "2", COLUMNS[i], HEAD);
        rows.append("</row><row r=\"3\" ht=\"12\" customHeight=\"1\">");
        for (int i = 0; i < 8; i++) blank(rows, COLS.charAt(i) + "3", DIVIDER);
        rows.append("</row>");
        List<Models.SeaCanMove> filled = p.filledSeaCans();
        int total = filled.size() + SPARE_ROWS;
        for (int n = 1; n <= total; n++) {
            int r = n + 3;
            rows.append("<row r=\"").append(r).append("\" ht=\"22\" customHeight=\"1\">");
            rows.append("<c r=\"A").append(r).append("\" s=\"").append(MOVE).append("\"><v>").append(n).append("</v></c>");
            Models.SeaCanMove m = n <= filled.size() ? filled.get(n - 1) : null;
            String[] v = m == null ? new String[]{"", "", ""} : new String[]{m.can, m.fromLocation(), m.toLocation()};
            for (int i = 0; i < 3; i++) {
                if (v[i].isEmpty()) blank(rows, COLS.charAt(i + 1) + "" + r, DATA);
                else cell(rows, COLS.charAt(i + 1) + "" + r, v[i], DATA);
            }
            String w = m == null ? "" : m.weight.trim();
            if (w.matches("\\d+(\\.\\d+)?")) {
                rows.append("<c r=\"E").append(r).append("\" s=\"").append(DATA).append("\"><v>").append(w).append("</v></c>");
            } else if (!w.isEmpty()) {
                cell(rows, "E" + r, w, DATA);
            } else {
                blank(rows, "E" + r, DATA);
            }
            for (int i = 5; i < 8; i++) blank(rows, COLS.charAt(i) + "" + r, DATA);
            rows.append("</row>");
        }

        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<cols>"
                + col(1, 16) + col(2, 20) + col(3, 20) + col(4, 18) + col(5, 11) + col(6, 12) + col(7, 20) + col(8, 18)
                + "</cols>"
                + "<sheetData>" + rows + "</sheetData>"
                + "<mergeCells count=\"2\"><mergeCell ref=\"A1:E1\"/><mergeCell ref=\"F1:H1\"/></mergeCells>"
                + "</worksheet>";

        String thin = "<left style=\"thin\"><color rgb=\"FF000000\"/></left><right style=\"thin\"><color rgb=\"FF000000\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FF000000\"/></top><bottom style=\"thin\"><color rgb=\"FF000000\"/></bottom><diagonal/>";
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
                    + "<sheets><sheet name=\"Sea Can Movements\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"4\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"14\"/><name val=\"Times New Roman\"/></font>"
                    + "<font><b/><sz val=\"12\"/><name val=\"Times New Roman\"/></font>"
                    + "<font><sz val=\"12\"/><name val=\"Times New Roman\"/></font></fonts>"
                    + "<fills count=\"5\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF8CBAD\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF262626\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCE6F1\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                    + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border>" + thin + "</border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"5\">"
                    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                    + "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                    + "<alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                    + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                    + "<alignment horizontal=\"right\" vertical=\"center\"/></xf>"
                    + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        }
    }

    /**
     * CSV with the same columns as the Excel sheet, one line per sea can. Commas, quotes and line
     * breaks are quoted, and it starts with a byte-order mark so Excel reads it as UTF-8.
     */
    static void writeCsv(Models.Project p, OutputStream out) throws IOException {
        StringBuilder b = new StringBuilder("﻿");
        csvLine(b, COLUMNS);
        List<Models.SeaCanMove> filled = p.filledSeaCans();
        for (int i = 0; i < filled.size(); i++) {
            Models.SeaCanMove m = filled.get(i);
            csvLine(b, new String[]{String.valueOf(i + 1), m.can, m.fromLocation(), m.toLocation(), m.weight.trim(), "", "", ""});
        }
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    static void csvLine(StringBuilder b, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            String c = cells[i] == null ? "" : cells[i];
            if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r")) {
                b.append('"').append(c.replace("\"", "\"\"")).append('"');
            } else {
                b.append(c);
            }
        }
        b.append("\r\n");
    }

    private static String col(int n, int width) {
        return "<col min=\"" + n + "\" max=\"" + n + "\" width=\"" + width + "\" customWidth=\"1\"/>";
    }

    private static void cell(StringBuilder sb, String ref, String text, int style) {
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(style)
                .append("\"><is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>");
    }

    private static void blank(StringBuilder sb, String ref, int style) {
        sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"/>");
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
