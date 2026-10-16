// Access request and approval codes, passed by text message. No server is involved:
//
//  - A new user's phone makes a request code from its install id and the user's name.
//  - Only the app owner's phone holds the private key; it signs "install id + name + expiry".
//  - Every copy of the app has the owner's public key built in, so it can check an approval is
//    genuine (and for this phone and name) but can't make one.
//
// Byte-for-byte compatible with AccessCodes.java: codes made by the Android app work here and
// the other way round.
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:pointycastle/asn1.dart';
import 'package:pointycastle/export.dart';

class AccessRequest {
  final String installId, name;

  const AccessRequest(this.installId, this.name);
}

class Approval {
  /// yyyy-MM-dd
  final String expiry;
  final Uint8List signature;

  /// Owner handovers since the original owner ("" if signed by the original owner).
  final String chain;

  Approval(this.expiry, this.signature, String? chain) : chain = chain ?? '';
}

class Handover {
  final Uint8List privateKeyPkcs8;
  final String chain;

  Handover(this.privateKeyPkcs8, this.chain);
}

class AccessCodes {
  AccessCodes._();

  static const req = 'DWL-REQ-', ok = 'DWL-OK-', owner = 'DWL-OWNER-';
  static const rounds = 120000;
  static final _reqCode = RegExp(r'DWL-REQ-([A-Za-z0-9_-]+)');
  static final _okCode = RegExp(r'DWL-OK-([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)(?:\.([A-Za-z0-9_.*-]+))?');
  static final _ownerCode =
      RegExp(r'DWL-OWNER-([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)~([A-Za-z0-9_-]+)~([A-Za-z0-9_.*-]+)');
  static final _date = RegExp(r'^\d{4}-\d{2}-\d{2}$');

  static String requestCode(String installId, String name) =>
      req + _b64('v1|$installId|${javaTrim(name)}');

  /// Finds a request code anywhere in pasted text (e.g. the whole text message).
  static AccessRequest? parseRequest(String? text) {
    if (text == null) return null;
    final m = _reqCode.firstMatch(text);
    if (m == null) return null;
    try {
      final p = _splitLimit(_utf8(unb64(m.group(1)!)), '|', 3);
      if (p.length != 3 || p[0] != 'v1' || p[1].isEmpty || javaTrim(p[2]).isEmpty) return null;
      return AccessRequest(p[1], javaTrim(p[2]));
    } on FormatException {
      return null;
    }
  }

  /// What the owner signs: ties the approval to one phone, one name and an end date.
  static Uint8List payload(String installId, String name, String expiry) =>
      _bytes('v1|$installId|${javaTrim(name)}|$expiry');

  static String approvalCode(String expiry, Uint8List signature, String? chain) =>
      '$ok${_b64('v1|$expiry|')}.${b64url(signature)}${chain == null || chain.isEmpty ? '' : '.$chain'}';

  /// Finds an approval code anywhere in pasted text.
  static Approval? parseApproval(String? text) {
    if (text == null) return null;
    final m = _okCode.firstMatch(text);
    if (m == null) return null;
    try {
      final p = _utf8(unb64(m.group(1)!)).split('|');
      if (p.length < 2 || p[0] != 'v1' || !_date.hasMatch(p[1])) return null;
      return Approval(p[1], unb64(m.group(2)!), m.group(3));
    } on FormatException {
      return null;
    }
  }

  /// True if the owner really signed this approval for this phone and name.
  static bool verify(ECPublicKey? ownerKey, String installId, String name, Approval? a) {
    if (ownerKey == null || a == null) return false;
    final signer = chainEnd(ownerKey, a.chain); // the owner at the time, after any handovers
    if (signer == null) return false;
    return verifySignature(signer, payload(installId, name, a.expiry), a.signature);
  }

  // ----- owner handover -----
  //
  // A chain is the list of handovers since the original owner: "key.sig*key.sig". Each key is a
  // new owner's public key, signed by the owner before it. The app trusts approvals signed by the
  // last key of a chain that leads back to the original owner built into the app.

