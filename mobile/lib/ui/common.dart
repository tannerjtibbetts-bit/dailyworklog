// Shared dialogs, bottom sheets and small widgets, matching the Android app's Ui class.
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import 'theme.dart';

class SheetItem {
  final IconData icon;
  final String label;
  final VoidCallback action;
  final bool danger;

  const SheetItem(this.icon, this.label, this.action, {this.danger = false});
}

/// Digits and + only, for phone numbers.
String phoneDigits(String? phone) => (phone ?? '').replaceAll(RegExp(r'[^0-9+]'), '');

/// The app's navigator, for messages after background work when the screen that started it has closed.
final appNavigator = GlobalKey<NavigatorState>();

void toast(BuildContext context, String message, {bool long = false}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(message),
      duration: Duration(seconds: long ? 4 : 2),
    ));
}

/// Yes/Cancel dialog; runs [onYes] if the positive button is tapped.
Future<void> confirm(BuildContext context, String title, String message, String positive, VoidCallback onYes) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
        TextButton(onPressed: () => Navigator.pop(c, true), child: Text(positive)),
      ],
    ),
  );
  if (ok == true) onYes();
}

/// One-line text prompt. Returns the trimmed text, or null if cancelled. Empty isn't allowed.
Future<String?> prompt(BuildContext context, String title, String hint, String? prefill, String positive,
    {TextInputType keyboard = TextInputType.text,
    String? Function(String)? validate,
    TextCapitalization caps = TextCapitalization.sentences}) {
  final ctl = TextEditingController(text: prefill ?? '');
  String? error;
  return showDialog<String>(
    context: context,
    builder: (c) => StatefulBuilder(
      builder: (c, set) {
        void submit() {
          final s = ctl.text.trim();
          final e = s.isEmpty ? 'Required' : validate?.call(s);
          if (e != null) {
            set(() => error = e);
            return;
          }
          Navigator.pop(c, s);
        }

        return AlertDialog(
          title: Text(title),
          content: TextField(
            controller: ctl,
            autofocus: true,
            keyboardType: keyboard,
            textCapitalization: caps,
            decoration: InputDecoration(hintText: hint, errorText: error),
            onSubmitted: (_) => submit(),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
            TextButton(onPressed: submit, child: Text(positive)),
          ],
        );
      },
    ),
  );
}

/// Phone number prompt with the phone keypad.
Future<String?> promptPhone(BuildContext context, String title, String? prefill) => prompt(
      context,
      title,
      'e.g. 519-555-0123',
      prefill,
      'Save',
      keyboard: TextInputType.phone,
      caps: TextCapitalization.none,
      validate: (s) => phoneDigits(s).length < 7 ? 'Enter a phone number' : null,
    );

class CrewDetails {
  final String name, phone, role;
  final bool texts;

  const CrewDetails(this.name, this.phone, this.role, this.texts);
}

/// Name + optional phone number for a new crew member.
Future<CrewDetails?> promptCrew(BuildContext context, String prefillName) =>
    crewDialog(context, 'Add crew member', 'Add', prefillName, '', Project.crewRole, false, null);

/// One crew member's details: name, phone, group (Crew / Tech / Supervision) and whether
/// they're texted when assigned. [onRemove] adds a Remove button when not null.
Future<CrewDetails?> crewDialog(BuildContext context, String title, String positive, String prefillName,
    String prefillPhone, String role, bool texts, VoidCallback? onRemove) {
  final name = TextEditingController(text: prefillName);
  final phone = TextEditingController(text: prefillPhone);
  var group = role;
  var textOn = texts;
  String? error;
  return showDialog<CrewDetails>(
    context: context,
    builder: (c) => StatefulBuilder(
      builder: (c, set) => AlertDialog(
        title: Text(title),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextField(
                controller: name,
                autofocus: onRemove == null,
                textCapitalization: TextCapitalization.words,
                decoration: InputDecoration(labelText: 'Name', errorText: error),
              ),
              TextField(
                controller: phone,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'Phone number (optional)'),
              ),
              const SizedBox(height: 12),
              const Text('Group', style: TextStyle(color: C.grey)),
              RadioGroup<String>(
                groupValue: group,
                onChanged: (v) => set(() => group = v ?? Project.crewRole),
                child: Wrap(
                  children: [
                    for (final r in const [
                      [Project.crewRole, 'Crew'],
                      [Project.techRole, 'Tech'],
                      [Project.mgmtRole, 'Supervision'],
                    ])
                      Row(mainAxisSize: MainAxisSize.min, children: [
                        Radio<String>(value: r[0]),
                        GestureDetector(onTap: () => set(() => group = r[0]), child: Text(r[1])),
                        const SizedBox(width: 8),
                      ]),
                  ],
                ),
              ),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                controlAffinity: ListTileControlAffinity.leading,
                value: textOn,
                onChanged: (v) => set(() => textOn = v ?? false),
                title: const Text('This contact will receive texts when assigned to a field or task'),
              ),
            ],
          ),
        ),
        actions: [
          if (onRemove != null)
            TextButton(
              onPressed: () {
                Navigator.pop(c);
                onRemove();
              },
              child: const Text('Remove', style: TextStyle(color: C.red)),
            ),
          TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
          TextButton(
            onPressed: () {
              final n = name.text.trim();
              if (n.isEmpty) {
                set(() => error = 'Enter a name');
                return;
              }
              Navigator.pop(c, CrewDetails(Fmt.capitalize(n), phone.text.trim(), group, textOn));
            },
            child: Text(positive),
          ),
        ],
      ),
    ),
  );
}

