// Notes tab: write a note, add photos, and tick it into today's report. Saves automatically.
import 'dart:io';

import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../services/photos.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

/// Title bar used by the Note and Notify screens: project name over the screen name.
class SmallTopBar extends StatelessWidget {
  final String project, title;
  final Widget? trailing;

  const SmallTopBar(this.project, this.title, {super.key, this.trailing});

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 72,
        child: Stack(children: [
          Positioned.fill(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 64),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                Text(project.toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.5)),
                Text(title, style: const TextStyle(color: C.navy, fontSize: 21)),
              ]),
            ),
          ),
          Positioned(
            left: 10,
            top: 8,
            child: IconButton(
              iconSize: 28,
              tooltip: 'Back',
              icon: const Icon(Icons.arrow_back, color: C.navy),
              onPressed: () => Navigator.of(context).maybePop(),
            ),
          ),
          if (trailing != null) Positioned(right: 10, top: 8, child: trailing!),
        ]),
      );
}

/// Row of photo thumbnails with a red remove button on each, then the add tile.
class PhotoStrip extends StatelessWidget {
  final List<File> files;
  final void Function(int index) onRemove;
  final void Function(int index)? onOpen;
  final VoidCallback onAdd;

  const PhotoStrip({super.key, required this.files, required this.onRemove, required this.onAdd, this.onOpen});

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        clipBehavior: Clip.none,
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          for (var i = 0; i < files.length; i++)
            Padding(
              padding: const EdgeInsets.only(top: 10, right: 22),
              child: SizedBox(
                width: 120,
                height: 120,
                child: Stack(clipBehavior: Clip.none, children: [
                  Positioned.fill(
                    child: GestureDetector(
                      onTap: onOpen == null ? null : () => onOpen!(i),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.file(files[i],
                            fit: BoxFit.cover,
                            cacheWidth: 360,
                            errorBuilder: (_, _, _) => Container(color: C.chip)),
                      ),
                    ),
                  ),
                  Positioned(
                    top: -10,
                    right: -10,
                    child: Semantics(
                      label: 'Remove photo',
                      button: true,
                      child: GestureDetector(
                        onTap: () => onRemove(i),
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: const BoxDecoration(color: C.red, shape: BoxShape.circle),
                          child: const Icon(Icons.close, color: Colors.white, size: 20),
                        ),
                      ),
                    ),
                  ),
                ]),
              ),
            ),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Semantics(
              label: 'Add photo',
              button: true,
              child: InkWell(
                onTap: onAdd,
                borderRadius: BorderRadius.circular(12),
                child: Container(
                  width: 120,
                  height: 120,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF2F7FD),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: C.cardStroke, width: 1.5),
                  ),
                  child: const Icon(Icons.photo_camera_outlined, color: C.blue, size: 36),
                ),
              ),
            ),
          ),
        ]),
      );
}

/// "+ Photos" heading row.
Widget addPhotosRow(VoidCallback onTap) => InkWell(
      onTap: onTap,
      child: const Padding(
        padding: EdgeInsets.only(top: 18, bottom: 12),
        child: Row(children: [
          Icon(Icons.add, color: C.blue, size: 30),
          SizedBox(width: 16),
          Text('Photos', style: TextStyle(color: C.blue, fontSize: 22)),
        ]),
      ),
    );

/// Big blue button at the bottom of the Note and Notify screens.
Widget bottomPill(String label, VoidCallback onTap) => Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
      child: SizedBox(
        height: 64,
        width: double.infinity,
        child: Material(
          color: C.blue,
          elevation: 6,
          borderRadius: BorderRadius.circular(100),
          child: InkWell(
            borderRadius: BorderRadius.circular(100),
            onTap: onTap,
            child: Center(
              child: Text(label,
                  style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
            ),
          ),
        ),
      ),
    );

class NoteScreen extends StatefulWidget {
  final String projectId;
  final String? noteId;

  const NoteScreen({super.key, required this.projectId, this.noteId});

  @override
  State<NoteScreen> createState() => _NoteScreenState();
}

class _NoteScreenState extends State<NoteScreen> with WidgetsBindingObserver {
  Project? project;
  Note? n;

  /// True until the note has something in it and is added to the project.
  bool isNew = true;
  late final TextEditingController note;

  Store get store => Store.instance;

