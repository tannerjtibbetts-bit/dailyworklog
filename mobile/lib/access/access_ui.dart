// Small building blocks shared by the sign-in and approvals screens.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../ui/theme.dart';

Widget accessTitle(String s) => Padding(
  padding: const EdgeInsets.only(bottom: 8),
  child: Text(
    s,
    style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: C.navy),
  ),
);

Widget accessHeading(String s) => Padding(
  padding: const EdgeInsets.only(top: 22, bottom: 4),
  child: Text(
    s,
    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: C.navy),
  ),
);

Widget accessPara(String s) => Padding(
  padding: const EdgeInsets.symmetric(vertical: 6),
  child: Text(s, style: const TextStyle(fontSize: 16, color: C.grey)),
);

/// Fine print at the bottom.
Widget accessCredit() => const Padding(
  padding: EdgeInsets.only(top: 40),
  child: Opacity(
    opacity: 0.8,
    child: Text(
      'App created by Tanner Tibbetts',
      textAlign: TextAlign.center,
      style: TextStyle(fontSize: 11, color: C.greyLight),
    ),
  ),
);

Widget accessInput(
  TextEditingController c,
  String hint, {
  String? error,
  TextInputType? keyboard,
  bool obscure = false,
  int minLines = 1,
  TextCapitalization caps = TextCapitalization.none,
  ValueChanged<String>? onChanged,
}) => TextField(
  controller: c,
  keyboardType: minLines > 1 ? TextInputType.multiline : keyboard,
  obscureText: obscure,
  minLines: minLines,
  maxLines: obscure ? 1 : (minLines > 1 ? null : 1),
  textCapitalization: caps,
  style: const TextStyle(fontSize: 18),
  onChanged: onChanged,
  decoration: InputDecoration(hintText: hint, errorText: error),
);

/// "Keep me signed in on this phone"
Widget accessRemember(bool value, ValueChanged<bool> onChanged) => CheckboxListTile(
  value: value,
  onChanged: (v) => onChanged(v ?? false),
  contentPadding: EdgeInsets.zero,
  controlAffinity: ListTileControlAffinity.leading,
  title: const Text('Keep me signed in on this phone', style: TextStyle(fontSize: 16)),
);

Future<String> clipboardText() async => (await Clipboard.getData(Clipboard.kTextPlain))?.text ?? '';

/// The page body: light background, padded, scrolls.
Widget accessPage(List<Widget> children, {double top = 40, bool appBar = false}) => Scaffold(
  backgroundColor: C.bg,
  appBar: appBar ? AppBar() : null,
  body: SafeArea(
    child: ListView(padding: EdgeInsets.fromLTRB(24, appBar ? 0 : top, 24, 24), children: children),
  ),
);
