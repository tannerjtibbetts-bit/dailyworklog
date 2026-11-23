// Link reminders in the phone's own calendar (Android calendar provider, iPhone Calendar), written by
// the app itself after asking once for calendar access. See android MainActivity / CalendarReminders.kt
// and ios AppDelegate.swift for the native side.
import 'package:flutter/services.dart';

import '../data/models.dart';

class CalendarReminders {
  CalendarReminders._();

  static const _channel = MethodChannel('dailyworklog/calendar');

  /// Puts [l]'s reminder in the calendar (or updates it). Returns null when saved, otherwise what went wrong
  /// in words the person can act on.
  static Future<String?> save(Link l) async {
    final r = l.reminder;
    if (r == null) return null;
    try {
      final id = await _channel.invokeMethod<String>('save', {
        'eventId': r.eventId,
        'title': l.title,
        'url': l.url,
        'notes': 'Daily Work Log link: ${l.title}\n${l.url}',
        'start': r.start,
        'repeat': r.repeat,
        'alert': r.alert,
        'alerts': r.alertList(),
      });
      if (id == null || id.isEmpty) return "Couldn't save the reminder in your calendar.";
      r.eventId = id;
      return null;
    } on PlatformException catch (e) {
      return switch (e.code) {
        'denied' => 'Calendar access is off. Turn on Calendar for Daily Work Log in your phone Settings, then try again.',
        'no_calendar' => 'No calendar was found on this phone to add the reminder to.',
        _ => "Couldn't save the reminder in your calendar.",
      };
    } on MissingPluginException {
      return "This phone can't add calendar reminders.";
    }
  }

  /// Takes [l]'s reminder out of the calendar. Quietly does nothing if it isn't there.
  static Future<void> remove(LinkReminder? r) async {
    if (r == null || r.eventId.isEmpty) return;
    try {
      await _channel.invokeMethod<bool>('delete', {'eventId': r.eventId});
    } catch (_) {}
  }
}
