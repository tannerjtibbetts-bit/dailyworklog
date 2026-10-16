// One daily work log (checklist): header with progress, filter chips, and the list of fields.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../export/export.dart';
import '../import/import_screen.dart';
import '../services/done_text.dart';
import '../services/photos.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'assign_sheet.dart';
import 'field_widgets.dart';
import 'task_screen.dart';

enum FieldFilter { none, me, incomplete, complete, unassigned }

bool matchesFilter(Task t, FieldFilter f, String me) {
  switch (f) {
    case FieldFilter.me:
      return t.assignees.contains(me);
    case FieldFilter.incomplete:
      return !t.done;
    case FieldFilter.complete:
      return t.done;
    case FieldFilter.unassigned:
      return t.assignees.isEmpty;
    case FieldFilter.none:
      return true;
  }
}

/// South first, then North, then Unzoned, then fields with no area (same order as the report).
int areaRank(Task t) => Task.areaRank(t.side);

/// The fields shown for a filter, South, North, Unzoned, then the rest, keeping the order within an area.
List<Task> visibleFields(List<Task> tasks, FieldFilter f, String me) {
  final groups = [<Task>[], <Task>[], <Task>[], <Task>[]];
  for (final t in tasks) {
    if (matchesFilter(t, f, me)) groups[areaRank(t)].add(t);
  }
  return [for (final g in groups) ...g];
}

/// Moves a dragged field from [from] to [to] (index after removal). Fields stay inside their
/// area (South / North / Unzoned / none): returns null if the move would take it into another area.
List<Task>? moveField(List<Task> visible, int from, int to) {
  if (from < 0 || from >= visible.length || to < 0 || to >= visible.length) return null;
  final out = List.of(visible);
  final t = out.removeAt(from);
  out.insert(to, t);
  for (var i = 1; i < out.length; i++) {
    if (areaRank(out[i - 1]) > areaRank(out[i])) return null;
  }
  return out;
}

/// Ticks or unticks a field. False (nothing changed) if it needs a photo before it can be completed.
bool toggleDone(Task t, String me, int now) {
  if (!t.done && t.photosRequired && t.photos.isEmpty) return false;
  t.done = !t.done;
  t.completedBy = t.done ? me : '';
  t.completedAt = t.done ? now : 0;
  return true;
}

Task newField(String title, String side, String me) => Task()
  ..title = title
  ..side = side
  ..createdBy = me;

/// One line on the "Also today" list.
String extraText(Extra e) {
  var text = '${DateFormat('h:mm a', 'en_US').format(DateTime.fromMillisecondsSinceEpoch(e.at))}  ${e.label()}';
  if ((e.type == Extra.notify || e.type == Extra.note) && e.detail.isNotEmpty) text += '\n${e.detail}';
  if (e.photos.isNotEmpty) text += '\n${Fmt.plural(e.photos.length, 'photo', 'photos')}';
  return text;
}

/// Save checklist as template: the fields with a clean slate.
Template templateFrom(DailyLog log, String name) => Template()
  ..name = name
  ..tasks = [for (final t in log.tasks) t.freshCopy()];

class LogScreen extends StatefulWidget {
  final String projectId, logId;

  const LogScreen({super.key, required this.projectId, required this.logId});

  @override
  State<LogScreen> createState() => _LogScreenState();
}

class _LogScreenState extends State<LogScreen> {
  final scroll = ScrollController();
  final cards = <String, BuildContext>{};
  var filter = FieldFilter.none;
  var topShown = false;
  var closing = false;

  Store get store => Store.instance;
  Project? get project => store.project(widget.projectId);
  DailyLog? get log => project?.findLog(widget.logId);

  @override
  void initState() {
    super.initState();
    scroll.addListener(() {
      final show = scroll.hasClients && scroll.offset > 110;
      if (show != topShown) setState(() => topShown = show);
    });
  }

  @override
  void dispose() {
    scroll.dispose();
    super.dispose();
  }

