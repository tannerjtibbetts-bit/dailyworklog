// "Edit Field": title, notes, area, assignees (texted when assigned), photos, load securement,
// next shift. Saves automatically.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../services/photos.dart';
import '../services/sms.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'assign_sheet.dart';
import 'field_widgets.dart';
import 'log_screen.dart' show toggleDone;

/// People newly put on a field, in the order picked.
List<String> newlyAssigned(List<String> before, List<String> after) => [
  for (final n in after)
    if (!before.contains(n)) n,
];

/// Only people ticked for assignment texts in Manage crew get one; never yourself.
bool getsAssignText(Project p, String name, String me) => name != me && p.assignTexts.contains(name);

/// Ticks or unticks "Load securement completed", stamping who and when.
void toggleSecured(Task t, String me, int now) {
  t.loadSecured = !t.loadSecured;
  t.securedBy = t.loadSecured ? me : '';
  t.securedAt = t.loadSecured ? now : 0;
}

/// "Added by Tanner, 10/05/2026 at 7:02 AM"
String addedLine(Task t) {
  if (t.createdAt <= 0) return '';
  final by = t.createdBy.isEmpty ? '' : ' by ${t.createdBy}';
  return 'Added$by, ${Fmt.stamp(t.createdAt)}';
}

class TaskScreen extends StatefulWidget {
  final String projectId, logId, taskId;

  const TaskScreen({super.key, required this.projectId, required this.logId, required this.taskId});

  @override
  State<TaskScreen> createState() => _TaskScreenState();
}

class _TaskScreenState extends State<TaskScreen> with WidgetsBindingObserver {
  late final TextEditingController title, notes;
  var closing = false;

  Store get store => Store.instance;
  Project? get project => store.project(widget.projectId);
  DailyLog? get log => project?.findLog(widget.logId);
  Task? get task => log?.findTask(widget.taskId);

