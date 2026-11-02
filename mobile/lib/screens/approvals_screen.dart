// App owner: set this phone up as the owner, approve people's access requests, and hand the
// owner role to another phone. Other phones: take over as owner from a handover text.
import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../access/access.dart';
import '../access/access_codes.dart';
import '../access/access_ui.dart';
import '../data/fmt.dart';
import '../data/store.dart';
import '../ui/common.dart';
import 'sign_in_screen.dart';

class ApprovalsScreen extends StatefulWidget {
  /// Text shared or passed in (a request or handover), used once to fill in the paste box.
  final String? passedText;

  const ApprovalsScreen({super.key, this.passedText});

  @override
  State<ApprovalsScreen> createState() => _ApprovalsScreenState();
}

enum _Mode { loading, setup, owner, takeOver }

class _ApprovalsScreenState extends State<ApprovalsScreen> {
  _Mode _mode = _Mode.loading;
  bool _hasKey = false, _handedOver = false, _busy = false;
  int _handedOverAt = 0;
  String _handoverCode = '';
  List<Map<String, dynamic>> _approved = [];
  final _req = TextEditingController();
  final _phone = TextEditingController();
  final _text = TextEditingController();
  final _code = TextEditingController();
  String? _phoneError, _codeError;

  @override
  void initState() {
    super.initState();
    final t = widget.passedText ?? '';
    if (AccessCodes.parseRequest(t) != null) _req.text = t;
    if (AccessCodes.looksLikeHandover(t)) _text.text = t;
    _show();
  }