/// Bottom sheet with large icon rows, styled like the Android app's menu.
Future<void> menu(BuildContext context, String? title, List<SheetItem> items) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (c) => SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(22, 0, 22, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (title != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(title, style: const TextStyle(color: C.grey, fontSize: 16)),
              ),
            for (final it in items)
              InkWell(
                borderRadius: BorderRadius.circular(14),
                onTap: () {
                  Navigator.pop(c);
                  it.action();
                },
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Row(children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(color: C.sky, borderRadius: BorderRadius.circular(14)),
                      child: Icon(it.icon, color: it.danger ? C.red : C.deepBlue),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Text(it.label,
                          style: TextStyle(fontSize: 18, color: it.danger ? C.red : C.text)),
                    ),
                  ]),
                ),
              ),
          ],
        ),
      ),
    ),
  );
}

/// Round initials badge, like "JM" and "SH" in the old app.
class InitialsBadge extends StatelessWidget {
  final String name;
  final double size;
  final String? label;

  const InitialsBadge(this.name, {super.key, this.size = 36, this.label});

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: C.sky,
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 2),
        ),
        child: Text(
          label ?? Fmt.initials(name),
          style: TextStyle(color: C.deepBlue, fontWeight: FontWeight.bold, fontSize: size * 0.32),
        ),
      );
}

/// Overlapping badges for up to [max] people, then "+N".
class Badges extends StatelessWidget {
  final List<String> people;
  final double size;
  final int max;

  const Badges(this.people, {super.key, this.size = 32, this.max = 4});

  @override
  Widget build(BuildContext context) {
    final shown = people.length < max ? people.length : max;
    final kids = <Widget>[
      for (var i = 0; i < shown; i++) InitialsBadge(people[i], size: size),
      if (people.length > shown) InitialsBadge('+', size: size, label: '+${people.length - shown}'),
    ];
    return SizedBox(
      height: size,
      width: kids.isEmpty ? 0 : size + (kids.length - 1) * (size - 6),
      child: Stack(children: [
        for (var i = 0; i < kids.length; i++) Positioned(left: i * (size - 6), child: kids[i]),
      ]),
    );
  }
}

/// Full-screen photo with who took it and when. Tap anywhere to close.
void showPhoto(BuildContext context, Photo p, File f) {
  Navigator.of(context).push(PageRouteBuilder(
    opaque: false,
    pageBuilder: (c, _, _) => GestureDetector(
      onTap: () => Navigator.pop(c),
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(children: [
          Positioned.fill(child: InteractiveViewer(child: Image.file(f, fit: BoxFit.contain))),
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              color: const Color(0x99000000),
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 56),
              child: Text('${p.addedBy}, ${Fmt.stamp(p.addedAt)}',
                  style: const TextStyle(color: Colors.white, fontSize: 16)),
            ),
          ),
        ]),
      ),
    ),
  ));
}

/// Big rounded button: filled blue, or light sky with blue text.
class BigButton extends StatelessWidget {
  final String label;
  final VoidCallback? onTap;
  final bool filled;

  const BigButton(this.label, this.onTap, {super.key, this.filled = true});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 10),
        child: SizedBox(
          height: filled ? 56 : 46,
          width: double.infinity,
          child: Material(
            color: filled ? C.blue : C.sky,
            borderRadius: BorderRadius.circular(100),
            child: InkWell(
              borderRadius: BorderRadius.circular(100),
              onTap: onTap,
              child: Center(
                child: Text(label,
                    style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: filled ? Colors.white : C.deepBlue)),
              ),
            ),
          ),
        ),
      );
}
