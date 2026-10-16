// STUB (owner: fields agent). Texting crew.
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/models.dart';

class Sms {
  Sms._();

  /// Opens Messages with the text (and photos) ready; numbers may be empty.
  static Future<void> compose(BuildContext context, List<String> numbers, String body, {List<File> photos = const []}) async {}

  /// Sends a plain text straight from this phone (Android only). False if it couldn't.
  static Future<bool> sendDirect(String phone, String body) async => false;

  /// True where texts can be sent straight from the app (Android with permission).
  static Future<bool> canSendDirect() async => false;

  static String taskText(Project p, DailyLog l, Task t, String me, String contact) => '';

  static String noticeText(Project p, String note, String me, String contact) => '';

  /// Asks for a number when none is saved. False if cancelled.
  static Future<bool> withPhone(BuildContext context, Project p, String name) async => true;
}
