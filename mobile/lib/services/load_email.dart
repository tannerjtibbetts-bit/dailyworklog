// Load securement photos by email: the Gmail settings (the app password is kept in the phone's
// secure storage, never in worklog.json or backups) and building and sending the email.
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:image/image.dart' as img;

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import 'mailer.dart';

class MailSettings {
  MailSettings._();

  static const _storage = FlutterSecureStorage();
  static const _key = 'mailAppPassword';

  static Future<String> password() async {
    try {
      return await _storage.read(key: _key) ?? '';
    } catch (_) {
      return '';
    }
  }

  /// Spaces are dropped: Google shows the app password in groups of four.
  static Future<void> setPassword(String p) => _storage.write(key: _key, value: p.replaceAll(RegExp(r'\s'), ''));

  static Future<bool> isSetUp() async => Store.instance.mailFrom.trim().isNotEmpty && (await password()).isNotEmpty;
}

class LoadEmail {
  LoadEmail._();

  /// "Load securement - Pack up sea can 317503"
  static String defaultSubject(Task t) =>
      'Load securement - ${t.title.trim().isEmpty ? '(Untitled field)' : t.title.trim()}';

  static String body(Project p, DailyLog log, Task t, String me, int at, int count) {
    final title = t.title.trim().isEmpty ? '(Untitled field)' : t.title.trim();
    return 'Load securement photos\n'
        'Field: $title\n'
        'Checklist: ${log.displayTitle()}${p.name.isEmpty ? '' : ' (${p.name})'}\n'
        'Secured by $me, ${Fmt.stamp(at)}\n'
        '${Fmt.plural(count, 'photo', 'photos')} attached.\n'
        '\n'
        'Sent from Daily Work Log';
  }

  /// Sends the photos (shrunk to 1600 px) to [to], split into several emails if they're large.
  /// Throws [MailError] with a plain-words reason.
  static Future<void> send({
    required Project project,
    required DailyLog log,
    required Task task,
    required List<Photo> photos,
    required String subject,
    required String to,
  }) async {
    final store = Store.instance;
    final from = store.mailFrom.trim();
    final pass = await MailSettings.password();
    if (from.isEmpty || pass.isEmpty) throw MailError('Set up Email sending first.');
    final files = await compute(_shrinkAll, [for (final p in photos) store.photoFile(p.file).path]);
    final attachments = [
      for (var i = 0; i < photos.length; i++)
        if (files[i] != null) MailAttachment(photos[i].file, files[i]!),
    ];
    if (attachments.isEmpty) throw MailError("The photos couldn't be read.");
    final me = store.me();
    final at = nowMs();
    final groups = Mailer.groups(attachments);
    final recipients = Mailer.addresses(to);
    for (var g = 0; g < groups.length; g++) {
      final s = groups.length == 1 ? subject : '$subject (${g + 1} of ${groups.length})';
      final message = Mailer.buildMessage(
        fromName: me,
        from: from,
        to: recipients,
        subject: s,
        body: body(project, log, task, me, at, groups[g].length),
        attachments: groups[g],
      );
      await Mailer.send(user: from, password: pass, to: recipients, message: message);
    }
  }

  /// A short test email to the sender's own address.
  static Future<void> sendTest(String from, String password) async {
    final message = Mailer.buildMessage(
      fromName: Store.instance.me(),
      from: from,
      to: [from],
      subject: 'Daily Work Log test',
      body: 'Email sending works.',
    );
    await Mailer.send(user: from, password: password.replaceAll(RegExp(r'\s'), ''), to: [from], message: message);
  }
}

/// JPEGs at most 1600 px on the long side, quality 85 (null if a photo can't be read). Runs in the background.
List<Uint8List?> _shrinkAll(List<String> paths) => [for (final p in paths) shrinkForEmail(p)];

Uint8List? shrinkForEmail(String path) {
  try {
    final bytes = File(path).readAsBytesSync();
    final im = img.decodeImage(bytes);
    if (im == null) return null;
    final upright = img.bakeOrientation(im);
    final long = upright.width > upright.height ? upright.width : upright.height;
    if (long <= 1600 && bytes.length < 1500000) return bytes;
    final small = long <= 1600
        ? upright
        : upright.width >= upright.height
        ? img.copyResize(upright, width: 1600)
        : img.copyResize(upright, height: 1600);
    return Uint8List.fromList(img.encodeJpg(small, quality: 85));
  } catch (_) {
    return null;
  }
}