  @override
  void initState() {
    super.initState();
    final t = task;
    title = TextEditingController(text: t?.title ?? '');
    notes = TextEditingController(text: t?.notes ?? '');
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) _saveText();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    if (_applyText()) scheduleMicrotask(store.save);
    title.dispose();
    notes.dispose();
    super.dispose();
  }

  /// Copies the typed title and notes into the field. True if anything changed.
  bool _applyText() {
    final t = task;
    if (t == null || closing) return false;
    final ti = title.text.trim(), n = notes.text.trim();
    var changed = false;
    if (ti.isNotEmpty && ti != t.title) {
      t.title = ti;
      changed = true;
    }
    if (n != t.notes) {
      t.notes = n;
      changed = true;
    }
    return changed;
  }

  void _saveText() {
    if (_applyText()) store.save();
  }

  ScaffoldMessengerState get _messenger => ScaffoldMessenger.of(context);

  void _snack(String message, {String? action, VoidCallback? onAction, Duration? duration}) {
    _messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          duration: duration ?? const Duration(seconds: 4),
          action: action == null ? null : SnackBarAction(label: action, onPressed: onAction ?? () {}),
        ),
      );
  }

  // ----- completion -----

  void _toggle(Task t) {
    if (!toggleDone(t, store.me(), nowMs())) {
      HapticFeedback.heavyImpact();
      _snack('This field needs a photo before it can be completed', action: 'Take photo', onAction: _startCamera);
      return;
    }
    HapticFeedback.selectionClick();
    store.save();
  }

  // ----- assignees -----

  Future<void> _assign() async {
    final p = project, t = task;
    if (p == null || t == null) return;
    final sel = await showAssignSheet(context, p, t.assignees, 'Assign Users');
    if (sel == null || !mounted) return;
    final added = newlyAssigned(t.assignees, sel);
    t.assignees = sel;
    store.save();
    if (added.isNotEmpty) await _autoText(added);
  }

  /// Texts each newly assigned person in turn, asking for any missing numbers first.
  Future<void> _autoText(List<String> people) async {
    for (final name in people) {
      final p = project;
      if (p == null || !mounted) return;
      if (!getsAssignText(p, name, store.me())) continue;
      if (!await Sms.withPhone(context, p, name)) return; // cancelled: stop, like the Android app
      if (!mounted) return;
      await _sendWithUndo(name);
    }
  }

  Future<void> _sendWithUndo(String name) async {
    final p = project, l = log, t = task;
    if (p == null || l == null || t == null) return;
    final body = Sms.taskText(p, l, t, store.me(), store.contactPhone);
    final phone = p.phoneOf(name);
    if (!await Sms.canSendDirect()) {
      // iPhone, or no permission yet: ask (Android), else open Messages with the text ready, one tap to send.
      if (Platform.isAndroid && await Sms.requestDirect()) {
        if (mounted) await _sendWithUndo(name);
        return;
      }
      if (mounted) await Sms.compose(context, [phone], body);
      return;
    }
    if (!mounted) return;
    final messenger = _messenger;
    var cancelled = false;
    // Keeps going if this screen is closed, like the Android app.
    final timer = Timer(const Duration(seconds: 5), () async {
      if (cancelled) return;
      final ok = await Sms.sendDirect(phone, body);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(ok ? 'Texted $name' : "Couldn't text $name. Use Text it to try again."),
            duration: const Duration(seconds: 2),
          ),
        );
    });
    _snack(
      'Texting $name…',
      duration: const Duration(seconds: 5),
      action: 'Undo',
      onAction: () {
        cancelled = true;
        timer.cancel();
        messenger.showSnackBar(
          SnackBar(content: Text('Text to $name cancelled'), duration: const Duration(seconds: 2)),
        );
      },
    );
  }

  /// Manual resend: opens Messages with the text ready.
  Future<void> _textIt(String name) async {
    final p = project, l = log, t = task;
    if (p == null || l == null || t == null) return;
    if (!await Sms.withPhone(context, p, name) || !mounted) return;
    await Sms.compose(context, [p.phoneOf(name)], Sms.taskText(p, l, t, store.me(), store.contactPhone));
  }

  void _removeAssignee(Task t, String name) =>
      confirm(context, 'Remove $name from this field?', 'They stay on the crew. No text is sent.', 'Remove', () {
        t.assignees.remove(name);
        store.save();
      });

  // ----- photos -----

  void _addPhotos(List<String> names) {
    final t = task;
    if (t == null) {
      for (final n in names) {
        store.deletePhoto(Photo(file: n));
      }
      return;
    }
    for (final n in names) {
      t.photos.add(Photo(file: n, addedBy: store.me(), addedAt: nowMs()));
    }
    if (names.isNotEmpty) store.save();
  }

  Future<void> _startCamera() async {
    final n = await Photos.take(context);
    if (n != null) _addPhotos([n]);
  }

  Future<void> _startGallery() async => _addPhotos(await Photos.pick(context));

  Future<void> _photoSource() async => _addPhotos(await Photos.choose(context));

  void _removePhoto(Task t, Photo p) =>
      confirm(context, 'Remove this photo?', 'It will be deleted from this field.', 'Remove', () {
        t.photos.remove(p);
        store.deletePhoto(p);
        store.save();
      });

  // ----- delete -----

  void _confirmDelete() {
    final l = log, t = task;
    if (l == null || t == null) return;
    confirm(context, 'Delete this field?', 'Its notes and photos will be removed.', 'Delete', () {
      closing = true;
      store.deleteTaskPhotos(t);
      l.tasks.remove(t);
      store.save();
      Navigator.of(context).pop();
    });
  }

  void _more() => menu(context, null, [
    SheetItem(Icons.photo_camera_outlined, 'Take photo', _startCamera),
    SheetItem(Icons.photo_library_outlined, 'Add from gallery', _startGallery),
    SheetItem(Icons.delete_outline, 'Delete field', _confirmDelete, danger: true),
  ]);

  // ----- building -----

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) {
      final p = project, l = log, t = task;
      if (p == null || l == null || t == null) {
        if (!closing) {
          closing = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) Navigator.of(context).maybePop();
          });
        }
        return const Scaffold();
      }
      return PopScope(
        onPopInvokedWithResult: (didPop, _) {
          if (didPop) _saveText();
        },
        child: Scaffold(
          body: SafeArea(
            child: Column(
              children: [
                _topBar(l),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(14, 0, 14, 24),
                    child: Container(
                      decoration: cardDecoration(),
                      padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: _body(p, l, t)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );

  Widget _topBar(DailyLog l) => SizedBox(
    height: 76,
    child: Row(
      children: [
        const SizedBox(width: 10),
        SizedBox(
          width: 56,
          height: 56,
          child: IconButton(
            tooltip: 'Back',
            icon: const Icon(Icons.arrow_back, color: C.navy),
            onPressed: () => Navigator.of(context).maybePop(),
          ),
        ),
        Expanded(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                l.displayTitle().toUpperCase(),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: C.grey, fontSize: 14, letterSpacing: 0.5),
              ),
              const Text('Edit Field', style: TextStyle(color: C.navy, fontSize: 21)),
            ],
          ),
        ),
        SizedBox(
          width: 56,
          height: 56,
          child: IconButton(
            tooltip: 'Field options',
            icon: const Icon(Icons.more_vert, color: C.navy),
            onPressed: _more,
          ),
        ),
        const SizedBox(width: 10),
      ],
    ),
  );

  static const _divider = Divider(height: 1, thickness: 1, color: C.divider);

  Widget _stamp(String text, double size) => Row(
    children: [
      const Icon(Icons.person_outline, size: 18, color: C.greyLight),
      const SizedBox(width: 6),
      Flexible(
        child: Text(
          text,
          style: TextStyle(color: C.greyLight, fontSize: size),
        ),
      ),
    ],
  );

  Widget _blueRow(String label, VoidCallback onTap, {double vertical = 22}) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: EdgeInsets.symmetric(vertical: vertical),
      child: Row(
        children: [
          const Icon(Icons.add, color: C.blue, size: 32),
          const SizedBox(width: 16),
          Flexible(
            child: Text(label, style: const TextStyle(color: C.blue, fontSize: 23)),
          ),
        ],
      ),
    ),
  );

  Widget _tickRow(bool value, VoidCallback onTap, String label, Widget? sub) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        children: [
          Transform.scale(
            scale: 1.25,
            child: IgnorePointer(
              child: Checkbox(value: value, onChanged: (_) {}),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: const TextStyle(color: C.navy, fontSize: 21)),
                if (sub != null) Padding(padding: const EdgeInsets.only(top: 2), child: sub),
              ],
            ),
          ),
        ],
      ),
    ),
  );

  List<Widget> _body(Project p, DailyLog l, Task t) => [
    Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: CheckCircle(checked: t.done, size: 42, onTap: () => _toggle(t)),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: TextField(
              controller: title,
              maxLines: null,
              textCapitalization: TextCapitalization.sentences,
              style: const TextStyle(color: C.navy, fontSize: 28, fontWeight: FontWeight.bold),
              decoration: const InputDecoration(
                border: InputBorder.none,
                hintText: 'Field title',
                hintStyle: TextStyle(color: C.greyLight),
              ),
            ),
          ),
        ],
      ),
    ),
    if (t.done)
      Padding(
        padding: const EdgeInsets.only(top: 14),
        child: _stamp('${t.completedBy}, ${Fmt.stamp(t.completedAt)}', 16),
      ),
    Padding(
      padding: const EdgeInsets.only(top: 14),
      child: TextField(
        controller: notes,
        minLines: 2,
        maxLines: null,
        textCapitalization: TextCapitalization.sentences,
        style: const TextStyle(color: C.text, fontSize: 19, height: 1.3),
        decoration: const InputDecoration(
          border: InputBorder.none,
          hintText: 'Add notes…',
          hintStyle: TextStyle(color: C.greyLight),
        ),
      ),
    ),
    const SizedBox(height: 18),
    _divider,
    // South / North / Unzoned: reports list South fields first, then North, then Unzoned
    Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Area', style: TextStyle(color: C.navy, fontSize: 21)),
          Wrap(
            spacing: 12,
            children: [
              _side(t, Task.south, 'South'),
              _side(t, Task.north, 'North'),
              _side(t, Task.unzoned, 'Unzoned'),
            ],
          ),
        ],
      ),
    ),
    _divider,
    _blueRow('Assign User', _assign),
    for (final name in t.assignees) _assigneeRow(p, t, name),
    _divider,
    Padding(
      padding: const EdgeInsets.symmetric(vertical: 18),
      child: Row(
        children: [
          Expanded(child: _blueRow('Photos', _photoSource, vertical: 6)),
          const Flexible(
            child: Text(
              'Photos required?',
              textAlign: TextAlign.end,
              style: TextStyle(color: C.grey, fontSize: 17),
            ),
          ),
          const SizedBox(width: 10),
          Switch(
            value: t.photosRequired,
            onChanged: (on) {
              t.photosRequired = on;
              store.save();
            },
          ),
        ],
      ),
    ),
    SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      clipBehavior: Clip.none,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final ph in t.photos) _photoCell(t, ph),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Semantics(
              button: true,
              label: 'Add photo',
              child: GestureDetector(
                onTap: _photoSource,
                child: const DashedBox(size: 140, child: Icon(Icons.photo_camera_outlined, color: C.blue, size: 40)),
              ),
            ),
          ),
        ],
      ),
    ),
    const SizedBox(height: 22),
    _divider,
    _tickRow(
      t.loadSecured,
      () {
        toggleSecured(t, store.me(), nowMs());
        store.save();
        HapticFeedback.selectionClick();
      },
      'Load securement completed',
      t.loadSecured ? _stamp('${t.securedBy}, ${Fmt.stamp(t.securedAt)}', 15) : null,
    ),
    _divider,
    _tickRow(
      t.nextShift,
      () {
        t.nextShift = !t.nextShift;
        store.save();
        HapticFeedback.selectionClick();
      },
      'Carry over to next shift',
      const Text(
        'Shown on the report, and offered when you start the next checklist',
        style: TextStyle(color: C.greyLight, fontSize: 15),
      ),
    ),
    _divider,
    Padding(
      padding: const EdgeInsets.only(top: 18),
      child: Text(addedLine(t), style: const TextStyle(color: C.grey, fontSize: 15)),
    ),
    const SizedBox(height: 14),
    InkWell(
      onTap: _confirmDelete,
      child: const Padding(
        padding: EdgeInsets.fromLTRB(0, 14, 12, 14),
        child: Row(
          children: [
            Icon(Icons.delete_outline, color: C.red),
            SizedBox(width: 12),
            Text('Delete field', style: TextStyle(color: C.red, fontSize: 18)),
          ],
        ),
      ),
    ),
  ];

  /// Two ticks that act as one choice: South, North, or neither.
  Widget _side(Task t, String side, String label) {
    void set(bool on) {
      t.side = on ? side : '';
      store.save();
    }

    return InkWell(
      onTap: () => set(t.side != side),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Checkbox(value: t.side == side, onChanged: (v) => set(v ?? false)),
          Text(label, style: const TextStyle(fontSize: 19, color: C.text)),
        ],
      ),
    );
  }

  Widget _assigneeRow(Project p, Task t, String name) {
    final number = p.phoneOf(name);
    return InkWell(
      onTap: _assign,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Row(
          children: [
            InitialsBadge(name, size: 44),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 8, 0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(name, style: const TextStyle(color: C.text, fontSize: 19)),
                    Text(
                      number.isEmpty ? 'No phone number yet' : number,
                      style: const TextStyle(color: C.grey, fontSize: 14),
                    ),
                  ],
                ),
              ),
            ),
            Material(
              color: C.sky,
              borderRadius: BorderRadius.circular(100),
              child: InkWell(
                borderRadius: BorderRadius.circular(100),
                onTap: () => _textIt(name),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 16, vertical: 9),
                  child: Text('Text it', style: TextStyle(color: C.deepBlue, fontSize: 15)),
                ),
              ),
            ),
            // Remove this person from the field right here (handy after carrying a field over).
            const SizedBox(width: 12),
            _redCircle('Remove $name from this field', () => _removeAssignee(t, name)),
          ],
        ),
      ),
    );
  }

  Widget _redCircle(String label, VoidCallback onTap) => Semantics(
    button: true,
    label: label,
    child: GestureDetector(
      onTap: onTap,
      child: Container(
        width: 34,
        height: 34,
        decoration: const BoxDecoration(color: C.red, shape: BoxShape.circle),
        child: const Icon(Icons.remove, color: Colors.white, size: 22),
      ),
    ),
  );

  Widget _photoCell(Task t, Photo ph) {
    final f = store.photoFile(ph.file);
    return Padding(
      padding: const EdgeInsets.only(right: 14),
      child: SizedBox(
        width: 150,
        height: 150,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Positioned(
              left: 0,
              top: 10,
              child: GestureDetector(
                onTap: () => showPhoto(context, ph, f),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.file(
                    f,
                    width: 140,
                    height: 140,
                    fit: BoxFit.cover,
                    cacheWidth: 420,
                    errorBuilder: (c, e, s) => Container(width: 140, height: 140, color: C.sky),
                  ),
                ),
              ),
            ),
            Positioned(right: -7, top: 0, child: _redCircle('Remove photo', () => _removePhoto(t, ph))),
          ],
        ),
      ),
    );
  }
}
