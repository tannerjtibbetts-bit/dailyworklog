// Who may use the app on this phone. Until the owner's key is built into the app, everyone can
// (so the owner is never locked out). After that: the owner's phone, or a phone with a valid
// approval code from the owner. A PIN can be added, with "keep me signed in" to skip it.
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:pointycastle/export.dart' show ECPublicKey, ECPrivateKey, SHA256Digest;
import 'package:share_plus/share_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import '../screens/approvals_screen.dart';
import '../screens/projects_screen.dart';
import '../screens/sign_in_screen.dart';
import 'access_codes.dart';

enum Need { none, approval, newPin, pin }

class Access {
  Access._();

  /// File name for the owner's public key; the build puts it in assets/owner_key.txt.
  static const keyFile = 'dailyworklog-owner-key.txt';

  /// Sign-in settings in shared_preferences start with this.
  static const _p = 'access.';

  /// Unlocked for as long as the app stays open.
  static bool _unlocked = false;

  static const _secure = FlutterSecureStorage(aOptions: AndroidOptions());
  static const _legacy = MethodChannel('dailyworklog/legacy_access');

  static Future<SharedPreferences> _prefs() async {
    final p = await SharedPreferences.getInstance();
    await (_migrating ??= _migrate(p));
    return p;
  }

  // ----- the owner's key -----

  static Future<ECPublicKey?>? _built;

  /// The owner's public key built into this copy of the app, or null if not set up yet.
  static Future<ECPublicKey?> builtInOwnerKey() => _built ??= _loadBuiltIn();

  static Future<ECPublicKey?> _loadBuiltIn() async {
    try {
      return AccessCodes.publicKey(await rootBundle.loadString('assets/owner_key.txt'));
    } catch (_) {
      return null;
    }
  }

  /// This phone's owner signing key (created here or handed over), or null.
  static Future<ECPrivateKey?> _softwareKey() async {
    final s = await _secure.read(key: '${_p}ownerKey');
    if (s == null || s.isEmpty) return null;
    try {
      return AccessCodes.privateKeyFromPkcs8(base64.decode(s));
    } catch (_) {
      return null;
    }
  }

  static ECPublicKey? _keystorePublic;

  /// The original owner key made by the earlier Android version, kept in the Android key store.
  static Future<ECPublicKey?> _keystoreKey(SharedPreferences p) async {
    if (!(p.getBool('${_p}keystoreOwner') ?? false)) return null;
    if (_keystorePublic != null) return _keystorePublic;
    try {
      _keystorePublic = AccessCodes.publicKey(await _legacy.invokeMethod<String>('keystoreOwnerPublicKey'));
    } catch (_) {
      return null;
    }
    return _keystorePublic;
  }

  /// True if this phone holds an owner signing key.
  static Future<bool> hasOwnerKey() async {
    final p = await _prefs();
    return await _softwareKey() != null || await _keystoreKey(p) != null;
  }

  /// True if this phone is the owner of this build of the app (the original, or handed over to).
  static Future<bool> isOwner() async {
    final p = await _prefs();
    if (p.getBool('${_p}handedOver') ?? false) return false;
    final built = await builtInOwnerKey();
    if (built == null) return false;
    final mine = await _softwareKey();
    if (mine != null) {
      final chain = p.getString('${_p}ownerChain') ?? '';
      if (chain.isEmpty) return AccessCodes.sameKey(AccessCodes.publicOf(mine), built);
      return AccessCodes.chainEnd(built, chain) != null;
    }
    return AccessCodes.sameKey(await _keystoreKey(p), built);
  }

  /// Handovers that lead to this phone's owner key ("" for the original owner).
  static Future<String> _ownerChain(SharedPreferences p) async =>
      await _softwareKey() == null ? '' : (p.getString('${_p}ownerChain') ?? '');

  /// Signs with this phone's owner key: a handed-over or software key if it has one, otherwise
  /// the original key in the Android key store.
  static Future<Uint8List> _sign(SharedPreferences p, Uint8List data) async {
    final k = await _softwareKey();
    if (k != null) return AccessCodes.sign(k, data);
    if (p.getBool('${_p}keystoreOwner') ?? false) {
      final sig = await _legacy.invokeMethod<Uint8List>('keystoreSign', {'data': data});
      if (sig != null) return sig;
    }
    throw Exception('this phone has no owner key');
  }

  // ----- handing the owner role to another phone -----

