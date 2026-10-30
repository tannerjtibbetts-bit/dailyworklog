// Sends email straight from the app through Gmail (SMTP over TLS), so load securement photos
// go out the moment Done is tapped, without opening a mail app.
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:intl/intl.dart';

class MailAttachment {
  final String name;
  final Uint8List bytes;

  MailAttachment(this.name, this.bytes);
}

/// A sending problem, with a message in plain words for the person.
class MailError implements Exception {
  final String message;

  MailError(this.message);

  @override
  String toString() => message;
}

class Mailer {
  Mailer._();

  static const gmailHost = 'smtp.gmail.com', gmailPort = 465;

  /// Attachments per email are kept under this, so Gmail's 25 MB limit isn't hit after encoding.
  static const maxGroupBytes = 18 * 1024 * 1024;

  /// "a@x.com, b@y.com" -> ["a@x.com", "b@y.com"]. Also splits on ";" and spaces.
  static List<String> addresses(String typed) =>
      typed.split(RegExp(r'[,;\s]+')).map((s) => s.trim()).where((s) => s.isNotEmpty).toList();

  static bool validAddress(String a) => RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(a);

  /// Every address looks like an email address, and there's at least one.
  static bool validList(String typed) {
    final a = addresses(typed);
    return a.isNotEmpty && a.every(validAddress);
  }

  /// Splits attachments into groups of at most [limit] bytes (a bigger single photo gets its own group).
  static List<List<MailAttachment>> groups(List<MailAttachment> files, {int limit = maxGroupBytes}) {
    final out = <List<MailAttachment>>[];
    var cur = <MailAttachment>[];
    var size = 0;
    for (final f in files) {
      if (cur.isNotEmpty && size + f.bytes.length > limit) {
        out.add(cur);
        cur = [];
        size = 0;
      }
      cur.add(f);
      size += f.bytes.length;
    }
    if (cur.isNotEmpty) out.add(cur);
    return out;
  }

  /// A header value: as is if plain ASCII, otherwise RFC 2047 encoded.
  static String encodeHeader(String s) {
    if (RegExp(r'^[\x20-\x7E]*$').hasMatch(s)) return s;
    return '=?UTF-8?B?${base64.encode(utf8.encode(s))}?=';
  }

  /// Base64 in 76-character lines.
  static String base64Lines(List<int> bytes) {
    final b = base64.encode(bytes);
    final out = StringBuffer();
    for (var i = 0; i < b.length; i += 76) {
      out.write(b.substring(i, min(i + 76, b.length)));
      out.write('\r\n');
    }
    return out.toString();
  }

  /// The whole email (headers and body) with CRLF line endings.
  static String buildMessage({
    required String fromName,
    required String from,
    required List<String> to,
    required String subject,
    required String body,
    List<MailAttachment> attachments = const [],
    String? boundary,
    DateTime? date,
  }) {
    final bnd = boundary ?? 'dwl-${Random().nextInt(1 << 32).toRadixString(16)}${DateTime.now().microsecondsSinceEpoch}';
    final when = (date ?? DateTime.now()).toUtc();
    final name = fromName.trim();
    final fromHeader = name.isEmpty
        ? '<$from>'
        : RegExp(r'^[\x20-\x7E]*$').hasMatch(name)
        ? '"${name.replaceAll('"', "'")}" <$from>'
        : '${encodeHeader(name)} <$from>';
    final s = StringBuffer()
      ..write('From: $fromHeader\r\n')
      ..write('To: ${to.join(', ')}\r\n')
      ..write('Subject: ${encodeHeader(subject)}\r\n')
      ..write('Date: ${DateFormat("EEE, dd MMM yyyy HH:mm:ss", 'en_US').format(when)} +0000\r\n')
      ..write('MIME-Version: 1.0\r\n')
      ..write('Content-Type: multipart/mixed; boundary="$bnd"\r\n')
      ..write('\r\n')
      ..write('--$bnd\r\n')
      ..write('Content-Type: text/plain; charset=UTF-8\r\n')
      ..write('Content-Transfer-Encoding: base64\r\n\r\n')
      ..write(base64Lines(utf8.encode(body.replaceAll('\r\n', '\n').replaceAll('\n', '\r\n'))));
    for (final a in attachments) {
      final n = a.name.replaceAll('"', '');
      s
        ..write('--$bnd\r\n')
        ..write('Content-Type: image/jpeg; name="$n"\r\n')
        ..write('Content-Disposition: attachment; filename="$n"\r\n')
        ..write('Content-Transfer-Encoding: base64\r\n\r\n')
        ..write(base64Lines(a.bytes));
    }
    s.write('--$bnd--\r\n');
    return s.toString();
  }

