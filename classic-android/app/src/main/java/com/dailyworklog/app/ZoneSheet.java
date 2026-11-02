package com.dailyworklog.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads a photo of the zone task sheet (the "North Zone 2" / "South" tabs) and puts each task back
 * together. The sheet is laid out in location groups (Tent 1A, Tent 1B, Mock-Up...), each three
 * columns wide:
 *
 *   top row:     Series (the job) | seacan #  | Owner (contact)
 *   lower rows:  comments         | Task      | Delay code
 *
 * Each sea can starts a task; the Task text is under it in the same column, the job and contact are
 * beside it, and comments / delay code are under those. The job cell's colour says whose task it is.
 */
final class ZoneSheet {
    private ZoneSheet() {}

    /** Pixel colours from the photo, so the job cell's colour can be read. */
    interface Pixels {
        int width();

        int height();

        /** 0xRRGGBB */
        int rgb(int x, int y);
    }

    static final String BLUE = "blue", ORANGE = "orange", PINK = "pink", GREEN = "green", YELLOW = "yellow", NONE = "none";

    static final class Task {
        String seacan = "", task = "", job = "", contact = "", comments = "", delay = "", location = "";
        /** Models.Task.NORTH / SOUTH, or "" if the photo didn't show which zone. */
        String side = "";
        /** Colour of the job cell: BLUE (your crew), ORANGE, PINK, GREEN (done), YELLOW (ongoing) or NONE. */
        String colour = NONE;

        /** "Unload Can – BPPU 211028-7 – Tent 1A (west)" */
        String title() {
            List<String> parts = new ArrayList<>();
            if (!task.isEmpty()) parts.add(task);
            if (!seacan.isEmpty()) parts.add(seacan);
            if (!location.isEmpty()) parts.add(location);
            if (parts.isEmpty() && !job.isEmpty()) parts.add(job);
            return String.join(" – ", parts);
        }

        String notes() {
            StringBuilder sb = new StringBuilder();
            line(sb, "Job", job);
            line(sb, "Contact", contact);
            line(sb, "Location", location);
            line(sb, "Comments", comments);
            line(sb, "Delay code", delay);
            if (GREEN.equals(colour)) line(sb, "Status", "Completed (green on the sheet)");
            else if (YELLOW.equals(colour)) line(sb, "Status", "Ongoing (yellow on the sheet)");
            return sb.toString();
        }

        private static void line(StringBuilder sb, String label, String value) {
            if (value == null || value.isEmpty()) return;
            if (sb.length() > 0) sb.append('\n');
            sb.append(label).append(": ").append(value);
        }
    }

    /** One location group: three columns side by side. */
    private static final class Group {
        final float cx, w;
        String location = "";

        Group(float cx, float w) {
            this.cx = cx;
            this.w = w;
        }

        /** 0 = Series/comments, 1 = seacan/Task, 2 = Owner/Delay code, -1 = not in this group. */
        int column(float x) {
            float d = x - (cx - 1.5f * w);
            if (d < 0 || d >= 3 * w) return -1;
            return (int) (d / w);
        }
    }

