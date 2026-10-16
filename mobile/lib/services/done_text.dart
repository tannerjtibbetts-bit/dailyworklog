// "Text when completed": when a field is ticked done, texts the person chosen on its field page.
// Android sends it straight from the app after 5 seconds (with Undo), like assignment texts;
// iPhone (or no permission) opens Messages with the text ready.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/models.dart';
import '../data/store.dart';
import 'sms.dart';

/// True when the field has someone to text when it's done.
bool hasDoneText(Task t) => t.doneTextPhone.replaceAll(RegExp(r'[^0-9]'), '').length >= 7;

/// Name shown in messages: who it's to, or their number if no name was given.
String doneTextWho(Task t) =>
    t.doneTextName.trim().isNotEmpty ? t.doneTextName.trim() : Sms.formatPhone(t.doneTextPhone);

/// Call right after a field is ticked. Does nothing if it was unticked or nobody is set.
/// [margin] floats the message above something (the checklist's mic).
Future<void> textDone(BuildContext context, Project p, DailyLog l, Task t, {EdgeInsets? margin}) async {
  if (!t.done || !hasDoneText(t)) return;
  final store = Store.instance;
  final name = doneTextWho(t), phone = t.doneTextPhone;
  final body = Sms.doneText(p, l, t, store.me(), store.contactPhone);
  if (!Platform.isAndroid) {
    await Sms.compose(context, [phone], body);
    return;
  }
  if (!await Sms.canSendDirect() && !await Sms.requestDirect()) {
    if (context.mounted) await Sms.compose(context, [phone], body);
    return;
  }
  if (!context.mounted) return;
  final messenger = ScaffoldMessenger.of(context);
  SnackBar bar(String text, int seconds, [SnackBarAction? action]) => SnackBar(
    content: Text(text),
    behavior: margin == null ? null : SnackBarBehavior.floating,
    margin: margin,
    duration: Duration(seconds: seconds),
    action: action,
  );
  var cancelled = false;
  // Keeps going if the screen is closed, like assignment texts.
  final timer = Timer(const Duration(seconds: 5), () async {
    if (cancelled) return;
    final ok = await Sms.sendDirect(phone, body);
    if (ok) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(bar('Texted $name', 2));
      return;
    }
    messenger.hideCurrentSnackBar();
    // Couldn't send: open Messages with the text ready instead.
    await Sms.compose(context, [phone], body);
  });
  messenger
    ..hideCurrentSnackBar()
    ..showSnackBar(
      bar(
        "Texting $name that it's done…",
        5,
        SnackBarAction(
          label: 'Undo',
          onPressed: () {
            cancelled = true;
            timer.cancel();
            messenger.showSnackBar(bar('Text to $name cancelled', 2));
          },
        ),
      ),
    );
}
