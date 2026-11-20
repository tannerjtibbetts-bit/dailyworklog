// Support > Ownership proof: the copyright notice, and a signed proof that the owner's phone holds
// the app's owner key (make one on the owner's phone; anyone can check one).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../about/ownership.dart';
import '../access/access.dart';
import '../access/ownership_proof.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

class OwnershipScreen extends StatefulWidget {
  const OwnershipScreen({super.key});

  @override
  State<OwnershipScreen> createState() => _OwnershipScreenState();
}

class _OwnershipScreenState extends State<OwnershipScreen> {
  bool _owner = false;
  String? _fingerprint;
  late final _name = TextEditingController(text: Store.instance.userName);
  final _paste = TextEditingController();
  Widget? _result;

  @override
  void initState() {
    super.initState();
    () async {
      final key = await Access.builtInOwnerKey();
      final owner = await Access.isOwner();
      if (mounted) {
        setState(() {
          _owner = owner;
          _fingerprint = key == null ? null : OwnershipProof.fingerprint(key);
        });
      }
    }();
  }

  @override
  void dispose() {
    _name.dispose();
    _paste.dispose();
    super.dispose();
  }

  Future<void> _make() async {
    final name = _name.text.trim();
    if (name.isEmpty) {
      toast(context, 'Type your name first');
      return;
    }
    try {
      final text = await Access.ownershipProof(name);
      await Clipboard.setData(ClipboardData(text: text));
      if (!mounted) return;
      final box = context.findRenderObject() as RenderBox?;
      await SharePlus.instance.share(ShareParams(
        text: text,
        subject: 'Daily Work Log ownership proof',
        sharePositionOrigin: box == null ? null : box.localToGlobal(Offset.zero) & box.size,
      ));
    } catch (e) {
      if (mounted) toast(context, "Couldn't make the proof: $e", long: true);
    }
  }

  Future<void> _check() async {
    final r = OwnershipProof.check(_paste.text, await Access.builtInOwnerKey());
    if (!mounted) return;
    Widget card(Color color, IconData icon, String title, String body) => Container(
          margin: const EdgeInsets.only(top: 14),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.10),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: color, width: 1.5),
          ),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(icon, color: color),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: TextStyle(color: color, fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text(body, style: const TextStyle(color: C.text, fontSize: 16)),
              ]),
            ),
          ]),
        );
    setState(() {
      if (r == null) {
        _result = card(C.red, Icons.help_outline, 'No proof found',
            'Paste the whole ownership proof, including the line that starts with ${OwnershipProof.prefix}');
      } else if (!r.genuine) {
        _result = card(C.red, Icons.gpp_bad_outlined, 'Not genuine',
            "This proof was changed, or it wasn't signed with an owner key.");
      } else {
        final match = switch (r.sameAsThisApp) {
          true => 'It is the owner key built into this copy of the app.',
          false => 'It is NOT the owner key built into this copy of the app.',
          null => "This copy of the app has no owner key built in, so compare the fingerprint with the owner's.",
        };
        _result = card(r.sameAsThisApp == false ? C.red : C.green, Icons.verified_outlined, 'Genuine',
            'Signed by ${r.name} on ${r.at} with owner key ${r.fingerprint}. $match');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    const para = TextStyle(color: C.text, fontSize: 16, height: 1.35);
    const head = TextStyle(color: C.navy, fontSize: 20, fontWeight: FontWeight.bold);
    return Scaffold(
      appBar: AppBar(title: const Text('Ownership proof')),
      body: ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 40), children: [
        const Text(Ownership.notice, style: TextStyle(color: C.navy, fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        const Text(
          'Daily Work Log was created by ${Ownership.owner}. Its code, screens, manual and pictures are his '
          'work and may not be copied.',
          style: para,
        ),
        if (_fingerprint != null) ...[
          const SizedBox(height: 10),
          SelectableText("This app's owner key fingerprint: $_fingerprint", style: para),
        ],
        const SizedBox(height: 26),
        const Text('Make an ownership proof', style: head),
        const SizedBox(height: 6),
        Text(
          _owner
              ? "This phone holds the app's owner key, so it can sign a proof that you own Daily Work Log. "
                  'Keep a copy somewhere safe, and send it to anyone who needs to see it.'
              : "Only the owner's phone can make one.",
          style: para,
        ),
        if (_owner) ...[
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Your name'),
          ),
          const SizedBox(height: 12),
          BigButton('Make ownership proof', _make),
        ],
        const SizedBox(height: 26),
        const Text('Check an ownership proof', style: head),
        const SizedBox(height: 6),
        const Text('Paste a proof you were sent, then tap Check.', style: para),
        TextField(
          controller: _paste,
          minLines: 3,
          maxLines: 8,
          decoration: const InputDecoration(hintText: 'Paste the ownership proof here'),
        ),
        const SizedBox(height: 12),
        BigButton('Check', _check, filled: false),
        ?_result,
      ]),
    );
  }
}
