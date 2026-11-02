// Sign-in rules (Access): PIN, approval, owner, handover, and moving over from the Java app.
import 'dart:convert';
import 'dart:io';

import 'package:dailyworklog/access/access.dart';
import 'package:dailyworklog/access/access_codes.dart';
import 'package:dailyworklog/screens/approvals_screen.dart';
import 'package:dailyworklog/screens/sign_in_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

const legacy = MethodChannel('dailyworklog/legacy_access');

/// A fresh phone with nothing saved, running a build with [builtInKey] (null: no owner yet).
void freshPhone({String? builtInKey, Map<String, Object> prefs = const {}}) {
  SharedPreferences.setMockInitialValues(prefs);
  FlutterSecureStorage.setMockInitialValues({});
  Access.debugReset(builtInKey: builtInKey);
}

String approvalFor(dynamic root, String installId, String name, String expiry, [String chain = '']) =>
    AccessCodes.approvalCode(expiry, AccessCodes.sign(root, AccessCodes.payload(installId, name, expiry)), chain);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final root = AccessCodes.generateKeyPair();
  final rootText = AccessCodes.encodeKey(root.publicKey);
  final java = jsonDecode(File('test/fixtures/access_interop_java.json').readAsStringSync()) as Map<String, dynamic>;

  tearDown(() {
    Access.debugMigrateEverywhere = false;
    messenger.setMockMethodCallHandler(legacy, null);
  });

  group('no owner built in (unlocked)', () {
    test('straight in without a PIN; PIN once set', () async {
      freshPhone();
      expect(await Access.need(), Need.none);
      expect(await Access.isOwner(), isFalse);
      await Access.setPin('4321', false);
      expect(await Access.hasPin(), isTrue);
      expect(await Access.need(), Need.none, reason: 'unlocked while the app is open');
      Access.debugReset();
      expect(await Access.need(), Need.pin);
      expect(await Access.checkPin('1111', true), isFalse);
      expect(await Access.checkPin('4321', true), isTrue);
      Access.debugReset();
      expect(await Access.need(), Need.none, reason: 'kept signed in');
      await Access.signOut();
      expect(await Access.need(), Need.pin);
      await Access.forgotPin();
      expect(await Access.hasPin(), isFalse);
      expect(await Access.hadApproval(), isTrue);
      expect(await Access.need(), Need.none);
    });

    test('install id stays the same', () async {
      freshPhone();
      final id = await Access.installId();
      expect(await Access.installId(), id);
      await Access.forgotPin();
      expect(await Access.installId(), id);
    });
  });

  group('owner built in', () {
    test('approval, then a new PIN, then the PIN', () async {
      freshPhone(builtInKey: rootText);
      expect(await Access.need(), Need.approval);
      expect(await Access.hadApproval(), isFalse);
      final req = await Access.requestCode('  Sam Maint ');
      expect(await Access.requestedName(), 'Sam Maint');
      final r = AccessCodes.parseRequest(req)!;
      expect(r.installId, await Access.installId());

      expect(
        await Access.acceptApproval('hello'),
        "That doesn't look like an approval code. Paste the whole text you were sent.",
      );
      expect(
        await Access.acceptApproval(approvalFor(root.privateKey, 'other-phone', 'Sam Maint', '2099-01-01')),
        "That code isn't for this phone and name. Ask for a new one using the request from this phone.",
      );
      expect(
        await Access.acceptApproval(approvalFor(root.privateKey, r.installId, r.name, '2020-01-01')),
        'That approval has expired. Send a new request.',
      );
      expect(
        await Access.acceptApproval('Approval:\n${approvalFor(root.privateKey, r.installId, r.name, '2099-01-01')}\n'),
        isNull,
      );
      expect(await Access.isApproved(), isTrue);
      expect(await Access.need(), Need.newPin);
      await Access.setPin('123456', false);
      Access.debugReset(builtInKey: rootText);
      expect(await Access.need(), Need.pin);
      await Access.forgotPin();
      expect(await Access.need(), Need.approval);
      expect(await Access.hadApproval(), isTrue);
    });

    test('owner set up on this phone, approves, hands over to another phone', () async {
      freshPhone();
      final key = await Access.createOwnerKey();
      expect(await Access.hasOwnerKey(), isTrue);
      expect(await Access.createOwnerKey(), key, reason: 'same key again');
      expect(await Access.isOwner(), isFalse, reason: 'not built in yet');
      Access.debugReset(builtInKey: key);
      final built = AccessCodes.publicKey(key);
      expect(await Access.isOwner(), isTrue);
      expect(await Access.need(), Need.newPin);

      final code = await Access.approve(const AccessRequest('install-9', 'Pat'));
      expect(AccessCodes.verify(built, 'install-9', 'Pat', AccessCodes.parseApproval(code)), isTrue);
      expect((await Access.approvedList()).single['name'], 'Pat');

      final text = await Access.handOver('654321', 'Tanner');
      await Access.rememberHandoverCode('654321');
      expect(await Access.handedOver(), isTrue);
      expect(await Access.handedOverAt(), greaterThan(0));
      expect(await Access.handoverCode(), '654321');
      expect(await Access.isOwner(), isFalse);
      expect(await Access.isApproved(), isTrue, reason: 'keeps its own access');
      expect(await Access.requestedName(), 'Tanner');
      await Access.undoHandover();
      expect(await Access.isOwner(), isTrue);
      expect(await Access.handoverCode(), '');

      // The other phone
      freshPhone(builtInKey: key);
      expect(
        await Access.acceptHandover('hello', '654321'),
        "That doesn't look like a handover text. Paste the whole text you were sent.",
      );
      expect(
        await Access.acceptHandover(text, '654320'),
        "Wrong code, or the text isn't complete. Check the 6-digit code on the owner phone.",
      );
      expect(await Access.acceptHandover(text, '654321'), isNull);
      expect(await Access.isOwner(), isTrue);
      final code2 = await Access.approve(const AccessRequest('install-10', 'Lee'));
      expect(AccessCodes.verify(built, 'install-10', 'Lee', AccessCodes.parseApproval(code2)), isTrue);
      expect(AccessCodes.parseApproval(code2)!.chain, isNotEmpty);

      // A build with a different owner refuses it
      freshPhone(builtInKey: rootText);
      expect(await Access.acceptHandover(text, '654321'), "That handover isn't from this app's owner.");
      freshPhone();
      expect(await Access.acceptHandover(text, '654321'), "This copy of the app doesn't have an owner set up.");
    });

    test('takes over from a handover made by the Java app', () async {
      freshPhone(builtInKey: java['ownerKey'] as String);
      expect(await Access.acceptHandover(java['handover'] as String, java['handoverCode'] as String), isNull);
      expect(await Access.isOwner(), isTrue);
      final code = await Access.approve(const AccessRequest('install-11', 'Kim'));
      final a = AccessCodes.parseApproval(code)!;
      expect(a.chain, java['chain']);
      expect(AccessCodes.verify(AccessCodes.publicKey(java['ownerKey'] as String), 'install-11', 'Kim', a), isTrue);
    });
  });

  group('moving over from the Java app', () {
    void mockLegacy(Map<String, Object?> prefs, {bool keystore = false}) {
      Access.debugMigrateEverywhere = true;
      messenger.setMockMethodCallHandler(legacy, (call) async {
        switch (call.method) {
          case 'readPrefs':
            return prefs;
          case 'hasKeystoreOwnerKey':
            return keystore;
          case 'keystoreOwnerPublicKey':
            return keystore ? rootText : null;
          case 'keystoreSign':
            return AccessCodes.sign(root.privateKey, (call.arguments as Map)['data'] as Uint8List);
        }
        return null;
      });
    }

    test('approved user with a PIN stays signed up', () async {
      freshPhone(builtInKey: rootText);
      mockLegacy({
        'installId': 'legacy-id',
        'name': 'Sam Maint',
        'approval': approvalFor(root.privateKey, 'legacy-id', 'Sam Maint', '2099-01-01'),
        'salt': 'c2FsdHNhbHRzYWx0MTIzNA==',
        'pin': 'U72khqV/6ytgW5Akb6SoX3ujrGUqI2Z7MvWsGKEsTWs=',
        'remember': false,
        'hadApproval': true,
      });
      expect(await Access.installId(), 'legacy-id');
      expect(await Access.isApproved(), isTrue);
      expect(await Access.need(), Need.pin);
      expect(await Access.checkPin('1235', false), isFalse);
      expect(await Access.checkPin('1234', false), isTrue);
      // Only once
      mockLegacy({'installId': 'other'});
      Access.debugReset(builtInKey: rootText);
      expect(await Access.installId(), 'legacy-id');
    });

    test('no channel (iPhone, tests): nothing imported, tried again later', () async {
      freshPhone(builtInKey: rootText);
      Access.debugMigrateEverywhere = true;
      expect(await Access.need(), Need.approval);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getBool('access.migrated'), isNull);
    });

    test('original owner key in the Android key store signs through the channel', () async {
      freshPhone(builtInKey: rootText);
      mockLegacy({'installId': 'owner-id', 'remember': true}, keystore: true);
      expect(await Access.isOwner(), isTrue);
      expect(await Access.need(), Need.none);
      expect(await Access.createOwnerKey(), rootText, reason: 'keeps the key store key');
      final code = await Access.approve(const AccessRequest('install-12', 'Ana'));
      expect(AccessCodes.verify(root.publicKey, 'install-12', 'Ana', AccessCodes.parseApproval(code)), isTrue);
      expect(AccessCodes.parseApproval(code)!.chain, '');
      final text = await Access.handOver('111222', '');
      expect(await Access.isOwner(), isFalse);
      expect(await Access.isApproved(), isTrue);
      Access.debugMigrateEverywhere = false;
      freshPhone(builtInKey: rootText);
      expect(await Access.acceptHandover(text, '111222'), isNull);
      expect(await Access.isOwner(), isTrue);
    });

    test('handed-over owner key comes across', () async {
      final h = AccessCodes.openHandover(java['handover'] as String, java['handoverCode'] as String)!;
      freshPhone(builtInKey: java['ownerKey'] as String);
      mockLegacy({
        'installId': 'work-id',
        'ownerKey': 'aXY=:Y3Q=',
        'unwrappedOwnerKey': base64.encode(h.privateKeyPkcs8),
        'ownerChain': h.chain,
        'approved': '[{"name":"Old Friend","at":1760000000000,"expiry":"2026-10-09"}]',
      });
      expect(await Access.isOwner(), isTrue);
      expect((await Access.approvedList()).single['name'], 'Old Friend');
      final code = await Access.approve(const AccessRequest('install-13', 'Bo'));
      expect(
        AccessCodes.verify(
          AccessCodes.publicKey(java['ownerKey'] as String),
          'install-13',
          'Bo',
          AccessCodes.parseApproval(code),
        ),
        isTrue,
      );
    });
  });

  group('screens', () {
    testWidgets('sign-in asks for access', (tester) async {
      freshPhone(builtInKey: rootText);
      await tester.pumpWidget(const MaterialApp(home: SignInScreen()));
      await tester.pumpAndSettle();
      expect(find.text('1. Ask for access'), findsOneWidget);
      expect(find.text('Request access'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('App created by Tanner Tibbetts'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('App created by Tanner Tibbetts'), findsOneWidget);
    });

    testWidgets('approvals offers setup when no owner is built in', (tester) async {
      freshPhone();
      await tester.pumpWidget(const MaterialApp(home: ApprovalsScreen()));
      await tester.pumpAndSettle();
      expect(find.text('Become the app owner'), findsOneWidget);
      expect(find.text('Set up this phone as the owner'), findsOneWidget);
    });
  });
}
