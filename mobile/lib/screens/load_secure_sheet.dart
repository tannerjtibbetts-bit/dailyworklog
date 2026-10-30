// The Load securement pop-up: take photos, type a subject, tap Done, and the photos are emailed
// straight away through Gmail. Also the Email sending setup and the "send to" address.
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/models.dart';
import '../data/store.dart';
import '../services/load_email.dart';
import '../services/mailer.dart';
import '../services/photos.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

/// Asks who gets load securement photos. Returns true if an address was saved.
Future<bool> changeLoadEmailTo(BuildContext context) async {
  final store = Store.instance;
  final v = await prompt(
    context,
    'Send load securement photos to',
    'Email address',
    store.loadEmailTo,
    'Save',
    keyboard: TextInputType.emailAddress,
    caps: TextCapitalization.none,
    validate: (s) => Mailer.validList(s) ? null : 'Enter a valid email address',
  );
  if (v == null) return false;
  store.loadEmailTo = Mailer.addresses(v).join(', ');
  store.save();
  return true;
}

/// The Gmail address and app password the app sends from. Returns true if saved and set up.
Future<bool> emailSetupDialog(BuildContext context) async {
  final store = Store.instance;
  final from = TextEditingController(text: store.mailFrom);
  final pass = TextEditingController(text: await MailSettings.password());
  final to = TextEditingController(text: store.loadEmailTo);
  if (!context.mounted) return false;
  String? fromError, passError, toError;
  var busy = false;
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => StatefulBuilder(
      builder: (c, set) {
        bool check() {
          fromError = Mailer.validAddress(from.text.trim()) ? null : 'Enter your Gmail address';
          passError = pass.text.replaceAll(RegExp(r'\s'), '').length < 8 ? 'Enter the app password' : null;
          toError = to.text.trim().isEmpty || Mailer.validList(to.text) ? null : 'Enter a valid email address';
          set(() {});
          return fromError == null && passError == null && toError == null;
        }

        return AlertDialog(
          title: const Text('Email sending'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'The app sends load securement photos from your Gmail. You need a Gmail "app password" '
                  '(not your normal password). In your Google account: Security > 2-Step Verification '
                  '(turn it on) > App passwords > make one named Daily Work Log, then type the 16 letters here.',
                  style: TextStyle(color: C.grey, fontSize: 15),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: from,
                  keyboardType: TextInputType.emailAddress,
                  textCapitalization: TextCapitalization.none,
                  autocorrect: false,
                  decoration: InputDecoration(labelText: 'Your Gmail address', errorText: fromError),
                ),
                TextField(
                  controller: pass,
                  obscureText: true,
                  autocorrect: false,
                  enableSuggestions: false,
                  decoration: InputDecoration(labelText: 'App password', errorText: passError),
                ),
                // Who gets the photos: remembered until changed here or with Change in the pop-up.
                TextField(
                  controller: to,
                  keyboardType: TextInputType.emailAddress,
                  textCapitalization: TextCapitalization.none,
                  autocorrect: false,
                  decoration: InputDecoration(labelText: 'Send load securement photos to', errorText: toError),
                ),
                if (busy)
                  const Padding(padding: EdgeInsets.only(top: 14), child: LinearProgressIndicator()),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
            TextButton(
              onPressed: busy
                  ? null
                  : () async {
                      if (!check()) return;
                      set(() => busy = true);
                      try {
                        await LoadEmail.sendTest(from.text.trim(), pass.text);
                        if (c.mounted) toast(c, 'Test email sent');
                      } on MailError catch (e) {
                        if (c.mounted) await _errorDialog(c, e.message, null);
                      }
                      if (c.mounted) set(() => busy = false);
                    },
              child: const Text('Send a test'),
            ),
            TextButton(
              onPressed: busy
                  ? null
                  : () {
                      if (check()) Navigator.pop(c, true);
                    },
              child: const Text('Save'),
            ),
          ],
        );
      },
    ),
  );
  if (ok != true) return false;
  store.mailFrom = from.text.trim();
  store.loadEmailTo = Mailer.addresses(to.text).join(', ');
  store.save();
  await MailSettings.setPassword(pass.text);
  return true;
}

Future<void> _errorDialog(BuildContext context, String reason, VoidCallback? retry) => showDialog<void>(
  context: context,
  builder: (c) => AlertDialog(
    title: const Text("Couldn't send the email"),
    content: Text(reason),
    actions: [
      if (retry != null)
        TextButton(
          onPressed: () {
            Navigator.pop(c);
            retry();
          },
          child: const Text('Try again'),
        ),
      TextButton(onPressed: () => Navigator.pop(c), child: const Text('Close')),
    ],
  ),
);

/// Sends in the background, with "Sending…", "Sent" and a Try again dialog if it fails.
Future<void> sendLoadPhotos(BuildContext context, Project p, DailyLog l, Task t, List<Photo> photos, String subject) async {
  final to = Store.instance.loadEmailTo;
  final n = photos.length;
  BuildContext? ui() => context.mounted ? context : appNavigator.currentContext;
  final c0 = ui();
  if (c0 != null) toast(c0, 'Sending ${n == 1 ? '1 photo' : '$n photos'} to $to…', long: true);
  try {
    await LoadEmail.send(project: p, log: l, task: t, photos: photos, subject: subject, to: to);
    final c = ui();
    if (c != null && c.mounted) toast(c, 'Sent ${n == 1 ? '1 photo' : '$n photos'} to $to', long: true);
  } on MailError catch (e) {
    final c = ui();
    if (c != null && c.mounted) {
      await _errorDialog(c, e.message, () => sendLoadPhotos(c, p, l, t, photos, subject));
    }
  }
}

