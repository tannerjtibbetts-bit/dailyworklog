package com.dailyworklog.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** The Sea Can Movements tab as an Excel (.xlsx) file or a CSV file. */
final class SeaCanExport {
    private SeaCanExport() {}

    /** Cell styles in styles.xml: 0 normal, 1 bold, 2 bold heading row (grey fill). */
    private static final int NORMAL = 0, BOLD = 1, HEAD = 2;
    static final String[] COLUMNS = {"Sea can", "From row", "From level", "Current row", "Current level"};

    static void writeXlsx(Models.Project p, String by, long at, OutputStream out) throws IOException {
        StringBuilder rows = new StringBuilder();
        int[] r = {0};
        row(rows, r, new String[]{"Sea can movements" + (p.name.isEmpty() ? "" : " - " + p.name)}, BOLD);
        row(rows, r, new String[]{"Exported " + Fmt.longStamp(at) + " by " + by}, NORMAL);
        r[0]++;
        row(rows, r, COLUMNS, HEAD);
        List<Models.SeaCanMove> filled = p.filledSeaCans();
        if (filled.isEmpty()) row(rows, r, new String[]{"No sea cans entered yet."}, NORMAL);
        for (Models.SeaCanMove m : filled) {
            r[0]++;
            rows.append("<row r=\"").append(r[0]).append("\">");
            cell(rows, "A", r[0], m.can, NORMAL);
            String[] v = {m.fromRow, m.fromLevel, m.toRow, m.toLevel};
            String[] cols = {"B", "C", "D", "E"};
            for (int i = 0; i < 4; i++) {
                // Picked rows and levels are numbers, so they sort and filter as numbers.
                if (v[i].matches("\\d+")) rows.append("<c r=\"").append(cols[i]).append(r[0]).append("\"><v>").append(v[i]).append("</v></c>");
            }
            rows.append("</row>");
        }

        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<cols><col min=\"1\" max=\"1\" width=\"24\" customWidth=\"1\"/>"
                + "<col min=\"2\" max=\"5\" width=\"14\" customWidth=\"1\"/></cols>"
                + "<sheetData>" + rows + "</sheetData></worksheet>";

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
                    + "<sheets><sheet name=\"Sea cans\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"4\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><color rgb=\"FF148A3A\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><color rgb=\"FFC9353A\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE4E7EC\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"5\">"
                    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                    + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        }
    }

    /**
     * CSV: one line per sea can, with a heading line. Commas, quotes and line breaks are quoted,
     * and it starts with a byte-order mark so Excel reads it as UTF-8.
     */
    static void writeCsv(Models.Project p, OutputStream out) throws IOException {
        StringBuilder b = new StringBuilder("\uFEFF");
        csvLine(b, COLUMNS);
        for (Models.SeaCanMove m : p.filledSeaCans()) {
            csvLine(b, new String[]{m.can, m.fromRow, m.fromLevel, m.toRow, m.toLevel});
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

    /** A row of text cells starting at column A. */
    private static void row(StringBuilder sb, int[] r, String[] cells, int style) {
        r[0]++;
        sb.append("<row r=\"").append(r[0]).append("\">");
        for (int i = 0; i < cells.length; i++) cell(sb, String.valueOf((char) ('A' + i)), r[0], cells[i], style);
        sb.append("</row>");
    }

    private static void cell(StringBuilder sb, String col, int row, String text, int style) {
        sb.append("<c r=\"").append(col).append(row).append("\" t=\"inlineStr\"");
        if (style != NORMAL) sb.append(" s=\"").append(style).append("\"");
        sb.append("><is><t xml:space=\"preserve\">").append(esc(text == null ? "" : text)).append("</t></is></c>");
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
