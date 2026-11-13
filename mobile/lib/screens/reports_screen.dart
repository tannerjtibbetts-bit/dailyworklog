// Apprentice reports and Disciplinary action (from the Links tab): add a blank form (photos or a file),
// check the questions the app found, fill it in for a crew member (answers worked out from Manage Crew and
// your profile, the rest asked one by one, typed or spoken), and export it as a PDF to sign.
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/forms.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../export/export.dart';
import '../export/report_form_pdf.dart';
import '../services/form_files.dart';
import '../services/photos.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'form_layout.dart';
import 'note_screen.dart' show SmallTopBar, bottomPill;

/// A text box with a mic button that adds spoken words to the end.
class AnswerBox extends StatefulWidget {
  final TextEditingController controller;
  final bool long;
  final ValueChanged<String> onChanged;
  final String? hint;
  final bool autofocus;

  const AnswerBox(
      {super.key, required this.controller, required this.long, required this.onChanged, this.hint, this.autofocus = false});

  @override
  State<AnswerBox> createState() => _AnswerBoxState();
}

class _AnswerBoxState extends State<AnswerBox> {
  bool _listening = false;
  String _before = '';

  @override
  void dispose() {
    if (_listening) Mic.stop();
    super.dispose();
  }

  Future<void> _mic() async {
    if (_listening) {
      await Mic.stop();
      if (mounted) setState(() => _listening = false);
      return;
    }
    _before = widget.controller.text.trimRight();
    final ok = await Mic.listen(context, (words, done) {
      if (!mounted) return;
      final t = words.trim().isEmpty ? _before : (_before.isEmpty ? words.trim() : '$_before ${words.trim()}');
      widget.controller.text = t;
      widget.controller.selection = TextSelection.collapsed(offset: t.length);
      widget.onChanged(t);
      if (done) setState(() => _listening = false);
    }, onStop: () {
      if (mounted) setState(() => _listening = false);
    });
    if (ok && mounted) setState(() => _listening = true);
  }

  @override
  Widget build(BuildContext context) => Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: TextField(
            controller: widget.controller,
            autofocus: widget.autofocus,
            minLines: widget.long ? 3 : 1,
            maxLines: widget.long ? 8 : 2,
            textCapitalization: TextCapitalization.sentences,
            style: const TextStyle(fontSize: 17, color: C.text),
            decoration: InputDecoration(
              hintText: widget.hint ?? 'Type or tap the mic to speak',
              hintStyle: const TextStyle(color: C.greyLight, fontSize: 15),
              isDense: true,
              filled: true,
              fillColor: const Color(0xFFF2F7FD),
              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.cardStroke)),
              focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.blue)),
            ),
            onChanged: widget.onChanged,
          ),
        ),
        const SizedBox(width: 6),
        Material(
          color: _listening ? C.red : C.sky,
          shape: const CircleBorder(),
          child: IconButton(
            tooltip: _listening ? 'Stop listening' : 'Speak your answer',
            icon: Icon(_listening ? Icons.stop : Icons.mic_none, color: _listening ? Colors.white : C.deepBlue),
            onPressed: _mic,
          ),
        ),
      ]);
}

/// Full-screen pictures of a form's or document's pages, zoomable.
Future<void> showPages(BuildContext context, String title, List<String> files, String kind) async {
  List<String> pics;
  try {
    pics = await FormFiles.pictures(files, kind);
  } catch (_) {
    pics = const [];
  }
  if (!context.mounted) return;
  if (pics.isEmpty) {
    toast(context, "This file can't be shown here");
    return;
  }
  await Navigator.of(context).push(MaterialPageRoute<void>(
    builder: (c) => Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      ),
      body: InteractiveViewer(
        maxScale: 5,
        child: ListView(children: [
          for (final p in pics)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Image.file(Store.instance.photoFile(p), fit: BoxFit.fitWidth),
            ),
        ]),
      ),
    ),
  ));
}

class ReportsScreen extends StatefulWidget {
  final String projectId;
  final String group;

  const ReportsScreen({super.key, required this.projectId, required this.group});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  Store get store => Store.instance;
  Project? get project => store.project(widget.projectId);

