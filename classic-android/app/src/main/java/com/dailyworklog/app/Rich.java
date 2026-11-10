package com.dailyworklog.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatting for notes: bold, underline and colour on chosen words, kept as "runs" next to the
 * plain text, plus the automatic highlighting of "Delay" (red, bold) and "on hold" (bold, yellow
 * highlight). The same rules as the new app's rich.dart.
 */
final class Rich {
    private Rich() {}

    /** Colours a person can give their typing ("" is the normal black). */
    static final List<String> COLOURS = Collections.unmodifiableList(Arrays.asList("", "yellow", "orange", "red"));

    /** Screen / report colours (RGB, no alpha). */
    static final int YELLOW = 0xC99A00, ORANGE = 0xE8730C, RED = 0xD93B3F, HIGHLIGHT = 0xFFE45C, BLACK = 0x111827, GREEN = 0x148A3A;

    /** RGB of a typing colour, or -1 for "" (normal text colour). */
    static int rgb(String colour) {
        switch (colour) {
            case "yellow": return YELLOW;
            case "orange": return ORANGE;
            case "red": return RED;
            case "green": return GREEN;  // not a user colour: only for report text like "Delay cleared"
            default: return -1;
        }
    }

    /** Hex like "D93B3F" for a typing colour, or "" for black. */
    static String hex(String colour) {
        int c = rgb(colour);
        return c < 0 ? "" : String.format("%06X", c);
    }

    static final class Style {
        final boolean bold, underline, highlight;
        /** "", "yellow", "orange" or "red". */
        final String colour;

        static final Style PLAIN = new Style(false, false, "", false);

        Style(boolean bold, boolean underline, String colour, boolean highlight) {
            this.bold = bold;
            this.underline = underline;
            this.colour = colour == null ? "" : colour;
            this.highlight = highlight;
        }

        boolean isPlain() {
            return !bold && !underline && colour.isEmpty() && !highlight;
        }

