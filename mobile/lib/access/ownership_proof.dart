// Ownership proof: a statement signed on the owner's phone with the app's owner key (the same key
// that approves people). Only the phone holding that key can make one, and anyone can check it,
// so it proves who owns Daily Work Log. See Support > Ownership proof.
import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart' show ECPublicKey;

import '../about/ownership.dart';
import 'access_codes.dart';

/// What checking a proof found.
class ProofResult {
  /// The signature is good: it was made with the owner key shown by [fingerprint].
  final bool genuine;
  final String name, at, fingerprint;

  /// Whether that key is the one built into this copy of the app (null: this copy has none).
  final bool? sameAsThisApp;

  const ProofResult(this.genuine, this.name, this.at, this.fingerprint, this.sameAsThisApp);
}

class OwnershipProof {
  OwnershipProof._();

  static const prefix = 'DWL-PROOF-';
  static final _code = RegExp(r'DWL-PROOF-([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)(?:~([A-Za-z0-9_.*-]+))?');

  /// What gets signed.
  static Uint8List payload(String name, String at) =>
      Uint8List.fromList(utf8.encode('v1|proof|${name.trim()}|$at|${Ownership.mark}'));

  /// The fingerprint of an owner key, e.g. "3F9A-0C21-77BE-D410".
  static String fingerprint(ECPublicKey k) => Ownership.fingerprint(AccessCodes.publicKeyDer(k));

  /// The proof as text to send or keep. [root] is the original owner key built into the app,
  /// [chain] the handovers from it to the key that signed ('' for the original owner).
  static String text(String name, String at, Uint8List signature, ECPublicKey root, String chain) {
    final code = '$prefix${AccessCodes.b64url(payload(name, at))}~${AccessCodes.b64url(signature)}'
        '~${AccessCodes.keyUrl(root)}${chain.isEmpty ? '' : '~$chain'}';
    return 'Daily Work Log ownership proof\n'
        'Owner: ${name.trim()}\n'
        'Signed: $at\n'
        'Owner key fingerprint: ${fingerprint(root)}\n'
        'App mark: ${Ownership.mark}\n'
        '${Ownership.notice}\n\n'
        "This was signed on the app owner's phone with the owner key built into the owner's copies of "
        'Daily Work Log. Only that phone can make it. To check it: open Daily Work Log, tap Support, '
        'then Ownership proof, and paste this whole text under "Check an ownership proof".\n\n'
        '$code';
  }

  /// Checks a proof pasted from anywhere. Null if there is no proof in [text].
  static ProofResult? check(String? text, ECPublicKey? builtIn) {
    final m = _code.firstMatch(text ?? '');
    if (m == null) return null;
    try {
      final data = AccessCodes.unb64(m.group(1)!);
      final parts = utf8.decode(data).split('|');
      final root = AccessCodes.publicKeyFromDer(AccessCodes.unb64(m.group(3)!));
      if (root == null || parts.length != 5 || parts[0] != 'v1' || parts[1] != 'proof') return null;
      final signer = AccessCodes.chainEnd(root, m.group(4));
      final good = signer != null &&
          parts[4] == Ownership.mark &&
          AccessCodes.verifySignature(signer, data, AccessCodes.unb64(m.group(2)!));
      return ProofResult(good, parts[2], parts[3], fingerprint(root),
          builtIn == null ? null : AccessCodes.sameKey(builtIn, root));
    } catch (_) {
      return null;
    }
  }
}
