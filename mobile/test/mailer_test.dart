import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/services/load_email.dart';
import 'package:dailyworklog/services/mailer.dart';
import 'package:flutter_test/flutter_test.dart';

/// A tiny SMTP server: answers like Gmail and records what it was sent.
Future<(ServerSocket, List<String>)> fakeServer({String password = 'secret'}) async {
  final server = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
  final log = <String>[];
  server.listen((s) {
    var inData = false, buffer = '';
    s.write('220 smtp.test ready\r\n');
    var step = '';
    s.listen((bytes) {
      buffer += utf8.decode(bytes);
      while (buffer.contains('\r\n')) {
        final i = buffer.indexOf('\r\n');
        final line = buffer.substring(0, i);
        buffer = buffer.substring(i + 2);
        log.add(line);
        if (inData) {
          if (line == '.') {
            inData = false;
            s.write('250 2.0.0 OK queued\r\n');
          }
          continue;
        }
        if (step == 'user') {
          step = 'pass';
          s.write('334 UGFzc3dvcmQ6\r\n');
        } else if (step == 'pass') {
          step = '';
          s.write(utf8.decode(base64.decode(line)) == password
              ? '235 2.7.0 Accepted\r\n'
              : '535-5.7.8 Username and Password not accepted.\r\n535 5.7.8 Learn more\r\n');
        } else if (line.startsWith('EHLO')) {
          s.write('250-smtp.test at your service\r\n250-AUTH LOGIN PLAIN\r\n250 SMTPUTF8\r\n');
        } else if (line == 'AUTH LOGIN') {
          step = 'user';
          s.write('334 VXNlcm5hbWU6\r\n');
        } else if (line.startsWith('MAIL FROM')) {
          s.write('250 OK\r\n');
        } else if (line.startsWith('RCPT TO')) {
          s.write(line.contains('nobody@') ? '550 5.1.1 No such user\r\n' : '250 OK\r\n');
        } else if (line == 'DATA') {
          inData = true;
          s.write('354 Go ahead\r\n');
        } else if (line == 'QUIT') {
          s.write('221 Bye\r\n');
          s.close();
        }
      }
    }, onError: (_) {});
  });
  return (server, log);
}

