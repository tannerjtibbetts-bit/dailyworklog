// Organization Contacts tab: company contacts who aren't on the crew, shared by every project.
// Call, text, email or open their link; add, edit and delete them.
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../data/models.dart';
import '../data/store.dart';
import '../services/sms.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

bool _validEmail(String s) => RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(s.trim());

/// "example.com" -> "https://example.com" (the same as Links).
String _normalize(String raw) {
  final t = raw.trim();
  if (t.isEmpty) return '';
  return RegExp(r'^[a-z][a-z0-9+.-]*://', caseSensitive: false).hasMatch(t) ? t : 'https://$t';
}

bool _validLink(String url) {
  final u = Uri.tryParse(url);
  return u != null && u.host.contains('.') && !url.contains(' ');
}

Future<void> _launch(BuildContext context, Uri uri) async {
  var ok = false;
  try {
    ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
  } catch (_) {}
  if (!ok && context.mounted) toast(context, "Couldn't open that on this phone");
}

void callContact(BuildContext context, Contact c) =>
    _launch(context, Uri(scheme: 'tel', path: Sms.digits(c.phone)));

void textContact(BuildContext context, Contact c) => Sms.compose(context, [c.phone], '');

void emailContact(BuildContext context, Contact c) => _launch(context, Uri(scheme: 'mailto', path: c.email.trim()));

void openContactLink(BuildContext context, Contact c) => _launch(context, Uri.parse(c.link));

/// Add (when [existing] is null) or edit a contact. Saves.
Future<void> editContact(BuildContext context, Contact? existing) async {
  final store = Store.instance;
  final c = existing;
  final name = TextEditingController(text: c?.name ?? '');
  final company = TextEditingController(text: c?.company ?? '');
  final title = TextEditingController(text: c?.title ?? '');
  final phone = TextEditingController(text: c?.phone ?? '');
  final email = TextEditingController(text: c?.email ?? '');
  final link = TextEditingController(text: c?.link ?? '');
  final notes = TextEditingController(text: c?.notes ?? '');
  String? nameError, emailError, linkError;
  final ok = await showDialog<bool>(
    context: context,
    builder: (d) => StatefulBuilder(
      builder: (d, set) {
        void save() {
          final l = _normalize(link.text);
          set(() {
            nameError = name.text.trim().isEmpty ? 'Enter a name' : null;
            emailError = email.text.trim().isNotEmpty && !_validEmail(email.text) ? 'Enter a valid email address' : null;
            linkError = l.isNotEmpty && !_validLink(l) ? 'Enter a web address' : null;
          });
          if (nameError == null && emailError == null && linkError == null) Navigator.pop(d, true);
        }

        return AlertDialog(
          title: Text(c == null ? 'Add contact' : 'Edit contact'),
          content: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              TextField(
                controller: name,
                autofocus: c == null,
                textCapitalization: TextCapitalization.words,
                decoration: InputDecoration(labelText: 'Name', errorText: nameError),
              ),
              TextField(
                controller: company,
                textCapitalization: TextCapitalization.words,
                decoration: const InputDecoration(labelText: 'Company'),
              ),
              TextField(
                controller: title,
                textCapitalization: TextCapitalization.words,
                decoration: const InputDecoration(labelText: 'Job title', hintText: 'e.g. Safety coordinator'),
              ),
              TextField(
                controller: phone,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'Phone number'),
              ),
              TextField(
                controller: email,
                keyboardType: TextInputType.emailAddress,
                autocorrect: false,
                decoration: InputDecoration(labelText: 'Email', errorText: emailError),
              ),
              TextField(
                controller: link,
                keyboardType: TextInputType.url,
                autocorrect: false,
                decoration: InputDecoration(labelText: 'Web link (optional)', errorText: linkError),
              ),
              TextField(
                controller: notes,
                minLines: 1,
                maxLines: 4,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(labelText: 'Notes'),
              ),
            ]),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(d, false), child: const Text('Cancel')),
            TextButton(onPressed: save, child: const Text('Save')),
          ],
        );
      },
    ),
  );
  if (ok != true) return;
  final x = c ?? Contact();
  x
    ..name = name.text.trim()
    ..company = company.text.trim()
    ..title = title.text.trim()
    ..phone = phone.text.trim()
    ..email = email.text.trim()
    ..link = _normalize(link.text)
    ..notes = notes.text.trim();
  if (c == null) store.contacts.add(x);
  store.save();
}

/// Press and hold: what can be done with a contact.
void contactMenu(BuildContext context, Contact c, VoidCallback changed) {
  menu(context, c.name, [
    if (c.phone.trim().isNotEmpty) SheetItem(Icons.call_outlined, 'Call', () => callContact(context, c)),
    if (c.phone.trim().isNotEmpty) SheetItem(Icons.sms_outlined, 'Text', () => textContact(context, c)),
    if (c.email.trim().isNotEmpty) SheetItem(Icons.mail_outline, 'Email', () => emailContact(context, c)),
    if (c.link.trim().isNotEmpty) SheetItem(Icons.open_in_new, 'Open link', () => openContactLink(context, c)),
    SheetItem(Icons.edit_outlined, 'Edit contact', () async {
      await editContact(context, c);
      changed();
    }),
    SheetItem(Icons.delete_outline, 'Delete contact', () {
      confirm(context, 'Delete this contact?', '${c.name} will be removed from every project.', 'Delete', () {
        Store.instance.contacts.remove(c);
        Store.instance.save();
        changed();
      });
    }, danger: true),
  ]);
}

/// The lines and buttons inside a contact card.
class ContactCardBody extends StatelessWidget {
  final Contact contact;

  const ContactCardBody(this.contact, {super.key});

  @override
  Widget build(BuildContext context) {
    final c = contact;
    Widget button(IconData icon, String tip, VoidCallback onTap) => Padding(
          padding: const EdgeInsets.only(right: 10, top: 8),
          child: Material(
            color: C.sky,
            shape: const CircleBorder(),
            child: IconButton(
              tooltip: tip,
              icon: Icon(icon, color: C.deepBlue),
              onPressed: onTap,
            ),
          ),
        );
    const grey = TextStyle(color: C.grey, fontSize: 15);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(c.name, style: const TextStyle(color: C.navy, fontSize: 20, fontWeight: FontWeight.bold)),
      if (c.titleLine().isNotEmpty) Padding(padding: const EdgeInsets.only(top: 3), child: Text(c.titleLine(), style: grey)),
      if (c.phone.trim().isNotEmpty) Text(c.phone.trim(), style: grey),
      if (c.email.trim().isNotEmpty) Text(c.email.trim(), style: grey),
      Wrap(children: [
        if (c.phone.trim().isNotEmpty) button(Icons.call_outlined, 'Call', () => callContact(context, c)),
        if (c.phone.trim().isNotEmpty) button(Icons.sms_outlined, 'Text', () => textContact(context, c)),
        if (c.email.trim().isNotEmpty) button(Icons.mail_outline, 'Email', () => emailContact(context, c)),
        if (c.link.trim().isNotEmpty) button(Icons.open_in_new, 'Open link', () => openContactLink(context, c)),
      ]),
    ]);
  }
}
