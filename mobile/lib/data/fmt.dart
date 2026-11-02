// Date and text formatting, matching the Android app.
import 'package:intl/intl.dart';

class Fmt {
  Fmt._();

  static DateTime? _parseIso(String iso) {
    try {
      return DateFormat('yyyy-MM-dd', 'en_US').parseStrict(iso);
    } catch (_) {
      return null;
    }
  }

  /// yyyy-MM-dd to dd/MM/yyyy, matching the old app's titles.
  static String logDate(String iso) {
    final d = _parseIso(iso);
    return d == null ? iso : DateFormat('dd/MM/yyyy', 'en_US').format(d);
  }

  /// "09/25/2026 at 2:03 PM"
  static String stamp(int t) =>
      DateFormat("MM/dd/yyyy 'at' h:mm a", 'en_US').format(DateTime.fromMillisecondsSinceEpoch(t));

  /// "Sep 25, 2026 at 2:03 PM"
  static String longStamp(int t) =>
      DateFormat("MMM d, yyyy 'at' h:mm a", 'en_US').format(DateTime.fromMillisecondsSinceEpoch(t));

  /// month is 1-based here (Dart dates).
  static String iso(int year, int month, int day) =>
      '${year.toString().padLeft(4, '0')}-${month.toString().padLeft(2, '0')}-${day.toString().padLeft(2, '0')}';

  static String isoOf(DateTime d) => iso(d.year, d.month, d.day);

  /// The Friday of the coming weekend (this weekend if it's already Fri, Sat or Sun).
  static String upcomingFriday([DateTime? now]) {
    final n = now ?? DateTime.now();
    final dow = n.weekday; // Mon=1 .. Sun=7
    final shift = dow == DateTime.saturday
        ? -1
        : dow == DateTime.sunday
            ? -2
            : DateTime.friday - dow;
    return isoOf(DateTime(n.year, n.month, n.day + shift));
  }

  /// "Fri Oct 2" for the Friday + offset days.
  static String weekendDay(String fridayIso, int offset) {
    final d = _parseIso(fridayIso);
    if (d == null) return const ['Fri', 'Sat', 'Sun'][offset.clamp(0, 2)];
    return DateFormat('EEE MMM d', 'en_US').format(DateTime(d.year, d.month, d.day + offset));
  }

  static String todayIso() => isoOf(DateTime.now());

  /// "Tue Oct 27, 2026" (the text as given if it isn't a date).
  static String longDay(String iso) {
    final d = _parseIso(iso);
    return d == null ? iso : DateFormat('EEE MMM d, yyyy', 'en_US').format(d);
  }

  static String initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty || parts[0].isEmpty) return '?';
    final a = parts[0].substring(0, 1);
    final b = parts.length > 1 ? parts.last.substring(0, 1) : '';
    return (a + b).toUpperCase();
  }

  static String join(List<String> names) => names.join(', ');

  static String fileSafe(String s) {
    final out = s.replaceAll(RegExp(r'[^A-Za-z0-9 _-]'), '-').trim();
    return out.isEmpty ? 'report' : out;
  }

  static String capitalize(String s) {
    s = s.trim();
    if (s.isEmpty) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  static String plural(int n, String one, String many) => '$n ${n == 1 ? one : many}';
}