  List<ReportForm> get forms => [
        for (final f in store.reportForms)
          if (f.group == widget.group) f,
      ];

  List<FilledReport> get filled => [
        for (final r in store.filledReports)
          if (r.group == widget.group && r.project == (project?.name ?? '')) r,
      ]..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));

  void _refresh() {
    if (mounted) setState(() {});
  }

  // ----- adding a form -----

  Future<void> _addForm({ReportForm? replacing}) async {
    var choice = 0;
    await menu(context, null, [
      SheetItem(Icons.photo_camera_outlined, 'Take photos of a form', () => choice = 1),
      SheetItem(Icons.upload_file, 'Upload a file', () => choice = 2),
    ]);
    if (!mounted || choice == 0) return;
    final form = ReportForm()..group = widget.group;
    String suggested = '';
    if (choice == 1) {
      while (true) {
        if (!mounted) return;
        final n = await Photos.take(context);
        if (n == null || !mounted) break;
        // Crop off anything around the page (the desk, or a screen's buttons).
        await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => CropScreen(file: n)));
        if (!mounted) return;
        form.files.add(n);
        final more = await showDialog<bool>(
          context: context,
          builder: (c) => AlertDialog(
            title: Text('Page ${form.files.length} added'),
            content: const Text('Add another page?'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Done')),
              TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Add page')),
            ],
          ),
        );
        if (more != true) break;
      }
      form.kind = Doc.photo;
    } else {
      final picked = await FormFiles.pickFile(context, formsOnly: true);
      if (picked == null) return;
      form.files.add(picked.$1);
      form.kind = Doc.kindOf(picked.$1);
      suggested = picked.$2;
    }
    if (!mounted || form.files.isEmpty) return;
    final name = replacing != null
        ? replacing.name
        : await prompt(context, 'Name this form', 'e.g. Apprentice review', suggested, 'Save',
            caps: TextCapitalization.sentences);
    if (name == null || !mounted) {
      FormFiles.deleteFiles(form.files);
      return;
    }
    form.name = name;
    ReadForm? read;
    final nav = Navigator.of(context, rootNavigator: true);
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      useRootNavigator: true,
      builder: (c) => const PopScope(
        canPop: false,
        child: AlertDialog(
          content: Row(children: [
            SizedBox(width: 36, height: 36, child: CircularProgressIndicator()),
            SizedBox(width: 24),
            Expanded(child: Text('Reading the form…', style: TextStyle(fontSize: 17))),
          ]),
        ),
      ),
    );
    try {
      read = await FormFiles.readForm(form);
    } catch (_) {
      read = null;
    }
    nav.pop();
    if (!mounted) return;
    if (read == null) toast(context, "Couldn't read the text on that form. Add the questions yourself.", long: true);
    form.questions = read?.questions ?? [];
    form.signatures = (read?.signatures.isNotEmpty ?? false) ? read!.signatures : ReportForm.defaultSignatures(form.group);
    form.textSize = read?.textSize ?? 0.014;
    if (replacing != null) {
      final i = store.reportForms.indexOf(replacing);
      deleteFormFiles(replacing);
      store.reportForms.remove(replacing);
      store.reportForms.insert(i < 0 ? store.reportForms.length : i, form);
    } else {
      store.reportForms.add(form);
    }
    store.save();
    if (replacing != null) toast(context, 'Form replaced. Reports already filled in keep the old form.', long: true);
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => FormCheckScreen(formId: form.id)));
    _refresh();
  }

  // ----- filling in -----

  Future<void> _fill(ReportForm form) async {
    final p = project;
    if (p == null) return;
    final people = [
      for (final n in p.crew)
        if (widget.group == ReportForm.discipline || p.levels[n] == 'Apprentice') n,
    ]..sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
    if (people.isEmpty) {
      await showDialog<void>(
        context: context,
        builder: (c) => AlertDialog(
          content: Text(widget.group == ReportForm.discipline
              ? 'Add your crew first: tap ⋯ at the top, then Manage crew.'
              : "Nobody on this project's crew is marked Apprentice. In Manage crew, tap a person and pick Apprentice."),
          actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('OK'))],
        ),
      );
      return;
    }
    var who = people.first;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: const Text('Who is this report for?'),
          content: DropdownButtonFormField<String>(
            initialValue: who,
            isExpanded: true,
            decoration: InputDecoration(labelText: ReportForm.personWord(widget.group)),
            items: [for (final n in people) DropdownMenuItem(value: n, child: Text(n))],
            onChanged: (v) => set(() => who = v ?? who),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
            TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Start')),
          ],
        ),
      ),
    );
    if (ok != true || !mounted) return;
    final r = FilledReport()
      ..formId = form.id
      ..formName = form.name
      ..group = form.group
      ..person = who
      ..project = p.name
      ..form = form.copy();
    FormFill.autoFill(r, form, p,
        myName: store.me(),
        myPhone: store.contactPhone,
        myEmail: store.userEmail,
        myPosition: store.workPosition,
        myLocation: store.workLocation,
        today: DateTime.now(),
        earlier: store.filledReports);
    store.filledReports.add(r);
    store.save();
    await askMissing(context, r, form);
    if (!mounted) return;
    await Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => FillScreen(reportId: r.id, projectId: widget.projectId)));
    _refresh();
  }

  void _formMenu(ReportForm f) {
    menu(context, f.name, [
      SheetItem(Icons.edit_note, 'Fill in', () => _fill(f)),
      SheetItem(Icons.checklist, 'Edit questions', () async {
        await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => FormCheckScreen(formId: f.id)));
        _refresh();
      }),
      SheetItem(Icons.image_outlined, 'View the form', () => showPages(context, f.name, f.files, f.kind)),
      if (f.pagePictures.isNotEmpty)
        SheetItem(Icons.open_with, 'Place the answers', () async {
          await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => PlaceScreen(formId: f.id)));
          _refresh();
        }),
      SheetItem(Icons.find_replace, 'Replace the form', () => _addForm(replacing: f)),
      SheetItem(Icons.drive_file_rename_outline, 'Rename', () async {
        final n = await prompt(context, 'Rename form', 'Form name', f.name, 'Save');
        if (n == null) return;
        f.name = n;
        store.save();
        _refresh();
      }),
      SheetItem(Icons.delete_outline, 'Delete form', () {
        confirm(context, 'Delete this form?', 'Reports already filled in stay.', 'Delete', () {
          deleteFormFiles(f);
          store.reportForms.remove(f);
          store.save();
          _refresh();
        });
      }, danger: true),
    ]);
  }

  void _reportMenu(FilledReport r) {
    menu(context, '${r.person} · ${r.formName}', [
      SheetItem(Icons.open_in_new, 'Open', () => _open(r)),
      SheetItem(Icons.picture_as_pdf_outlined, 'Export PDF', () {
        final f = formFor(r);
        if (f == null) {
          toast(context, 'The form for this report was deleted');
          return;
        }
        exportReportPdf(context, f, r);
      }),
      SheetItem(Icons.delete_outline, 'Delete', () {
        confirm(context, 'Delete this report?', '${r.person} · ${r.formName}', 'Delete', () {
          store.filledReports.remove(r);
          store.save();
          _refresh();
        });
      }, danger: true),
    ]);
  }

  Future<void> _open(FilledReport r) async {
    if (formFor(r) == null) {
      toast(context, 'The form for this report was deleted');
      return;
    }
    await Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => FillScreen(reportId: r.id, projectId: widget.projectId)));
    _refresh();
  }

  Widget _card({required Widget child, VoidCallback? onTap, VoidCallback? onLongPress}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Material(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          child: InkWell(
            borderRadius: BorderRadius.circular(20),
            onTap: onTap,
            onLongPress: onLongPress,
            child: Container(
              decoration: cardDecoration(),
              padding: const EdgeInsets.fromLTRB(18, 14, 14, 14),
              child: child,
            ),
          ),
        ),
      );

  static Widget _heading(String s) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 18, 4, 10),
        child: Text(s.toUpperCase(),
            style: const TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.8, fontWeight: FontWeight.bold)),
      );

  @override
  Widget build(BuildContext context) {
    final p = project;
    final fs = forms, done = filled;
    return Scaffold(
      backgroundColor: C.bg,
      body: SafeArea(
        child: Column(children: [
          SmallTopBar(p?.name ?? '', ReportForm.groupTitle(widget.group)),
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(18, 0, 18, 40), children: [
              _heading('Forms'),
              if (fs.isEmpty)
                const Padding(
                  padding: EdgeInsets.fromLTRB(4, 0, 4, 8),
                  child: Text('No forms yet. Tap + Add a form to take photos of a blank form or upload one.',
                      style: TextStyle(color: C.grey, fontSize: 16)),
                ),
              for (final f in fs)
                _card(
                  onTap: () => _fill(f),
                  onLongPress: () => _formMenu(f),
                  child: Row(children: [
                    const Icon(Icons.description_outlined, color: C.deepBlue, size: 30),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(f.name, style: const TextStyle(color: C.navy, fontSize: 19, fontWeight: FontWeight.bold)),
                        Text(
                          '${f.questions.length} ${f.questions.length == 1 ? 'question' : 'questions'} · '
                          '${FormFill.date(DateTime.fromMillisecondsSinceEpoch(f.createdAt))}',
                          style: const TextStyle(color: C.grey, fontSize: 15),
                        ),
                      ]),
                    ),
                    IconButton(
                      tooltip: 'More',
                      icon: const Icon(Icons.more_vert, color: C.grey),
                      onPressed: () => _formMenu(f),
                    ),
                  ]),
                ),
              InkWell(
                onTap: _addForm,
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 14),
                  child: Text('+  Add a form', style: TextStyle(color: C.blue, fontSize: 19)),
                ),
              ),
              if (done.isNotEmpty) _heading('Filled in'),
              for (final r in done)
                _card(
                  onTap: () => _open(r),
                  onLongPress: () => _reportMenu(r),
                  child: Row(children: [
                    const Icon(Icons.assignment_turned_in_outlined, color: C.green, size: 28),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('${r.person} · ${r.formName}',
                            style: const TextStyle(color: C.navy, fontSize: 18, fontWeight: FontWeight.bold)),
                        Text(FormFill.date(DateTime.fromMillisecondsSinceEpoch(r.createdAt)),
                            style: const TextStyle(color: C.grey, fontSize: 15)),
                      ]),
                    ),
                  ]),
                ),
            ]),
          ),
        ]),
      ),
    );
  }
}

