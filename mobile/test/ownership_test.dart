import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:dailyworklog/about/ownership.dart';
import 'package:dailyworklog/access/access_codes.dart';
import 'package:dailyworklog/access/ownership_proof.dart';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/export/overtime_xlsx.dart';
import 'package:dailyworklog/export/report_docx.dart';
import 'package:dailyworklog/export/report_pdf.dart';
import 'package:dailyworklog/export/report_xlsx.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('an ownership proof signed with the owner key checks out; a changed one does not', () {
    final owner = AccessCodes.generateKeyPair();
    const at = '2026-11-20 14:03';
    final sig = AccessCodes.sign(owner.privateKey, OwnershipProof.payload('Tanner Tibbetts', at));
    final text = OwnershipProof.text('Tanner Tibbetts', at, sig, owner.publicKey, '');
    expect(text, contains(Ownership.notice));
    expect(text, contains(Ownership.mark));

    final r = OwnershipProof.check(text, owner.publicKey)!;
    expect(r.genuine, true);
    expect(r.name, 'Tanner Tibbetts');
    expect(r.at, at);
    expect(r.sameAsThisApp, true);
    expect(r.fingerprint, OwnershipProof.fingerprint(owner.publicKey));
    expect(RegExp(r'^[0-9A-F]{4}(-[0-9A-F]{4}){3}$').hasMatch(r.fingerprint), true);

    // A copy of the app with someone else's key: genuine, but not this app's owner.
    expect(OwnershipProof.check(text, AccessCodes.generateKeyPair().publicKey)!.sameAsThisApp, false);
    // A copy with no key built in (iPhone): genuine, compare fingerprints.
    expect(OwnershipProof.check(text, null)!.sameAsThisApp, isNull);

    // Someone changes the name: the signature no longer matches.
    final code = text.split('\n').last;
    final parts = code.substring(OwnershipProof.prefix.length).split('~');
    final forged = '${OwnershipProof.prefix}${AccessCodes.b64url(OwnershipProof.payload('Someone Else', at))}'
        '~${parts.sublist(1).join('~')}';
    expect(OwnershipProof.check(forged, owner.publicKey)!.genuine, false);
    expect(OwnershipProof.check('hello', owner.publicKey), isNull);
  });

  test('a proof from a phone the ownership was handed over to checks against the original key', () {
    final root = AccessCodes.generateKeyPair(), next = AccessCodes.generateKeyPair();
    final url = AccessCodes.keyUrl(next.publicKey);
    final chain = AccessCodes.extendChain('', url, AccessCodes.sign(root.privateKey, AccessCodes.handoverPayload(url)));
    const at = '2027-01-05 08:00';
    final sig = AccessCodes.sign(next.privateKey, OwnershipProof.payload('New Owner', at));
    final r = OwnershipProof.check(OwnershipProof.text('New Owner', at, sig, root.publicKey, chain), root.publicKey)!;
    expect(r.genuine, true);
    expect(r.sameAsThisApp, true);
  });

  test('report files carry the hidden tag', () async {
    final p = Project()..name = 'B44';
    final l = DailyLog()..date = '2026-11-20';
    p.logs.add(l);
    for (final bytes in [
      ReportDocx.write(p, l, 'Tanner', 0, Directory.systemTemp.path),
      ReportXlsx.write(p, l, 'Tanner', 0),
      OvertimeXlsx.write(p, 'Tanner', 0),
    ]) {
      final zip = ZipDecoder().decodeBytes(bytes);
      String text(String n) => utf8.decode(zip.files.firstWhere((f) => f.name == n).content);
      expect(text('docProps/core.xml'), contains(Ownership.mark));
      expect(text('docProps/app.xml'), contains(Ownership.notice));
      expect(text('[Content_Types].xml'), contains('/docProps/core.xml'));
      expect(text('_rels/.rels'), contains('docProps/app.xml'));
    }
    final pdf = await ReportPdf.write(p, l, 'Tanner', 0, Directory.systemTemp.path);
    expect(latin1.decode(pdf, allowInvalid: true), contains(Ownership.mark));
  });

  test('proofs made by the classic app check in this app too', () {
    const v1 = 'DWL-PROOF-djF8cHJvb2Z8VGFubmVyIFRpYmJldHRzfDIwMjYtMTEtMjAgMTQ6MDN8RFdMLVBHOUgtWE1FVS1QQlo0LUhORks~MEYCIQDAbtlfThCkQjFWBAWSmKanKhJlFp0N3_3F4F7heMVjoQIhAMEb9Tw0DpgJC9Vs7GQJ9SMSqg6cHIgSrp0VfXvt_yBn~MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEKI4AA0QVdgOEs8TXq1aZEUvUoJiXOnRMY9jSZtwvZLE4-VQr2UdR-8Idb53U5f22wpCGy-N7J25GgiLNnyF5eA';
    const v2 = 'DWL-PROOF-djF8cHJvb2Z8TmV3IE93bmVyfDIwMjctMDEtMDUgMDg6MDB8RFdMLVBHOUgtWE1FVS1QQlo0LUhORks~MEQCIADkNd57LUwoA9OSYChCnHxDzTYU33BWSTuOMQIekUvtAiAZE4el_kZLExKX1hLeMJInyg4MiUqRCLwtPYyvsYk3rQ~MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEKI4AA0QVdgOEs8TXq1aZEUvUoJiXOnRMY9jSZtwvZLE4-VQr2UdR-8Idb53U5f22wpCGy-N7J25GgiLNnyF5eA~MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEn1F0EosXjmLjK_1BXwxp2UdEreC-ejWrNNSwdCqn08KwU6uBCDLBsQmFKadPc0kDvi064XYVe8yW9QYs1Za7Ow.MEUCIExcIfvAsElwuOLW-CirZ70BIuzZ12o6qefFrg2QZHPCAiEAh2xUL3SJf39oK4O3ty2c3lVQYESy7Leuq8a5uv6y2ec';
    final a = OwnershipProof.check(v1, null)!;
    expect(a.genuine, true);
    expect(a.name, 'Tanner Tibbetts');
    expect(a.at, '2026-11-20 14:03');
    final b = OwnershipProof.check(v2, null)!;
    expect(b.genuine, true);
    expect(b.name, 'New Owner');
    expect(b.fingerprint, a.fingerprint); // the same original owner key
  });
}
