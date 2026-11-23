package com.dailyworklog.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Link reminders written straight into the phone's calendar (needs READ_CALENDAR and WRITE_CALENDAR).
 * A repeating reminder keeps the phone's time zone, so it stays at the same clock time through daylight saving.
 */
object CalendarReminders {
    private const val LENGTH_MS = 30 * 60 * 1000L

    /** The calendar to add to: the phone's main one if it can be written to, else the first that can. */
    private fun calendarId(cr: ContentResolver): Long? {
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        var first: Long? = null
        cr.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val primary = !c.isNull(1) && c.getInt(1) == 1
                val access = c.getInt(2)
                val visible = c.getInt(3) == 1
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR || !visible) continue
                if (primary) return id
                if (first == null) first = id
            }
        }
        return first
    }

    private fun rrule(repeat: String): String? = when (repeat) {
        "daily" -> "FREQ=DAILY"
        "weekly" -> "FREQ=WEEKLY"
        "monthly" -> "FREQ=MONTHLY"
        "yearly" -> "FREQ=YEARLY"
        else -> null
    }

    /** Adds or updates the event and its alert. Returns its id, or throws IllegalStateException("no_calendar"). */
    fun save(cr: ContentResolver, eventId: String, title: String, notes: String, start: Long, repeat: String, alert: Int): String {
        val rule = rrule(repeat)
        val v = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, notes)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            if (rule == null) {
                put(CalendarContract.Events.DTEND, start + LENGTH_MS)
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
            } else {
                // A repeating event has a duration instead of an end.
                putNull(CalendarContract.Events.DTEND)
                put(CalendarContract.Events.RRULE, rule)
                put(CalendarContract.Events.DURATION, "PT30M")
            }
        }
        var id = eventId.toLongOrNull()
        val updated = id != null &&
            cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0
        if (!updated) {
            val cal = calendarId(cr) ?: throw IllegalStateException("no_calendar")
            v.put(CalendarContract.Events.CALENDAR_ID, cal)
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, v) ?: throw IllegalStateException("failed")
            id = ContentUris.parseId(uri)
        }
        // One alert: replace whatever was there.
        cr.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
        cr.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, id)
            put(CalendarContract.Reminders.MINUTES, alert)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        })
        return id.toString()
    }

    fun delete(cr: ContentResolver, eventId: String): Boolean {
        val id = eventId.toLongOrNull() ?: return false
        return cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
    }
}
