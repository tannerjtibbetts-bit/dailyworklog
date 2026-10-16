// Adding fields from a photo or spreadsheet: gets the photo or file, reads it, then the review screen
// where you pick the task column, untick rows that aren't tasks, fix any misread words, and add them.
import 'dart:io';
import 'dart:isolate';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../services/sms.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'assignment_text.dart';
import 'import_review.dart';
import 'scan_reader.dart';
import 'sheet_import.dart';

/// How the import starts: a photo of a list, the North/South zone sheet photo, a file,
/// or a screenshot of an assignment text.
enum ImportSource { camera, gallery, zoneCamera, zoneGallery, file, textScreenshot }

/// Pops with the number of fields added (0 if nothing was added).
class ImportScreen extends StatefulWidget {
  final Project project;
  final DailyLog log;
  final ImportSource source;

  /// For tests: skip picking and reading, and review these instead.
  final ImportReview? review;

  /// For tests: skip picking and reading, and review these assignment texts instead.
  final List<AssignmentText>? texts;

  const ImportScreen({
    super.key,
    required this.project,
    required this.log,
    required this.source,
    this.review,
    this.texts,
  });

  @override
  State<ImportScreen> createState() => _ImportScreenState();
}

class _ImportScreenState extends State<ImportScreen> {
  ImportReview? review;
  AssignmentReview? texts;
  final titles = <TextEditingController>[];
  String busy = '';

