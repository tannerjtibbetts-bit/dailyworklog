package com.dailyworklog.app;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns a photo's text or a spreadsheet file into rows of cells, so tasks can be picked from them.
 * Every row has the same number of columns; empty cells are "".
 */
final class SheetImport {
    private SheetImport() {}

    /** One line of text found in a photo, with where it sits on the page. */
    static final class Box {
        final String text;
        final int left, top, right, bottom;

        Box(String text, int left, int top, int right, int bottom) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float centerY() {
            return (top + bottom) / 2f;
        }

        int height() {
            return Math.max(1, bottom - top);
        }

        int width() {
            return Math.max(1, right - left);
        }
    }

    // ----- photos -----

    /** Groups lines of text into table rows (by height on the page) and columns (by left edge). */
    static List<List<String>> fromBoxes(List<Box> input) {
        List<Box> boxes = new ArrayList<>();
        for (Box b : input) if (b.text != null && !b.text.trim().isEmpty()) boxes.add(b);
        if (boxes.isEmpty()) return new ArrayList<>();

        List<Integer> heights = new ArrayList<>();
        for (Box b : boxes) heights.add(b.height());
        Collections.sort(heights);
        float h = heights.get(heights.size() / 2);

        // Rows: lines whose middles are within half a line height of each other.
        boxes.sort((a, b) -> Float.compare(a.centerY(), b.centerY()));
        List<List<Box>> rows = new ArrayList<>();
        List<Box> cur = null;
        float curY = 0;
        for (Box b : boxes) {
            if (cur != null && Math.abs(b.centerY() - curY) <= h * 0.5f) {
                cur.add(b);
                curY = (curY * (cur.size() - 1) + b.centerY()) / cur.size();
            } else {
                cur = new ArrayList<>();
                cur.add(b);
                curY = b.centerY();
                rows.add(cur);
            }
        }

        // Pieces of one cell can come back separately; join ones that nearly touch.
        for (int i = 0; i < rows.size(); i++) {
            List<Box> row = rows.get(i);
            row.sort((a, b) -> Integer.compare(a.left, b.left));
            List<Box> merged = new ArrayList<>();
            for (Box b : row) {
                Box last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                if (last != null && b.left - last.right < h * 1.2f) {
                    merged.set(merged.size() - 1, new Box(last.text.trim() + " " + b.text.trim(), last.left,
                            Math.min(last.top, b.top), Math.max(last.right, b.right), Math.max(last.bottom, b.bottom)));
                } else {
                    merged.add(b);
                }
            }
            rows.set(i, merged);
        }

        // Columns: left edges that bunch together, split wherever there's a wide gap.
        List<Integer> lefts = new ArrayList<>();
        for (List<Box> row : rows) for (Box b : row) lefts.add(b.left);
        Collections.sort(lefts);
        List<Float> centers = new ArrayList<>();
        float sum = lefts.get(0);
        int n = 1, prev = lefts.get(0);
        for (int i = 1; i < lefts.size(); i++) {
            int x = lefts.get(i);
            if (x - prev > h * 2f) {
                centers.add(sum / n);
                sum = 0;
                n = 0;
            }
            sum += x;
            n++;
            prev = x;
        }
        centers.add(sum / n);

        List<List<String>> out = new ArrayList<>();
        for (List<Box> row : rows) {
            String[] cells = new String[centers.size()];
            for (Box b : row) {
                int col = nearest(centers, b.left);
                String t = b.text.trim();
                cells[col] = cells[col] == null ? t : cells[col] + " " + t;
            }
            out.add(toRow(cells));
        }
        return tidy(out);
    }

    private static int nearest(List<Float> centers, int x) {
        int best = 0;
        for (int i = 1; i < centers.size(); i++) {
            if (Math.abs(centers.get(i) - x) < Math.abs(centers.get(best) - x)) best = i;
        }
        return best;
    }

    // ----- files -----