  @override
  void dispose() {
    for (final c in [_req, _phone, _text, _code]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _show() async {
    final built = await Access.builtInOwnerKey() != null;
    final owner = await Access.isOwner();
    final hasKey = await Access.hasOwnerKey();
    final approved = await Access.approvedList();
    final handedOver = await Access.handedOver();
    final at = await Access.handedOverAt();
    final code = await Access.handoverCode();
    if (!mounted) return;
    setState(() {
      _mode = owner ? _Mode.owner : (built ? _Mode.takeOver : _Mode.setup);
      _hasKey = hasKey;
      _approved = approved;
      _handedOver = handedOver;
      _handedOverAt = at;
      _handoverCode = code;
    });
  }

  @override
  Widget build(BuildContext context) {
    final List<Widget> body;
    switch (_mode) {
      case _Mode.loading:
        body = [];
      case _Mode.setup:
        body = _setup();
      case _Mode.owner:
        body = [..._approvals(), ..._handover()];
      case _Mode.takeOver:
        body = _takeOver();
    }
    return accessPage(body, appBar: Navigator.of(context).canPop(), top: 32);
  }

  Rect? _origin() {
    final box = context.findRenderObject() as RenderBox?;
    return box == null ? null : box.localToGlobal(Offset.zero) & box.size;
  }

  // ----- one-time setup -----

  List<Widget> _setup() => [
    accessTitle('Become the app owner'),
    if (!_hasKey) ...[
      accessPara(
        'This makes this phone the only one that can approve people. A secret key is created and kept '
        "in the phone's secure storage. Its public half is shared as ${Access.keyFile} so the next build of "
        'the app includes it.',
      ),
      BigButton('Set up this phone as the owner', _setUp),
    ] else ...[
      accessPara("This phone has the owner key, but the app hasn't been rebuilt with it yet."),
      BigButton('Share the key again', _setUp, filled: false),
    ],
    accessHeading('Then:'),
    accessPara(
      'Put ${Access.keyFile} in the app build as assets/owner_key.txt and install the new build. '
      'From then on, other phones using that app need your approval, and this page lets you approve them. '
      'Keep ${Access.keyFile}: every future build needs it.',
    ),
  ];

  Future<void> _setUp() async {
    try {
      await Access.becomeOwner(shareOrigin: _origin());
    } catch (e) {
      if (mounted) toast(context, "Couldn't set up the owner key: $e", long: true);
    }
    _show();
  }

  // ----- approving people -----

  List<Widget> _approvals() => [
    accessTitle('Approvals'),
    accessPara('When someone texts you an access request, copy the whole text, then tap Paste and Approve.'),
    accessInput(_req, 'Paste the request here', minLines: 3),
    BigButton('Paste', () async {
      final t = await clipboardText();
      setState(() => _req.text = t);
    }, filled: false),
    BigButton('Approve', () {
      final r = AccessCodes.parseRequest(_req.text);
      if (r == null) {
        toast(context, "That doesn't look like an access request", long: true);
        return;
      }
      confirm(
        context,
        'Approve ${r.name}?',
        'They can use the app on that phone for one year.',
        'Approve',
        () => _approve(r),
      );
    }),
    accessHeading('Approved'),
    if (_approved.isEmpty) accessPara('No one yet.'),
    for (final o in _approved.reversed)
      accessPara(
        '${o['name'] ?? ''}\nApproved ${Fmt.longStamp((o['at'] as num?)?.toInt() ?? 0)}'
        ' · until ${o['expiry'] ?? ''}',
      ),
  ];

  Future<void> _approve(AccessRequest r) async {
    try {
      final code = await Access.approve(r);
      await Clipboard.setData(ClipboardData(text: code));
      final origin = _origin();
      await _show();
      await SharePlus.instance.share(
        ShareParams(
          text: 'Your Daily Work Log approval. Copy this whole text, then paste it on the sign-in screen:\n$code',
          title: 'Send the approval to ${r.name}',
          sharePositionOrigin: origin,
        ),
      );
    } catch (e) {
      if (mounted) toast(context, "Couldn't approve: $e", long: true);
    }
  }

  // ----- handing over to another phone -----

  List<Widget> _handover() => [
    accessHeading('Hand over ownership'),
    accessPara(
      'Moving to another phone, like a work phone? Install this same app there first. Then type that '
      "phone's number and tap Send. You'll get a 6-digit code to type on that phone. Everyone you've "
      'approved stays approved, and no rebuild is needed.',
    ),
    accessInput(_phone, "New phone's number", error: _phoneError, keyboard: TextInputType.phone),
    BigButton('Send handover text', () {
      final number = phoneDigits(_phone.text);
      if (number.replaceAll('+', '').length < 7) {
        setState(() => _phoneError = 'Enter the phone number');
        return;
      }
      setState(() => _phoneError = null);
      confirm(
        context,
        'Hand over ownership?',
        'Once you send it, this phone stops approving people. '
            'It keeps its own access and everything saved on it.',
        'Hand over',
        () => _handOver(number),
      );
    }),
  ];

  Future<void> _handOver(String number) async {
    if (_busy) return;
    _busy = true;
    final code = Random.secure().nextInt(1000000).toString().padLeft(6, '0');
    String myName = '';
    try {
      myName = Store.instance.userName;
    } catch (_) {}
    toast(context, 'Preparing…');
    try {
      final text = await Access.handOver(code, myName);
      await Access.rememberHandoverCode(code);
      await _sendHandover(number, code, text);
    } catch (e) {
      if (mounted) toast(context, "Couldn't hand over: $e", long: true);
    } finally {
      _busy = false;
    }
  }

  Future<void> _sendHandover(String number, String code, String text) async {
    await _show();
    final msg =
        'Daily Work Log owner handover. On your new phone, copy this whole text, open the app and tap '
        '"Take over as the app owner":\n$text';
    await Clipboard.setData(ClipboardData(text: msg));
    if (!mounted) return;
    showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text('Your code: $code'),
        content: const Text(
          "Type this code on the new phone after pasting the text. Don't text the code; "
          "it's also shown on this page until the handover is done.",
        ),
        actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('OK'))],
      ),
    );
    final body = Uri.encodeComponent(msg);
    final uri = Uri.parse(Platform.isIOS ? 'sms:$number&body=$body' : 'sms:$number?body=$body');
    var opened = false;
    try {
      opened = await launchUrl(uri);
    } catch (_) {}
    if (!opened) {
      await SharePlus.instance.share(ShareParams(text: msg, title: 'Send the handover to your new phone'));
    }
  }

  List<Widget> _takeOver() => [
    if (_handedOver) ...[
      accessTitle('Ownership handed over'),
      accessPara(
        'On ${Fmt.longStamp(_handedOverAt)} you sent the owner role to another phone. '
        "This phone can't approve people any more. Everything else works as before, and everyone "
        'you approved stays approved.',
      ),
      if (_handoverCode.isNotEmpty) accessPara('Code for the new phone: $_handoverCode'),
      BigButton('Undo: keep this phone as the owner', () {
        confirm(
          context,
          'Keep this phone as the owner?',
          "Only do this if the new phone didn't take over. If it did, you'd have two owner phones.",
          'Keep',
          () async {
            await Access.undoHandover();
            _show();
          },
        );
      }, filled: false),
      accessHeading('Take it back later'),
      accessPara(
        'To make this phone the owner again, send a handover from the current owner phone and enter it below.',
      ),
    ] else ...[
      accessTitle('Take over as the app owner'),
      accessPara(
        "Only the app owner's phone can approve people. To make this phone the owner: on the owner phone, "
        'open Approvals and use Hand over ownership. Copy the whole text it sends to this phone, paste it '
        'here, then type the 6-digit code shown on the owner phone.',
      ),
    ],
    accessInput(_text, 'Paste the handover text here', minLines: 3),
    BigButton('Paste', () async {
      final t = await clipboardText();
      setState(() => _text.text = t);
    }, filled: false),
    accessInput(_code, '6-digit code', error: _codeError, keyboard: TextInputType.number),
    BigButton('Take over', _takeOverNow),
  ];

  Future<void> _takeOverNow() async {
    final t = _text.text, c = _code.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(c)) {
      setState(() => _codeError = 'Enter the 6 digits');
      return;
    }
    setState(() => _codeError = null);
    if (_busy) return;
    _busy = true;
    toast(context, 'Checking…');
    final err = await Access.acceptHandover(t, c);
    _busy = false;
    if (!mounted) return;
    if (err != null) {
      toast(context, err, long: true);
      return;
    }
    toast(context, 'This phone is now the app owner', long: true);
    Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const SignInScreen()), (_) => false);
  }
}