  static Uint8List handoverPayload(String keyUrl) => _bytes('v1|owner|$keyUrl');

  static String keyUrl(ECPublicKey k) => b64url(publicKeyDer(k));

  static String extendChain(String? chain, String keyUrl, Uint8List sig) {
    final entry = '$keyUrl.${b64url(sig)}';
    return chain == null || chain.isEmpty ? entry : '$chain*$entry';
  }

  /// The current owner's key after following the chain from the original owner, or null if broken.
  static ECPublicKey? chainEnd(ECPublicKey root, String? chain) {
    var signer = root;
    if (chain == null || chain.isEmpty) return signer;
    try {
      for (final entry in _javaSplit(chain, '*')) {
        final kv = _javaSplit(entry, '.');
        if (kv.length != 2) return null;
        if (!verifySignature(signer, handoverPayload(kv[0]), unb64(kv[1]))) return null;
        final next = publicKeyFromDer(unb64(kv[0]));
        if (next == null) return null;
        signer = next;
      }
      return signer;
    } catch (_) {
      return null;
    }
  }

  /// What travels by text: the new owner's key, locked with the 6-digit code, plus the chain.
  /// Slow (PBKDF2): run it off the UI thread.
  static String handoverCode(Uint8List privateKeyPkcs8, String chain, String code) {
    final r = Random.secure();
    final salt = Uint8List.fromList(List.generate(16, (_) => r.nextInt(256)));
    final iv = Uint8List.fromList(List.generate(12, (_) => r.nextInt(256)));
    final c = GCMBlockCipher(AESEngine())
      ..init(true, AEADParameters(KeyParameter(_codeKey(code, salt)), 128, iv, _bytes(chain)));
    final ct = c.process(privateKeyPkcs8);
    return '$owner${b64url(salt)}~${b64url(iv)}~${b64url(ct)}~$chain';
  }

  static bool looksLikeHandover(String? text) => text != null && text.contains(owner);

  /// Unlocks a handover with its 6-digit code; null if the code is wrong or the text was changed.
  /// Slow (PBKDF2): run it off the UI thread.
  static Handover? openHandover(String? text, String code) {
    if (text == null) return null;
    final m = _ownerCode.firstMatch(text);
    if (m == null) return null;
    try {
      final c = GCMBlockCipher(AESEngine())
        ..init(false,
            AEADParameters(KeyParameter(_codeKey(code, unb64(m.group(1)!))), 128, unb64(m.group(2)!), _bytes(m.group(4)!)));
      return Handover(c.process(unb64(m.group(3)!)), m.group(4)!);
    } catch (_) {
      return null;
    }
  }

  static Uint8List _codeKey(String code, Uint8List salt) {
    final d = PBKDF2KeyDerivator(HMac(SHA256Digest(), 64))..init(Pbkdf2Parameters(salt, rounds, 32));
    return d.process(_bytes(javaTrim(code)));
  }

  // ----- keys -----

  static final ECDomainParameters curve = ECCurve_secp256r1();
  static final _ecPublicKeyOid = [1, 2, 840, 10045, 2, 1];
  static final _p256Oid = [1, 2, 840, 10045, 3, 1, 7];
  static final _p = BigInt.parse('ffffffff00000001000000000000000000000000ffffffffffffffffffffffff', radix: 16);

  /// A key from base64 X.509 SubjectPublicKeyInfo DER (the built-in owner key), or null.
  static ECPublicKey? publicKey(String? base64Text) {
    if (base64Text == null || base64Text.trim().isEmpty) return null;
    try {
      return publicKeyFromDer(base64.decode(base64.normalize(base64Text.replaceAll(RegExp(r'\s+'), ''))));
    } catch (_) {
      return null;
    }
  }

  static String encodeKey(ECPublicKey k) => base64.encode(publicKeyDer(k));