    /** Reads an Excel (.xlsx) or CSV file. Old .xls files aren't supported. */
    static List<List<String>> fromFile(InputStream in) throws IOException {
        byte[] data = readAll(in);
        if (data.length >= 2 && data[0] == 'P' && data[1] == 'K') return fromXlsx(data);
        if (data.length >= 4 && (data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF) {
            throw new IOException("old-xls");
        }
        return fromCsv(new String(data, StandardCharsets.UTF_8));
    }

    static List<List<String>> fromCsv(String text) {
        if (text.startsWith("﻿")) text = text.substring(1);
        char sep = guessSeparator(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            rows.add(row);
        }
        return tidy(rows);
    }

    private static char guessSeparator(String text) {
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        int commas = 0, semis = 0, tabs = 0;
        for (char c : first.toCharArray()) {
            if (c == ',') commas++;
            else if (c == ';') semis++;
            else if (c == '\t') tabs++;
        }
        if (tabs > commas && tabs > semis) return '\t';
        return semis > commas ? ';' : ',';
    }

    static List<List<String>> fromXlsx(byte[] data) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals("xl/sharedStrings.xml") || name.equals("xl/workbook.xml")
                        || name.equals("xl/_rels/workbook.xml.rels") || name.startsWith("xl/worksheets/sheet")) {
                    parts.put(name, readAll(zip));
                }
            }
        }
        byte[] sheet = parts.get(firstSheetPath(parts));
        if (sheet == null) throw new IOException("no-sheet");
        List<String> shared = sharedStrings(parts.get("xl/sharedStrings.xml"));
        return tidy(sheetRows(sheet, shared));
    }

    /** The first sheet tab in the workbook (falls back to sheet1.xml). */
    private static String firstSheetPath(Map<String, byte[]> parts) {
        try {
            String rid = null;
            XmlPullParser p = parser(parts.get("xl/workbook.xml"));
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT && rid == null; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG && "sheet".equals(p.getName())) {
                    for (int i = 0; i < p.getAttributeCount(); i++) {
                        if ("id".equals(localName(p.getAttributeName(i)))) rid = p.getAttributeValue(i);
                    }
                }
            }
            if (rid != null) {
                XmlPullParser r = parser(parts.get("xl/_rels/workbook.xml.rels"));
                for (int ev = r.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = r.next()) {
                    if (ev == XmlPullParser.START_TAG && "Relationship".equals(r.getName())
                            && rid.equals(r.getAttributeValue(null, "Id"))) {
                        String target = r.getAttributeValue(null, "Target");
                        if (target.startsWith("/")) return target.substring(1);
                        return "xl/" + target;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to the usual name
        }
        if (parts.containsKey("xl/worksheets/sheet1.xml")) return "xl/worksheets/sheet1.xml";
        for (String k : parts.keySet()) if (k.startsWith("xl/worksheets/sheet")) return k;
        return null;
    }

    private static String localName(String n) {
        int i = n.indexOf(':');
        return i < 0 ? n : n.substring(i + 1);
    }

    private static List<String> sharedStrings(byte[] xml) throws IOException {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        try {
            XmlPullParser p = parser(xml);
            StringBuilder si = null;
            int skip = 0;  // inside phonetic guides, which aren't part of the text
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    if ("si".equals(p.getName())) si = new StringBuilder();
                    else if ("rPh".equals(p.getName())) skip++;
                    else if ("t".equals(p.getName()) && si != null && skip == 0) si.append(p.nextText());
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("rPh".equals(p.getName())) skip--;
                    else if ("si".equals(p.getName()) && si != null) {
                        out.add(si.toString());
                        si = null;
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("bad-strings", e);
        }
        return out;
    }

    private static List<List<String>> sheetRows(byte[] xml, List<String> shared) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try {
            XmlPullParser p = parser(xml);
            List<String> row = null;
            String type = null;
            int col = 0, rowNum = 0;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if ("row".equals(name)) {
                        int r = parseInt(p.getAttributeValue(null, "r"), rowNum + 1);
                        while (rowNum < r - 1) {  // keep blank rows in place
                            rows.add(new ArrayList<>());
                            rowNum++;
                        }
                        rowNum = r;
                        row = new ArrayList<>();
                        rows.add(row);
                        col = 0;
                    } else if ("c".equals(name) && row != null) {
                        String ref = p.getAttributeValue(null, "r");
                        col = ref == null ? row.size() : columnIndex(ref);
                        type = p.getAttributeValue(null, "t");
                    } else if (("v".equals(name) || "t".equals(name)) && row != null) {
                        String v = p.nextText();
                        if ("s".equals(type)) {
                            int i = parseInt(v, -1);
                            v = i >= 0 && i < shared.size() ? shared.get(i) : "";
                        } else if ("b".equals(type)) {
                            v = "1".equals(v) ? "TRUE" : "FALSE";
                        } else if (type == null || "n".equals(type)) {
                            v = number(v);
                        }
                        while (row.size() <= col) row.add("");
                        String old = row.get(col);
                        row.set(col, old.isEmpty() ? v : old + v);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("bad-sheet", e);
        }
        return rows;
    }

    /** "B12" -> 1, "AA3" -> 26. */
    static int columnIndex(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = Character.toUpperCase(ref.charAt(i));
            if (c < 'A' || c > 'Z') break;
            n = n * 26 + (c - 'A' + 1);
        }
        return Math.max(0, n - 1);
    }

    /** Whole numbers without a trailing ".0", so tool numbers read naturally. */
    private static String number(String v) {
        try {
            double d = Double.parseDouble(v);
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        } catch (NumberFormatException ignored) {
            // not a number; show as is
        }
        return v;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static XmlPullParser parser(byte[] xml) throws Exception {
        XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
        p.setInput(new ByteArrayInputStream(xml), "UTF-8");
        return p;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ----- shared -----

    private static List<String> toRow(String[] cells) {
        List<String> r = new ArrayList<>();
        for (String c : cells) r.add(c == null ? "" : c);
        return r;
    }

    /** Drops empty rows and columns and pads every row to the same width. */
    static List<List<String>> tidy(List<List<String>> rows) {
        int width = 0;
        for (List<String> r : rows) width = Math.max(width, r.size());
        boolean[] used = new boolean[width];
        List<List<String>> kept = new ArrayList<>();
        for (List<String> r : rows) {
            boolean any = false;
            for (int i = 0; i < r.size(); i++) {
                String c = r.get(i) == null ? "" : r.get(i).trim();
                if (!c.isEmpty()) {
                    used[i] = true;
                    any = true;
                }
            }
            if (any) kept.add(r);
        }
        List<List<String>> out = new ArrayList<>();
        for (List<String> r : kept) {
            List<String> row = new ArrayList<>();
            for (int i = 0; i < width; i++) {
                if (!used[i]) continue;
                String c = i < r.size() && r.get(i) != null ? r.get(i).trim() : "";
                row.add(c);
            }
            out.add(row);
        }
        return out;
    }

    /** The column with the most words in it: usually the task descriptions. */
    static int likelyTaskColumn(List<List<String>> rows) {
        if (rows.isEmpty()) return 0;
        int width = rows.get(0).size();
        int best = 0, bestScore = -1;
        for (int c = 0; c < width; c++) {
            int score = 0;
            for (List<String> r : rows) {
                String s = r.get(c);
                for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /**
     * Merges the rows at pick (ascending) into one, kept where the first one was. The task column
     * becomes text; other columns join the rows' distinct values with spaces.
     */
    static void combineRows(List<List<String>> rows, List<Integer> pick, int taskColumn, String text) {
        int first = pick.get(0);
        List<String> merged = new ArrayList<>(rows.get(first));
        for (int c = 0; c < merged.size(); c++) {
            if (c == taskColumn) {
                merged.set(c, text);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i : pick) {
                String v = rows.get(i).get(c).trim();
                if (v.isEmpty() || sb.indexOf(v) >= 0) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(v);
            }
            merged.set(c, sb.toString());
        }
        List<List<String>> kept = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (i != first && pick.contains(i)) continue;
            kept.add(i == first ? merged : rows.get(i));
        }
        rows.clear();
        rows.addAll(kept);
    }

    static boolean hasLetters(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }
}