/// The Load securement pop-up for field [t].
Future<void> openLoadSecurement(BuildContext context, Project p, DailyLog l, Task t) async {
  final store = Store.instance;
  final taken = <String>[]; // photo files taken in this pop-up
  final subject = TextEditingController(text: LoadEmail.defaultSubject(t));
  var setUp = await MailSettings.isSetUp();
  if (!context.mounted) return;

  // What Done does after the pop-up closes.
  final result = await showDialog<String>(
    context: context,
    barrierDismissible: false,
    builder: (c) => StatefulBuilder(
      builder: (c, set) {
        Future<void> add(Future<List<String>> pick) async {
          final names = await pick;
          set(() => taken.addAll(names));
        }

        Future<void> done() async {
          if (taken.isNotEmpty) {
            if (!Mailer.validList(store.loadEmailTo) && !await changeLoadEmailTo(c)) return;
            if (!await MailSettings.isSetUp()) {
              if (!c.mounted || !await emailSetupDialog(c)) return;
              set(() => setUp = true);
            }
          }
          if (c.mounted) Navigator.pop(c, 'done');
        }

        final to = store.loadEmailTo;
        return AlertDialog(
          title: const Text('Load securement'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  'Take photos of the load, type a subject, then tap Done. The photos are emailed straight away.',
                  style: TextStyle(color: C.grey, fontSize: 15),
                ),
                const SizedBox(height: 12),
                if (taken.isNotEmpty)
                  SizedBox(
                    height: 92,
                    child: ListView(
                      scrollDirection: Axis.horizontal,
                      children: [
                        for (final n in taken)
                          Padding(
                            padding: const EdgeInsets.only(right: 8, top: 6),
                            child: Stack(
                              clipBehavior: Clip.none,
                              children: [
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(10),
                                  child: Image.file(
                                    File(store.photoFile(n).path),
                                    width: 80,
                                    height: 80,
                                    fit: BoxFit.cover,
                                    cacheWidth: 240,
                                    errorBuilder: (_, _, _) => Container(width: 80, height: 80, color: C.sky),
                                  ),
                                ),
                                Positioned(
                                  right: -8,
                                  top: -8,
                                  child: IconButton(
                                    tooltip: 'Remove photo',
                                    visualDensity: VisualDensity.compact,
                                    icon: const Icon(Icons.cancel, color: C.red),
                                    onPressed: () => set(() {
                                      taken.remove(n);
                                      store.deletePhoto(Photo(file: n));
                                    }),
                                  ),
                                ),
                              ],
                            ),
                          ),
                      ],
                    ),
                  ),
                FilledButton.icon(
                  onPressed: () => add(Photos.take(c).then((n) => n == null ? <String>[] : [n])),
                  icon: const Icon(Icons.photo_camera),
                  label: Text(taken.isEmpty ? 'Take photo' : 'Take another'),
                ),
                OutlinedButton.icon(
                  onPressed: () => add(Photos.pick(c)),
                  icon: const Icon(Icons.photo_library_outlined),
                  label: const Text('From gallery'),
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'To: ${to.isEmpty ? 'Not set' : to}',
                        style: TextStyle(fontSize: 16, color: to.isEmpty ? C.greyLight : C.text),
                      ),
                    ),
                    TextButton(
                      onPressed: () async {
                        await changeLoadEmailTo(c);
                        set(() {});
                      },
                      child: const Text('Change'),
                    ),
                  ],
                ),
                TextField(
                  controller: subject,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(labelText: 'Subject'),
                ),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        setUp ? 'From: ${store.mailFrom}' : 'From: email sending isn’t set up',
                        style: const TextStyle(fontSize: 14, color: C.grey),
                      ),
                    ),
                    TextButton(
                      onPressed: () async {
                        final ok = await emailSetupDialog(c);
                        if (ok) set(() => setUp = true);
                      },
                      child: Text(setUp ? 'Change' : 'Set up'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            if (t.loadSecured)
              TextButton(onPressed: () => Navigator.pop(c, 'unsecure'), child: const Text('Mark not secured')),
            TextButton(onPressed: () => Navigator.pop(c, 'cancel'), child: const Text('Cancel')),
            FilledButton(onPressed: done, child: const Text('Done')),
          ],
        );
      },
    ),
  );

  if (result == 'unsecure') {
    t.loadSecured = false;
    t.securedBy = '';
    t.securedAt = 0;
  }
  if (result != 'done') {
    for (final n in taken) {
      store.deletePhoto(Photo(file: n));
    }
    if (result == 'unsecure') store.save();
    return;
  }
  final now = nowMs();
  if (!t.loadSecured) {
    t.loadSecured = true;
    t.securedBy = store.me();
    t.securedAt = now;
  }
  final photos = [for (final n in taken) Photo(file: n, addedBy: store.me(), addedAt: now)];
  t.securedPhotos.addAll(photos);
  store.save();
  if (!context.mounted) return;
  if (photos.isEmpty) {
    toast(context, 'Load secured');
    return;
  }
  final s = subject.text.trim().isEmpty ? LoadEmail.defaultSubject(t) : subject.text.trim();
  await sendLoadPhotos(context, p, l, t, photos, s);
}