  /// X.509 SubjectPublicKeyInfo DER, as Java's PublicKey.getEncoded().
  static Uint8List publicKeyDer(ECPublicKey k) {
    final alg = ASN1Sequence(elements: [ASN1ObjectIdentifier(_ecPublicKeyOid), ASN1ObjectIdentifier(_p256Oid)]);
    final spki = ASN1Sequence(elements: [alg, ASN1BitString(stringValues: k.Q!.getEncoded(false))]);
    return spki.encode();
  }

  static ECPublicKey? publicKeyFromDer(Uint8List der) {
    try {
      final spki = ASN1Parser(der).nextObject() as ASN1Sequence;
      final alg = spki.elements![0] as ASN1Sequence;
      if (!_isOid(alg.elements![0], _ecPublicKeyOid) || !_isOid(alg.elements![1], _p256Oid)) return null;
      final bits = spki.elements![1] as ASN1BitString;
      final q = curve.curve.decodePoint(bits.stringValues!);
      if (q == null || q.isInfinity || !_onCurve(q)) return null;
      return ECPublicKey(q, curve);
    } catch (_) {
      return null;
    }
  }

  static bool _onCurve(ECPoint q) {
    final x = q.x!.toBigInteger()!, y = q.y!.toBigInteger()!;
    final b = curve.curve.b!.toBigInteger()!;
    return (y * y - (x * x * x - BigInt.from(3) * x + b)) % _p == BigInt.zero;
  }

  static bool _isOid(ASN1Object o, List<int> oid) =>
      o is ASN1ObjectIdentifier && o.objectIdentifierAsString == oid.join('.');

  /// PKCS#8 DER of a private key, as Java's PrivateKey.getEncoded() (readable by Java's KeyFactory).
  static Uint8List privateKeyPkcs8(ECPrivateKey k) {
    final d = _fixed(k.d!, 32);
    final pub = (curve.G * k.d)!.getEncoded(false);
    final ecKey = ASN1Sequence(elements: [
      ASN1Integer(BigInt.one),
      ASN1OctetString(octets: d),
      ASN1Sequence(tag: 0xA1, elements: [ASN1BitString(stringValues: pub)]),
    ]);
    final alg = ASN1Sequence(elements: [ASN1ObjectIdentifier(_ecPublicKeyOid), ASN1ObjectIdentifier(_p256Oid)]);
    return ASN1Sequence(elements: [ASN1Integer(BigInt.zero), alg, ASN1OctetString(octets: ecKey.encode())]).encode();
  }

  /// Reads a PKCS#8 EC P-256 private key (from Java's getEncoded() or [privateKeyPkcs8]); null if not one.
  static ECPrivateKey? privateKeyFromPkcs8(Uint8List der) {
    try {
      final seq = ASN1Parser(der).nextObject() as ASN1Sequence;
      final alg = seq.elements![1] as ASN1Sequence;
      if (!_isOid(alg.elements![0], _ecPublicKeyOid)) return null;
      if (alg.elements!.length > 1 && alg.elements![1] is ASN1ObjectIdentifier && !_isOid(alg.elements![1], _p256Oid)) {
        return null;
      }
      final inner = ASN1Parser((seq.elements![2] as ASN1OctetString).octets).nextObject() as ASN1Sequence;
      final d = _unsigned((inner.elements![1] as ASN1OctetString).octets!);
      if (d <= BigInt.zero || d >= curve.n) return null;
      return ECPrivateKey(d, curve);
    } catch (_) {
      return null;
    }
  }

  static ECPublicKey publicOf(ECPrivateKey k) => ECPublicKey((curve.G * k.d)!, curve);

  static bool sameKey(ECPublicKey? a, ECPublicKey? b) =>
      a != null && b != null && _eq(a.Q!.getEncoded(false), b.Q!.getEncoded(false));

  static AsymmetricKeyPair<ECPublicKey, ECPrivateKey> generateKeyPair() {
    final g = ECKeyGenerator()..init(ParametersWithRandom(ECKeyGeneratorParameters(curve), _random()));
    final p = g.generateKeyPair();
    return AsymmetricKeyPair(p.publicKey as ECPublicKey, p.privateKey as ECPrivateKey);
  }

