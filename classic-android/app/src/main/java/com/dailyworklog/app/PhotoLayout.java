package com.dailyworklog.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Report photo layout: photos sit side by side at the same height and wrap onto a new row
 * when the next one won't fit. Tall phone photos pack several to a row; wide ones fewer.
 * Used by both the PDF and Word reports so they match.
 */
final class PhotoLayout {
    private PhotoLayout() {}

    /** Height of report photos in points (about 1.1 inches). Smaller than the old 130 so they take less room. */
    static final float REPORT_H = 80f;
    /** Narrowest room each report photo gets in its row, so its caption ("TT 7:32 AM") fits. */
    static final float REPORT_SLOT = 40f;

    /** One photo's place in a row: which photo, its left offset in the row, and drawn size. */
    static final class Cell {
        final int index;
        final float x, w, h;

        Cell(int index, float x, float w, float h) {
            this.index = index;
            this.x = x;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * sizes: each photo's pixel width and height ({w, h}), or null if missing (drawn as a small
     * placeholder). Every photo is scaled to rowHeight, or narrower if it would overflow the width.
     */
    static List<List<Cell>> rows(List<float[]> sizes, float width, float rowHeight, float gap, float missingWidth) {
        return rows(sizes, width, rowHeight, gap, missingWidth, 0f);
    }

    /** As above, but each photo takes at least minSlot of the row so a narrow photo has room for its caption. */
    static List<List<Cell>> rows(List<float[]> sizes, float width, float rowHeight, float gap, float missingWidth, float minSlot) {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        float used = 0;
        for (int i = 0; i < sizes.size(); i++) {
            float[] s = sizes.get(i);
            float w, h;
            if (s == null || s[0] <= 0 || s[1] <= 0) {
                w = missingWidth;
                h = 14f;
            } else {
                h = rowHeight;
                w = s[0] * (rowHeight / s[1]);
                if (w > width) {  // very wide photo: fit to the width instead
                    w = width;
                    h = s[1] * (width / s[0]);
                }
            }
            float slot = Math.min(width, Math.max(w, minSlot));
            float x = row.isEmpty() ? 0 : used + gap;
            if (!row.isEmpty() && x + slot > width) {
                rows.add(row);
                row = new ArrayList<>();
                x = 0;
            }
            row.add(new Cell(i, x, w, h));
            used = x + slot;
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    static float height(List<Cell> row) {
        float h = 0;
        for (Cell c : row) h = Math.max(h, c.h);
        return h;
    }

    /**
     * Short caption that fits under a narrow photo: initials and time, e.g. "TT 7:32 AM".
     * The date is added only when the photo is from a different day than the log.
     */
    static String caption(Models.Photo p, String logIso) {
        Date d = new Date(p.addedAt);
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
        String time = new SimpleDateFormat(day.equals(logIso) ? "h:mm a" : "MM/dd h:mm a", Locale.US).format(d);
        String who = p.addedBy == null || p.addedBy.trim().isEmpty() ? "" : Fmt.initials(p.addedBy) + " ";
        return who + time;
    }
}
