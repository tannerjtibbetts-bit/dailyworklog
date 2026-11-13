// Opens the phone's Messages app with a text (and photos) ready to send, or sends a plain text
// straight from the app (Android only: assignment texts, and Notify notes without photos).
// An iPhone app can't send texts by itself, so on iOS texts always open the Messages composer.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';

class Sms {
  Sms._();

  static const _channel = MethodChannel('dailyworklog/sms');

  static String digits(String? phone) => phoneDigits(phone);

  /// For tests: called instead of opening Messages.
  @visibleForTesting
  static Future<void> Function(List<String> numbers, String body)? composeHook;

  /// The sms: link that opens Messages with [body] ready for [numbers].
  /// Android: sms:a;b?body=..  iOS: sms:/open?addresses=a,b&body=..
  static String smsUri(List<String> numbers, String body, {required bool ios}) {
    final clean = [for (final n in numbers) digits(n)]..removeWhere((d) => d.isEmpty);
    final b = Uri.encodeComponent(body);
    if (ios) return 'sms:/open?addresses=${clean.join(',')}&body=$b';
    return 'sms:${clean.join(';')}?body=$b';
  }

  /// Opens Messages with the text (and photos) ready; numbers may be empty.
  static Future<void> compose(
    BuildContext context,
    List<String> numbers,
    String body, {
    List<File> photos = const [],
  }) async {
    final hook = composeHook;
    if (hook != null) return hook(numbers, body);
    final files = [
      for (final f in photos)
        if (f.existsSync()) f,
    ];
    final hasNumbers = numbers.any((n) => digits(n).isNotEmpty);
    if (files.isNotEmpty) {
      // Photos go through the share sheet, which can't fill in who it's to.
      if (hasNumbers && context.mounted) {
        toast(context, 'Pick Messages, then choose who to send it to', long: true);
      }
      try {
        await SharePlus.instance.share(
          ShareParams(
            text: body,
            files: [for (final f in files) XFile(f.path, mimeType: 'image/jpeg')],
          ),
        );
      } catch (_) {
        if (context.mounted) toast(context, 'No messaging app found on this phone', long: true);
      }
      return;
    }
    final uri = Uri.parse(smsUri(numbers, body, ios: Platform.isIOS));
    var ok = false;
    try {
      ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
    if (!ok && context.mounted) toast(context, 'No messaging app found on this phone', long: true);
  }

  /// Sends a plain text straight from this phone (Android only). False if it couldn't.
  static Future<bool> sendDirect(String phone, String body) async {
    if (!Platform.isAndroid) return false;
    if (!await canSendDirect() && !await requestDirect()) return false;
    try {
      return await _channel.invokeMethod<bool>('send', {'phone': digits(phone), 'body': body}) ?? false;
    } catch (_) {
      return false;
    }
  }

  /// True where texts can be sent straight from the app (Android with permission).
  static Future<bool> canSendDirect() async {
    if (!Platform.isAndroid) return false;
    try {
      return await _channel.invokeMethod<bool>('hasPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Asks for permission to send texts (Android). True if granted.
  static Future<bool> requestDirect() async {
    if (!Platform.isAndroid) return false;
    try {
      return await _channel.invokeMethod<bool>('requestPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Text for a field assignment, matching the web version.
  static String taskText(Project p, DailyLog l, Task t, String me, String contact) {
    final s = StringBuffer(l.displayTitle());
    if (p.name.isNotEmpty) s.write(' (${p.name})');
    s.write('\n\nAssigned to you: ${t.title}'); // blank line under the title
    // Field notes stay in the app; texts only carry the photo reminder.
    if (t.photosRequired) s.write('\n\nPhotos required.');
    s.write(signOff(me, contact));
    return s.toString();
  }

  /// Text to the person who wanted to know when a field is done.
  static String doneText(Project p, DailyLog l, Task t, String me, String contact) {
    final s = StringBuffer(l.displayTitle());
    if (p.name.isNotEmpty) s.write(' (${p.name})');
    s.write('\n\nCompleted: ${t.title}');
    s.write('\nDone by $me, ${Fmt.stamp(t.completedAt)}');
    s.write(signOff(me, contact));
    return s.toString();
  }

  static String noticeText(Project p, String note, String me, String contact) =>
      note.trim() + signOff(me + (p.name.isEmpty ? '' : ' (${p.name})'), contact);

  /// A divider line, then who sent it and how to reach them, so the message and the
  /// contact details are clearly separate. Plain hyphens keep it a normal-length text.
  static String signOff(String from, String contact) => '\n\n--------------------\nFrom: $from${contactLine(contact)}';

  /// "\nCall or text me at 1-226-555-0123", or nothing when no number is set.
  static String contactLine(String? contact) {
    if (contact == null || contact.trim().isEmpty) return '';
    return '\nCall or text me at ${formatPhone(contact)}';
  }

  /// 12265550123 -> 1-226-555-0123; 2265550123 -> 226-555-0123; anything else as typed.
  static String formatPhone(String phone) {
    final d = phone.replaceAll(RegExp(r'[^0-9]'), '');
    if (d.length == 11 && d.startsWith('1')) {
      return '1-${d.substring(1, 4)}-${d.substring(4, 7)}-${d.substring(7)}';
    }
    if (d.length == 10) return '${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}';
    return phone.trim();
  }

  /// Asks for a number when none is saved. False if cancelled.
  static Future<bool> withPhone(BuildContext context, Project p, String name) async {
    if (p.phoneOf(name).isNotEmpty) return true;
    final number = await promptPhone(context, 'Phone number for $name', '');
    if (number == null) return false;
    p.phones[name] = number;
    Store.instance.save();
    return true;
  }
}
