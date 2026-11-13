package com.dailyworklog.app;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Picking report dates on a calendar: one date, or a period (start and end) as one pick. Answers are "Nov 3, 2026". */
final class DatePick {
    private DatePick() {}

    interface One {
        void onDate(String date);
    }

    interface Two {
        void onRange(String from, String to);
    }

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** The calendar's selection (midnight UTC) as "Nov 3, 2026". */
    static String format(long utcMillis) {
        SimpleDateFormat f = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        f.setTimeZone(UTC);
        return f.format(new Date(utcMillis));
    }

    /** "Nov 3, 2026" as midnight UTC for the calendar, or null. */
    static Long utc(String answer) {
        Calendar d = ReportFill.parseDate(answer);
        if (d == null) return null;
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH));
        return c.getTimeInMillis();
    }

    private static long todayUtc() {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        return c.getTimeInMillis();
    }

    /** 5 years back to 5 years ahead, opening at open. */
    private static CalendarConstraints range(long open) {
        Calendar a = Calendar.getInstance(UTC), b = Calendar.getInstance(UTC);
        a.setTimeInMillis(todayUtc());
        a.add(Calendar.YEAR, -5);
        b.setTimeInMillis(todayUtc());
        b.add(Calendar.YEAR, 5);
        return new CalendarConstraints.Builder().setStart(a.getTimeInMillis()).setEnd(b.getTimeInMillis()).setOpenAt(open).build();
    }

    /** A saved date outside the calendar's 10 years would stop it opening: use today then. */
    private static long within(Long at) {
        if (at == null) return todayUtc();
        Calendar a = Calendar.getInstance(UTC), b = Calendar.getInstance(UTC);
        a.setTimeInMillis(todayUtc());
        a.add(Calendar.YEAR, -5);
        b.setTimeInMillis(todayUtc());
        b.add(Calendar.YEAR, 5);
        return at < a.getTimeInMillis() || at > b.getTimeInMillis() ? todayUtc() : at;
    }

    static void pick(FragmentActivity a, String title, String current, One done) {
        long sel = within(utc(current));
        try {
            MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(title).setSelection(sel).setCalendarConstraints(range(sel)).build();
            p.addOnPositiveButtonClickListener(v -> {
                if (v != null) done.onDate(format(v));
            });
            p.show(a.getSupportFragmentManager(), "date");
        } catch (RuntimeException e) {
            simple(a, title, sel, done); // the phone's own date picker instead
        }
    }

    /** Android's built-in calendar (month by month), if the Material one can't open. */
    static void simple(android.app.Activity a, String title, long utcMillis, One done) {
        Calendar c = Calendar.getInstance(UTC);
        c.setTimeInMillis(utcMillis);
        android.app.DatePickerDialog d = new android.app.DatePickerDialog(a, (view, y, m, day) -> {
            Calendar out = Calendar.getInstance(UTC);
            out.clear();
            out.set(y, m, day);
            done.onDate(format(out.getTimeInMillis()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.setTitle(title);
        d.show();
    }

    static void pickRange(FragmentActivity a, String title, String from, String to, Two done) {
        long start = within(utc(from));
        Long t = utc(to);
        long end = t != null && t >= start ? within(t) : start;
        try {
            MaterialDatePicker<androidx.core.util.Pair<Long, Long>> p = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(title).setSelection(androidx.core.util.Pair.create(start, end))
                    .setCalendarConstraints(range(start)).build();
            p.addOnPositiveButtonClickListener(v -> {
                if (v != null && v.first != null && v.second != null) done.onRange(format(v.first), format(v.second));
            });
            p.show(a.getSupportFragmentManager(), "range");
        } catch (RuntimeException e) {
            // The phone's own date picker, twice: the start, then the end.
            simple(a, title + ": start date", start, s -> simple(a, title + ": end date", end, e2 -> done.onRange(s, e2)));
        }
    }

    /** "Period covered from" -> "Period covered". */
    static String periodLabel(String fromLabel) {
        return fromLabel.toLowerCase(Locale.ROOT).endsWith(" from") ? fromLabel.substring(0, fromLabel.length() - 5) : fromLabel;
    }
}
