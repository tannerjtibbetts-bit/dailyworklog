package com.dailyworklog.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Finding the fill-in lines (the printed lines answers are written on) in a form's page picture, so answers
 * start where a line starts and fit its length. The same steps as the new app's form_lines.dart.
 */
final class FormLines {
    private FormLines() {}

    /** Width the picture is scaled to before looking for lines. */
    static final int SCAN_WIDTH = 1000;

    /** A horizontal line on the page: from x0 to x1 at height y, all fractions of the page (0..1). */
    static final class Segment {
        final int page;
        final double x0, x1, y;

        Segment(int page, double x0, double x1, double y) {
            this.page = page;
            this.x0 = x0;
            this.x1 = x1;
            this.y = y;
        }

        double length() {
            return x1 - x0;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.US, "p%d(%.3f-%.3f @%.3f)", page, x0, x1, y);
        }
    }

    private static final class Run {
        final int x0, x1, y;

        Run(int x0, int x1, int y) {
            this.x0 = x0;
            this.x1 = x1;
            this.y = y;
        }
    }

    private static final class Group {
        int x0, x1, yMax, n = 1;
        double ySum;

        Group(Run r) {
            x0 = r.x0;
            x1 = r.x1;
            yMax = r.y;
            ySum = r.y;
        }

        void add(Run r) {
            if (r.x0 < x0) x0 = r.x0;
            if (r.x1 > x1) x1 = r.x1;
            if (r.y > yMax) yMax = r.y;
            ySum += r.y;
            n++;
        }
    }

    /** Luminance 0..255 of an RGB colour (0.299R + 0.587G + 0.114B, rounded). */
    static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return Math.max(0, Math.min(255, (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b)));
    }

    /** Lines found in a picture given as luminance values (row by row, w x h). */
    static List<Segment> find(int[] lum, int w, int h, int page) {
        int[] hist = new int[256];
        for (int v : lum) hist[Math.max(0, Math.min(255, v))]++;
        // Dark = well below the page's usual (median) brightness.
        int count = 0, median = 255;
        for (int i = 0; i < 256; i++) {
            count += hist[i];
            if (count * 2.0 >= (double) w * h) {
                median = i;
                break;
            }
        }
        int dark = (int) Math.round(median * 0.65);

        // 1. Long dark runs in each row (gaps up to 2 pixels allowed), thin (light 3 pixels above and below).
        List<Run> runs = new ArrayList<>();
        int minRun = (int) Math.round(w * 0.04);
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (!isDark(lum, w, h, dark, x, y)) {
                    x++;
                    continue;
                }
                int start = x, last = x, gap = 0;
                while (x < w && gap <= 2) {
                    if (isDark(lum, w, h, dark, x, y)) {
                        last = x;
                        gap = 0;
                    } else {
                        gap++;
                    }
                    x++;
                }
                int len = last - start + 1;
                if (len >= minRun) {
                    int above = 0, below = 0;
                    for (int i = start; i <= last; i++) {
                        if (isDark(lum, w, h, dark, i, y - 3)) above++;
                        if (isDark(lum, w, h, dark, i, y + 3)) below++;
                    }
                    if (above < len * 0.3 && below < len * 0.3) runs.add(new Run(start, last, y));
                }
            }
        }
        // 2. Join runs in nearby rows (a line 2-3 pixels thick, or slightly slanted) into one line.
        runs.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x0, b.x0));
        List<Group> groups = new ArrayList<>();
        for (Run r : runs) {
            Group into = null;
            for (Group g : groups) {
                if (r.y - g.yMax <= 3 && r.x0 <= g.x1 + 6 && r.x1 >= g.x0 - 6) {
                    into = g;
                    break;
                }
            }
            if (into == null) groups.add(new Group(r));
            else into.add(r);
        }
        List<Segment> out = new ArrayList<>();
        for (Group g : groups) {
            if (g.x1 - g.x0 + 1 >= minRun) out.add(new Segment(page, (double) g.x0 / w, (g.x1 + 1.0) / w, g.ySum / g.n / h));
        }
        out.sort((a, b) -> a.y != b.y ? Double.compare(a.y, b.y) : Double.compare(a.x0, b.x0));
        return out;
    }

    private static boolean isDark(int[] lum, int w, int h, int dark, int x, int y) {
        return y >= 0 && y < h && lum[y * w + x] < dark;
    }

    /** Lines in a page picture file (scaled to 1000 wide first). Background thread; none if it can't be read. */
    static List<Segment> find(File picture, int page) {
        try {
            android.graphics.Bitmap b = ImageLoader.decode(picture, 2 * SCAN_WIDTH);
            if (b == null) return new ArrayList<>();
            if (b.getWidth() > SCAN_WIDTH) {
                int h = Math.max(1, Math.round((float) SCAN_WIDTH * b.getHeight() / b.getWidth()));
                android.graphics.Bitmap s = android.graphics.Bitmap.createScaledBitmap(b, SCAN_WIDTH, h, true);
                if (s != b) b.recycle();
                b = s;
            }
            int w = b.getWidth(), h = b.getHeight();
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            b.recycle();
            for (int i = 0; i < px.length; i++) px[i] = luminance(px[i]);
            return find(px, w, h, page);
        } catch (Exception | OutOfMemoryError e) {
            return new ArrayList<>();
        }
    }
}
