// STUB (owner: access agent). Who may use the app on this phone.
import 'package:flutter/material.dart';

enum Need { none, approval, newPin, pin }

class Access {
  Access._();

  /// What the sign-in screen needs to ask for, or Need.none to go straight in.
  static Future<Need> need() async => Need.none;

  /// True if this phone is the app owner.
  static Future<bool> isOwner() async => false;

  static Future<void> signOut() async {}
}

/// Opens the home screen if this phone may use the app, otherwise the sign-in screen.
Future<void> enterApp(BuildContext context) async {}
