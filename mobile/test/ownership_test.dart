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
}
