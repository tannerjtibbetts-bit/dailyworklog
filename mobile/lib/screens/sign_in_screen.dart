// Sign in: ask the owner for access (request code by text, approval code back), choose a PIN,
// or enter it. "Keep me signed in on this phone" skips the PIN next time.
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../support/support_screens.dart';
import '../access/access.dart';
import '../access/access_codes.dart';
import '../access/access_ui.dart';
import '../data/store.dart';
import '../ui/common.dart';
import 'approvals_screen.dart';
import 'projects_screen.dart';

class SignInScreen extends StatefulWidget {
  /// A text shared or passed in (an approval code), used to fill in the paste box.
  final String? passedText;

  const SignInScreen({super.key, this.passedText});

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  Need? _need;
  bool _hadApproval = false;
  final _name = TextEditingController();
  final _code = TextEditingController();
  final _pin = TextEditingController();
  final _again = TextEditingController();
  String? _nameError, _pinError, _againError;
  bool _remember = true;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    final passed = widget.passedText;
    if (AccessCodes.parseApproval(passed) != null) _code.text = passed!;
    _show();
  }

  @override
  void dispose() {
    for (final c in [_name, _code, _pin, _again]) {
      c.dispose();
    }
    super.dispose();
  }

  static String _userName() {
    try {
      return Store.instance.userName;
    } catch (_) {
      return '';
    }
  }

  Future<void> _show() async {
    final n = await Access.need();
    if (n == Need.none) {
      _open();
      return;
    }
    final had = await Access.hadApproval();
    final saved = await Access.requestedName();
    if (!mounted) return;
    setState(() {
      _need = n;
      _hadApproval = had;
      _pin.clear();
      _again.clear();
      _pinError = _againError = null;
      if (_name.text.isEmpty) _name.text = saved.isEmpty ? _userName() : saved;
    });
  }

  void _open() {
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const ProjectsScreen()), (_) => false);
  }

  @override
  Widget build(BuildContext context) {
    final List<Widget> body;
    switch (_need) {
      case Need.approval:
        body = _request();
      case Need.newPin:
        body = _newPin();
      case Need.pin:
        body = _enterPin();
      default:
        return const Scaffold(body: SizedBox());
    }
    return accessPage([
      ...body,
      const Padding(
        padding: EdgeInsets.only(top: 24),
        child: Center(child: SupportButton()),
      ),
      accessCredit(),
    ]);
  }

  // ----- asking the owner for access -----

  List<Widget> _request() => [
    accessTitle('Sign in'),
    accessPara(
      _hadApproval
          ? "Your approval has ended or was reset. Ask the app owner for a new one. Everything you've saved on this phone is still here."
          : 'This app needs approval from its owner before you can use it.',
    ),
    accessHeading('1. Ask for access'),
    accessInput(_name, 'Your full name', error: _nameError, caps: TextCapitalization.words),
    BigButton('Request access', () async {
      final n = _name.text.trim();
      if (n.isEmpty) {
        setState(() => _nameError = 'Enter your name');
        return;
      }
      setState(() => _nameError = null);
      final box = context.findRenderObject() as RenderBox?;
      final code = await Access.requestCode(n);
      await SharePlus.instance.share(
        ShareParams(
          text: 'Daily Work Log access request from $n:\n$code',
          title: 'Send your request to the app owner',
          sharePositionOrigin: box == null ? null : box.localToGlobal(Offset.zero) & box.size,
        ),
      );
    }),
    accessPara("Send it to the app owner by text. They'll text back an approval code."),
    accessHeading('2. Enter the approval code'),
    accessInput(_code, 'Paste the whole text you were sent', minLines: 3),
    BigButton('Paste', () async {
      final t = await clipboardText();
      setState(() => _code.text = t);
    }, filled: false),
    BigButton('Sign in', () async {
      final err = await Access.acceptApproval(_code.text);
      if (!mounted) return;
      if (err != null) {
        toast(context, err, long: true);
        return;
      }
      try {
        final store = Store.instance;
        if (store.userName.isEmpty) {
          store.userName = await Access.requestedName();
          store.save();
        }
      } catch (_) {}
      if (!mounted) return;
      toast(context, 'Approved. Welcome!');
      _show();
    }),
    accessHeading('Taking over as the app owner?'),
    accessPara('If the owner sent this phone a handover text, use this instead.'),
    BigButton('Take over as the app owner', () async {
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ApprovalsScreen()));
      _show();
    }, filled: false),
  ];

  // ----- PIN -----

  List<Widget> _newPin() => [
    accessTitle('Choose a PIN'),
    accessPara("You'll use it to open the app. 4 to 8 digits."),
    accessInput(_pin, 'PIN', error: _pinError, keyboard: TextInputType.number, obscure: true),
    accessInput(_again, 'PIN again', error: _againError, keyboard: TextInputType.number, obscure: true),
    accessRemember(_remember, (v) => setState(() => _remember = v)),
    BigButton('Save', () async {
      final p = _pin.text;
      if (!RegExp(r'^\d{4,8}$').hasMatch(p)) {
        setState(() {
          _pinError = '4 to 8 digits';
          _againError = null;
        });
        return;
      }
      if (p != _again.text) {
        setState(() {
          _pinError = null;
          _againError = "The PINs don't match";
        });
        return;
      }
      if (_busy) return;
      _busy = true;
      await Access.setPin(p, _remember);
      _busy = false;
      _open();
    }),
  ];

  List<Widget> _enterPin() {
    final name = _userName();
    return [
      accessTitle('Sign in'),
      if (name.isNotEmpty) accessPara('Welcome back, $name.'),
      accessInput(_pin, 'PIN', error: _pinError, keyboard: TextInputType.number, obscure: true),
      accessRemember(_remember, (v) => setState(() => _remember = v)),
      BigButton('Sign in', () async {
        if (await Access.checkPin(_pin.text, _remember)) {
          _open();
        } else if (mounted) {
          setState(() => _pinError = 'Wrong PIN');
        }
      }),
      BigButton('Forgot PIN', () {
        confirm(
          context,
          'Reset your PIN?',
          "You'll need the app owner to approve this phone again, then you choose a new PIN. "
              'Your projects, checklists, photos and crew stay on this phone; nothing is deleted.',
          'Reset',
          () async {
            await Access.forgotPin();
            _show();
          },
        );
      }, filled: false),
    ];
  }
}
