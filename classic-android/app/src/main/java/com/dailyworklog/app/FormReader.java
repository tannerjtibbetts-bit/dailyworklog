package com.dailyworklog.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns the text lines of a blank form (from a photo, PDF, Word or Excel file) into questions and signature
 * lines. Plain Java, the same rules as the new app's form reader.
 */
final class FormReader {
    private FormReader() {}

    /** Stands for a fill-in blank (a run of ___, ..., … or ---). */
    static final char BLANK = '\u2423';

    static final class Result {
        final List<Models.FormQuestion> questions = new ArrayList<>();
        final List<String> signatures = new ArrayList<>();
        /** The rating scale found on the form, in order. */
        final List<Models.Choice> scale = new ArrayList<>();
        /** Answer text height as a fraction of the page height (from the line boxes; 0.014 when there are none). */
        double textSize = DEFAULT_TEXT_SIZE;
    }

    static final double DEFAULT_TEXT_SIZE = 0.014;

    /** A line of text, with where it sits on its page picture (fractions 0..1) when it was read from a picture. */
    static final class Line {
        final String text;
        final int page;
        final double left, top, right, bottom;
        final boolean hasBox;

        Line(String text) {
            this(text, 0, 0, 0, 0, 0, false);
        }

        Line(String text, int page, double left, double top, double right, double bottom) {
            this(text, page, left, top, right, bottom, true);
        }

        private Line(String text, int page, double left, double top, double right, double bottom, boolean hasBox) {
            this.text = text;
            this.page = page;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.hasBox = hasBox;
        }
    }