        Style withBold(boolean b) { return new Style(b, underline, colour, highlight); }
        Style withUnderline(boolean u) { return new Style(bold, u, colour, highlight); }
        Style withColour(String c) { return new Style(bold, underline, c, highlight); }
        Style withHighlight(boolean h) { return new Style(bold, underline, colour, h); }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Style)) return false;
            Style s = (Style) o;
            return s.bold == bold && s.underline == underline && s.colour.equals(colour) && s.highlight == highlight;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bold, underline, colour, highlight);
        }

        @Override
        public String toString() {
            List<String> p = new ArrayList<>();
            if (bold) p.add("b");
            if (underline) p.add("u");
            if (!colour.isEmpty()) p.add(colour);
            if (highlight) p.add("hl");
            return String.join(",", p);
        }
    }

    /** User formatting on [start, end). */
    static final class Run {
        final int start, end;
        final Style style;

        Run(int start, int end, Style style) {
            this.start = start;
            this.end = end;
            this.style = style;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("s", start);
            o.put("e", end);
            if (style.bold) o.put("b", true);
            if (style.underline) o.put("u", true);
            if (!style.colour.isEmpty()) o.put("c", style.colour);
            return o;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Run)) return false;
            Run r = (Run) o;
            return r.start == start && r.end == end && r.style.equals(style);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end, style);
        }

        @Override
        public String toString() {
            return "{" + start + "," + end + "," + style + "}";
        }
    }

    /** A piece of text in one style. */
    static final class Segment {
        final String text;
        final int start;
        final Style style;

        Segment(String text, int start, Style style) {
            this.text = text;
            this.start = start;
            this.style = style;
        }

        int end() {
            return start + text.length();
        }

        @Override
        public String toString() {
            return "\"" + text + "\" " + style;
        }
    }

    private static final Pattern DELAY = Pattern.compile("\\bdelay[a-z]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_HOLD = Pattern.compile("\\bon\\s+hold\\b", Pattern.CASE_INSENSITIVE);

    /** Runs from worklog.json; anything invalid is dropped, the rest clipped to length and normalized. */
    static List<Run> fromJson(JSONArray list, int length) {
        List<Run> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            Object s = o.opt("s"), e = o.opt("e");
            if (!(s instanceof Integer || s instanceof Long) || !(e instanceof Integer || e instanceof Long)) continue;
            Object cv = o.opt("c");
            String c = cv == null || cv == JSONObject.NULL ? "" : cv.toString();
            if (!COLOURS.contains(c)) continue;
            long sl = ((Number) s).longValue(), el = ((Number) e).longValue();
            int start = (int) Math.max(0, Math.min(sl, Integer.MAX_VALUE));
            int end = (int) Math.min(el, length);
            if (start >= end) continue;
            out.add(new Run(start, end, new Style(Boolean.TRUE.equals(o.opt("b")), Boolean.TRUE.equals(o.opt("u")), c, false)));
        }
        out.sort((a, b) -> Integer.compare(a.start, b.start));
        // Overlaps: a later run is cut so it starts after the one before.
        List<Run> fixed = new ArrayList<>();
        for (Run r : out) {
            int from = fixed.isEmpty() ? r.start : Math.max(r.start, fixed.get(fixed.size() - 1).end);
            if (from < r.end) fixed.add(new Run(from, r.end, r.style));
        }
        return fromStyles(styles(length, fixed));
    }

    static JSONArray toJson(List<Run> runs) throws JSONException {
        JSONArray a = new JSONArray();
        for (Run r : runs) a.put(r.toJson());
        return a;
    }

    /** Each character's user style. */
    private static Style[] styles(int length, List<Run> runs) {
        Style[] s = new Style[length];
        Arrays.fill(s, Style.PLAIN);
        for (Run r : runs) {
            for (int i = r.start; i < r.end && i < length; i++) {
                if (i >= 0) s[i] = r.style;
            }
        }
        return s;
    }

    /** Normalized runs from per-character styles (highlight is never stored). */
    private static List<Run> fromStyles(Style[] styles) {
        List<Run> out = new ArrayList<>();
        int i = 0;
        while (i < styles.length) {
            Style st = styles[i].withHighlight(false);
            int j = i + 1;
            while (j < styles.length && styles[j].withHighlight(false).equals(st)) j++;
            if (!st.isPlain()) out.add(new Run(i, j, st));
            i = j;
        }
        return out;
    }

    /** The text in pieces of one style each: user formatting, plus Delay / on hold when keywords. */
    static List<Segment> segments(String text, List<Run> runs, boolean keywords) {
        List<Segment> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        Style[] st = styles(text.length(), runs == null ? Collections.emptyList() : runs);
        if (keywords) {
            Matcher m = DELAY.matcher(text);
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) st[i] = st[i].withBold(true).withColour("red");
            }
            m = ON_HOLD.matcher(text);
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) st[i] = st[i].withBold(true).withHighlight(true);
            }
        }
        int i = 0;
        while (i < text.length()) {
            int j = i + 1;
            while (j < text.length() && st[j].equals(st[i])) j++;
            out.add(new Segment(text.substring(i, j), i, st[i]));
            i = j;
        }
        return out;
    }

    /** Keywords only (titles and other plain text). */
    static List<Segment> segments(String text) {
        return segments(text, Collections.emptyList(), true);
    }

    /** Segments with [start, end) forced to style (on top of formatting and keywords), re-merged. */
    static List<Segment> overlay(String text, List<Segment> segs, int start, int end, Style style) {
        if (start >= end) return segs;
        Style[] st = new Style[text.length()];
        for (Segment x : segs) for (int i = x.start; i < x.end(); i++) st[i] = x.style;
        for (int i = Math.max(0, start); i < Math.min(end, text.length()); i++) st[i] = style;
        List<Segment> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int j = i + 1;
            while (j < text.length() && st[j].equals(st[i])) j++;
            out.add(new Segment(text.substring(i, j), i, st[i]));
            i = j;
        }
        return out;
    }

    /** True if "delay" or "on hold" appears (so the text needs rich output at all). */
    static boolean hasKeywords(String text) {
        return text != null && (DELAY.matcher(text).find() || ON_HOLD.matcher(text).find());
    }

    /** True if the text contains "on hold" (Excel: the cell gets a light yellow fill). */
    static boolean hasOnHold(String text) {
        return text != null && ON_HOLD.matcher(text).find();
    }

    /** True if every character in [start, end) has bold (or underline when underline). */
    static boolean allHave(String text, List<Run> runs, int start, int end, boolean underline) {
        if (start >= end) return false;
        Style[] st = styles(text.length(), runs);
        for (int i = start; i < end; i++) {
            if (!(underline ? st[i].underline : st[i].bold)) return false;
        }
        return true;
    }

    /** Bold or underline on [start, end): off if it's all on already, otherwise on. */
    static List<Run> applyToggle(String text, List<Run> runs, int start, int end, boolean underline) {
        if (start >= end) return runs;
        boolean on = !allHave(text, runs, start, end, underline);
        Style[] st = styles(text.length(), runs);
        for (int i = start; i < end; i++) st[i] = underline ? st[i].withUnderline(on) : st[i].withBold(on);
        return fromStyles(st);
    }

    /** Sets the colour of [start, end) ("" is black). */
    static List<Run> applyColour(String text, List<Run> runs, int start, int end, String colour) {
        if (start >= end) return runs;
        Style[] st = styles(text.length(), runs);
        for (int i = start; i < end; i++) st[i] = st[i].withColour(colour);
        return fromStyles(st);
    }

    /** The user style of the character at i (plain outside the text). */
    static Style styleAt(String text, List<Run> runs, int i) {
        for (Run r : runs) {
            if (i >= r.start && i < r.end) return r.style;
        }
        return Style.PLAIN;
    }

    /**
     * Keeps the runs in step when the text changes from oldText to newText. Typed text gets typing,
     * or else the style of the character before it.
     */
    static List<Run> afterEdit(String oldText, String newText, List<Run> runs, Style typing) {
        if (oldText.equals(newText)) return runs;
        int p = 0;
        int maxP = Math.min(oldText.length(), newText.length());
        while (p < maxP && oldText.charAt(p) == newText.charAt(p)) p++;
        int q = 0;
        while (q < maxP - p && oldText.charAt(oldText.length() - 1 - q) == newText.charAt(newText.length() - 1 - q)) q++;
        Style[] old = styles(oldText.length(), runs);
        int inserted = newText.length() - q - p;
        Style fill = typing != null ? typing : p > 0 ? old[p - 1] : old.length > 0 ? old[0] : Style.PLAIN;
        Style[] st = new Style[newText.length()];
        System.arraycopy(old, 0, st, 0, p);
        for (int i = 0; i < inserted; i++) st[p + i] = fill;
        System.arraycopy(old, oldText.length() - q, st, p + inserted, q);
        return fromStyles(st);
    }
}