  @override
  void initState() {
    super.initState();
    review = widget.review;
    if (widget.texts != null) _showTexts(widget.texts!);
    if (review == null && texts == null) WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  @override
  void dispose() {
    for (final c in titles) {
      c.dispose();
    }
    super.dispose();
  }

  void _showTexts(List<AssignmentText> found) {
    final r = AssignmentReview(found);
    texts = r;
    for (var i = 0; i < found.length; i++) {
      titles.add(TextEditingController(text: r.titles[i]));
    }
  }

  void _quit([String? message]) {
    if (!mounted) return;
    if (message != null) toast(context, message, long: true);
    Navigator.of(context).pop(0);
  }

  Future<void> _start() async {
    switch (widget.source) {
      case ImportSource.camera:
      case ImportSource.zoneCamera:
        await _photo(ImageSource.camera);
      case ImportSource.gallery:
      case ImportSource.zoneGallery:
      case ImportSource.textScreenshot:
        await _photo(ImageSource.gallery);
      case ImportSource.file:
        await _file();
    }
  }

  /// Reads the text in a photo, then shows the review list. A camera photo is deleted afterwards.
  Future<void> _photo(ImageSource from) async {
    final XFile? picked;
    try {
      picked = await ImagePicker().pickImage(source: from, requestFullMetadata: false);
    } catch (_) {
      _quit(from == ImageSource.camera ? 'No camera app found' : 'No gallery app found');
      return;
    }
    if (picked == null) return _quit();
    setState(
      () => busy = widget.source == ImportSource.textScreenshot ? 'Reading the screenshot…' : 'Reading the photo…',
    );
    final r = await ScanReader.read(picked.path);
    if (from == ImageSource.camera) {
      try {
        File(picked.path).deleteSync();
      } catch (_) {
        // already gone
      }
    }
    if (!mounted) return;
    if (r.error != null) return _quit(r.error);
    if (widget.source == ImportSource.textScreenshot) {
      final found = parseAssignmentTexts(r.lines);
      if (found.isNotEmpty) {
        setState(() {
          busy = '';
          _showTexts(found);
        });
        return;
      }
      if (!await _noTextFound() || !mounted) return _quit();
    }
    if (r.zone != null) {
      setState(() {
        busy = '';
        review = ImportReview.zone(r.zone!, Store.instance.myColours);
      });
      return;
    }
    final rows = r.rows ?? [];
    if (rows.isEmpty) return _quit('No text found. Try a closer, straighter photo in good light.');
    final zoneWanted = widget.source == ImportSource.zoneCamera || widget.source == ImportSource.zoneGallery;
    if (zoneWanted) toast(context, "That doesn't look like the zone sheet, so here's the text as rows.", long: true);
    setState(() {
      busy = '';
      review = ImportReview.rows(rows, 'the photo');
    });
  }

  Future<void> _file() async {
    final PlatformFile? f;
    try {
      f = await FilePicker.pickFile(type: FileType.any);
    } catch (_) {
      _quit('No file app found');
      return;
    }
    if (f == null) return _quit();
    setState(() => busy = 'Opening the file…');
    List<List<String>>? rows;
    String? error;
    try {
      final data = await f.readAsBytes();
      rows = await Isolate.run(() => SheetImport.fromFile(data));
    } on SheetException catch (e) {
      error = e.code == 'old-xls'
          ? "That's an older Excel file (.xls). Save it as .xlsx or .csv and try again."
          : "Couldn't read that file. It needs to be an Excel (.xlsx) or CSV file.";
    } catch (_) {
      error = "Couldn't read that file. It needs to be an Excel (.xlsx) or CSV file.";
    }
    if (!mounted) return;
    if (error != null) return _quit(error);
    if (rows == null || rows.isEmpty) return _quit('That file looks empty');
    setState(() {
      busy = '';
      review = ImportReview.rows(rows!, 'the spreadsheet', header: true);
    });
  }

  /// Nothing like an assignment text in the picture. True to read it as a list instead.
  Future<bool> _noTextFound() async {
    final asList = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Add from text'),
        content: const Text(
          "Couldn't find an assignment text in that picture. Make sure the whole text, "
          'starting at "Assigned to you", is in the screenshot.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Read it as a list')),
        ],
      ),
    );
    return asList == true;
  }

  // ----- actions -----

  Future<void> _fix(int i) async {
    final r = review!;
    final s = await prompt(context, 'Fix this field', 'Field', r.title(i), 'Save');
    if (s == null || !mounted) return;
    setState(() => r.fix(i, s));
  }

  /// Joins the ticked rows into one field (you can edit the wording first).
  Future<void> _combineTicked() async {
    final r = review!;
    final pick = r.chosen();
    if (pick.length < 2) {
      toast(context, 'Tick two or more rows to combine them');
      return;
    }
    final ctl = TextEditingController(text: r.joinedTitles(pick));
    String? error;
    final text = await showDialog<String>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: Text('Combine ${pick.length} rows into one field'),
          content: TextField(
            controller: ctl,
            autofocus: true,
            minLines: 2,
            maxLines: 6,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(errorText: error),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
            TextButton(
              onPressed: () {
                final t = ctl.text.trim().replaceAll(RegExp(r'\s*\n\s*'), ' ');
                if (t.isEmpty) {
                  set(() => error = 'Required');
                  return;
                }
                Navigator.pop(c, t);
              },
              child: const Text('Combine'),
            ),
          ],
        ),
      ),
    );
    if (text == null || !mounted) return;
    setState(() => r.combine(pick, text));
    toast(context, 'Combined ${pick.length} rows into one field');
  }

  void _add() {
    final r = review!;
    final tasks = r.toTasks(Store.instance.me());
    if (tasks.isEmpty) return;
    widget.log.tasks.addAll(tasks);
    Store.instance.save();
    toast(context, 'Added ${Fmt.plural(tasks.length, 'field', 'fields')}');
    Navigator.of(context).pop(tasks.length);
  }

  void _addTexts() {
    final r = texts!;
    final tasks = r.toTasks(Store.instance.me());
    if (tasks.isEmpty) return;
    widget.log.tasks.addAll(tasks);
    Store.instance.save();
    toast(context, 'Added ${Fmt.plural(tasks.length, 'field', 'fields')}');
    Navigator.of(context).pop(tasks.length);
  }

  // ----- layout -----

  @override
  Widget build(BuildContext context) {
    final r = review, x = texts;
    return Scaffold(
      backgroundColor: C.bg,
      body: SafeArea(
        child: Column(
          children: [
            _topBar(),
            Expanded(
              child: x != null
                  ? Stack(
                      children: [
                        _textsBody(x),
                        Positioned(
                          left: 20,
                          right: 20,
                          bottom: 16,
                          child: _bigButton(x.chosen().length, _addTexts, zero: 'Add 0 fields'),
                        ),
                      ],
                    )
                  : r == null
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (busy.isNotEmpty) const CircularProgressIndicator(),
                          const SizedBox(height: 16),
                          Text(busy, style: const TextStyle(fontSize: 17, color: C.text)),
                        ],
                      ),
                    )
                  : Stack(
                      children: [
                        _body(r),
                        Positioned(left: 20, right: 20, bottom: 16, child: _addButton(r)),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _topBar() => SizedBox(
    height: 72,
    child: Stack(
      alignment: Alignment.center,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 72),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                widget.project.name.toUpperCase(),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 13, color: C.grey),
              ),
              Text(texts != null ? 'Add from text' : 'Add fields', style: const TextStyle(fontSize: 21, color: C.navy)),
            ],
          ),
        ),
        Positioned(
          left: 10,
          child: IconButton(
            iconSize: 28,
            tooltip: 'Back',
            color: C.navy,
            icon: const Icon(Icons.arrow_back),
            onPressed: () => Navigator.of(context).pop(0),
          ),
        ),
      ],
    ),
  );

  Widget _body(ImportReview r) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 120),
      children: [
        Text(r.intro(), style: const TextStyle(fontSize: 16, color: C.text)),
        if (r.width > 1 && !r.zone) ...[
          const Padding(
            padding: EdgeInsets.only(top: 16, bottom: 6),
            child: Text(
              'Which column has the tasks?',
              style: TextStyle(fontSize: 16, color: C.navy, fontWeight: FontWeight.bold),
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: cardDecoration(),
            child: DropdownButton<int>(
              value: r.column,
              isExpanded: true,
              underline: const SizedBox(),
              items: [
                for (var c = 0; c < r.width; c++)
                  DropdownMenuItem(
                    value: c,
                    child: Text(r.columnOption(c), maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
              ],
              onChanged: (c) {
                if (c != null) setState(() => r.setColumn(c));
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Row(
              children: [
                Checkbox(value: r.notesFromOthers, onChanged: (on) => setState(() => r.notesFromOthers = on ?? false)),
                Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => r.notesFromOthers = !r.notesFromOthers),
                    child: const Text("Put the other columns in each field's notes", style: TextStyle(fontSize: 16)),
                  ),
                ),
              ],
            ),
          ),
        ],
        Padding(
          padding: const EdgeInsets.only(top: 12, bottom: 6),
          child: Wrap(
            children: [
              _link('Tick all', () => setState(r.tickAll)),
              _link('Untick all', () => setState(r.untickAll)),
              _link('Combine ticked', _combineTicked),
            ],
          ),
        ),
        Container(
          decoration: cardDecoration(),
          padding: const EdgeInsets.fromLTRB(8, 6, 12, 6),
          child: Column(
            children: [
              for (var i = 0; i < r.rows.length; i++)
                if (r.title(i).isNotEmpty) _row(r, i),
            ],
          ),
        ),
      ],
    );
  }

  Widget _row(ImportReview r, int i) {
    final sub = r.subtitle(i);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Checkbox(value: r.ticked(i), onChanged: (on) => setState(() => r.tick(i, on ?? false))),
          Expanded(
            child: InkWell(
              onTap: () => _fix(i),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.title(i), style: const TextStyle(fontSize: 17, color: C.text)),
                    if (sub.isNotEmpty) Text(sub, style: const TextStyle(fontSize: 14, color: C.grey)),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _link(String label, VoidCallback onTap) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 20, 8),
      child: Text(label, style: const TextStyle(fontSize: 16, color: C.blue)),
    ),
  );

  Widget _addButton(ImportReview r) => _bigButton(r.chosen().length, _add);

  Widget _bigButton(int n, VoidCallback onTap, {String zero = 'Tick the fields to add'}) {
    return Opacity(
      opacity: n == 0 ? 0.5 : 1,
      child: Material(
        color: C.blue,
        elevation: 6,
        borderRadius: BorderRadius.circular(100),
        child: InkWell(
          borderRadius: BorderRadius.circular(100),
          onTap: n == 0 ? null : onTap,
          child: SizedBox(
            height: 64,
            child: Center(
              child: Text(
                n == 0 ? zero : 'Add ${Fmt.plural(n, 'field', 'fields')}',
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // ----- assignment texts -----

  Widget _textsBody(AssignmentReview r) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 0, 16, 120),
    children: [
      for (var i = 0; i < r.texts.length; i++)
        Container(
          margin: const EdgeInsets.only(bottom: 12),
          decoration: cardDecoration(),
          padding: const EdgeInsets.fromLTRB(8, 8, 12, 8),
          child: _textItem(r, i),
        ),
    ],
  );

  Widget _textItem(AssignmentReview r, int i) {
    final t = r.texts[i];
    final phone = t.phone.isEmpty ? '' : Sms.formatPhone(t.phone);
    final fromLine = t.from.isEmpty ? '' : 'From ${t.from}${phone.isEmpty ? '' : ' · $phone'}';
    final who = t.from.isNotEmpty ? t.from : 'them';
    Widget tick(bool value, VoidCallback onTap, String label) => InkWell(
      onTap: onTap,
      child: Row(
        children: [
          IgnorePointer(
            child: Checkbox(value: value, onChanged: (_) {}),
          ),
          Flexible(
            child: Text(label, style: const TextStyle(fontSize: 16, color: C.text)),
          ),
        ],
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        tick(
          r.ticked[i],
          () => setState(() => r.ticked[i] = !r.ticked[i]),
          t.photosRequired ? 'Add this field  ·  Photos required' : 'Add this field',
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: TextField(
            controller: titles[i],
            minLines: 1,
            maxLines: 4,
            textCapitalization: TextCapitalization.sentences,
            style: const TextStyle(fontSize: 17, color: C.text),
            decoration: const InputDecoration(hintText: 'Field title'),
            onChanged: (s) => setState(() => r.titles[i] = s),
          ),
        ),
        if (fromLine.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: Text(fromLine, style: const TextStyle(fontSize: 14, color: C.grey)),
          ),
        if (t.phone.isNotEmpty)
          tick(
            r.textWhenDone[i],
            () => setState(() => r.textWhenDone[i] = !r.textWhenDone[i]),
            "Text $who when it's done",
          ),
      ],
    );
  }
}
