// Opens links; Formstack forms open with your usual answers already filled in. Formstack accepts
// answers in the link (?field123456=value), but only by each box's hidden number, so we read the
// form page, find each box by its label, and build the link. You still check the form and tap Submit.
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

/// One question on the form, found by words in its label.
class FormQuestion {
  final String id, label, hint;
  final List<String> keys;

  const FormQuestion(this.id, this.label, this.hint, this.keys);
}

/// Your usual answers, by question id. Blank ones are skipped.
class FormDetails {
  final Map<String, String> answers = {};
  String name = '';

  String get(String id) => (answers[id] ?? '').trim();

  bool get isEmpty => answers.values.every((v) => v.trim().isEmpty);
}

class PrefillResult {
  final String url;
  final List<String> filled = [], missed = [];

  PrefillResult(this.url);
}

class FormPrefill {
  FormPrefill._();

  static bool isFormstack(String url) => url.toLowerCase().contains('formstack.com/forms/');

  /// The Observation & Coaching form's questions that usually get the same answer, in form order.
  /// Name of Observer 1 (your name) and Observation Date (today) are filled in automatically.
  /// For pick-one questions, typing part of the answer is enough (e.g. a gang number, or "Single").
  static const questions = [
    FormQuestion('observation', 'Single or Group Observation?', 'e.g. Single', ['single or group observation']),
    FormQuestion('project', 'Bruce Power Project', 'e.g. FCFR', ['bruce power project']),
    FormQuestion('site', 'Shoreline FCFR (site)', 'e.g. B44 - Warehouse', ['shoreline fcfr']),
    FormQuestion('unit', 'Unit Number', 'e.g. Unit 5', ['unit number']),
    FormQuestion('location', 'Location / Area of Work', 'e.g. the area you work in',
        ['location / area of work', 'area of work']),
    FormQuestion('workGroup', 'Work Group Observed', 'e.g. your trade', ['work group observed']),
    FormQuestion('position', 'Position of Observer 1', 'e.g. FM', ['position of observer 1']),
    FormQuestion('gang', 'Superintendent and Gang ID', 'Part of it is enough, like the gang number',
        ['superintendent and gang id']),
    FormQuestion('email', 'Email for a copy', 'Your work email', ['email address for a copy']),
    FormQuestion('interview', 'Interview the worker?', 'Yes or No', ['interview the worker']),
    FormQuestion('photos', 'Upload picture of good compliance items?', 'Yes or No',
        ['upload picture of good compliance', 'upload picture']),
    FormQuestion('actions', 'Actions assigned?', 'None, 1, 2 or 3', ['any actions assigned']),
    FormQuestion('definitions', 'See the definitions?', 'Yes or No', ['see the definitions']),
    FormQuestion('rating', 'Overall rating', 'e.g. Positive Feedback', ['overall rating']),
    FormQuestion('supervisor', 'Send a copy to the supervisor?', 'Yes or No',
        ['send a copy of this observation', 'copy of this observation']),
  ];

  static const _months = [
    'January', 'February', 'March', 'April', 'May', 'June', //
    'July', 'August', 'September', 'October', 'November', 'December'
  ];

  /// "October 09, 2026", the way the form wants it.
  static String formDate(DateTime d) => '${_months[d.month - 1]} ${d.day.toString().padLeft(2, '0')}, ${d.year}';