  /// Owner only: makes the text that moves the owner role to another phone, locked with
  /// [code]. This phone stops approving people but keeps its own access for a year, and
  /// every approval it already gave stays valid.
  static Future<String> handOver(String code, String myName) async {
    final p = await _prefs();
    final next = AccessCodes.generateKeyPair();
    final url = AccessCodes.keyUrl(next.publicKey);
    final mine = await _ownerChain(p);
    final chain = AccessCodes.extendChain(mine, url, await _sign(p, AccessCodes.handoverPayload(url)));
    final text = await compute(_lock, [AccessCodes.privateKeyPkcs8(next.privateKey), chain, code]);
    // Approve this phone for itself first, so it can still open the app afterwards
    var name = await requestedName();
    if (name.isEmpty) name = myName.trim().isEmpty ? 'Owner' : myName.trim();
    final expiry = _yearFromNow();
    final self = AccessCodes.approvalCode(
      expiry,
      await _sign(p, AccessCodes.payload(await installId(), name, expiry)),
      mine,
    );
    await p.setString('${_p}name', name);
    await p.setString('${_p}approval', self);
    await p.setBool('${_p}handedOver', true);
    await p.setInt('${_p}handedOverAt', DateTime.now().millisecondsSinceEpoch);
    return text;
  }

  static Future<bool> handedOver() async => (await _prefs()).getBool('${_p}handedOver') ?? false;

  static Future<int> handedOverAt() async => (await _prefs()).getInt('${_p}handedOverAt') ?? 0;

  /// Kept on the old phone so the code can be read again until the handover is done.
  static Future<void> rememberHandoverCode(String code) async {
    await _prefs();
    await _secure.write(key: '${_p}handoverCode', value: code);
  }

  static Future<String> handoverCode() async {
    await _prefs();
    return await _secure.read(key: '${_p}handoverCode') ?? '';
  }

  /// Takes the owner role back on this phone (only sensible if the other phone didn't take it).
  static Future<void> undoHandover() async {
    final p = await _prefs();
    await p.remove('${_p}handedOver');
    await _secure.delete(key: '${_p}handoverCode');
  }

  /// Makes this phone the owner from a handover text and its code. Returns null if fine, or what's wrong.
  static Future<String?> acceptHandover(String text, String code) async {
    final p = await _prefs();
    final built = await builtInOwnerKey();
    if (built == null) return "This copy of the app doesn't have an owner set up.";
    if (!AccessCodes.looksLikeHandover(text)) {
      return "That doesn't look like a handover text. Paste the whole text you were sent.";
    }
    final h = await compute(_open, [text, code]);
    if (h == null) return "Wrong code, or the text isn't complete. Check the 6-digit code on the owner phone.";
    final keyBytes = h[0] as Uint8List, chain = h[1] as String;
    final end = chain.isEmpty ? null : AccessCodes.chainEnd(built, chain);
    if (end == null) return "That handover isn't from this app's owner.";
    try {
      final key = AccessCodes.privateKeyFromPkcs8(keyBytes);
      if (key == null) return "That handover isn't from this app's owner.";
      final test = Uint8List.fromList(utf8.encode('check'));
      if (!AccessCodes.verifySignature(end, test, AccessCodes.sign(key, test))) {
        return "That handover isn't from this app's owner.";
      }
      await _secure.write(key: '${_p}ownerKey', value: base64.encode(keyBytes));
      await p.setString('${_p}ownerChain', chain);
      await p.remove('${_p}handedOver');
      await _secure.delete(key: '${_p}handoverCode');
      return null;
    } catch (e) {
      return "Couldn't take over: $e";
    }
  }

  static String _yearFromNow() {
    final n = DateTime.now();
    // Like Calendar.add(YEAR, 1): Feb 29 becomes Feb 28
    final d = DateTime(n.year + 1, n.month, n.month == 2 && n.day == 29 ? 28 : n.day);
    return _iso(d);
  }

