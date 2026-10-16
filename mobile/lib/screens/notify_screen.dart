// Notify: pick crew, write a note, add photos. Everyone picked gets it, whether or not they're
// ticked for assignment texts. A note without photos is texted straight from the app to each
// person (Android); with photos, or on iPhone, Messages opens with it all ready.
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../services/photos.dart';
import '../services/sms.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'assign_sheet.dart';
import 'note_screen.dart';

/// Records a sent note on the project and on today's checklist (not ticked for the report).
Notice recordNotice(Project p, List<String> to, String text, List<String> photoFiles, String me, int at) {
  final n = Notice()
    ..to = List.of(to)
    ..note = text
    ..sentAt = at;
  for (final f in photoFiles) {
    n.photos.add(Photo(file: f, addedBy: me, addedAt: at));
  }
  p.notices.insert(0, n);
  p.record(null, Fmt.todayIso(), Extra.notify, n.id, Fmt.join(n.to), n.note, n.photos, me, at);
  return n;
}

class NotifyScreen extends StatefulWidget {
  final String projectId;

  const NotifyScreen({super.key, required this.projectId});

  @override
  State<NotifyScreen> createState() => _NotifyScreenState();
}

class _NotifyScreenState extends State<NotifyScreen> {
  Project? project;
  final List<String> to = [];
  final List<String> photoFiles = [];
  bool sent = false, sending = false;
  String? noteError;
  final note = TextEditingController();

  Store get store => Store.instance;

  @override
  void initState() {
    super.initState();
    project = store.project(widget.projectId);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (project == null) {
        Navigator.of(context).pop();
      } else {
        _chooseTo();
      }
    });
  }

  @override
  void dispose() {
    // Drafts that were never sent shouldn't leave photos behind.
    if (!sent) {
      for (final f in photoFiles) {
        try {
          store.photoFile(f).deleteSync();
        } catch (_) {}
      }
    }
    note.dispose();
    super.dispose();
  }

  Future<void> _chooseTo() async {
    final sel = await showAssignSheet(context, project!, List.of(to), 'Send to');
    if (sel == null || !mounted) return;
    setState(() {
      to
        ..clear()
        ..addAll(sel);
    });
  }

  Future<void> _addPhotos() async {
    final files = await Photos.choose(context);
    if (files.isEmpty || !mounted) return;
    setState(() => photoFiles.addAll(files));
  }

  void _removePhoto(int i) {
    final name = photoFiles[i];
    try {
      store.photoFile(name).deleteSync();
    } catch (_) {}
    setState(() => photoFiles.removeAt(i));
  }

  Widget _recipient(String name) {
    final p = project!;
    final number = p.phoneOf(name);
    return InkWell(
      onTap: _chooseTo,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Row(children: [
          InitialsBadge(name, size: 44),
          const SizedBox(width: 14),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(name, style: const TextStyle(fontSize: 19, color: C.text)),
              GestureDetector(
                onTap: () async {
                  final num = await promptPhone(context, 'Phone number for $name', number);
                  if (num == null) return;
                  p.phones[name] = num;
                  store.save();
                  if (mounted) setState(() {});
                },
                child: Text(number.isEmpty ? '+ Add phone number' : number,
                    style: TextStyle(fontSize: 14, color: number.isEmpty ? C.blue : C.grey)),
              ),
            ]),
          ),
        ]),
      ),
    );
  }

  Future<void> _send() async {
    final p = project!;
    final text = note.text.trim();
    if (to.isEmpty) {
      toast(context, 'Choose who to send it to');
      _chooseTo();
      return;
    }
    if (text.isEmpty && photoFiles.isEmpty) {
      setState(() => noteError = 'Write a note or add a photo');
      return;
    }
    if (sending) return;
    sending = true;
    try {
      // Makes sure every recipient has a number, asking for any that are missing.
      for (final name in List.of(to)) {
        if (!await Sms.withPhone(context, p, name)) return;
        if (!mounted) return;
        setState(() {});
      }
      final numbers = [for (final n in to) p.phoneOf(n)];
      final files = [for (final f in photoFiles) store.photoFile(f)];
      final me = store.me();
      recordNotice(p, to, text, photoFiles, me, nowMs());
      store.save();
      sent = true;

      final body = Sms.noticeText(p, text, me, store.contactPhone);
      if (files.isNotEmpty) {
        await Sms.compose(context, numbers, body, photos: files);
      } else if (await Sms.canSendDirect() || await Sms.requestDirect()) {
        await _textEach(numbers, body);
      } else {
        // Can't text from the app (iPhone, or no permission): open Messages with everyone and the text ready.
        if (!mounted) return;
        await Sms.compose(context, numbers, body);
      }
      if (mounted) Navigator.of(context).pop();
    } finally {
      sending = false;
    }
  }

  /// Texts each person separately from this phone; anyone it couldn't reach goes to Messages.
  Future<void> _textEach(List<String> numbers, String body) async {
    final sentTo = <String>[], failed = <String>[];
    for (var i = 0; i < to.length; i++) {
      if (await Sms.sendDirect(numbers[i], body)) {
        sentTo.add(to[i]);
      } else {
        failed.add(numbers[i]);
      }
    }
    if (!mounted) return;
    if (sentTo.isNotEmpty) toast(context, 'Texted ${Fmt.join(sentTo)}', long: true);
    if (failed.isNotEmpty) await Sms.compose(context, failed, body);
  }

  @override
  Widget build(BuildContext context) {
    final p = project;
    if (p == null) return const Scaffold();
    return Scaffold(
      backgroundColor: C.bg,
      body: SafeArea(
        child: Column(children: [
          SmallTopBar(p.name, 'Notify'),
          Expanded(
            child: SingleChildScrollView(
              child: Container(
                margin: const EdgeInsets.fromLTRB(14, 0, 14, 20),
                padding: const EdgeInsets.fromLTRB(20, 6, 20, 18),
                decoration: cardDecoration(),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  InkWell(
                    onTap: _chooseTo,
                    child: const Padding(
                      padding: EdgeInsets.symmetric(vertical: 18),
                      child: Row(children: [
                        Icon(Icons.person_add_alt_1, color: C.blue, size: 30),
                        SizedBox(width: 16),
                        Text('Send to', style: TextStyle(color: C.blue, fontSize: 22)),
                      ]),
                    ),
                  ),
                  for (final name in to) _recipient(name),
                  const Divider(height: 1, color: C.divider),
                  Padding(
                    padding: const EdgeInsets.only(top: 14),
                    child: TextField(
                      controller: note,
                      minLines: 3,
                      maxLines: null,
                      keyboardType: TextInputType.multiline,
                      textCapitalization: TextCapitalization.sentences,
                      style: const TextStyle(fontSize: 19, color: C.text, height: 1.3),
                      onChanged: (_) {
                        if (noteError != null) setState(() => noteError = null);
                      },
                      decoration: InputDecoration(
                        hintText: 'Write your note…',
                        hintStyle: const TextStyle(color: C.greyLight),
                        border: InputBorder.none,
                        isCollapsed: noteError == null,
                        errorText: noteError,
                      ),
                    ),
                  ),
                  const Padding(padding: EdgeInsets.only(top: 14), child: Divider(height: 1, color: C.divider)),
                  addPhotosRow(_addPhotos),
                  PhotoStrip(
                    files: <File>[for (final f in photoFiles) store.photoFile(f)],
                    onAdd: _addPhotos,
                    onRemove: _removePhoto,
                  ),
                ]),
              ),
            ),
          ),
          bottomPill('Send note', _send),
        ]),
      ),
    );
  }
}
