package com.dailyworklog.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.TimeZone;

/**
 * Link reminders written straight into the phone's calendar (needs READ_CALENDAR and WRITE_CALENDAR).
 * A repeating reminder keeps the phone's time zone, so it stays at the same clock time through daylight
 * saving. Same as the new app's CalendarReminders.kt.
 */
final class CalendarReminders {
    private CalendarReminders() {}

    static final String[] PERMISSIONS = {Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR};
    static final String DENIED = "Calendar access is off. Turn on Calendar for Daily Work Log in your phone Settings, then try again.";
    static final String NO_CALENDAR = "No calendar was found on this phone to add the reminder to.";
    static final String FAILED = "Couldn't save the reminder in your calendar.";
    private static final long LENGTH_MS = 30 * 60 * 1000L;

    static boolean allowed(Context c) {
        for (String p : PERMISSIONS) if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    /** The calendar event's description. */
    static String notes(Models.Link l) {
        return "Daily Work Log link: " + l.title + "\n" + l.url;
    }

    /** RRULE for a repeat, or null for one time only. */
    static String rrule(String repeat) {
        switch (repeat) {
            case Models.LinkReminder.DAILY: return "FREQ=DAILY";
            case Models.LinkReminder.WEEKLY: return "FREQ=WEEKLY";
            case Models.LinkReminder.MONTHLY: return "FREQ=MONTHLY";
            case Models.LinkReminder.YEARLY: return "FREQ=YEARLY";
            default: return null;
        }
    }

    /** The calendar to add to: the phone's main one if it can be written to, else the first that can. */
    private static Long calendarId(ContentResolver cr) {
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE};
        Long first = null;
        try (Cursor c = cr.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                boolean primary = !c.isNull(1) && c.getInt(1) == 1;
                if (c.getInt(2) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR || c.getInt(3) != 1) continue;
                if (primary) return id;
                if (first == null) first = id;
            }
        }
        return first;
    }

    /**
     * Adds or updates the link's event and its alert, and keeps its id in the reminder. Returns null when
     * saved, or the message to show.
     */
    static String save(Context ctx, Models.Link l) {
        Models.LinkReminder r = l.reminder;
        if (r == null) return null;
        if (!allowed(ctx)) return DENIED;
        try {
            ContentResolver cr = ctx.getContentResolver();
            String rule = rrule(r.repeat);
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.TITLE, l.title);
            v.put(CalendarContract.Events.DESCRIPTION, notes(l));
            v.put(CalendarContract.Events.DTSTART, r.start);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            v.put(CalendarContract.Events.HAS_ALARM, 1);
            if (rule == null) {
                v.put(CalendarContract.Events.DTEND, r.start + LENGTH_MS);
                v.putNull(CalendarContract.Events.RRULE);
                v.putNull(CalendarContract.Events.DURATION);
            } else {
                // A repeating event has a duration instead of an end.
                v.putNull(CalendarContract.Events.DTEND);
                v.put(CalendarContract.Events.RRULE, rule);
                v.put(CalendarContract.Events.DURATION, "PT30M");
            }
            Long id = parse(r.eventId);
            boolean updated = id != null
                    && cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0;
            if (!updated) {
                Long cal = calendarId(cr);
                if (cal == null) return NO_CALENDAR;
                v.put(CalendarContract.Events.CALENDAR_ID, cal);
                Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, v);
                if (uri == null) return FAILED;
                id = ContentUris.parseId(uri);
            }
            // One alert: replace whatever was there.
            cr.delete(CalendarContract.Reminders.CONTENT_URI, CalendarContract.Reminders.EVENT_ID + " = ?", new String[]{String.valueOf(id)});
            ContentValues a = new ContentValues();
            a.put(CalendarContract.Reminders.EVENT_ID, id);
            a.put(CalendarContract.Reminders.MINUTES, r.alert);
            a.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            cr.insert(CalendarContract.Reminders.CONTENT_URI, a);
            r.eventId = String.valueOf(id);
            return null;
        } catch (SecurityException e) {
            return DENIED;
        } catch (Exception e) {
            return FAILED;
        }
    }

    /** Takes a reminder's event out of the calendar (quietly does nothing without access or an event). */
    static void remove(Context ctx, Models.LinkReminder r) {
        Long id = r == null ? null : parse(r.eventId);
        if (id == null || !allowed(ctx)) return;
        try {
            ctx.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null);
        } catch (Exception ignored) {
            // already gone
        }
    }

    private static Long parse(String s) {
        try {
            return s == null || s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