  /// SHA256withECDSA, DER-encoded like Java's Signature.sign().
  static Uint8List sign(ECPrivateKey k, Uint8List data) {
    final s = ECDSASigner(SHA256Digest())..init(true, ParametersWithRandom(PrivateKeyParameter<ECPrivateKey>(k), _random()));
    final sig = s.generateSignature(data) as ECSignature;
    return ASN1Sequence(elements: [ASN1Integer(sig.r), ASN1Integer(sig.s)]).encode();
  }

  /// Checks a DER-encoded SHA256withECDSA signature, as Java's Signature.verify().
  static bool verifySignature(ECPublicKey k, Uint8List data, Uint8List der) {
    try {
      final p = ASN1Parser(der);
      final seq = p.nextObject() as ASN1Sequence;
      if (p.hasNext() || seq.elements!.length != 2) return false;
      final r = (seq.elements![0] as ASN1Integer).integer!, s = (seq.elements![1] as ASN1Integer).integer!;
      if (r <= BigInt.zero || s <= BigInt.zero || r >= curve.n || s >= curve.n) return false;
      final v = ECDSASigner(SHA256Digest())..init(false, PublicKeyParameter<ECPublicKey>(k));
      return v.verifySignature(data, ECSignature(r, s));
    } catch (_) {
      return false;
    }
  }

  // ----- helpers -----

  static SecureRandom _random() {
    final r = Random.secure();
    return FortunaRandom()..seed(KeyParameter(Uint8List.fromList(List.generate(32, (_) => r.nextInt(256)))));
  }

  static Uint8List _fixed(BigInt n, int len) {
    final out = Uint8List(len);
    var v = n;
    for (var i = len - 1; i >= 0; i--) {
      out[i] = (v & BigInt.from(0xff)).toInt();
      v >>= 8;
    }
    return out;
  }

  static BigInt _unsigned(Uint8List b) {
    var v = BigInt.zero;
    for (final x in b) {
      v = (v << 8) | BigInt.from(x);
    }
    return v;
  }

  static bool _eq(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  /// Java's String.trim(): strips characters up to U+0020 from both ends.
  static String javaTrim(String s) {
    var a = 0, b = s.length;
    while (a < b && s.codeUnitAt(a) <= 0x20) {
      a++;
    }
    while (b > a && s.codeUnitAt(b - 1) <= 0x20) {
      b--;
    }
    return s.substring(a, b);
  }

  /// Java's String.split(sep) with no limit: trailing empty strings are dropped.
  static List<String> _javaSplit(String s, String sep) {
    final p = s.split(sep);
    while (p.isNotEmpty && p.last.isEmpty) {
      p.removeLast();
    }
    return p;
  }

  /// Java's String.split(sep, limit).
  static List<String> _splitLimit(String s, String sep, int limit) {
    final out = <String>[];
    var from = 0;
    while (out.length < limit - 1) {
      final i = s.indexOf(sep, from);
      if (i < 0) break;
      out.add(s.substring(from, i));
      from = i + sep.length;
    }
    out.add(s.substring(from));
    return out;
  }

  static Uint8List _bytes(String s) => Uint8List.fromList(utf8.encode(s));

  static String _utf8(Uint8List b) => utf8.decode(b, allowMalformed: true);

  static String _b64(String s) => b64url(_bytes(s));

  /// URL-safe base64 without padding, as Java's Base64.getUrlEncoder().withoutPadding().
  static String b64url(List<int> b) => base64Url.encode(b).replaceAll('=', '');

  /// As Java's Base64.getUrlDecoder(): padding optional. Throws FormatException on bad input.
  static Uint8List unb64(String s) {
    if (s.contains('+') || s.contains('/')) throw const FormatException('not base64url');
    return base64Url.decode(base64Url.normalize(s));
  }
}