/// The form a report uses: the form itself while it exists, else the copy kept with the report.
ReportForm? formFor(FilledReport r) =>
    Store.instance.reportForms.where((x) => x.id == r.formId).firstOrNull ?? r.form;

/// Deletes a form's files unless a filled report's copy of the form still uses them.
void deleteFormFiles(ReportForm f) {
  final used = {
    for (final r in Store.instance.filledReports)
      if (r.form != null) ...r.form!.files,
  };
  FormFiles.deleteFiles(f.files.where((x) => !used.contains(x)));
}

/// Builds the PDF and offers Share or email / Save to phone.
Future<void> exportReportPdf(BuildContext context, ReportForm form, FilledReport r) {
  final now = DateTime.now();
  final pages = form.pagePictures;
  return Export.offer(context, 'Building PDF…', ReportFormPdf.fileName(r, now), Export.pdf, () async {
    // Photo and PDF forms: the answers are written on the form itself.
    if (pages.isNotEmpty) {
      final pictures = [for (final p in pages) await Store.instance.photoFile(p).readAsBytes()];
      return ReportFormPdf.writeOnForm(form, r, pictures);
    }
    return ReportFormPdf.write(form, r, now);
  });
}

/// Asks each unanswered question one at a time. Answers save as they're given.
Future<void> askMissing(BuildContext context, FilledReport r, ReportForm form) async {
  final store = Store.instance;
  final list = FormFill.missing(r, form);
  for (var i = 0; i < list.length; i++) {
    if (!context.mounted) return;
    final q = list[i];
    final ctl = TextEditingController(text: r.answer(q.id));
    var picked = FormFill.choiceOf(q, r.answer(q.id))?.label ?? r.answer(q.id);
    void keep(String v) {
      r.answers[q.id] = v;
      r.updatedAt = nowMs();
      store.save();
    }

    final go = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: Text('Question ${i + 1} of ${list.length}', style: const TextStyle(color: C.grey, fontSize: 15)),
          content: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text(q.label, style: const TextStyle(color: C.navy, fontSize: 19, fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              if (q.kind == ReportQuestion.rating)
                RadioGroup<String>(
                  groupValue: picked,
                  onChanged: (v) => set(() {
                    picked = v ?? '';
                    keep(picked);
                  }),
                  child: Column(children: [
                    for (final ch in q.choices)
                      RadioListTile<String>(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        value: ch.label,
                        title: Text(ch.display, style: const TextStyle(fontSize: 17)),
                      ),
                  ]),
                )
              else
                AnswerBox(controller: ctl, long: q.kind == ReportQuestion.long, autofocus: true, onChanged: keep),
            ]),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, 'stop'), child: const Text('Stop')),
            TextButton(onPressed: () => Navigator.pop(c, 'skip'), child: const Text('Skip')),
            TextButton(
                onPressed: () => Navigator.pop(c, 'next'),
                child: Text(i == list.length - 1 ? 'Done' : 'Next',
                    style: const TextStyle(fontWeight: FontWeight.bold))),
          ],
        ),
      ),
    );
    await Mic.stop();
    if (go == 'stop' || go == null) return;
  }
}