  /// Sends one message. [secure] false is only for tests against a local server.
  static Future<void> send({
    required String user,
    required String password,
    required List<String> to,
    required String message,
    String host = gmailHost,
    int port = gmailPort,
    bool secure = true,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    Socket socket;
    try {
      socket = secure
          ? await SecureSocket.connect(host, port, timeout: timeout)
          : await Socket.connect(host, port, timeout: timeout);
    } catch (_) {
      throw MailError(_offline);
    }
    final replies = _Replies(socket);
    try {
      Future<String> expect(String? command, List<int> codes) async {
        if (command != null) socket.write('$command\r\n');
        final r = await replies.next().timeout(timeout, onTimeout: () => throw MailError(_offline));
        final code = int.tryParse(r.length >= 3 ? r.substring(0, 3) : '') ?? 0;
        if (!codes.contains(code)) throw _SmtpReply(code, r);
        return r;
      }

      await expect(null, [220]);
      await expect('EHLO dailyworklog', [250]);
      await expect('AUTH LOGIN', [334]);
      await expect(base64.encode(utf8.encode(user)), [334]);
      try {
        await expect(base64.encode(utf8.encode(password)), [235]);
      } on _SmtpReply catch (e) {
        throw MailError(e.code == 535 || e.code == 534 ? _badLogin : 'Gmail said: ${e.text}');
      }
      await expect('MAIL FROM:<$user>', [250]);
      for (final a in to) {
        try {
          await expect('RCPT TO:<$a>', [250, 251]);
        } on _SmtpReply catch (e) {
          throw MailError(e.code >= 550 && e.code < 560 ? "Gmail wouldn't send to $a." : 'Gmail said: ${e.text}');
        }
      }
      await expect('DATA', [354]);
      // Lines starting with "." get an extra "." (SMTP's rule); base64 never starts with one.
      final data = message.split('\r\n').map((l) => l.startsWith('.') ? '.$l' : l).join('\r\n');
      socket.write(data.endsWith('\r\n') ? data : '$data\r\n');
      await expect('.', [250]);
      try {
        socket.write('QUIT\r\n');
        await socket.flush();
      } catch (_) {}
    } on _SmtpReply catch (e) {
      throw MailError('Gmail said: ${e.text}');
    } on MailError {
      rethrow;
    } catch (_) {
      throw MailError(_offline);
    } finally {
      replies.cancel();
      socket.destroy();
    }
  }

  static const _offline = "No internet connection, or Gmail didn't answer. Try again when you have signal.";
  static const _badLogin = "Gmail didn't accept the address or app password. Check them in Email sending.";
}

class _SmtpReply implements Exception {
  final int code;
  final String text;

  _SmtpReply(this.code, this.text);
}

/// Reads complete SMTP replies (joining "250-..." continuation lines) from the socket.
class _Replies {
  final _lines = <String>[];
  final _waiting = <Completer<void>>[];
  var _buffer = '';
  var _closed = false;
  late final StreamSubscription<Uint8List> _sub;

  _Replies(Socket s) {
    _sub = s.listen((data) {
      _buffer += utf8.decode(data, allowMalformed: true);
      var i = _buffer.indexOf('\n');
      while (i >= 0) {
        _lines.add(_buffer.substring(0, i).replaceAll('\r', ''));
        _buffer = _buffer.substring(i + 1);
        i = _buffer.indexOf('\n');
      }
      _wake();
    }, onDone: () {
      _closed = true;
      _wake();
    }, onError: (_) {
      _closed = true;
      _wake();
    });
  }

  void _wake() {
    for (final c in _waiting) {
      if (!c.isCompleted) c.complete();
    }
    _waiting.clear();
  }

  Future<String> next() async {
    final parts = <String>[];
    while (true) {
      while (_lines.isEmpty) {
        if (_closed) throw MailError("No internet connection, or Gmail didn't answer. Try again when you have signal.");
        final c = Completer<void>();
        _waiting.add(c);
        await c.future;
      }
      final l = _lines.removeAt(0);
      parts.add(l.length > 4 ? l.substring(4) : '');
      if (l.length < 4 || l[3] != '-') return '${l.substring(0, min(3, l.length))} ${parts.join(' ')}'.trim();
    }
  }

  void cancel() => _sub.cancel();
}