    private static final Pattern BLANKS = Pattern.compile("_{3,}|\\.{3,}|…|-{3,}");
    private static final Pattern SCALE = Pattern.compile(
            "^(excellent|very good|good|above average|average plus|average|average minus|below average|fair|satisfactory"
                    + "|needs improvement|unsatisfactory|poor)\\s*[:=-]?\\s*(\\d+(\\.\\d+)?)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCALE_HEADING = Pattern.compile("^(point system|rating scale|scale|rating|ratings)\\s*:?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION = Pattern.compile("^[A-H][.)]\\s+");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,2})[.)]\\s+(.+)$");
    private static final Pattern CHUNK = Pattern.compile("([^:]+):");
    private static final String[] LONG_WORDS = {"comment", "work done", "describe", "description", "details", "explain",
            "remarks", "notes", "incident", "reason", "corrective action", "action plan", "improvement plan", "what happened"};

    /** One question found, before its kind is settled. */
    private static final class Found {
        String label;
        String kind;  // "numbered", "long", "average" or "text"
        boolean section;
        /** Index of its input line, and (for several on one line) the character just after its label. */
        int line;
        int endChar = -1;

        Found(String label, String kind, boolean section, int line) {
            this.label = label;
            this.kind = kind;
            this.section = section;
            this.line = line;
        }
    }

    /** Trims, turns runs of ___ / ... / … / --- into the blank marker, and collapses spaces. */
    static String normalize(String line) {
        String s = BLANKS.matcher(line.trim()).replaceAll(" " + BLANK + " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static int letters(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) n++;
        return n;
    }

    private static String withoutBlanks(String s) {
        return s.replace(String.valueOf(BLANK), " ").replaceAll("\\s+", " ").trim();
    }

    /** Trim, drop trailing ":" and blanks, collapse spaces; all-capitals labels become sentence case. */
    static String cleanLabel(String s) {
        String l = withoutBlanks(s);
        while (l.endsWith(":") || l.endsWith(String.valueOf(BLANK))) l = l.substring(0, l.length() - 1).trim();
        l = l.replaceAll("\\s+", " ").trim();
        if (!l.equals(l.toUpperCase(Locale.ROOT)) || l.isEmpty()) return l;
        String lower = l.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    /** Lines without positions (Word, Excel, tests). */
    static Result read(List<String> lines) {
        List<Line> out = new ArrayList<>();
        for (String l : lines) out.add(new Line(l == null ? "" : l));
        return readLines(out);
    }

    static Result readLines(List<Line> lines) {
        Result r = new Result();
        List<Found> found = new ArrayList<>();
        String[] normalized = new String[lines.size()];
        boolean haveAverage = false;
        for (int li = 0; li < lines.size(); li++) {
            String raw = lines.get(li).text;
            if (raw == null) continue;
            String line = normalize(raw);
            normalized[li] = line;
            if (letters(line) < 2) continue;
            if (line.startsWith("(") && line.endsWith(")")) continue;
            String lower = line.toLowerCase(Locale.ROOT);

            // 1. Signature lines
            if (lower.contains("signature")) {
                List<Integer> cuts = new ArrayList<>();
                for (int i = lower.indexOf("signature", 1); i > 0; i = lower.indexOf("signature", i + 1)) cuts.add(i);
                int from = 0;
                cuts.add(line.length());
                for (int cut : cuts) {
                    String piece = withoutBlanks(line.substring(from, cut));
                    if (!piece.isEmpty() && !containsIgnoreCase(r.signatures, piece)) r.signatures.add(piece);
                    from = cut;
                }
                continue;
            }
            // 2. The rating scale
            String plain = withoutBlanks(line);
            if (SCALE_HEADING.matcher(plain).matches()) continue;
            Matcher sm = SCALE.matcher(plain);
            if (sm.matches()) {
                String label = sm.group(1).trim();
                boolean dup = false;
                for (Models.Choice c : r.scale) if (c.label.equalsIgnoreCase(label)) dup = true;
                if (!dup) r.scale.add(new Models.Choice(label, sm.group(2) == null ? "" : sm.group(2)));
                continue;
            }
            // 3. Section letter ("A. ", "B) ")
            boolean section = false;
            int offset = 0;
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                section = true;
                offset = sec.end();
                line = line.substring(sec.end());
                lower = line.toLowerCase(Locale.ROOT);
            }
            // 4. Numbered items
            Matcher num = NUMBERED.matcher(line);
            if (num.matches()) {
                found.add(new Found(num.group(2), "numbered", section, li));
                continue;
            }
            // 5. The overall average
            if (lower.contains("average") && (lower.contains("of above") || lower.contains("total") || lower.endsWith(":"))) {
                if (!haveAverage) found.add(new Found("Overall average", Models.FormQuestion.AVERAGE, false, li));
                haveAverage = true;
                continue;
            }
            // 6. Labels with colons or blanks
            boolean hasBlank = line.indexOf(BLANK) >= 0;
            if (line.contains(":") || hasBlank) {
                int segStart = 0;
                while (segStart <= line.length()) {
                    int segEnd = line.indexOf(BLANK, segStart);
                    boolean followedByBlank = segEnd >= 0;
                    if (segEnd < 0) segEnd = line.length();
                    String raw6 = line.substring(segStart, segEnd);
                    String s = raw6.trim();
                    int sAt = segStart + raw6.indexOf(s.isEmpty() ? "" : s.substring(0, 1));
                    if (!s.isEmpty()) {
                        if (s.contains(":")) {
                            Matcher ch = CHUNK.matcher(s);
                            List<int[]> chunks = new ArrayList<>();
                            while (ch.find()) chunks.add(new int[]{ch.start(1), ch.end(1), ch.end()});
                            for (int k = 0; k < chunks.size(); k++) {
                                int[] c = chunks.get(k);
                                int nextStart = k + 1 < chunks.size() ? chunks.get(k + 1)[0] : s.length();
                                String after = s.substring(c[2], nextStart).trim();
                                if (after.isEmpty()) {
                                    Found f = new Found(s.substring(c[0], c[1]), "text", section, li);
                                    f.endChar = offset + sAt + c[2];
                                    found.add(f);
                                }
                            }
                        } else if (followedByBlank) {
                            Found f = new Found(s, "text", section, li);
                            f.endChar = offset + sAt + s.length();
                            found.add(f);
                        }
                    }
                    if (segEnd >= line.length()) break;
                    segStart = segEnd + 1;
                }
                continue;
            }
            line = line.trim();
            // 7. ALL-CAPS labels ("EMPLOYER", "EMPLOYEE #")
            if (line.equals(line.toUpperCase(Locale.ROOT)) && line.length() >= 2 && line.length() <= 30
                    && line.split(" ").length <= 4) {
                found.add(new Found(line, "text", section, li));
                continue;
            }
            // 8. Questions
            if (line.endsWith("?") && line.length() <= 120) found.add(new Found(line, "text", section, li));
        }

        // Labels, "To" after "... from", duplicates, then kinds.
        List<String> labels = new ArrayList<>();
        List<Found> kept = new ArrayList<>();
        for (Found f : found) {
            String label = f.kind.equals(Models.FormQuestion.AVERAGE) ? f.label : cleanLabel(f.label);
            if (label.isEmpty()) continue;
            if (label.equalsIgnoreCase("to") && !labels.isEmpty()) {
                String prev = labels.get(labels.size() - 1);
                if (prev.toLowerCase(Locale.ROOT).endsWith(" from")) label = prev.substring(0, prev.length() - 4) + "to";
            }
            if (containsIgnoreCase(labels, label)) continue;
            labels.add(label);
            String kind;
            if (f.kind.equals("numbered")) {
                kind = r.scale.size() >= 2 ? Models.FormQuestion.RATING : Models.FormQuestion.TEXT;
            } else if (f.kind.equals(Models.FormQuestion.AVERAGE)) {
                kind = Models.FormQuestion.AVERAGE;
            } else if (f.section || isLong(label)) {
                kind = Models.FormQuestion.LONG;
            } else {
                kind = Models.FormQuestion.TEXT;
            }
            Models.FormQuestion q = new Models.FormQuestion("q" + (r.questions.size() + 1), label, kind);
            if (kind.equals(Models.FormQuestion.RATING)) {
                for (Models.Choice c : r.scale) q.choices.add(new Models.Choice(c.label, c.value));
            }
            r.questions.add(q);
            kept.add(f);
        }
        place(r, kept, lines, normalized);
        return r;
    }

    /** Where each answer goes on the page picture, from its line's box (only for lines read from a picture). */
    private static void place(Result r, List<Found> kept, List<Line> lines, String[] normalized) {
        double ratingRight = -1;
        for (int i = 0; i < kept.size(); i++) {
            Line l = lines.get(kept.get(i).line);
            if (l.hasBox && Models.FormQuestion.RATING.equals(r.questions.get(i).kind)) ratingRight = Math.max(ratingRight, l.right);
        }
        double ratingX = ratingRight < 0 ? -1 : ratingRight + 0.06;
        List<Double> heights = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            Found f = kept.get(i);
            Line l = lines.get(f.line);
            if (!l.hasBox) continue;
            Models.FormQuestion q = r.questions.get(i);
            double h = l.bottom - l.top;
            heights.add(h);
            int onLine = 0;
            for (Found o : kept) if (o.line == f.line) onLine++;
            double labelEnd = l.right;
            String norm = normalized[f.line];
            if (onLine > 1 && f.endChar >= 0 && norm != null && !norm.isEmpty()) {
                labelEnd = l.left + (l.right - l.left) * f.endChar / norm.length();
            }
            double x, y;
            switch (q.kind) {
                case Models.FormQuestion.LONG:
                    x = l.left;
                    y = l.bottom + 1.1 * h;
                    break;
                case Models.FormQuestion.RATING:
                    x = ratingX;
                    y = l.bottom;
                    break;
                case Models.FormQuestion.AVERAGE:
                    // Right of the "(total ...)" line when it follows (else of the average line), and in the rating column.
                    int paren = nextParenthesis(normalized, f.line);
                    Line pl = paren >= 0 ? lines.get(paren) : null;
                    double right = pl != null && pl.hasBox ? pl.right : l.right;
                    x = Math.max(ratingX, right + 0.015);
                    y = paren >= 0 ? l.bottom + 1.2 * h : l.bottom;
                    break;
                default:
                    x = labelEnd + 0.012;
                    y = l.bottom;
            }
            q.place = new Models.Place(l.page, clamp(x), clamp(y));
        }
        if (!heights.isEmpty()) {
            java.util.Collections.sort(heights);
            int n = heights.size();
            double median = n % 2 == 1 ? heights.get(n / 2) : (heights.get(n / 2 - 1) + heights.get(n / 2)) / 2;
            r.textSize = Math.max(0.008, Math.min(0.03, median * 0.75));
        }
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** The next line with any text, if it's wholly in parentheses ("(total #1 - #10 ...)"); else -1. */
    private static int nextParenthesis(String[] normalized, int at) {
        for (int i = at + 1; i < normalized.length; i++) {
            String s = normalized[i];
            if (s == null || s.isEmpty()) continue;
            return s.startsWith("(") && s.endsWith(")") ? i : -1;
        }
        return -1;
    }

    static boolean isLong(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        for (String w : LONG_WORDS) if (l.contains(w)) return true;
        return false;
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String x : list) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    // ----- text from Word and Excel files -----

    private static String entry(ZipFile z, String name) throws IOException {
        ZipEntry e = z.getEntry(name);
        if (e == null) return null;
        try (InputStream in = z.getInputStream(e)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Backup.copy(in, b);
            return b.toString("UTF-8");
        }
    }

    static String unescape(String s) {
        Matcher m = Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String rep;
            switch (e) {
                case "amp": rep = "&"; break;
                case "lt": rep = "<"; break;
                case "gt": rep = ">"; break;
                case "quot": rep = "\""; break;
                case "apos": rep = "'"; break;
                default:
                    int cp = e.startsWith("#x") || e.startsWith("#X") ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                    rep = new String(Character.toChars(cp));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern PARA = Pattern.compile("<w:p(?:\\s[^>]*)?>(.*?)</w:p>|<w:p(?:\\s[^>]*)?/>", Pattern.DOTALL);
    private static final Pattern PPR = Pattern.compile("<w:pPr>.*?</w:pPr>", Pattern.DOTALL);
    private static final Pattern RUN_BITS = Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab(?:\\s[^>]*)?/>|<w:(?:br|cr)(?:\\s[^>]*)?/>");

    /** Word: each paragraph (table cells too) is a line; tabs are 4 spaces and breaks start a new line. */
    static List<String> docxLines(File f) throws IOException {
        try (ZipFile z = new ZipFile(f)) {
            String xml = entry(z, "word/document.xml");
            if (xml == null) throw new IOException("not a Word file");
            return docxLines(xml);
        }
    }

    static List<String> docxLines(String xml) {
        List<String> out = new ArrayList<>();
        Matcher p = PARA.matcher(xml);
        while (p.find()) {
            if (p.group(1) == null) continue;  // an empty <w:p/>
            String body = PPR.matcher(p.group(1)).replaceAll("");
            StringBuilder line = new StringBuilder();
            Matcher m = RUN_BITS.matcher(body);
            while (m.find()) {
                String tag = m.group();
                if (m.group(1) != null) line.append(unescape(m.group(1)));
                else if (tag.startsWith("<w:tab")) line.append("    ");
                else line.append('\n');
            }
            for (String l : line.toString().split("\n")) if (!l.trim().isEmpty()) out.add(l);
        }
        return out;
    }

    private static final Pattern SI = Pattern.compile("<si>(.*?)</si>|<si/>", Pattern.DOTALL);
    private static final Pattern T = Pattern.compile("<t(?:\\s[^>]*)?>([^<]*)</t>");
    private static final Pattern PHONETIC = Pattern.compile("<rPh\\b.*?</rPh>", Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("<row\\b[^>]*>(.*?)</row>", Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", Pattern.DOTALL);
    private static final Pattern V = Pattern.compile("<v>([^<]*)</v>");

    private static String allT(String xml) {
        StringBuilder b = new StringBuilder();
        Matcher m = T.matcher(PHONETIC.matcher(xml).replaceAll(""));
        while (m.find()) b.append(unescape(m.group(1)));
        return b.toString();
    }

    /** Excel: each non-empty text cell is a line, row by row, left to right, sheet after sheet. */
    static List<String> xlsxLines(File f) throws IOException {
        try (ZipFile z = new ZipFile(f)) {
            List<String> shared = new ArrayList<>();
            String ss = entry(z, "xl/sharedStrings.xml");
            if (ss != null) {
                Matcher m = SI.matcher(ss);
                while (m.find()) shared.add(m.group(1) == null ? "" : allT(m.group(1)));
            }
            TreeMap<Integer, String> sheets = new TreeMap<>();
            for (java.util.Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                String n = en.nextElement().getName();
                Matcher m = Pattern.compile("^xl/worksheets/sheet(\\d+)\\.xml$").matcher(n);
                if (m.matches()) sheets.put(Integer.parseInt(m.group(1)), n);
            }
            if (sheets.isEmpty()) throw new IOException("not an Excel file");
            List<String> out = new ArrayList<>();
            for (String name : sheets.values()) out.addAll(xlsxSheetLines(entry(z, name), shared));
            return out;
        }
    }

    static List<String> xlsxSheetLines(String xml, List<String> shared) {
        List<String> out = new ArrayList<>();
        Matcher row = ROW.matcher(xml);
        while (row.find()) {
            Matcher c = CELL.matcher(row.group(1));
            while (c.find()) {
                String attrs = c.group(1), body = c.group(2) == null ? "" : c.group(2);
                Matcher t = Pattern.compile("\\bt=\"([^\"]*)\"").matcher(attrs);
                String type = t.find() ? t.group(1) : "";
                String text = "";
                if (type.equals("s")) {
                    Matcher v = V.matcher(body);
                    if (v.find()) {
                        try {
                            int i = Integer.parseInt(v.group(1).trim());
                            if (i >= 0 && i < shared.size()) text = shared.get(i);
                        } catch (NumberFormatException ignored) {
                            // not an index
                        }
                    }
                } else if (type.equals("inlineStr")) {
                    text = allT(body);
                } else if (type.equals("str")) {
                    Matcher v = V.matcher(body);
                    if (v.find()) text = unescape(v.group(1));
                }
                if (!text.trim().isEmpty()) out.add(text.trim());
            }
        }
        return out;
    }

    /** The default signature lines when the form had none. */
    static List<String> defaultSignatures(String group) {
        List<String> out = new ArrayList<>();
        out.add("Supervisor signature");
        if (Models.ReportForm.DISCIPLINE.equals(group)) {
            out.add("Employee signature");
            out.add("Witness signature");
        } else {
            out.add("Apprentice signature");
        }
        return out;
    }

    /** Lines from text read off a form, e.g. "A / B / C" joined with line breaks (used by tests). */
    static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        for (String l : text.split("\n")) out.add(l);
        return out;
    }
}