/// One filled-in report: every question with its answer, Ask the missing questions, Export PDF.
class FillScreen extends StatefulWidget {
  final String reportId, projectId;

  const FillScreen({super.key, required this.reportId, required this.projectId});

  @override
  State<FillScreen> createState() => _FillScreenState();
}

class _FillScreenState extends State<FillScreen> {
  Store get store => Store.instance;
  late final FilledReport? r = store.filledReports.where((x) => x.id == widget.reportId).firstOrNull;
  late final ReportForm? form = r == null ? null : formFor(r!);
  final Map<String, TextEditingController> _boxes = {};

  @override
  void dispose() {
    for (final c in _boxes.values) {
      c.dispose();
    }
    final rr = r, f = form;
    final p = store.project(widget.projectId);
    if (rr != null && f != null && p != null && FormFill.saveBack(rr, f, p)) store.save();
    super.dispose();
  }

  void _keep(String id, String v) {
    r!.answers[id] = v;
    r!.updatedAt = nowMs();
    store.save();
  }

  TextEditingController _box(ReportQuestion q) =>
      _boxes.putIfAbsent(q.id, () => TextEditingController(text: r!.answer(q.id)));

  Widget _question(ReportQuestion q) {
    final rr = r!, f = form!;
    final empty = q.kind != ReportQuestion.average && rr.answer(q.id).isEmpty;
    Widget answer;
    switch (q.kind) {
      case ReportQuestion.average:
        final v = FormFill.average(rr, f);
        answer = Text(v.isEmpty ? 'Worked out from the ratings' : v,
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: v.isEmpty ? C.greyLight : C.navy));
      case ReportQuestion.rating:
        final current = FormFill.choiceOf(q, rr.answer(q.id))?.label ?? '';
        final values = [for (final c in q.choices) c.label];
        answer = DropdownButtonFormField<String>(
          initialValue: values.contains(current) ? current : '',
          isExpanded: true,
          decoration: const InputDecoration(isDense: true),
          items: [
            const DropdownMenuItem(value: '', child: Text('Pick a rating', style: TextStyle(color: C.greyLight))),
            for (var i = 0; i < q.choices.length; i++)
              DropdownMenuItem(value: values[i], child: Text(q.choices[i].display)),
          ],
          onChanged: (v) => setState(() => _keep(q.id, v ?? '')),
        );
      default:
        answer = AnswerBox(
          controller: _box(q),
          long: q.kind == ReportQuestion.long,
          onChanged: (v) {
            final wasEmpty = rr.answer(q.id).isEmpty;
            _keep(q.id, v);
            if (wasEmpty != v.trim().isEmpty) setState(() {});
          },
        );
    }
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
      decoration: cardDecoration(color: empty ? const Color(0xFFFFF4E5) : Colors.white),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(q.label, style: const TextStyle(color: C.navy, fontSize: 17, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        answer,
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    final rr = r, f = form;
    if (rr == null || f == null) return const Scaffold();
    final missing = FormFill.missing(rr, f);
    return Scaffold(
      backgroundColor: C.bg,
      body: SafeArea(
        child: Column(children: [
          SmallTopBar(rr.person, f.name),
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  '${rr.person} · ${rr.project.isEmpty ? '' : '${rr.project} · '}'
                  '${FormFill.date(DateTime.fromMillisecondsSinceEpoch(rr.createdAt))}',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: C.grey, fontSize: 15),
                ),
              ),
              if (f.questions.isEmpty)
                const Text('This form has no questions. Long-press it on the list and pick Edit questions.',
                    style: TextStyle(color: C.grey, fontSize: 16)),
              for (final q in f.questions) _question(q),
              if (f.pagePictures.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.open_with),
                    label: const Text('Check where the answers go'),
                    style: OutlinedButton.styleFrom(minimumSize: const Size(0, 52)),
                    onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                        builder: (_) => PlaceScreen(formId: f.id, report: rr, form: f))),
                  ),
                ),
              if (missing.isNotEmpty)
                OutlinedButton.icon(
                  icon: const Icon(Icons.question_answer_outlined),
                  label: Text('Ask the missing questions (${missing.length})'),
                  style: OutlinedButton.styleFrom(minimumSize: const Size(0, 52)),
                  onPressed: () async {
                    await askMissing(context, rr, f);
                    for (final q in f.questions) {
                      _boxes[q.id]?.text = rr.answer(q.id);
                    }
                    if (mounted) setState(() {});
                  },
                ),
            ]),
          ),
          bottomPill('Export PDF', () => exportReportPdf(context, f, rr)),
        ]),
      ),
    );
  }
}