  /// Downloads the form page.
  static Future<String> fetch(String url, {http.Client? client}) async {
    final c = client ?? http.Client();
    try {
      final r = await c.get(Uri.parse(url), headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android) DailyWorkLog'}).timeout(
          const Duration(seconds: 15));
      if (r.statusCode >= 400) throw http.ClientException('HTTP ${r.statusCode}', Uri.parse(url));
      return utf8.decode(r.bodyBytes, allowMalformed: true);
    } finally {
      if (client == null) c.close();
    }
  }

  /// Builds the filled-in link from the form page. Anything that can't be matched is listed in missed.
  static PrefillResult build(String url, String html, FormDetails d, DateTime today) {
    final page = _unescape(html);
    final lower = page.toLowerCase();
    final params = <String>[];
    final r = PrefillResult(url);

    for (final q in questions) {
      _answer(r, params, page, lower, q.label, d.get(q.id), q.keys);
      if (q.id == 'workGroup') {
        // the form asks for the date and your name next
        _answer(r, params, page, lower, 'Observation Date', formDate(today), ['observation date']);
        _name(r, params, lower, d.name);
      }
    }

    if (params.isEmpty) return r;
    final base = url.contains('?') ? '$url&' : '$url?';
    final out = PrefillResult(base + params.join('&'));
    out.filled.addAll(r.filled);
    out.missed.addAll(r.missed);
    return out;
  }

  // ----- one kind of box each -----

  /// Pick-one questions must match one of the form's options; anything else is typed in as is.
  static void _answer(PrefillResult r, List<String> params, String page, String lower, String label, String value,
      List<String> keys) {
    if (value.trim().isEmpty) return;
    final id = fieldId(lower, keys);
    if (id == null) {
      r.missed.add(label);
      return;
    }
    final opts = options(page, id);
    final v = opts.isEmpty ? value.trim() : pick(opts, value.trim());
    if (v == null) {
      r.missed.add(label);
      return;
    }
    params.add('field$id=${_enc(v)}');
    r.filled.add(label);
  }

  /// Name boxes are usually split into first and last name.
  static void _name(PrefillResult r, List<String> params, String lower, String name) {
    if (name.trim().isEmpty || name == 'Me') return;
    final id = fieldId(lower, ['name of observer 1']);
    if (id == null) {
      r.missed.add('Name of Observer 1');
      return;
    }
    final n = name.trim();
    if (lower.contains('field$id-first')) {
      final sp = n.lastIndexOf(' ');
      params.add('field$id-first=${_enc(sp < 0 ? n : n.substring(0, sp))}');
      if (sp > 0) params.add('field$id-last=${_enc(n.substring(sp + 1))}');
    } else {
      params.add('field$id=${_enc(n)}');
    }
    r.filled.add('Name of Observer 1');
  }

  // ----- reading the page -----

  static final _field = RegExp(r'field(\d{5,})');

  /// Markers Formstack puts around each question: fsCell123, label123, for="field123", field123.
  static final _near = RegExp(r'(?:fscell|fslabel|label|for="field|field)(\d{5,})');

  /// The box number nearest after its label (labels come just before their box).
  static String? fieldId(String lower, List<String> keys) {
    for (final key in keys) {
      var from = 0;
      while (true) {
        final at = lower.indexOf(key, from);
        if (at < 0) break;
        // Most pages wrap a question as <div id="fsCell123"><label for="field123">Label...
        // The closest marker before the label belongs to it (the one before that is the
        // previous question's).
        final backStart = math.max(0, at - 400);
        String? id;
        for (final m in _near.allMatches(lower.substring(backStart, at))) {
          id = m.group(1);
        }
        if (id == null) {
          final m = _field.allMatches(lower, at).firstOrNull;
          if (m != null && m.start - at < 3000) id = m.group(1);
        }
        if (id != null) return id;
        from = at + key.length;
      }
    }
    return null;
  }

  static final _option = RegExp(r'<option[^>]*value="([^"]*)"[^>]*>(.*?)</option>', caseSensitive: false, dotAll: true);
  static final _input = RegExp(r'<input[^>]*>', caseSensitive: false);
  static final _attrValue = RegExp(r'value="([^"]*)"', caseSensitive: false);

  /// Finds the form's own option that matches what you typed (exact first, then "contains").
  static String? matchOption(String page, String id, String wanted) => pick(options(page, id), wanted);

  /// The choices of a drop-down list or radio buttons; empty for a typed-in box.
  static List<String> options(String page, String id) {
    final out = <String>[];
    // Drop-down list
    final sel = RegExp('<select[^>]*name="field$id"[^>]*>(.*?)</select>', caseSensitive: false, dotAll: true)
        .firstMatch(page);
    if (sel != null) {
      for (final o in _option.allMatches(sel.group(1)!)) {
        if (o.group(1)!.trim().isNotEmpty) out.add(o.group(1)!.trim());
      }
    }
    // Radio buttons
    for (final m in _input.allMatches(page)) {
      final tag = m.group(0)!;
      if (!tag.contains('name="field$id"')) continue;
      final v = _attrValue.firstMatch(tag);
      if (v != null && v.group(1)!.trim().isNotEmpty) out.add(v.group(1)!.trim());
    }
    return out;
  }

  static String? pick(List<String> options, String wanted) {
    final w = _squash(wanted);
    for (final o in options) {
      if (_squash(o) == w) return o;
    }
    for (final o in options) {
      if (_squash(o).contains(w)) return o;
    }
    // Every word you typed appears in the option (e.g. "millwright boilermaker").
    final parts = w.split(RegExp(r'[ /&,-]+'));
    for (final o in options) {
      var all = true;
      for (final part in parts) {
        if (part.isNotEmpty && !_squash(o).contains(part)) all = false;
      }
      if (all) return o;
    }
    // Otherwise the first word that matches an option, e.g. "Millwright" from "millwright / boilermaker".
    for (final part in parts) {
      if (part.length < 3) continue;
      for (final o in options) {
        if (_squash(o).contains(part)) return o;
      }
    }
    return null;
  }

  static String _squash(String s) => s.toLowerCase().replaceAll(RegExp(r'\s+'), ' ').trim();

  static String _unescape(String s) => s
      .replaceAll('&amp;', '&')
      .replaceAll('&#39;', "'")
      .replaceAll('&#039;', "'")
      .replaceAll('&nbsp;', ' ')
      .replaceAll('&#x2F;', '/')
      .replaceAll(r'\/', '/');

  /// Like Java's URLEncoder, with spaces as %20.
  static String _enc(String v) {
    final sb = StringBuffer();
    for (final b in utf8.encode(v)) {
      final keep = (b >= 0x30 && b <= 0x39) ||
          (b >= 0x41 && b <= 0x5A) ||
          (b >= 0x61 && b <= 0x7A) ||
          b == 0x2E ||
          b == 0x2D ||
          b == 0x2A ||
          b == 0x5F;
      if (keep) {
        sb.writeCharCode(b);
      } else {
        sb.write('%${b.toRadixString(16).toUpperCase().padLeft(2, '0')}');
      }
    }
    return sb.toString();
  }

  // ----- screens -----

  /// Your saved answers plus your name from the Projects screen.
  static FormDetails details() {
    final d = FormDetails();
    d.answers.addAll(Store.instance.formAnswers);
    d.name = Store.instance.userName;
    return d;
  }

  /// Opens a saved link in the browser (prefilling Formstack forms). [onOpened] runs once the
  /// browser has opened it (e.g. to note the link on today's checklist).
  static Future<void> openLink(BuildContext context, Link link, {VoidCallback? onOpened}) async {
    // Observation form: open it with your usual answers filled in.
    if (isFormstack(link.url)) {
      if (!details().isEmpty) return _openFilled(context, link, onOpened);
      final choice = await showDialog<String>(
        context: context,
        builder: (c) => AlertDialog(
          title: const Text('Fill in your usual answers?'),
          content: const Text(
              'Save your project, location, position and so on once, and this form will open with them filled in.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, 'open'), child: const Text('Just open')),
            TextButton(onPressed: () => Navigator.pop(c, 'setup'), child: const Text('Set up')),
          ],
        ),
      );
      if (!context.mounted) return;
      if (choice == 'open') {
        await openPlain(context, link, onOpened: onOpened);
      } else if (choice == 'setup') {
        if (await editDetails(context) && context.mounted) await openLink(context, link, onOpened: onOpened);
      }
      return;
    }
    await openPlain(context, link, onOpened: onOpened);
  }

  /// Opens [url] (default: the link's own address) in the browser, without filling anything in.
  static Future<void> openPlain(BuildContext context, Link link, {String? url, VoidCallback? onOpened}) async {
    var ok = false;
    try {
      ok = await launchUrl(Uri.parse(url ?? link.url), mode: LaunchMode.externalApplication);
    } catch (_) {
      ok = false;
    }
    if (ok) {
      onOpened?.call();
    } else if (context.mounted) {
      toast(context, 'No app on this phone can open that link', long: true);
    }
  }

  static Future<void> _openFilled(BuildContext context, Link link, VoidCallback? onOpened) async {
    final d = details();
    final nav = Navigator.of(context, rootNavigator: true);
    unawaited(showDialog<void>(
      context: context,
      barrierDismissible: false,
      useRootNavigator: true,
      builder: (c) => const PopScope(
        canPop: false,
        child: AlertDialog(content: Text('Filling in your details…')),
      ),
    ));
    PrefillResult? result;
    try {
      result = build(link.url, await fetch(link.url), d, DateTime.now());
    } catch (_) {
      result = null;
    }
    nav.pop();
    if (!context.mounted) return;
    if (result != null && result.filled.isNotEmpty && result.missed.isEmpty) {
      await openPlain(context, link, url: result.url, onOpened: onOpened);
      if (context.mounted) {
        toast(context, 'Filled in ${Fmt.plural(result.filled.length, 'answer', 'answers')}. Check them, then Submit.',
            long: true);
      }
      return;
    }
    final msg = result == null
        ? "Couldn't read the form (no signal?). Open it and paste your details in:"
        : result.filled.isEmpty
            ? "Couldn't find the boxes on this form. Open it and paste your details in:"
            : 'Filled in ${Fmt.plural(result.filled.length, 'answer', 'answers')}. Couldn\'t fill '
                '${Fmt.join(result.missed)}. Copy and paste these:';
    await copyDetails(context, link, url: result?.url ?? link.url, message: msg, onOpened: onOpened);
  }

  /// Your details, each with a Copy button, plus a button to open the form.
  static Future<void> copyDetails(BuildContext context, Link link,
      {String? url, String message = 'Tap Copy, then paste into the form.', VoidCallback? onOpened}) async {
    final d = details();
    final rows = <MapEntry<String, String>>[];
    for (final q in questions) {
      rows.add(MapEntry(q.label, d.get(q.id)));
      if (q.id == 'workGroup') {
        rows.add(MapEntry('Observation Date', formDate(DateTime.now())));
        rows.add(MapEntry('Name of Observer 1', d.name));
      }
    }
    final open = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Your form details'),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(message, style: const TextStyle(fontSize: 15, color: C.grey)),
            ),
            for (final row in rows)
              if (row.value.trim().isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(children: [
                    Expanded(
                      child: Text('${row.key}\n${row.value}', style: const TextStyle(fontSize: 15, color: C.text)),
                    ),
                    Material(
                      color: C.sky,
                      borderRadius: BorderRadius.circular(100),
                      child: InkWell(
                        borderRadius: BorderRadius.circular(100),
                        onTap: () {
                          Clipboard.setData(ClipboardData(text: row.value));
                          toast(context, 'Copied ${row.key}');
                        },
                        child: const Padding(
                          padding: EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                          child: Text('Copy', style: TextStyle(color: C.deepBlue)),
                        ),
                      ),
                    ),
                  ]),
                ),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Close')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Open form')),
        ],
      ),
    );
    if (open == true && context.mounted) await openPlain(context, link, url: url, onOpened: onOpened);
  }

  /// The "My form details" screen where you set your usual answers (saved on this phone only).
  /// Returns true if saved.
  static Future<bool> editDetails(BuildContext context) async {
    final store = Store.instance;
    final boxes = [for (final q in questions) TextEditingController(text: (store.formAnswers[q.id] ?? '').trim())];
    final saved = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('My form details'),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const Text(
                "Leave any blank that changes each time. Your name and today's date are filled in too. Saved on this phone only.",
                style: TextStyle(fontSize: 13, color: C.grey)),
            for (var i = 0; i < questions.length; i++) ...[
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Text(questions[i].label, style: const TextStyle(fontSize: 14, color: C.grey)),
              ),
              TextField(
                controller: boxes[i],
                decoration: InputDecoration(hintText: questions[i].hint),
                keyboardType: questions[i].id == 'email' ? TextInputType.emailAddress : TextInputType.text,
                textCapitalization: questions[i].id == 'email' ? TextCapitalization.none : TextCapitalization.words,
                autocorrect: questions[i].id != 'email',
              ),
            ],
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Save')),
        ],
      ),
    );
    if (saved != true) return false;
    for (var i = 0; i < questions.length; i++) {
      store.formAnswers[questions[i].id] = boxes[i].text.trim();
    }
    store.save();
    if (context.mounted) toast(context, 'Saved');
    return true;
  }
}
