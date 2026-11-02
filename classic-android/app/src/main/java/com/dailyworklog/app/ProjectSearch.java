package com.dailyworklog.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Search across every day of a project: fields, notes, names, "Also today", notes sent, the
 * Notes tab and the sea can list. Used by the search box on the Checklists tab.
 */
final class ProjectSearch {
    private ProjectSearch() {}

    /** One day that matched, with a short line for each place the word was found. */
    static final class DayHit {
        final Models.DailyLog log;
        final List<String> lines;

        DayHit(Models.DailyLog log, List<String> lines) {
            this.log = log;
            this.lines = lines;
        }
    }

    static final class Result {
        final List<DayHit> days = new ArrayList<>();
        /** Sea cans aren't tied to a day, so they're listed on their own. */
        final List<String> seaCans = new ArrayList<>();

        boolean isEmpty() {
            return days.isEmpty() && seaCans.isEmpty();
        }
    }

    /** Checklists newest date first, then newest made first (the Checklists tab order). */
    static List<Models.DailyLog> sortedLogs(Models.Project p) {
        List<Models.DailyLog> logs = new ArrayList<>(p.logs);
        logs.sort((a, b) -> {
            int c = b.date.compareTo(a.date);
            return c != 0 ? c : Long.compare(b.createdAt, a.createdAt);
        });
        return logs;
    }

    static Result run(Models.Project p, String query) {
        Result out = new Result();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return out;
        for (Models.DailyLog l : sortedLogs(p)) {
            List<String> lines = new ArrayList<>();
            if (has(l.displayTitle(), q)) add(lines, "Checklist: " + l.displayTitle());
            for (Models.Task t : l.tasks) {
                String title = t.title.trim().isEmpty() ? "(Untitled field)" : t.title.trim();
                if (has(t.title, q)) add(lines, "Field: " + title);
                if (has(t.notes, q)) add(lines, "Notes on " + title + ": " + snippet(t.notes, q));
                List<String> names = new ArrayList<>(t.assignees);
                names.add(t.completedBy);
                names.add(t.securedBy);
                for (String n : names) {
                    if (!n.isEmpty() && has(n, q)) {
                        add(lines, title + ": " + n);
                        break;
                    }
                }
            }
            for (Models.Extra e : l.extras) {
                if (has(e.title, q)) add(lines, "Also today: " + e.label());
                else if (has(e.detail, q)) add(lines, "Also today: " + e.label() + ": " + snippet(e.detail, q));
            }
            for (Models.Notice n : p.notices) {
                if (!day(n.sentAt).equals(l.date)) continue;
                boolean hit = has(n.note, q);
                for (String to : n.to) hit |= has(to, q);
                if (hit) add(lines, "Sent note: " + snippet(n.note, q));
            }
            for (Models.Note n : p.notes) {
                if (day(n.createdAt).equals(l.date) && has(n.text, q)) add(lines, "Note: " + snippet(n.text, q));
            }
            if (!lines.isEmpty()) out.days.add(new DayHit(l, lines));
        }
        for (Models.SeaCanMove m : p.filledSeaCans()) {
            String from = m.fromLocation(), to = m.toLocation(), w = m.weight.trim();
            if (has(m.can, q) || has(from, q) || has(to, q) || has(w, q)) {
                out.seaCans.add(m.can.trim() + " · " + (from.isEmpty() ? "–" : from) + " to "
                        + (to.isEmpty() ? "–" : to) + (w.isEmpty() ? "" : " · " + w));
            }
        }
        return out;
    }

    private static boolean has(String s, String q) {
        return s.toLowerCase(Locale.ROOT).contains(q);
    }

    private static void add(List<String> lines, String s) {
        if (!lines.contains(s)) lines.add(s);
    }

    private static String day(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return Fmt.iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    /** The text on one line; if it's long, about 60 characters around the first match. */
    static String snippet(String text, String query) {
        String s = text.replaceAll("\\s+", " ").trim();
        if (s.length() <= 60) return s;
        int i = s.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        int start = i < 0 ? 0 : Math.max(0, Math.min(i - 20, s.length() - 60));
        int end = start + 60;
        return (start > 0 ? "…" : "") + s.substring(start, end) + (end < s.length() ? "…" : "");
    }
}