void main() {
  test('addresses and checks', () {
    expect(Mailer.addresses(' a@x.com, b@y.ca;c@z.org '), ['a@x.com', 'b@y.ca', 'c@z.org']);
    expect(Mailer.validList('a@x.com, b@y.ca'), true);
    expect(Mailer.validList('a@x'), false);
    expect(Mailer.validList(''), false);
  });

  test('message: headers, encoded subject, base64 lines, attachments', () {
    final m = Mailer.buildMessage(
      fromName: 'Tanner Tibbetts',
      from: 'tanner@gmail.com',
      to: ['boss@aecon.com', 'b@y.ca'],
      subject: 'Load securement – bay 3',
      body: 'Line one\nLine two',
      attachments: [MailAttachment('IMG_1.jpg', Uint8List.fromList(List.generate(200, (i) => i % 256)))],
      boundary: 'BND',
      date: DateTime.utc(2026, 10, 30, 13, 5, 9),
    );
    expect(m, startsWith('From: "Tanner Tibbetts" <tanner@gmail.com>\r\nTo: boss@aecon.com, b@y.ca\r\n'));
    expect(m, contains('Subject: =?UTF-8?B?${base64.encode(utf8.encode('Load securement – bay 3'))}?=\r\n'));
    expect(m, contains('Date: Fri, 30 Oct 2026 13:05:09 +0000\r\n'));
    expect(m, contains('Content-Type: multipart/mixed; boundary="BND"'));
    expect(m, contains(base64.encode(utf8.encode('Line one\r\nLine two'))));
    expect(m, contains('Content-Disposition: attachment; filename="IMG_1.jpg"'));
    expect(m, endsWith('--BND--\r\n'));
    for (final line in m.split('\r\n')) {
      expect(line.length, lessThanOrEqualTo(998));
    }
    final b64 = base64.encode(List.generate(200, (i) => i % 256));
    expect(m, contains('${b64.substring(0, 76)}\r\n${b64.substring(76, 152)}\r\n'));
    expect(Mailer.encodeHeader('Plain subject'), 'Plain subject');
  });

  test('big sets of photos are split into several emails', () {
    MailAttachment f(int mb) => MailAttachment('x', Uint8List(mb * 1024 * 1024));
    expect(Mailer.groups([f(5), f(5), f(5), f(5)]).map((g) => g.length), [3, 1]);
    expect(Mailer.groups([f(20), f(1)]).map((g) => g.length), [1, 1]);
    expect(Mailer.groups([]), isEmpty);
  });

  test('sends through the server: login, recipients, the message', () async {
    final (server, log) = await fakeServer();
    final msg = Mailer.buildMessage(fromName: 'T', from: 't@gmail.com', to: ['boss@aecon.com'], subject: 'S', body: '.dot');
    await Mailer.send(
        user: 't@gmail.com', password: 'secret', to: ['boss@aecon.com'], message: msg,
        host: '127.0.0.1', port: server.port, secure: false);
    await server.close();
    expect(log, containsAllInOrder(['EHLO dailyworklog', 'AUTH LOGIN', base64.encode(utf8.encode('t@gmail.com')),
      base64.encode(utf8.encode('secret')), 'MAIL FROM:<t@gmail.com>', 'RCPT TO:<boss@aecon.com>', 'DATA', 'Subject: S', '.']));
  });

  test('wrong app password and refused address are explained in plain words', () async {
    final (server, _) = await fakeServer();
    final msg = Mailer.buildMessage(fromName: 'T', from: 't@gmail.com', to: ['x@y.com'], subject: 'S', body: 'b');
    await expectLater(
      Mailer.send(user: 't@gmail.com', password: 'wrong', to: ['x@y.com'], message: msg,
          host: '127.0.0.1', port: server.port, secure: false),
      throwsA(isA<MailError>().having((e) => e.message, 'message', contains("didn't accept the address or app password"))),
    );
    await expectLater(
      Mailer.send(user: 't@gmail.com', password: 'secret', to: ['nobody@y.com'], message: msg,
          host: '127.0.0.1', port: server.port, secure: false),
      throwsA(isA<MailError>().having((e) => e.message, 'message', "Gmail wouldn't send to nobody@y.com.")),
    );
    await server.close();
    await expectLater(
      Mailer.send(user: 't', password: 'p', to: ['x@y.com'], message: msg, host: '127.0.0.1', port: 1, secure: false),
      throwsA(isA<MailError>().having((e) => e.message, 'message', startsWith('No internet connection'))),
    );
  });

  test('email text and subject', () {
    final p = Project()..name = 'B44';
    final l = DailyLog()..date = '2026-10-30';
    final t = Task()..title = 'Pack up sea can 317503';
    expect(LoadEmail.defaultSubject(t), 'Load securement - Pack up sea can 317503');
    expect(LoadEmail.defaultSubject(Task()), 'Load securement - (Untitled field)');
    final b = LoadEmail.body(p, l, t, 'Tanner', DateTime(2026, 10, 30, 9, 5).millisecondsSinceEpoch, 3);
    expect(b, 'Load securement photos\nField: Pack up sea can 317503\nChecklist: Daily work log 30/10/2026 (B44)\n'
        'Secured by Tanner, 10/30/2026 at 9:05 AM\n3 photos attached.\n\nSent from Daily Work Log');
  });

  test('load securement photos are saved, not copied to new checklists', () {
    final t = Task()
      ..title = 'x'
      ..securedPhotos = [Photo(file: 'a.jpg', addedBy: 'T', addedAt: 1)]
      ..securedPhotosInReport = false;
    final again = Task.fromJson(jsonDecode(jsonEncode(t.toJson())) as Map<String, dynamic>);
    expect(again.securedPhotos.single.file, 'a.jpg');
    expect(again.securedPhotosInReport, false);
    final old = Task.fromJson({'title': 'old'});
    expect(old.securedPhotos, isEmpty);
    expect(old.securedPhotosInReport, true);
    expect(t.freshCopy().securedPhotos, isEmpty);
    expect(t.carryCopy().securedPhotosInReport, true);
  });
}
