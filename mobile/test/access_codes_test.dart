// Port of AccessTest (NoteTest.java) plus interop with codes made by the Android app.
//
// test/fixtures/access_interop_java.json was made by the Java AccessCodes (InteropGen.java).
// Set DWL_INTEROP_OUT=<file> to also write codes made here, for InteropVerify.java to check.
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dailyworklog/access/access_codes.dart';
import 'package:flutter_test/flutter_test.dart';

Uint8List b(String s) => Uint8List.fromList(utf8.encode(s));

void main() {
  group('AccessTest', () {
    final owner = AccessCodes.generateKeyPair(), stranger = AccessCodes.generateKeyPair();
    final built = AccessCodes.publicKey(AccessCodes.encodeKey(owner.publicKey))!;
    final req = AccessCodes.requestCode('install-123', 'Sam Maint');
    final sms = 'Daily Work Log access request from Sam Maint:\n$req';
    final ok = AccessCodes.approvalCode(
        '2027-10-16', AccessCodes.sign(owner.privateKey, AccessCodes.payload('install-123', 'Sam Maint', '2027-10-16')), '');
    final reply = 'Your Daily Work Log approval. Copy this whole text, then paste it on the sign-in screen:\n$ok';

    test('request round-trip', () {
      final r = AccessCodes.parseRequest(sms);
      expect(r?.installId, 'install-123');
      expect(r?.name, 'Sam Maint');
    });

    test('approvals', () {
      final ap = AccessCodes.parseApproval(reply);
      expect(AccessCodes.verify(built, 'install-123', 'Sam Maint', ap), isTrue, reason: "owner's approval accepted");
      expect(AccessCodes.verify(built, 'install-999', 'Sam Maint', ap), isFalse, reason: 'same code on another phone rejected');
      expect(AccessCodes.verify(built, 'install-123', 'Someone Else', ap), isFalse, reason: 'different name rejected');
      final forged = AccessCodes.approvalCode('2027-10-16',
          AccessCodes.sign(stranger.privateKey, AccessCodes.payload('install-123', 'Sam Maint', '2027-10-16')), null);
      expect(AccessCodes.verify(built, 'install-123', 'Sam Maint', AccessCodes.parseApproval(forged)), isFalse,
          reason: 'forged approval rejected');
      expect(
          AccessCodes.verify(
              built, 'install-123', 'Sam Maint', AccessCodes.parseApproval(ok.replaceAll('MjAyNy0xMC0xNnw', 'MjA5OS0xMC0xNnw'))),
          isFalse,
          reason: 'edited expiry rejected');
      expect(AccessCodes.parseApproval('hello'), isNull);
      expect(AccessCodes.parseRequest('DWL-REQ-!!'), isNull);
    });

    test('hand over: owner -> work phone -> another phone; old approvals keep working', () {
      final work = AccessCodes.generateKeyPair();
      final wurl = AccessCodes.keyUrl(work.publicKey);
      final chain = AccessCodes.extendChain('', wurl, AccessCodes.sign(owner.privateKey, AccessCodes.handoverPayload(wurl)));
      final text = 'Daily Work Log owner handover:\n${AccessCodes.handoverCode(AccessCodes.privateKeyPkcs8(work.privateKey), chain, '482913')}';
      expect(AccessCodes.looksLikeHandover(text), isTrue);
      expect(AccessCodes.openHandover(text, '482914'), isNull, reason: 'wrong code rejected');
      final got = AccessCodes.openHandover(text, '482913')!;
      expect(got.chain, chain);
      final wk = AccessCodes.privateKeyFromPkcs8(got.privateKeyPkcs8)!;
      expect(AccessCodes.sameKey(AccessCodes.chainEnd(built, got.chain), work.publicKey), isTrue, reason: 'chain leads to work phone');
      final ok2 = AccessCodes.approvalCode(
          '2027-11-01', AccessCodes.sign(wk, AccessCodes.payload('install-777', 'New Guy', '2027-11-01')), got.chain);
      expect(AccessCodes.verify(built, 'install-777', 'New Guy', AccessCodes.parseApproval('Approval:\n$ok2\n')), isTrue);
      expect(AccessCodes.verify(built, 'install-123', 'Sam Maint', AccessCodes.parseApproval(reply)), isTrue,
          reason: 'old approval still valid');
      expect(
          AccessCodes.verify(built, 'install-777', 'New Guy',
              AccessCodes.parseApproval(AccessCodes.approvalCode('2027-11-01', AccessCodes.parseApproval(ok2)!.signature, ''))),
          isFalse,
          reason: 'work signature without chain rejected');
      final fake = AccessCodes.extendChain('', wurl, AccessCodes.sign(stranger.privateKey, AccessCodes.handoverPayload(wurl)));
      expect(AccessCodes.chainEnd(built, fake), isNull, reason: 'fake handover rejected');
      final bad = text.substring(0, text.length - 3) + (text.endsWith('A') ? 'BBB' : 'AAA');
      expect(AccessCodes.openHandover(bad, '482913'), isNull, reason: 'edited handover rejected');
      final back = AccessCodes.generateKeyPair();
      final burl = AccessCodes.keyUrl(back.publicKey);
      final chain2 = AccessCodes.extendChain(got.chain, burl, AccessCodes.sign(wk, AccessCodes.handoverPayload(burl)));
      expect(AccessCodes.sameKey(AccessCodes.chainEnd(built, chain2), back.publicKey), isTrue, reason: 'second handover');
      expect(AccessCodes.verify(built, 'install-777', 'New Guy', AccessCodes.parseApproval(ok2)), isTrue);
    });

    test('keys round-trip', () {
      final der = AccessCodes.publicKeyDer(owner.publicKey);
      expect(der.length, 91);
      expect(AccessCodes.sameKey(AccessCodes.publicKeyFromDer(der), owner.publicKey), isTrue);
      final pk = AccessCodes.privateKeyFromPkcs8(AccessCodes.privateKeyPkcs8(owner.privateKey))!;
      expect(pk.d, owner.privateKey.d);
      expect(AccessCodes.sameKey(AccessCodes.publicOf(pk), owner.publicKey), isTrue);
      expect(AccessCodes.publicKey(''), isNull);
      expect(AccessCodes.publicKey('not a key'), isNull);
    });
  });

  group('interop with the Android app', () {
    final j = jsonDecode(File('test/fixtures/access_interop_java.json').readAsStringSync()) as Map<String, dynamic>;
    final javaOwner = AccessCodes.publicKey(j['ownerKey'] as String)!;

    test('Java keys and codes read here', () {
      expect(AccessCodes.encodeKey(javaOwner), j['ownerKey']);
      expect(AccessCodes.keyUrl(AccessCodes.publicKey(j['workKey'] as String)!), j['workKeyUrl']);
      expect(AccessCodes.requestCode('install-123', '  Sam Maint '), (j['request'] as String).split('\n').last);
      expect(AccessCodes.requestCode('install-456', 'José Ñuñez'), j['requestUnicode']);
      expect(AccessCodes.parseRequest(j['requestUnicode'] as String)?.name, 'José Ñuñez');
      expect(base64.encode(AccessCodes.payload('install-123', ' Sam Maint\t', '2027-10-16')), j['payload']);
      final r = AccessCodes.parseRequest(j['request'] as String)!;
      expect([r.installId, r.name], ['install-123', 'Sam Maint']);
    });

    test('Java approvals verify', () {
      final a = AccessCodes.parseApproval(j['approval'] as String);
      expect(AccessCodes.verify(javaOwner, 'install-123', 'Sam Maint', a), isTrue);
      expect(AccessCodes.verify(javaOwner, 'install-124', 'Sam Maint', a), isFalse);
      final w = AccessCodes.parseApproval(j['workApproval'] as String)!;
      expect(w.chain, j['chain']);
      expect(AccessCodes.verify(javaOwner, 'install-777', 'New Guy', w), isTrue);
      expect(AccessCodes.sameKey(AccessCodes.chainEnd(javaOwner, j['chain'] as String), AccessCodes.publicKey(j['workKey'] as String)),
          isTrue);
    });

    test('Java PKCS8 private key reads and signs', () {
      final k = AccessCodes.privateKeyFromPkcs8(base64.decode(j['ownerPkcs8'] as String))!;
      expect(AccessCodes.sameKey(AccessCodes.publicOf(k), javaOwner), isTrue);
      final sig = AccessCodes.sign(k, AccessCodes.payload('install-x', 'X', '2030-01-01'));
      expect(AccessCodes.verifySignature(javaOwner, AccessCodes.payload('install-x', 'X', '2030-01-01'), sig), isTrue);
    });

    test('Java handover opens here, then this side approves and hands over again', () {
      final text = j['handover'] as String;
      expect(AccessCodes.openHandover(text, '482914'), isNull);
      final h = AccessCodes.openHandover(text, j['handoverCode'] as String)!;
      expect(h.chain, j['chain']);
      final work = AccessCodes.privateKeyFromPkcs8(h.privateKeyPkcs8)!;
      final end = AccessCodes.chainEnd(javaOwner, h.chain)!;
      expect(AccessCodes.verifySignature(end, b('check'), AccessCodes.sign(work, b('check'))), isTrue);

      // Codes for InteropVerify.java
      final dartOwner = AccessCodes.generateKeyPair();
      final dartApproval = AccessCodes.approvalCode('2027-12-01',
          AccessCodes.sign(dartOwner.privateKey, AccessCodes.payload('install-d1', 'Dart User', '2027-12-01')), '');
      final dartWorkApproval = AccessCodes.approvalCode('2027-12-02',
          AccessCodes.sign(work, AccessCodes.payload('install-888', 'Flutter Person', '2027-12-02')), h.chain);
      expect(AccessCodes.verify(javaOwner, 'install-888', 'Flutter Person', AccessCodes.parseApproval(dartWorkApproval)), isTrue);
      final next = AccessCodes.generateKeyPair();
      final nurl = AccessCodes.keyUrl(next.publicKey);
      final chain2 = AccessCodes.extendChain(h.chain, nurl, AccessCodes.sign(work, AccessCodes.handoverPayload(nurl)));
      final dartHandover = 'Daily Work Log owner handover:\n'
          '${AccessCodes.handoverCode(AccessCodes.privateKeyPkcs8(next.privateKey), chain2, '135790')}';
      final dartApproval2 = AccessCodes.approvalCode('2028-01-05',
          AccessCodes.sign(next.privateKey, AccessCodes.payload('install-999', 'Third Phone', '2028-01-05')), chain2);
      expect(AccessCodes.verify(javaOwner, 'install-999', 'Third Phone', AccessCodes.parseApproval(dartApproval2)), isTrue);

      final out = Platform.environment['DWL_INTEROP_OUT'];
      if (out != null && out.isNotEmpty) {
        final m = {
          'javaOwnerKey': j['ownerKey'] as String,
          'dartOwnerKey': AccessCodes.encodeKey(dartOwner.publicKey),
          'dartApproval': dartApproval,
          'dartRequest': 'Daily Work Log access request from Dart User:\n${AccessCodes.requestCode('install-d1', 'Dart User')}',
          'dartRequestUnicode': AccessCodes.requestCode('install-d3', 'José Ñuñez'),
          'dartWorkApproval': dartWorkApproval,
          'dartHandover': dartHandover,
          'dartChain2': chain2,
          'dartApproval2': dartApproval2,
        };
        File(out).writeAsStringSync(m.entries.map((e) => '${e.key}=${base64.encode(utf8.encode(e.value))}').join('\n'));
      }
    });
  });
}
