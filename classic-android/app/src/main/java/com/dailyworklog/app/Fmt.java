package com.dailyworklog.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Date and text formatting. New formatter per call so it's safe on background threads. */
final class Fmt {
    private Fmt() {}

    /** yyyy-MM-dd to dd/MM/yyyy, matching the old app's titles. */
    static String logDate(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    /** "09/25/2026 at 2:03 PM" */
    static String stamp(long t) {
        return new SimpleDateFormat("MM/dd/yyyy 'at' h:mm a", Locale.US).format(new Date(t));
    }

    /** "Sep 25, 2026 at 2:03 PM" */
    static String longStamp(long t) {
        return new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(new Date(t));
    }

    /** month is 0-based, as Calendar and DatePicker give it. */
    static String iso(int year, int month, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
    }

    /** The Friday of the coming weekend (this weekend if it's already Fri, Sat or Sun). */
    static String upcomingFriday(Calendar now) {
        Calendar c = (Calendar) now.clone();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int shift = dow == Calendar.SATURDAY ? -1 : dow == Calendar.SUNDAY ? -2 : Calendar.FRIDAY - dow;
        c.add(Calendar.DAY_OF_MONTH, shift);
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    static String upcomingFriday() {
        return upcomingFriday(Calendar.getInstance());
    }

    /** "Fri Oct 2" for the Friday + offset days. */
    static String weekendDay(String fridayIso, int offset) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(fridayIso);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            c.add(Calendar.DAY_OF_MONTH, offset);
            return new SimpleDateFormat("EEE MMM d", Locale.US).format(c.getTime());
        } catch (Exception e) {
            return new String[]{"Fri", "Sat", "Sun"}[Math.max(0, Math.min(2, offset))];
        }
    }

    static String todayIso() {
        Calendar c = Calendar.getInstance();
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase(Locale.ROOT);
    }

    static String join(java.util.List<String> names) {
        return String.join(", ", names);
    }

    static String fileSafe(String s) {
        String out = s.replaceAll("[^A-Za-z0-9 _-]", "-").trim();
        return out.isEmpty() ? "report" : out;
    }

    static String capitalize(String s) {
        s = s.trim();
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