  @override
  void initState() {
    super.initState();
    project = store.project(widget.projectId);
    n = project?.findNote(widget.noteId);
    isNew = n == null;
    if (isNew) n = Note()..by = store.me();
    note = TextEditingController(text: n!.text);
    WidgetsBinding.instance.addObserver(this);
    if (project == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) Navigator.of(context).pop();
      });
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    note.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) persist();
  }

  /// Saves the note; a brand-new note is only kept once it has text or a photo.
  void persist() {
    final p = project, x = n;
    if (p == null || x == null) return;
    final text = note.text.trim();
    var changed = text != x.text;
    x.text = text;
    if (isNew) {
      if (x.isEmpty) return;
      p.notes.insert(0, x);
      isNew = false;
      changed = true;
    }
    if (!changed) return;
    x.updatedAt = nowMs();
    p.syncNote(x);
    store.save();
  }

  /// Leaving: save, and a note that was emptied out is removed.
  void _leave() {
    persist();
    final p = project, x = n;
    if (p != null && x != null && !isNew && x.isEmpty) {
      p.notes.remove(x);
      p.forgetExtras(x.id);
      store.save();
    }
  }

  void _delete() {
    confirm(context, 'Delete this note?', 'The note and its photos are deleted and taken off any report.', 'Delete',
        () {
      final p = project!, x = n!;
      store.deletePhotos(x.photos);
      if (!isNew) {
        p.notes.remove(x);
        p.forgetExtras(x.id);
        store.save();
      }
      n = null; // nothing left to save
      isNew = true;
      Navigator.of(context).pop();
    });
  }

  Future<void> _addPhotos() async {
    final files = await Photos.choose(context);
    if (files.isEmpty || !mounted || n == null) return;
    final now = nowMs();
    for (final f in files) {
      n!.photos.add(Photo(file: f, addedBy: store.me(), addedAt: now));
    }
    final wasNew = isNew;
    persist();
    if (!wasNew) {
      // persist() only saves on text changes once the note exists
      n!.updatedAt = now;
      project!.syncNote(n!);
      store.save();
    }
    setState(() {});
  }

  void _removePhoto(Photo ph) {
    confirm(context, 'Delete this photo?', "It's removed from this note and any report it's in.", 'Delete', () {
      final x = n;
      if (x == null) return;
      x.photos.remove(ph);
      store.deletePhoto(ph);
      x.updatedAt = nowMs();
      if (!isNew) {
        project!.syncNote(x);
        store.save();
      }
      setState(() {});
    });
  }

  // ----- include in today's report -----

  Widget _include() {
    final p = project!, x = n!;
    final today = Fmt.todayIso();
    final target = p.todayLog(today);
    if (target == null) {
      return const Padding(
        padding: EdgeInsets.only(top: 12),
        child: Text('Start a checklist for today to put this note in a report.',
            style: TextStyle(color: C.grey, fontSize: 15)),
      );
    }
    final e = target.findExtra(x.id);
    final on = e != null && e.include;
    void flip(bool v) {
      persist();
      if (isNew) {
        if (v) toast(context, 'Write a note or add a photo first');
        setState(() {});
        return;
      }
      final ex = target.findExtra(x.id) ??
          p.record(target, today, Extra.note, x.id, x.by, x.text, x.photos, store.me(), nowMs())!;
      ex.include = v;
      store.save();
      setState(() {});
    }

    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: InkWell(
        onTap: () => flip(!on),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48),
          child: Row(children: [
            Checkbox(value: on, onChanged: (v) => flip(v ?? false)),
            Expanded(
              child: Text(
                target.date == today ? "Include in today's report" : 'Include in ${Fmt.logDate(target.date)} report',
                style: const TextStyle(color: C.text, fontSize: 18),
              ),
            ),
          ]),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final p = project, x = n;
    if (p == null || x == null) return const Scaffold();
    return PopScope(
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) _leave();
      },
      child: Scaffold(
        backgroundColor: C.bg,
        body: SafeArea(
          child: Column(children: [
            SmallTopBar(
              p.name,
              'Note',
              trailing: IconButton(
                iconSize: 28,
                tooltip: 'Delete note',
                icon: const Icon(Icons.delete_outline, color: C.red),
                onPressed: _delete,
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                child: Container(
                  margin: const EdgeInsets.fromLTRB(14, 0, 14, 20),
                  padding: const EdgeInsets.fromLTRB(20, 10, 20, 18),
                  decoration: cardDecoration(),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    Padding(
                      padding: const EdgeInsets.only(top: 14),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(minHeight: 160),
                        child: TextField(
                          controller: note,
                          autofocus: isNew,
                          minLines: 5,
                          maxLines: null,
                          keyboardType: TextInputType.multiline,
                          textCapitalization: TextCapitalization.sentences,
                          style: const TextStyle(fontSize: 19, color: C.text, height: 1.3),
                          decoration: const InputDecoration(
                            hintText: 'Write your note…',
                            hintStyle: TextStyle(color: C.greyLight),
                            border: InputBorder.none,
                            isCollapsed: true,
                          ),
                        ),
                      ),
                    ),
                    const Padding(padding: EdgeInsets.only(top: 14), child: Divider(height: 1, color: C.divider)),
                    addPhotosRow(_addPhotos),
                    PhotoStrip(
                      files: [for (final ph in x.photos) store.photoFile(ph.file)],
                      onAdd: _addPhotos,
                      onRemove: (i) => _removePhoto(x.photos[i]),
                      onOpen: (i) => showPhoto(context, x.photos[i], store.photoFile(x.photos[i].file)),
                    ),
                    const Padding(padding: EdgeInsets.only(top: 18), child: Divider(height: 1, color: C.divider)),
                    _include(),
                  ]),
                ),
              ),
            ),
            bottomPill('Done', () => Navigator.of(context).pop()),
          ]),
        ),
      ),
    );
  }
}