    /**
     * Returns the tasks found, or null if the photo doesn't look like the zone sheet (no "seacan"
     * column headings), so the normal photo import can be used instead. pixels may be null.
     */
    static List<Task> parse(List<SheetImport.Box> boxes, Pixels pixels) {
        // 1. The "seacan #" headings mark the middle column of each location group.
        List<SheetImport.Box> anchors = new ArrayList<>();
        for (SheetImport.Box b : boxes) if (norm(b.text).contains("seacan") || norm(b.text).contains("sea can")) anchors.add(b);
        if (anchors.isEmpty()) return null;
        // Keep the heading row only (a sea can mentioned in a task lower down isn't a heading).
        anchors.sort((a, b) -> Integer.compare(a.top, b.top));
        int headTop = anchors.get(0).top, headH = anchors.get(0).height();
        List<SheetImport.Box> heads = new ArrayList<>();
        for (SheetImport.Box b : anchors) if (Math.abs(b.top - headTop) < headH * 2) heads.add(b);
        heads.sort((a, b) -> Integer.compare(a.left, b.left));

        float colW = columnWidth(heads, boxes);
        List<Group> groups = new ArrayList<>();
        for (SheetImport.Box h : heads) groups.add(new Group((h.left + h.right) / 2f, colW));

        // 2. Where the headings end and the task rows begin (and where they stop: the sheet tabs).
        int headerBottom = 0;
        for (SheetImport.Box b : boxes) {
            String t = norm(b.text);
            boolean label = t.contains("seacan") || t.equals("task") || t.startsWith("comment") || t.startsWith("delay")
                    || t.startsWith("owner") || t.startsWith("series") || t.startsWith("serles");
            if (label && b.top < headTop + headH * 6) headerBottom = Math.max(headerBottom, b.bottom);
        }
        int dataEnd = Integer.MAX_VALUE;
        for (SheetImport.Box b : boxes) {
            String t = norm(b.text).replace(" ", "");
            if (b.top > headerBottom && (t.equals("dashboard") || t.contains("seacanmovements") || t.contains("floorloading")
                    || t.contains("lastknownlocation"))) dataEnd = Math.min(dataEnd, b.top);
        }

        // 3. Above the headings: the zone title (North / South) and each group's location.
        String side = "";
        for (SheetImport.Box b : boxes) {
            if (b.bottom > headTop) continue;
            String t = norm(b.text);
            if (t.contains("zone") || t.equals("north") || t.equals("south")) {
                if (t.contains("north")) side = Models.Task.NORTH;
                else if (t.contains("south")) side = Models.Task.SOUTH;
                continue;
            }
            if (b.bottom < headTop - headH * 5 || !SheetImport.hasLetters(b.text)) continue;
            Group g = nearest(groups, (b.left + b.right) / 2f);
            if (g != null && Math.abs((b.left + b.right) / 2f - g.cx) < 1.5f * g.w && g.location.isEmpty()) {
                g.location = tidy(b.text);
            }
        }

        // 4. Each group's task rows.
        List<Task> out = new ArrayList<>();
        for (Group g : groups) {
            List<SheetImport.Box> left = new ArrayList<>(), mid = new ArrayList<>(), right = new ArrayList<>();
            for (SheetImport.Box b : boxes) {
                if (b.top <= headerBottom || b.top >= dataEnd) continue;
                int col = g.column((b.left + b.right) / 2f);
                if (col == 0) left.add(b);
                else if (col == 1) mid.add(b);
                else if (col == 2) right.add(b);
            }
            Comparator yOrder = new Comparator();
            left.sort(yOrder);
            mid.sort(yOrder);
            right.sort(yOrder);

            // Sea cans start tasks; everything under one (before the next) is its Task text.
            List<SheetImport.Box> seacans = new ArrayList<>();
            for (SheetImport.Box b : mid) if (isSeacan(b.text)) seacans.add(b);
            for (int i = 0; i < seacans.size(); i++) {
                SheetImport.Box sc = seacans.get(i);
                int h = sc.height();
                int topBandStart = sc.top - (int) (h * 0.7f), topBandEnd = sc.bottom + (int) (h * 0.7f);
                int blockEnd = i + 1 < seacans.size() ? seacans.get(i + 1).top - (int) (h * 0.7f)
                        : (dataEnd == Integer.MAX_VALUE ? Integer.MAX_VALUE : dataEnd);
                Task t = new Task();
                t.seacan = tidySeacan(sc.text);
                t.location = g.location;
                t.side = side;
                t.task = join(mid, sc.bottom, blockEnd, sc);
                t.job = join(left, topBandStart, topBandEnd, null);
                t.contact = join(right, topBandStart, topBandEnd, null);
                t.comments = join(left, topBandEnd, blockEnd, null);
                t.delay = join(right, topBandEnd, blockEnd, null);
                if (pixels != null) {
                    t.colour = colour(pixels, Math.round(g.cx - 1.5f * g.w), Math.round(g.cx - 0.5f * g.w),
                            topBandStart, topBandEnd);
                }
                if (!t.task.isEmpty() || !t.job.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    /** Column width: a third of the distance between groups, or from the Series/Owner headings. */
    private static float columnWidth(List<SheetImport.Box> heads, List<SheetImport.Box> boxes) {
        if (heads.size() > 1) {
            List<Float> gaps = new ArrayList<>();
            for (int i = 1; i < heads.size(); i++) {
                gaps.add(((heads.get(i).left + heads.get(i).right) - (heads.get(i - 1).left + heads.get(i - 1).right)) / 2f);
            }
            Collections.sort(gaps);
            return gaps.get(gaps.size() / 2) / 3f;
        }
        SheetImport.Box h = heads.get(0);
        float cx = (h.left + h.right) / 2f;
        for (SheetImport.Box b : boxes) {
            String t = norm(b.text);
            if ((t.startsWith("owner") || t.startsWith("series") || t.startsWith("serles")) && Math.abs(b.top - h.top) < h.height() * 2) {
                return Math.abs((b.left + b.right) / 2f - cx);
            }
        }
        return h.width() * 2.5f;
    }

    private static Group nearest(List<Group> groups, float x) {
        Group best = null;
        for (Group g : groups) if (best == null || Math.abs(g.cx - x) < Math.abs(best.cx - x)) best = g;
        return best;
    }

    /** Text of the boxes whose middle falls between y0 and y1, top to bottom. */
    private static String join(List<SheetImport.Box> col, int y0, int y1, SheetImport.Box skip) {
        StringBuilder sb = new StringBuilder();
        for (SheetImport.Box b : col) {
            if (b == skip || isSeacan(b.text) && skip != null) continue;
            float cy = b.centerY();
            if (cy < y0 || cy >= y1) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(tidy(b.text));
        }
        return sb.toString();
    }

    private static final class Comparator implements java.util.Comparator<SheetImport.Box> {
        @Override
        public int compare(SheetImport.Box a, SheetImport.Box b) {
            return Integer.compare(a.top, b.top);
        }
    }

    /** Sea can numbers look like "BPPU 211028-7": letters, then 5-8 digits, maybe a check digit. */
    static boolean isSeacan(String s) {
        String t = s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return t.matches("[A-Z]{2,6}[0-9OIl]{5,8}(-?[0-9OIl]{1,2})?");
    }

    /** "BPPU211028-7" -> "BPPU 211028-7" */
    static String tidySeacan(String s) {
        String t = s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Z]{2,6})(.*)$").matcher(t);
        return m.matches() ? m.group(1) + " " + m.group(2).replace('O', '0').replace('I', '1').replace('L', '1') : t;
    }

    private static String tidy(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 #]", "").replaceAll("\\s+", " ").trim();
    }

    // ----- cell colour -----

    /**
     * The fill colour of a cell, by majority vote over a grid of pixels inside it. Dark pixels
     * (the writing) are ignored, so black text on blue still reads as blue.
     */
    static String colour(Pixels px, int x0, int x1, int y0, int y1) {
        int pad = Math.max(2, (x1 - x0) / 12);
        x0 = Math.max(0, x0 + pad);
        x1 = Math.min(px.width() - 1, x1 - pad);
        y0 = Math.max(0, y0);
        y1 = Math.min(px.height() - 1, y1);
        if (x1 <= x0 || y1 <= y0) return NONE;
        int[] votes = new int[6];
        String[] names = {BLUE, ORANGE, PINK, GREEN, YELLOW, NONE};
        int total = 0;
        int stepX = Math.max(1, (x1 - x0) / 40), stepY = Math.max(1, (y1 - y0) / 12);
        for (int y = y0; y <= y1; y += stepY) {
            for (int x = x0; x <= x1; x += stepX) {
                int c = px.rgb(x, y);
                float[] hsv = hsv((c >> 16) & 255, (c >> 8) & 255, c & 255);
                if (hsv[2] < 0.22f) continue;  // the writing
                total++;
                votes[classify(hsv)]++;
            }
        }
        if (total == 0) return NONE;
        int best = 5;
        for (int i = 0; i < 5; i++) if (votes[i] > votes[best] * 0.8f && votes[i] > total * 0.3f && (best == 5 || votes[i] > votes[best])) best = i;
        return names[best];
    }

    /** 0 blue, 1 orange, 2 pink, 3 green, 4 yellow, 5 none (white / grey). */
    static int classify(float[] hsv) {
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (s < 0.28f || v < 0.25f) return 5;
        if (h >= 185 && h < 260) return 0;
        if (h >= 15 && h < 45) return 1;
        if (h >= 45 && h < 70) return 4;
        if (h >= 70 && h < 170) return 3;
        if (h >= 285 || h < 15) return 2;
        return 5;
    }

    static float[] hsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == rf) h = 60 * (((gf - bf) / d) % 6);
        else if (max == gf) h = 60 * (((bf - rf) / d) + 2);
        else h = 60 * (((rf - gf) / d) + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