  void _snack(String message, {String? action, VoidCallback? onAction}) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          behavior: SnackBarBehavior.floating,
          margin: const EdgeInsets.fromLTRB(16, 0, 16, 104), // above the mic
          duration: const Duration(seconds: 4),
          action: action == null ? null : SnackBarAction(label: action, onPressed: onAction ?? () {}),
        ),
      );
  }

  void _setFilter(FieldFilter f) => setState(() => filter = filter == f ? FieldFilter.none : f);

  // ----- actions -----

  void _toggle(Task t) {
    if (!toggleDone(t, store.me(), nowMs())) {
      HapticFeedback.heavyImpact();
      _snack(
        'This field needs a photo before it can be completed',
        action: 'Take photo',
        onAction: () => _takePhoto(t),
      );
      return;
    }
    HapticFeedback.selectionClick();
    store.save();
    final p = project, l = log;
    if (t.done && p != null && l != null) {
      textDone(context, p, l, t, margin: const EdgeInsets.fromLTRB(16, 0, 16, 104)); // above the mic
    }
  }

  void _addTask(String title, [String side = '']) {
    final l = log;
    if (l == null) return;
    final t = newField(title, side, store.me());
    l.tasks.add(t);
    store.save();
    if (!matchesFilter(t, filter, store.me())) {
      toast(context, 'Field added. Clear the filter to see it.');
      return;
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => _reveal(t.id, true));
  }

  void _reveal(String id, bool retry) {
    final ctx = cards[id];
    if (ctx != null && ctx.mounted) {
      Scrollable.ensureVisible(ctx, duration: const Duration(milliseconds: 300), alignment: 0.5);
    } else if (retry && scroll.hasClients) {
      // Not built yet (off screen): go towards the end, then try again.
      scroll.jumpTo(scroll.position.maxScrollExtent);
      WidgetsBinding.instance.addPostFrameCallback((_) => _reveal(id, false));
    }
  }

  Future<void> _takePhoto(Task t) async {
    final name = await Photos.take(context);
    if (name == null) return;
    final task = log?.findTask(t.id);
    if (task == null) {
      store.deletePhoto(Photo(file: name));
      return;
    }
    task.photos.add(Photo(file: name, addedBy: store.me(), addedAt: nowMs()));
    store.save();
  }

  void _openTask(Task t) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => TaskScreen(projectId: widget.projectId, logId: widget.logId, taskId: t.id),
      ),
    );
  }

  Future<void> _assignCrew() async {
    final p = project, l = log;
    if (p == null || l == null) return;
    final sel = await showAssignSheet(context, p, l.crew, 'Checklist Crew');
    if (sel == null) return;
    l.crew = sel;
    store.save();
  }

  // ----- add fields from a photo or spreadsheet -----

  void _scanMenu() {
    void open(ImportSource s) {
      final p = project, l = log;
      if (p == null || l == null) return;
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => ImportScreen(project: p, log: l, source: s),
        ),
      );
    }

    menu(context, "Add fields from a list. You'll check them before they're added.", [
      SheetItem(Icons.photo_camera_outlined, 'Take a photo of a task list', () => open(ImportSource.camera)),
      SheetItem(Icons.photo_library_outlined, 'Choose a photo', () => open(ImportSource.gallery)),
      SheetItem(Icons.table_chart_outlined, 'Import an Excel or CSV file', () => open(ImportSource.file)),
      SheetItem(Icons.sms_outlined, 'Add a field from a text screenshot', () => open(ImportSource.textScreenshot)),
    ]);
  }

  Future<void> _newFieldPrompt() async {
    final ctl = TextEditingController();
    var side = '';
    String? error;
    final result = await showDialog<(String, String)>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) {
          void submit() {
            final s = ctl.text.trim();
            if (s.isEmpty) {
              set(() => error = 'Required');
              return;
            }
            Navigator.pop(c, (s, side));
          }

          Widget tick(String value, String label) => InkWell(
            onTap: () => set(() => side = side == value ? '' : value),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Checkbox(value: side == value, onChanged: (on) => set(() => side = on == true ? value : '')),
                Text(label, style: const TextStyle(fontSize: 16)),
                const SizedBox(width: 12),
              ],
            ),
          );

          return AlertDialog(
            title: const Text('New field'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: ctl,
                  autofocus: true,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: InputDecoration(
                    hintText: 'e.g. Pack up sea can 317503 and send out of zone 2',
                    errorText: error,
                  ),
                  onSubmitted: (_) => submit(),
                ),
                const SizedBox(height: 8),
                // One of them (or none)
                Wrap(children: [
                  tick(Task.south, 'South'),
                  const SizedBox(width: 8),
                  tick(Task.north, 'North'),
                  const SizedBox(width: 8),
                  tick(Task.unzoned, 'Unzoned'),
                ]),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
              TextButton(onPressed: submit, child: const Text('Add')),
            ],
          );
        },
      ),
    );
    if (result != null && mounted) _addTask(result.$1, result.$2);
  }

  void _menu() {
    final p = project, l = log;
    if (p == null || l == null) return;
    menu(context, null, [
      SheetItem(Icons.edit_outlined, 'Rename Checklist', () async {
        final n = await prompt(context, 'Rename checklist', 'Checklist name', l.displayTitle(), 'Save');
        if (n == null) return;
        l.name = n;
        store.save();
      }),
      SheetItem(Icons.add, 'Add Field', _newFieldPrompt),
      SheetItem(Icons.photo_camera_outlined, 'Add Fields from a Photo or Spreadsheet', _scanMenu),
      SheetItem(Icons.reorder, 'Reorder Fields', () {
        if (filter != FieldFilter.none) setState(() => filter = FieldFilter.none);
        _snack('Press and hold a field, then drag it up or down');
      }),
      SheetItem(Icons.send_outlined, 'Email report', () => Export.email(context, p, l)),
      SheetItem(Icons.picture_as_pdf_outlined, 'Export to PDF', () => Export.report(context, p, l, word: false)),
      SheetItem(Icons.description_outlined, 'Export to Word', () => Export.report(context, p, l, word: true)),
      SheetItem(Icons.bookmark_add_outlined, 'Save Checklist as Template', () async {
        final n = await prompt(context, 'Save as template', 'Template name', 'Daily fields', 'Save');
        if (n == null) return;
        p.templates.add(templateFrom(l, n));
        store.save();
        if (mounted) _snack('Template saved. Use it from the + button.');
      }),
      SheetItem(Icons.content_copy, 'Duplicate Checklist', () {
        final copy = p.duplicate(l, Fmt.todayIso(), store.me());
        store.save();
        Navigator.of(context).pushReplacement(
          MaterialPageRoute<void>(
            builder: (_) => LogScreen(projectId: p.id, logId: copy.id),
          ),
        );
      }),
      SheetItem(
        Icons.delete_outline,
        'Delete Checklist',
        () => confirm(
          context,
          'Delete this checklist?',
          "${l.displayTitle()} and its photos will be removed. This can't be undone.",
          'Delete',
          () {
            closing = true;
            store.deleteLogPhotos(l);
            p.logs.remove(l);
            store.save();
            Navigator.of(context).pop();
          },
        ),
        danger: true,
      ),
    ]);
  }

  void _onReorder(List<Task> visible, int from, int to) {
    final l = log;
    if (l == null || filter != FieldFilter.none) return;
    final moved = moveField(visible, from, to);
    if (moved == null) return;
    // Dragging only happens with no filter, so the screen shows every field: keep that order.
    if (moved.length == l.tasks.length) {
      l.tasks
        ..clear()
        ..addAll(moved);
    }
    store.save();
  }

  // ----- building -----

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) {
      final p = project, l = log;
      if (p == null || l == null) {
        if (!closing) {
          closing = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) Navigator.of(context).maybePop();
          });
        }
        return const Scaffold();
      }
      final visible = visibleFields(l.tasks, filter, store.me());
      return Scaffold(
        body: SafeArea(
          bottom: false,
          child: Stack(
            children: [
              Column(
                children: [
                  _topBar(p, l),
                  Expanded(
                    child: ReorderableListView.builder(
                      scrollController: scroll,
                      buildDefaultDragHandles: false,
                      padding: const EdgeInsets.only(bottom: 150),
                      header: _header(p, l),
                      footer: _footer(l, visible),
                      itemCount: visible.length,
                      proxyDecorator: (child, i, anim) => AnimatedBuilder(
                        animation: anim,
                        builder: (c, w) => Transform.scale(
                          scale: 1 + 0.02 * anim.value,
                          child: Material(color: Colors.transparent, elevation: 8 * anim.value, child: w),
                        ),
                        child: child,
                      ),
                      onReorderItem: (a, b) => _onReorder(visible, a, b),
                      itemBuilder: (c, i) {
                        final t = visible[i];
                        final card = _card(t);
                        return filter == FieldFilter.none
                            ? ReorderableDelayedDragStartListener(key: ValueKey(t.id), index: i, child: card)
                            : KeyedSubtree(key: ValueKey(t.id), child: card);
                      },
                    ),
                  ),
                ],
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 20 + MediaQuery.paddingOf(context).bottom,
                child: Center(child: _mic()),
              ),
            ],
          ),
        ),
      );
    },
  );

  Widget _topBar(Project p, DailyLog l) {
    Widget button(IconData icon, String tip, VoidCallback onTap) => SizedBox(
      width: 56,
      height: 56,
      child: IconButton(
        icon: Icon(icon, color: C.navy),
        tooltip: tip,
        onPressed: onTap,
      ),
    );
    return Container(
      height: 72,
      decoration: BoxDecoration(
        color: C.bg,
        border: Border(bottom: BorderSide(color: topShown ? C.divider : Colors.transparent)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 10),
      child: Row(
        children: [
          button(Icons.arrow_back, 'Back', () => Navigator.of(context).maybePop()),
          Expanded(
            child: AnimatedOpacity(
              opacity: topShown ? 1 : 0,
              duration: const Duration(milliseconds: 160),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    p.name.toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.5),
                  ),
                  Text(
                    l.displayTitle(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: C.navy, fontSize: 19),
                  ),
                ],
              ),
            ),
          ),
          // Add fields from a photo or spreadsheet (not the per-field photo camera)
          button(Icons.photo_camera_outlined, 'Add fields from a photo or spreadsheet', _scanMenu),
          const SizedBox(width: 8),
          button(Icons.more_vert, 'Checklist options', _menu),
        ],
      ),
    );
  }

  Widget _mic() => Semantics(
    button: true,
    label: 'Add a field',
    child: Material(
      color: C.blue,
      elevation: 8,
      borderRadius: BorderRadius.circular(100),
      child: InkWell(
        borderRadius: BorderRadius.circular(100),
        // No built-in speech here: the New field box opens with the keyboard, whose mic key dictates.
        onTap: _newFieldPrompt,
        child: const SizedBox(width: 150, height: 72, child: Icon(Icons.mic, color: Colors.white, size: 32)),
      ),
    ),
  );

  Widget _header(Project p, DailyLog l) {
    final done = l.doneCount(), total = l.tasks.length;
    Widget chip(FieldFilter f, String label) {
      final on = filter == f;
      return Padding(
        padding: const EdgeInsets.only(right: 10),
        child: Material(
          color: on ? C.deepBlue : C.chip,
          borderRadius: BorderRadius.circular(100),
          child: InkWell(
            borderRadius: BorderRadius.circular(100),
            onTap: () => _setFilter(f),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 12),
              child: Text(label, style: TextStyle(fontSize: 18, color: on ? Colors.white : C.grey)),
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Text(
              l.displayTitle(),
              style: const TextStyle(color: C.navy, fontSize: 32, fontWeight: FontWeight.bold),
            ),
          ),
          if (p.location.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 2, 24, 0),
              child: Row(
                children: [
                  const Icon(Icons.place_outlined, size: 16, color: C.grey),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(p.location, style: const TextStyle(color: C.grey, fontSize: 15)),
                  ),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: ProgressRing(progress: total == 0 ? 0 : done / total, filledWhenComplete: false),
                ),
                const SizedBox(width: 20),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('$done/$total fields completed', style: const TextStyle(color: C.grey, fontSize: 17)),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Material(
                            color: Colors.white,
                            shape: const CircleBorder(side: BorderSide(color: C.cardStroke)),
                            child: InkWell(
                              customBorder: const CircleBorder(),
                              onTap: _assignCrew,
                              child: const SizedBox(
                                width: 62,
                                height: 62,
                                child: Icon(
                                  Icons.person_add_alt_1_outlined,
                                  color: C.blue,
                                  semanticLabel: 'Assign crew to this checklist',
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 14),
                          Flexible(
                            child: GestureDetector(onTap: _assignCrew, child: Badges(l.crew, size: 52, max: 4)),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Container(height: 1, margin: const EdgeInsets.fromLTRB(24, 18, 24, 0), color: C.divider),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 14),
            child: Row(
              children: [
                chip(FieldFilter.me, 'Assigned to Me'),
                chip(FieldFilter.incomplete, 'Incomplete'),
                chip(FieldFilter.complete, 'Complete'),
                chip(FieldFilter.unassigned, 'Unassigned'),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _card(Task t) {
    final missing = t.photosRequired && t.photos.isEmpty && !t.done;
    final reqColor = missing ? C.red : C.text;
    return _Tracked(
      id: t.id,
      into: cards,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        child: Container(
          decoration: fieldCardDecoration(t),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: () => _openTask(t),
                child: IntrinsicHeight(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Container(width: 5, color: fieldAccent(t)),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(16, 18, 12, 18),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Padding(
                                    padding: const EdgeInsets.only(top: 2),
                                    child: CheckCircle(checked: t.done, onTap: () => _toggle(t)),
                                  ),
                                  const SizedBox(width: 14),
                                  Expanded(
                                    child: Text(
                                      t.title,
                                      style: const TextStyle(color: C.navy, fontSize: 22, fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                  SizedBox(
                                    width: 46,
                                    height: 46,
                                    child: IconButton(
                                      tooltip: 'Edit field',
                                      icon: const Icon(Icons.add_circle_outline, color: C.blue),
                                      onPressed: () => _openTask(t),
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  SizedBox(
                                    width: 46,
                                    height: 46,
                                    child: IconButton(
                                      tooltip: 'Take photo',
                                      icon: const Icon(Icons.photo_camera_outlined, color: C.blue),
                                      onPressed: () => _takePhoto(t),
                                    ),
                                  ),
                                ],
                              ),
                              Padding(
                                padding: const EdgeInsets.only(left: 52),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    if (t.done)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 8),
                                        child: _iconLine(
                                          Icons.person_outline,
                                          C.greyLight,
                                          '${t.completedBy}, ${Fmt.stamp(t.completedAt)}',
                                        ),
                                      ),
                                    if (t.assignees.isNotEmpty)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 6),
                                        child: _iconLine(
                                          Icons.person_add_alt_1_outlined,
                                          C.blue,
                                          Fmt.join(t.assignees),
                                        ),
                                      ),
                                    if (t.notes.trim().isNotEmpty)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 10),
                                        child: Text(
                                          t.notes.trim(),
                                          style: const TextStyle(color: C.text, fontSize: 18, height: 1.3),
                                        ),
                                      ),
                                    if (t.loadSecured)
                                      const Padding(
                                        padding: EdgeInsets.only(top: 12),
                                        child: FieldChip(
                                          '✓  Load secured',
                                          bg: Color(0xFFE6F6EC),
                                          fg: Color(0xFF148A3A),
                                        ),
                                      ),
                                    if (t.side.isNotEmpty)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 12),
                                        child: FieldChip(
                                          Task.sideLabel(t.side),
                                          bg: const Color(0xFFEEF4FB),
                                          fg: C.navy,
                                          border: const Color(0xFFC7D7EA),
                                        ),
                                      ),
                                    if (t.nextShift)
                                      const Padding(
                                        padding: EdgeInsets.only(top: 12),
                                        child: FieldChip('Next shift', bg: C.sky, fg: C.deepBlue),
                                      ),
                                    if (t.photosRequired)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 14),
                                        child: FieldChip(
                                          'Photos Required',
                                          icon: Icons.info_outline,
                                          bg: const Color(0xFFEEF4FB),
                                          fg: reqColor,
                                          border: const Color(0xFFC7D7EA),
                                        ),
                                      ),
                                    if (t.photos.isNotEmpty)
                                      Padding(
                                        padding: const EdgeInsets.only(top: 14),
                                        child: SingleChildScrollView(
                                          scrollDirection: Axis.horizontal,
                                          child: Row(children: [for (final ph in t.photos) _thumb(ph, 100)]),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _iconLine(IconData icon, Color color, String text) => Row(
    children: [
      Icon(icon, size: 18, color: color),
      const SizedBox(width: 6),
      Flexible(
        child: Text(text, style: TextStyle(color: color, fontSize: 15)),
      ),
    ],
  );

  Widget _thumb(Photo ph, double size) {
    final File f = store.photoFile(ph.file);
    return Padding(
      padding: const EdgeInsets.only(right: 10),
      child: GestureDetector(
        onTap: () => showPhoto(context, ph, f),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: Image.file(
            f,
            width: size,
            height: size,
            fit: BoxFit.cover,
            cacheWidth: 300,
            errorBuilder: (c, e, s) => Container(width: size, height: size, color: C.sky),
          ),
        ),
      ),
    );
  }

  Widget _footer(DailyLog l, List<Task> visible) {
    final extras = List.of(l.extras)..sort((a, b) => a.at.compareTo(b.at));
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (visible.isEmpty)
            Padding(
              padding: const EdgeInsets.all(28),
              child: Center(
                child: Text(
                  l.tasks.isEmpty
                      ? 'No fields yet. Tap New Field below, the blue mic to say one, or the camera at the top to add them from a list.'
                      : 'No fields match this filter.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: C.grey, fontSize: 17),
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.only(left: 12),
            child: InkWell(
              onTap: _newFieldPrompt,
              child: const Padding(
                padding: EdgeInsets.fromLTRB(12, 16, 16, 16),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.add, color: C.blue, size: 30),
                    SizedBox(width: 14),
                    Text('New Field', style: TextStyle(color: C.blue, fontSize: 24)),
                  ],
                ),
              ),
            ),
          ),
          if (extras.isNotEmpty)
            Container(
              margin: const EdgeInsets.fromLTRB(14, 8, 14, 0),
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
              decoration: cardDecoration(),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Also today',
                    style: TextStyle(color: C.navy, fontSize: 19, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 2),
                  const Text(
                    'Ticked items are included in the PDF, Word and emailed report.',
                    style: TextStyle(color: C.grey, fontSize: 14),
                  ),
                  const SizedBox(height: 6),
                  for (final e in extras) _extraRow(l, e),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _extraRow(DailyLog l, Extra e) {
    void remove() => confirm(
      context,
      'Remove from this checklist?',
      "It's taken off the Also today list. The link, note or template itself stays.",
      'Remove',
      () {
        l.extras.remove(e);
        store.save();
      },
    );
    void set(bool on) {
      e.include = on;
      store.save();
    }

    return Row(
      children: [
        Checkbox(value: e.include, onChanged: (v) => set(v ?? false)),
        Expanded(
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () => set(!e.include),
            onLongPress: remove,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(extraText(e), style: const TextStyle(color: C.text, fontSize: 16)),
            ),
          ),
        ),
        SizedBox(
          width: 48,
          height: 48,
          child: IconButton(
            tooltip: 'Remove from this checklist',
            icon: const Icon(Icons.delete_outline, color: C.grey),
            onPressed: remove,
          ),
        ),
      ],
    );
  }
}

/// Remembers where a field card is on screen, so a new field can be scrolled into view.
class _Tracked extends StatefulWidget {
  final String id;
  final Map<String, BuildContext> into;
  final Widget child;

  const _Tracked({required this.id, required this.into, required this.child});

  @override
  State<_Tracked> createState() => _TrackedState();
}

class _TrackedState extends State<_Tracked> {
  @override
  Widget build(BuildContext context) {
    widget.into[widget.id] = context;
    return widget.child;
  }

  @override
  void dispose() {
    if (widget.into[widget.id] == context) widget.into.remove(widget.id);
    super.dispose();
  }
}