/// "Check the questions": what the app found on a form, to fix before using it.
class FormCheckScreen extends StatefulWidget {
  final String formId;

  const FormCheckScreen({super.key, required this.formId});

  @override
  State<FormCheckScreen> createState() => _FormCheckScreenState();
}

class _FormCheckScreenState extends State<FormCheckScreen> {
  Store get store => Store.instance;
  late final ReportForm? f = store.reportForms.where((x) => x.id == widget.formId).firstOrNull;

  void _save() {
    store.save();
    setState(() {});
  }

  Future<void> _edit(ReportQuestion? q) async {
    final form = f!;
    final label = TextEditingController(text: q?.label ?? '');
    var kind = q?.kind ?? ReportQuestion.text;
    final scale = form.scale();
    String? error;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: Text(q == null ? 'Add a question' : 'Edit question'),
          content: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              TextField(
                controller: label,
                autofocus: true,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(labelText: 'Question', errorText: error),
              ),
              const SizedBox(height: 10),
              RadioGroup<String>(
                groupValue: kind,
                onChanged: (v) => set(() => kind = v ?? kind),
                child: Column(children: [
                  for (final k in const [
                    [ReportQuestion.text, 'Short answer'],
                    [ReportQuestion.long, 'Long answer'],
                    [ReportQuestion.rating, 'Rating'],
                    [ReportQuestion.average, 'Average of ratings'],
                  ])
                    RadioListTile<String>(contentPadding: EdgeInsets.zero, dense: true, value: k[0], title: Text(k[1])),
                ]),
              ),
              if (kind == ReportQuestion.rating && scale.isEmpty)
                const Text('This form has no point system, so the choices are Excellent, Good, Fair and Poor.',
                    style: TextStyle(color: C.grey, fontSize: 14)),
            ]),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
            TextButton(
              onPressed: () {
                if (label.text.trim().isEmpty) {
                  set(() => error = 'Type the question');
                  return;
                }
                Navigator.pop(c, true);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
    if (ok != true) return;
    var k = kind;
    final useScale = scale.isEmpty ? FormFill.plainScale : scale;
    if (q == null) {
      form.questions.add(ReportQuestion(form.newQuestionId(), label.text.trim(), k,
          k == ReportQuestion.rating ? List.of(useScale) : null));
    } else {
      q
        ..label = label.text.trim()
        ..kind = k
        ..choices = k == ReportQuestion.rating ? (q.choices.isNotEmpty ? q.choices : List.of(useScale)) : [];
    }
    _save();
  }

  Future<void> _editSignature(int? i) async {
    final form = f!;
    final s = await prompt(context, i == null ? 'Add a signature line' : 'Edit signature line',
        'e.g. Supervisor signature', i == null ? null : form.signatures[i], 'Save');
    if (s == null) return;
    if (i == null) {
      form.signatures.add(s);
    } else {
      form.signatures[i] = s;
    }
    _save();
  }

  Widget _row(String title, String sub, VoidCallback onTap, VoidCallback onDelete) => Container(
        margin: const EdgeInsets.only(bottom: 8),
        decoration: cardDecoration(),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 10, 4, 10),
            child: Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(title, style: const TextStyle(color: C.navy, fontSize: 17, fontWeight: FontWeight.bold)),
                  if (sub.isNotEmpty) Text(sub, style: const TextStyle(color: C.grey, fontSize: 14)),
                ]),
              ),
              IconButton(tooltip: 'Delete', icon: const Icon(Icons.close, color: C.red), onPressed: onDelete),
            ]),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final form = f;
    if (form == null) return const Scaffold();
    return Scaffold(
      backgroundColor: C.bg,
      body: SafeArea(
        child: Column(children: [
          SmallTopBar(form.name, 'Check the questions'),
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [
              Text(
                form.questions.isEmpty
                    ? 'No questions were found on this form. Add them with + Add a question.'
                    : 'These are the questions the app found. Tap one to fix it, × to remove it.',
                style: const TextStyle(color: C.grey, fontSize: 15),
              ),
              Wrap(children: [
                TextButton.icon(
                  icon: const Icon(Icons.image_outlined),
                  label: const Text('View the form'),
                  onPressed: () => showPages(context, form.name, form.files, form.kind),
                ),
                if (form.pagePictures.isNotEmpty)
                  TextButton.icon(
                    icon: const Icon(Icons.open_with),
                    label: const Text('Place the answers on the form'),
                    onPressed: () async {
                      await Navigator.of(context)
                          .push(MaterialPageRoute<void>(builder: (_) => PlaceScreen(formId: form.id)));
                      if (mounted) setState(() {});
                    },
                  ),
              ]),
              for (final q in List.of(form.questions))
                _row(q.label, q.kindText(), () => _edit(q), () {
                  form.questions.remove(q);
                  _save();
                }),
              InkWell(
                onTap: () => _edit(null),
                child: const Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: Text('+  Add a question', style: TextStyle(color: C.blue, fontSize: 18)),
                ),
              ),
              const Padding(
                padding: EdgeInsets.fromLTRB(0, 14, 0, 8),
                child: Text('SIGNATURE LINES',
                    style: TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.8, fontWeight: FontWeight.bold)),
              ),
              for (var i = 0; i < form.signatures.length; i++)
                _row(form.signatures[i], '', () => _editSignature(i), () {
                  form.signatures.removeAt(i);
                  _save();
                }),
              InkWell(
                onTap: () => _editSignature(null),
                child: const Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: Text('+  Add a signature line', style: TextStyle(color: C.blue, fontSize: 18)),
                ),
              ),
            ]),
          ),
          bottomPill('Save', () => Navigator.of(context).pop()),
        ]),
      ),
    );
  }
}

/// Opens a Links-tab document: photos and PDFs on screen, other files in another app.
Future<void> openDocument(BuildContext context, Doc d) async {
  final f = Store.instance.photoFile(d.file);
  if (!f.existsSync()) {
    toast(context, "That file isn't on this phone any more");
    return;
  }
  if (d.kind == Doc.photo || d.kind == Doc.pdf) {
    await showPages(context, d.title, [d.file], d.kind);
    return;
  }
  await shareDocument(context, d);
}

Future<void> shareDocument(BuildContext context, Doc d) {
  final mime = switch (d.kind) {
    Doc.photo => 'image/*',
    Doc.pdf => 'application/pdf',
    Doc.docx => 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    Doc.xlsx => 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    _ => 'application/octet-stream',
  };
  return Export.shareFile(context, File(Store.instance.photoFile(d.file).path), mime);
}