  static String _iso(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  /// Makes this phone the owner's signing key if it doesn't have one, and returns the public
  /// key text (base64 X.509) to build into the app.
  static Future<String> createOwnerKey() async {
    final p = await _prefs();
    final ks = await _keystoreKey(p);
    final soft = await _softwareKey();
    if (soft != null) return AccessCodes.encodeKey(AccessCodes.publicOf(soft));
    if (ks != null) return AccessCodes.encodeKey(ks);
    final k = AccessCodes.generateKeyPair();
    await _secure.write(key: '${_p}ownerKey', value: base64.encode(AccessCodes.privateKeyPkcs8(k.privateKey)));
    await p.setString('${_p}ownerChain', '');
    return AccessCodes.encodeKey(k.publicKey);
  }

  /// Makes this phone the owner: creates the signing key (kept in the phone's secure storage) and
  /// shares the public half as [keyFile] for the build.
  static Future<String> becomeOwner({Rect? shareOrigin}) async {
    final text = await createOwnerKey();
    await SharePlus.instance.share(
      ShareParams(
        files: [XFile.fromData(Uint8List.fromList(utf8.encode(text)), mimeType: 'text/plain', name: keyFile)],
        fileNameOverrides: const [keyFile],
        subject: keyFile,
        sharePositionOrigin: shareOrigin,
      ),
    );
    return text;
  }

  /// Owner only: the approval code for a request, good for a year.
  static Future<String> approve(AccessRequest r) async {
    final p = await _prefs();
    final expiry = _yearFromNow();
    final code = AccessCodes.approvalCode(
      expiry,
      await _sign(p, AccessCodes.payload(r.installId, r.name, expiry)),
      await _ownerChain(p),
    );
    // Keep a list of who's been approved
    final list = await approvedList();
    list.add({'name': r.name, 'at': DateTime.now().millisecondsSinceEpoch, 'expiry': expiry});
    await p.setString('${_p}approved', jsonEncode(list));
    return code;
  }

  /// Everyone approved from this phone, oldest first: {name, at (ms), expiry}.
  static Future<List<Map<String, dynamic>>> approvedList() async {
    final p = await _prefs();
    try {
      return (jsonDecode(p.getString('${_p}approved') ?? '[]') as List)
          .whereType<Map>()
          .map((e) => Map<String, dynamic>.from(e))
          .toList();
    } catch (_) {
      return [];
    }
  }

  // ----- this phone's approval -----

  static Future<String> installId() async {
    final p = await _prefs();
    var id = p.getString('${_p}installId');
    if (id == null) {
      id = const Uuid().v4();
      await p.setString('${_p}installId', id);
    }
    return id;
  }

  static Future<String> requestCode(String name) async {
    final p = await _prefs();
    await p.setString('${_p}name', AccessCodes.javaTrim(name));
    return AccessCodes.requestCode(await installId(), name);
  }

  static Future<String> requestedName() async => (await _prefs()).getString('${_p}name') ?? '';

  /// Checks and keeps an approval code. Returns null if fine, or what's wrong.
  static Future<String?> acceptApproval(String text) async {
    final p = await _prefs();
    final a = AccessCodes.parseApproval(text);
    if (a == null) return "That doesn't look like an approval code. Paste the whole text you were sent.";
    final name = await requestedName();
    if (!AccessCodes.verify(await builtInOwnerKey(), await installId(), name, a)) {
      return "That code isn't for this phone and name. Ask for a new one using the request from this phone.";
    }
    if (_expired(a.expiry)) return 'That approval has expired. Send a new request.';
    await p.setString('${_p}approval', AccessCodes.javaTrim(text));
    return null;
  }

  static Future<bool> isApproved() async {
    final p = await _prefs();
    final a = AccessCodes.parseApproval(p.getString('${_p}approval'));
    return a != null &&
        !_expired(a.expiry) &&
        AccessCodes.verify(await builtInOwnerKey(), await installId(), await requestedName(), a);
  }

  static bool _expired(String expiry) => _iso(DateTime.now()).compareTo(expiry) > 0;

  // ----- PIN and "keep me signed in" -----

  static Future<bool> hasPin() async {
    await _prefs();
    return await _secure.read(key: '${_p}pin') != null;
  }

  static Future<void> setPin(String pin, bool remember) async {
    final p = await _prefs();
    final r = Random.secure();
    final s = base64.encode(List.generate(16, (_) => r.nextInt(256)));
    await _secure.write(key: '${_p}salt', value: s);
    await _secure.write(key: '${_p}pin', value: pinHash(s, pin));
    await p.setBool('${_p}remember', remember);
    _unlocked = true;
  }

  static Future<bool> checkPin(String pin, bool remember) async {
    final p = await _prefs();
    final s = await _secure.read(key: '${_p}salt') ?? '';
    if (pinHash(s, pin) != (await _secure.read(key: '${_p}pin') ?? '')) return false;
    await p.setBool('${_p}remember', remember);
    _unlocked = true;
    return true;
  }

  /// Clears the PIN and this phone's approval; the owner approves it again before a new PIN.
  /// Only sign-in settings are touched: your saved work (kept by Store) is never deleted, and
  /// the install id stays the same so a new approval fits this phone.
  static Future<void> forgotPin() async {
    final p = await _prefs();
    await _secure.delete(key: '${_p}pin');
    await _secure.delete(key: '${_p}salt');
    await p.remove('${_p}approval');
    await p.setBool('${_p}remember', false);
    await p.setBool('${_p}hadApproval', true);
    _unlocked = false;
  }

  /// True if this phone was approved before (expired, or reset after a forgotten PIN).
  static Future<bool> hadApproval() async {
    final p = await _prefs();
    return (p.getBool('${_p}hadApproval') ?? false) || p.getString('${_p}approval') != null;
  }

  static Future<void> signOut() async {
    final p = await _prefs();
    await p.setBool('${_p}remember', false);
    _unlocked = false;
  }

  /// Same as the Android app: base64(SHA-256(salt text, then PIN)).
  static String pinHash(String salt, String pin) {
    final d = SHA256Digest();
    final s = utf8.encode(salt), n = utf8.encode(pin);
    d.update(Uint8List.fromList(s), 0, s.length);
    d.update(Uint8List.fromList(n), 0, n.length);
    final out = Uint8List(32);
    d.doFinal(out, 0);
    return base64.encode(out);
  }

  /// What the sign-in screen needs to ask for, or Need.none to go straight in.
  static Future<Need> need() async {
    final p = await _prefs();
    final locked = await builtInOwnerKey() != null;
    if (locked && !await isOwner() && !await isApproved()) return Need.approval;
    if (_unlocked || (p.getBool('${_p}remember') ?? false)) return Need.none;
    if (!await hasPin()) return locked ? Need.newPin : Need.none;
    return Need.pin;
  }

  // ----- moving over from the earlier Android version -----

  static Future<void>? _migrating;

  /// Run the import of the earlier Android version's sign-in state on any platform (tests).
  @visibleForTesting
  static bool debugMigrateEverywhere = false;

  /// On first run after updating from the Java app: bring over its sign-in state so nobody has
  /// to be approved again. The install id stays the same, so existing approvals stay valid.
  static Future<void> _migrate(SharedPreferences p) async {
    if (!(debugMigrateEverywhere || (!kIsWeb && Platform.isAndroid))) return;
    if (p.getBool('${_p}migrated') ?? false) return;
    Map<Object?, Object?>? old;
    bool keystore;
    try {
      old = await _legacy.invokeMethod<Map<Object?, Object?>>('readPrefs');
      keystore = await _legacy.invokeMethod<bool>('hasKeystoreOwnerKey') ?? false;
    } catch (_) {
      return; // no legacy data here (or not wired up yet): try again next time
    }
    old ??= {};
    String? str(String k) => old![k] is String ? old[k] as String : null;
    bool? flag(String k) => old![k] is bool ? old[k] as bool : null;
    for (final k in ['installId', 'name', 'approval', 'approved', 'ownerChain']) {
      final v = str(k);
      if (v != null) await p.setString('$_p$k', v);
    }
    for (final k in ['remember', 'hadApproval', 'handedOver']) {
      final v = flag(k);
      if (v != null) await p.setBool('$_p$k', v);
    }
    final at = old['handedOverAt'];
    if (at is int) await p.setInt('${_p}handedOverAt', at);
    if (str('pin') != null) {
      await _secure.write(key: '${_p}pin', value: str('pin'));
      await _secure.write(key: '${_p}salt', value: str('salt') ?? '');
    }
    if (str('handoverCode') != null) await _secure.write(key: '${_p}handoverCode', value: str('handoverCode'));
    final key = str('unwrappedOwnerKey');
    if (key != null && str('ownerKey') != null) {
      await _secure.write(key: '${_p}ownerKey', value: key);
    } else if (str('ownerKey') != null) {
      // A handed-over key that can't be unlocked any more: like Java, this phone isn't an owner
      await p.remove('${_p}ownerChain');
    }
    await p.setBool('${_p}keystoreOwner', keystore);
    await p.setBool('${_p}migrated', true);
  }

  /// Forgets cached state between tests.
  @visibleForTesting
  static void debugReset({String? builtInKey}) {
    _unlocked = false;
    _migrating = null;
    _keystorePublic = null;
    _built = Future.value(AccessCodes.publicKey(builtInKey));
  }
}

String _lock(List<Object> a) => AccessCodes.handoverCode(a[0] as Uint8List, a[1] as String, a[2] as String);

List<Object>? _open(List<String> a) {
  final h = AccessCodes.openHandover(a[0], a[1]);
  return h == null ? null : [h.privateKeyPkcs8, h.chain];
}

/// Opens the home screen if this phone may use the app, otherwise the sign-in screen.
Future<void> enterApp(BuildContext context) async {
  final n = await Access.need();
  if (!context.mounted) return;
  Navigator.of(
    context,
  ).pushReplacement(MaterialPageRoute(builder: (_) => n == Need.none ? const ProjectsScreen() : const SignInScreen()));
}

/// A text shared into the app from Messages: the screen it belongs on (an approval code goes to
/// sign-in, a handover or access request to Approvals), or null if it isn't a Daily Work Log code.
Future<Widget?> screenForSharedText(String? t) async {
  if (AccessCodes.looksLikeHandover(t)) {
    return await Access.isOwner() && await Access.need() != Need.none
        ? SignInScreen(passedText: t)
        : ApprovalsScreen(passedText: t);
  }
  if (AccessCodes.parseApproval(t) != null) return SignInScreen(passedText: t);
  if (AccessCodes.parseRequest(t) != null) {
    return await Access.need() == Need.none ? ApprovalsScreen(passedText: t) : SignInScreen(passedText: t);
  }
  return null;
}
