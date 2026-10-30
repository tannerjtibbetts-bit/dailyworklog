// A project's screen: Checklists, Notify, Links, Overtime, Attendance, Notes and Sea Can Movements tabs.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/project_search.dart';
import '../data/store.dart';
import '../export/export.dart';
import '../services/form_prefill.dart';
import '../services/sms.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'assign_sheet.dart';
import 'field_widgets.dart' show ProgressRing;
import 'log_screen.dart';
import 'note_screen.dart';
import 'notify_screen.dart';
import 'attendance_pane.dart';
import 'overtime_pane.dart';
import 'seacan_pane.dart';
import 'projects_screen.dart' show AddPill;

enum ProjectTab { checklists, notify, links, overtime, attendance, notes, seaCans }

const _tabLabels = {
  ProjectTab.checklists: 'Checklists',
  ProjectTab.notify: 'Notify',
  ProjectTab.links: 'Links',
  ProjectTab.overtime: 'Overtime',
  ProjectTab.attendance: 'Attendance',
  ProjectTab.notes: 'Notes',
  ProjectTab.seaCans: 'Sea Can Movements',
};

/// Tab ids saved in Store.hiddenTabs (the same ids as the classic Android app); Checklists can't be hidden.
const tabIds = {
  ProjectTab.notify: 'notify',
  ProjectTab.links: 'links',
  ProjectTab.overtime: 'overtime',
  ProjectTab.attendance: 'attendance',
  ProjectTab.notes: 'notes',
  ProjectTab.seaCans: 'seacans',
};

/// Tabs shown, in their usual order.
List<ProjectTab> visibleTabs(Set<String> hidden) => [
  for (final t in ProjectTab.values)
    if (t == ProjectTab.checklists || !hidden.contains(tabIds[t])) t,
];

/// Splits the shown tabs into rows: threes, or four then three when there are seven.
List<List<ProjectTab>> tabRows(List<ProjectTab> shown) {
  if (shown.length > 6) return [shown.sublist(0, 4), shown.sublist(4)];
  return [
    for (var i = 0; i < shown.length; i += 3) shown.sublist(i, i + 3 > shown.length ? shown.length : i + 3),
  ];
}

/// Tick the tabs to show; unticked ones disappear from every project until ticked again.
Future<void> chooseTabs(BuildContext context) async {
  final store = Store.instance;
  final on = {for (final e in tabIds.entries) e.key: !store.hiddenTabs.contains(e.value)};
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => StatefulBuilder(
      builder: (c, set) => AlertDialog(
        title: const Text('Show or hide tabs'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Untick the tabs you don\u2019t use. Checklists is always shown.',
              style: TextStyle(color: C.grey, fontSize: 15),
            ),
            for (final t in tabIds.keys)
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                controlAffinity: ListTileControlAffinity.leading,
                value: on[t],
                title: Text(_tabLabels[t]!),
                onChanged: (v) => set(() => on[t] = v ?? true),
              ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Save')),
        ],
      ),
    ),
  );
  if (ok != true) return;
  store.hiddenTabs
    ..clear()
    ..addAll([
      for (final e in tabIds.entries)
        if (on[e.key] == false) e.value,
    ]);
  store.save();
}

/// Adds https:// when no scheme is typed; spaces are dropped.
String normalizeUrl(String raw) {
  var u = raw.trim().replaceAll(' ', '');
  if (u.isEmpty) return u;
  if (!u.contains('://') && !RegExp(r'^(mailto|tel):', caseSensitive: false).hasMatch(u)) u = 'https://$u';
  return u;
}

/// Host of a web address without "www.", or '' if there isn't one.
String hostOf(String url) {
  final h = Uri.tryParse(url)?.host ?? '';
  return h.startsWith('www.') ? h.substring(4) : h;
}

/// Checklists newest first (same date: newest created first).
List<DailyLog> sortedLogs(Project p) => List.of(p.logs)
  ..sort((a, b) {
    final c = b.date.compareTo(a.date);
    return c != 0 ? c : b.createdAt.compareTo(a.createdAt);
  });

/// New checklist for [iso]. [source] fields are copied (only unfinished ones, keeping notes, when
/// [unfinishedOnly]); the crew comes from the latest checklist. Using a template is noted on it.
DailyLog createLog(Project project, String iso, List<Task>? source, bool unfinishedOnly, Template? tmpl, String me) {
  final latest = project.latestLog();
  final l = DailyLog()..date = iso;
  if (latest != null) l.crew = List.of(latest.crew);
  if (source != null) {
    for (final t in source) {
      if (unfinishedOnly && t.done) continue;
      final c = unfinishedOnly ? t.carryCopy() : t.freshCopy();
      c.createdBy = me;
      l.tasks.add(c);
    }
  }
  project.logs.add(l);
  if (tmpl != null) {
    project.record(
      l,
      Fmt.todayIso(),
      Extra.template,
      tmpl.id,
      tmpl.name,
      Fmt.plural(tmpl.tasks.length, 'field', 'fields'),
      null,
      me,
      nowMs(),
    );
  }
  return l;
}

/// New checklist with just the fields marked "Carry over to next shift" (notes kept).
DailyLog carryMarked(Project project, String iso, List<Task> marked, String me) {
  final latest = project.latestLog();
  final l = DailyLog()..date = iso;
  if (latest != null) l.crew = List.of(latest.crew);
  for (final t in marked) {
    l.tasks.add(t.carryCopy()..createdBy = me);
  }
  project.logs.add(l);
  return l;
}

/// Deletes every note in the project and takes them off any report.
void clearAllNotes(Store store, Project p) {
  for (final n in List.of(p.notes)) {
    store.deletePhotos(n.photos);
    p.forgetExtras(n.id);
  }
  p.notes.clear();
}

class ProjectScreen extends StatefulWidget {
  final String projectId;

  /// Tab to open on (Checklists unless given).
  final ProjectTab initialTab;

  const ProjectScreen({super.key, required this.projectId, this.initialTab = ProjectTab.checklists});

  @override
  State<ProjectScreen> createState() => _ProjectScreenState();
}

class _ProjectScreenState extends State<ProjectScreen> {
  late ProjectTab mode = widget.initialTab;
  String query = '';
  final search = TextEditingController();
  bool _closing = false;

  Store get store => Store.instance;
  Project? get project => store.project(widget.projectId);

  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  void _push(Widget screen) => Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));

  // ----- creating logs -----

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );
    if (d == null || !mounted) return;
    _startOptions(Fmt.isoOf(d));
  }

  void _startOptions(String iso) {
    final p = project!;
    final latest = p.latestLog();
    if (latest == null && p.templates.isEmpty) {
      _create(iso, null, false, null);
      return;
    }
    final items = <SheetItem>[SheetItem(Icons.add, 'Blank checklist', () => _create(iso, null, false, null))];
    if (latest != null) {
      final open = latest.tasks.length - latest.doneCount();
      final from = Fmt.logDate(latest.date);
      final marked = latest.tasks.where((t) => t.nextShift).toList();
      if (marked.isNotEmpty) {
        items.add(
          SheetItem(Icons.redo, 'Carry over ${Fmt.plural(marked.length, 'field', 'fields')} marked for next shift', () {
            final l = carryMarked(p, iso, marked, store.me());
            store.save();
            _openLog(l);
          }),
        );
      }
      if (open > 0) {
        items.add(
          SheetItem(
            Icons.redo,
            'Carry over ${Fmt.plural(open, 'unfinished field', 'unfinished fields')} from $from',
            () => _create(iso, latest.tasks, true, null),
          ),
        );
      }
      if (latest.tasks.isNotEmpty) {
        items.add(SheetItem(Icons.copy, 'Copy all fields from $from', () => _create(iso, latest.tasks, false, null)));
      }
    }
    for (final t in p.templates) {
      items.add(SheetItem(Icons.description_outlined, 'Template: ${t.name}', () => _create(iso, t.tasks, false, t)));
    }
    menu(context, 'Start the ${Fmt.logDate(iso)} log with', items);
  }

  void _create(String iso, List<Task>? source, bool unfinishedOnly, Template? tmpl) {
    final l = createLog(project!, iso, source, unfinishedOnly, tmpl, store.me());
    store.save();
    _openLog(l);
  }

  void _openLog(DailyLog l) => _push(LogScreen(projectId: widget.projectId, logId: l.id));

  // ----- menus -----

  void _projectMenu() {
    final p = project!;
    menu(context, p.name, [
      SheetItem(Icons.edit_outlined, 'Rename project', () async {
        final n = await prompt(
          context,
          'Rename project',
          'Project name',
          p.name,
          'Save',
          caps: TextCapitalization.words,
        );
        if (n == null) return;
        p.name = n;
        store.save();
      }),
      SheetItem(Icons.place_outlined, 'Set location', () async {
        final n = await prompt(
          context,
          'Location',
          'e.g. Kincardine, ON',
          p.location,
          'Save',
          caps: TextCapitalization.words,
        );
        if (n == null) return;
        p.location = n;
        store.save();
      }),
      SheetItem(Icons.send_outlined, 'Report email list', () => Export.editEmailList(context, p)),
      SheetItem(Icons.person_add_alt_1, 'Manage crew', () => showManageCrew(context, p)),
      SheetItem(Icons.tab_outlined, 'Show or hide tabs', () => chooseTabs(context)),
      SheetItem(Icons.badge_outlined, 'My form details', () => FormPrefill.editDetails(context)),
      SheetItem(Icons.delete_outline, 'Delete project', () {
        confirm(
          context,
          'Delete ${p.name}?',
          "This removes all of its daily logs and photos. It can't be undone.",
          'Delete',
          () {
            store.deleteProjectPhotos(p);
            store.projects.remove(p);
            store.save();
          },
        );
      }, danger: true),
    ]);
  }

  void _logMenu(DailyLog l) {
    final p = project!;
    menu(context, l.displayTitle(), [
      SheetItem(Icons.send_outlined, 'Email report', () => Export.email(context, p, l)),
      SheetItem(Icons.picture_as_pdf_outlined, 'Export to PDF', () => Export.report(context, p, l, word: false)),
      SheetItem(Icons.article_outlined, 'Export to Word', () => Export.report(context, p, l, word: true)),
      SheetItem(Icons.grid_on, 'Export to Excel', () => Export.report(context, p, l, excel: true)),
      SheetItem(Icons.edit_outlined, 'Rename Checklist', () async {
        final n = await prompt(context, 'Rename checklist', 'Checklist name', l.displayTitle(), 'Save');
        if (n == null) return;
        l.name = n;
        store.save();
      }),
      SheetItem(Icons.copy, 'Duplicate Checklist', () {
        final copy = p.duplicate(l, Fmt.todayIso(), store.me());
        store.save();
        _openLog(copy);
      }),
      SheetItem(Icons.delete_outline, 'Delete Checklist', () {
        confirm(
          context,
          'Delete this checklist?',
          "${l.displayTitle()} and its photos will be removed. This can't be undone.",
          'Delete',
          () {
            store.deleteLogPhotos(l);
            p.logs.remove(l);
            store.save();
          },
        );
      }, danger: true),
    ]);
  }

  // ----- links -----

  /// Opens a link (forms open with your usual answers) and notes the use on today's checklist;
  /// it only goes in the report if ticked.
  Future<void> _openLink(Link l) => FormPrefill.openLink(context, l, onOpened: () => _recordLink(l));

  Future<void> _openPlain(Link l) => FormPrefill.openPlain(context, l, onOpened: () => _recordLink(l));

  void _recordLink(Link l) {
    final p = project;
    if (p == null) return;
    if (p.record(null, Fmt.todayIso(), Extra.link, l.id, l.title, l.url, null, store.me(), nowMs()) != null) {
      store.save();
    }
  }

  Future<void> _editLink(Link? existing) async {
    final url = TextEditingController(text: existing?.url ?? '');
    final name = TextEditingController(text: existing?.title ?? '');
    if (existing == null) {
      // Handy: if a web address was just copied, fill it in.
      try {
        final clip = (await Clipboard.getData(Clipboard.kTextPlain))?.text?.trim() ?? '';
        if (RegExp(r'^https?://\S+$', caseSensitive: false).hasMatch(clip)) url.text = clip;
      } catch (_) {}
      if (!mounted) return;
    }
    String? error;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) {
          void submit() {
            final u = normalizeUrl(url.text);
            if (u.isEmpty || hostOf(u).isEmpty) {
              set(() => error = 'Enter a web address');
              return;
            }
            Navigator.pop(c, true);
          }

          return AlertDialog(
            title: Text(existing == null ? 'Add link' : 'Edit link'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: url,
                  autofocus: url.text.isEmpty,
                  keyboardType: TextInputType.url,
                  decoration: InputDecoration(hintText: 'Web address, e.g. https://…', errorText: error),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: name,
                  autofocus: url.text.isNotEmpty,
                  textCapitalization: TextCapitalization.sentences,
                  decoration: const InputDecoration(hintText: 'Name (optional), e.g. Tool tracking portal'),
                  onSubmitted: (_) => submit(),
                ),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
              TextButton(onPressed: submit, child: const Text('Save')),
            ],
          );
        },
      ),
    );
    if (ok != true) return;
    final u = normalizeUrl(url.text);
    final n = name.text.trim();
    var l = existing;
    if (l == null) {
      l = Link();
      store.links.add(l);
    }
    l.url = u;
    l.title = n.isEmpty ? hostOf(u) : n;
    store.save();
  }

  void _linkMenu(Link l) {
    final form = FormPrefill.isFormstack(l.url);
    menu(context, l.title, [
      SheetItem(Icons.open_in_new, 'Open', () => _openLink(l)),
      if (form) SheetItem(Icons.person_outline, 'My form details', () => FormPrefill.editDetails(context)),
      if (form)
        SheetItem(
          Icons.copy,
          'Copy my details',
          () => FormPrefill.copyDetails(context, l, onOpened: () => _recordLink(l)),
        ),
      if (form) SheetItem(Icons.open_in_new, 'Open without filling in', () => _openPlain(l)),
      SheetItem(Icons.edit_outlined, 'Edit link', () => _editLink(l)),
      SheetItem(Icons.copy, 'Copy link', () {
        Clipboard.setData(ClipboardData(text: l.url));
        toast(context, 'Link copied');
      }),
      SheetItem(Icons.share_outlined, 'Share link', () {
        SharePlus.instance.share(ShareParams(text: l.url, subject: l.title, title: 'Share link'));
      }),
      SheetItem(Icons.delete_outline, 'Delete link', () {
        confirm(context, 'Delete this link?', '${l.title} will be removed from every project.', 'Delete', () {
          store.links.remove(l);
          store.save();
        });
      }, danger: true),
    ]);
  }

  // ----- notify -----

  void _noticeMenu(Notice n) {
    final p = project!;
    menu(context, 'To ${Fmt.join(n.to)}', [
      SheetItem(Icons.send_outlined, 'Send again', () {
        final numbers = [for (final name in n.to) p.phoneOf(name)];
        final files = [for (final ph in n.photos) store.photoFile(ph.file)];
        Sms.compose(context, numbers, Sms.noticeText(p, n.note, store.me(), store.contactPhone), photos: files);
      }),
      if (n.photos.isNotEmpty)
        SheetItem(
          Icons.photo_library_outlined,
          'View photos',
          () => showPhoto(context, n.photos.first, store.photoFile(n.photos.first.file)),
        ),
      SheetItem(Icons.delete_outline, 'Delete from list', () {
        confirm(
          context,
          'Delete this note?',
          "It's removed from this list and its photos are deleted. Texts already sent stay on their phones.",
          'Delete',
          () {
            store.deletePhotos(n.photos);
            p.notices.remove(n);
            store.save();
          },
        );
      }, danger: true),
    ]);
  }

  // ----- overtime -----

  /// Overtime tab's Export button: just that sheet, as PDF or Excel.
  void _exportOvertime() {
    final p = project!;
    menu(context, 'Send just the overtime sheet. Your daily report still includes it when ticked.', [
      SheetItem(
        Icons.picture_as_pdf_outlined,
        'Export overtime to PDF',
        () => Export.overtime(context, p, excel: false),
      ),
      SheetItem(Icons.grid_on, 'Export overtime to Excel', () => Export.overtime(context, p, excel: true)),
    ]);
  }

  // ----- attendance -----

  /// Attendance tab's Export button: just that sheet, as PDF or Excel.
  void _exportAttendance() {
    final p = project!;
    menu(context, 'Send just the attendance sheet. Your daily report still includes it when ticked.', [
      SheetItem(
        Icons.picture_as_pdf_outlined,
        'Export attendance to PDF',
        () => Export.attendance(context, p, excel: false),
      ),
      SheetItem(Icons.grid_on, 'Export attendance to Excel', () => Export.attendance(context, p, excel: true)),
    ]);
  }

  // ----- sea cans -----

  /// Sea Can Movements tab's Export button: just the list, as PDF, Excel, CSV or Word.
  void _exportSeaCans() {
    final p = project!;
    menu(context, 'Send just the sea can list. Your daily report still includes it when ticked.', [
      SheetItem(
        Icons.picture_as_pdf_outlined,
        'Export sea cans to PDF',
        () => Export.seaCans(context, p, format: 'pdf'),
      ),
      SheetItem(Icons.grid_on, 'Export sea cans to Excel', () => Export.seaCans(context, p, format: 'xlsx')),
      SheetItem(Icons.table_rows_outlined, 'Export sea cans to CSV', () => Export.seaCans(context, p, format: 'csv')),
      SheetItem(
        Icons.description_outlined,
        'Export sea cans to Word',
        () => Export.seaCans(context, p, format: 'docx'),
      ),
    ]);
  }

  // ----- notes -----

  void _openNote(Note? n) => _push(NoteScreen(projectId: widget.projectId, noteId: n?.id));

  void _noteMenu(Note n) {
    menu(context, 'Note from ${Fmt.longStamp(n.createdAt)}', [
      SheetItem(Icons.edit_outlined, 'Open note', () => _openNote(n)),
      SheetItem(Icons.delete_outline, 'Delete note', () => _deleteNote(n), danger: true),
    ]);
  }

  void _deleteNote(Note n) {
    final p = project!;
    confirm(
      context,
      'Delete this note?',
      'The note and its photos are deleted and taken off any report.',
      'Delete',
      () {
        store.deletePhotos(n.photos);
        p.notes.remove(n);
        p.forgetExtras(n.id);
        store.save();
      },
    );
  }

  /// Deletes every note in this project (all of them, not just the ones a search shows).
  void _clearNotes() {
    final p = project!;
    final count = p.notes.length;
    if (count == 0) return;
    confirm(
      context,
      'Delete all ${Fmt.plural(count, 'note', 'notes')}?',
      "Every note in ${p.name} and its photos are deleted and taken off any report. This can't be undone.",
      'Delete all',
      () {
        clearAllNotes(store, p);
        store.save();
      },
    );
  }

  // ----- "include in today's report" ticks -----

  Widget _include(String type, String refId, String title, String detail, List<Photo>? photos, int usedAt) {
    final p = project!;
    final today = Fmt.todayIso();
    final target = p.todayLog(today);
    if (target == null) return const SizedBox.shrink();
    final e = target.findExtra(refId);
    final on = e != null && e.include;
    void flip(bool v) {
      final x =
          target.findExtra(refId) ??
          p.record(target, today, type, refId, title, detail, photos, store.me(), usedAt > 0 ? usedAt : nowMs())!;
      x.include = v;
      HapticFeedback.selectionClick();
      store.save();
    }

    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: InkWell(
        onTap: () => flip(!on),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 40),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 32,
                height: 32,
                child: Checkbox(value: on, onChanged: (v) => flip(v ?? false)),
              ),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  target.date == today ? "Include in today's report" : 'Include in ${Fmt.logDate(target.date)} report',
                  style: const TextStyle(color: C.grey, fontSize: 15),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ----- list rows -----

  Widget _card({required Widget child, VoidCallback? onTap, VoidCallback? onLongPress, double bottom = 12}) => Padding(
    padding: EdgeInsets.only(bottom: bottom),
    child: Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(22),
        side: const BorderSide(color: C.cardStroke),
      ),
      child: InkWell(borderRadius: BorderRadius.circular(22), onTap: onTap, onLongPress: onLongPress, child: child),
    ),
  );

  static Widget _iconBox(IconData icon) => Container(
    width: 52,
    height: 52,
    decoration: BoxDecoration(color: C.sky, borderRadius: BorderRadius.circular(16)),
    child: Icon(icon, color: C.deepBlue, size: 24),
  );

  Widget _thumb(List<Photo> photos) {
    if (photos.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(left: 10),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Image.file(
          store.photoFile(photos.first.file),
          width: 58,
          height: 58,
          fit: BoxFit.cover,
          cacheWidth: 200,
          errorBuilder: (_, _, _) => Container(width: 58, height: 58, color: C.chip),
        ),
      ),
    );
  }

  /// A checklist card; [hits] are the search matches shown under it.
  Widget _logRow(DailyLog l, {List<String> hits = const []}) {
    final done = l.doneCount(), total = l.tasks.length;
    return _card(
      bottom: 14,
      onTap: () => _openLog(l),
      onLongPress: () => _logMenu(l),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 104),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 14, 18),
          child: Row(
            children: [
              ProgressRing(progress: total == 0 ? 0 : done / total, size: 58),
              const SizedBox(width: 18),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l.displayTitle(),
                      style: const TextStyle(color: C.navy, fontSize: 21, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 4),
                    Text('$done/$total fields completed', style: const TextStyle(color: C.grey, fontSize: 16)),
                    ..._hitLines(hits, 3),
                  ],
                ),
              ),
              Badges(l.people(), size: 44, max: 2),
              const SizedBox(width: 6),
              const Icon(Icons.chevron_right, color: C.navy, size: 28),
            ],
          ),
        ),
      ),
    );
  }

  /// Up to [max] search matches, then "+N more".
  List<Widget> _hitLines(List<String> hits, int max) => [
    if (hits.isNotEmpty) const SizedBox(height: 6),
    for (final h in hits.take(max))
      Padding(
        padding: const EdgeInsets.only(top: 2),
        child: Text(h, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: C.grey, fontSize: 15)),
      ),
    if (hits.length > max)
      Padding(
        padding: const EdgeInsets.only(top: 2),
        child: Text('+${hits.length - max} more', style: const TextStyle(color: C.greyLight, fontSize: 15)),
      ),
  ];

  /// Sea cans that match the search; tapping opens the Sea Can Movements tab.
  Widget _seaCanHits(List<String> lines) => _card(
    bottom: 14,
    onTap: () {
      if (!visibleTabs(store.hiddenTabs).contains(ProjectTab.seaCans)) {
        toast(context, 'The Sea Can Movements tab is hidden. Turn it on in Show or hide tabs.');
        return;
      }
      search.clear();
      setState(() {
        query = '';
        mode = ProjectTab.seaCans;
      });
    },
    child: Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 14, 18),
      child: Row(
        children: [
          _iconBox(Icons.local_shipping_outlined),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Sea Can Movements',
                  style: TextStyle(color: C.navy, fontSize: 21, fontWeight: FontWeight.bold),
                ),
                ..._hitLines(lines, 5),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: C.navy, size: 28),
        ],
      ),
    ),
  );

  Widget _linkRow(Link l) => _card(
    onTap: () => _openLink(l),
    onLongPress: () => _linkMenu(l),
    child: ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 88),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 16, 14, 16),
        child: Row(
          children: [
            _iconBox(Icons.link),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l.title,
                    style: const TextStyle(color: C.navy, fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    l.url.replaceFirst(RegExp(r'^https?://(www\.)?', caseSensitive: false), ''),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: C.blue, fontSize: 15),
                  ),
                  _include(Extra.link, l.id, l.title, l.url, null, 0),
                ],
              ),
            ),
            const SizedBox(width: 8),
            const Icon(Icons.open_in_new, color: C.grey, size: 26),
          ],
        ),
      ),
    ),
  );

  Widget _noticeLike({
    required IconData icon,
    required String title,
    required String text,
    required String when,
    required Widget include,
    required List<Photo> photos,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
    VoidCallback? onDelete,
  }) => _card(
    onTap: onTap,
    onLongPress: onLongPress,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(18, 16, 14, 16),
      child: Row(
        children: [
          _iconBox(icon),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: C.navy, fontSize: 19, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 3),
                Text(
                  text,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: C.text, fontSize: 16),
                ),
                if (when.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Text(when, style: const TextStyle(color: C.grey, fontSize: 14)),
                  ),
                include,
              ],
            ),
          ),
          _thumb(photos),
          if (onDelete != null)
            Padding(
              padding: const EdgeInsets.only(left: 6),
              child: IconButton(
                tooltip: 'Delete note',
                icon: const Icon(Icons.delete_outline, color: C.red),
                onPressed: onDelete,
              ),
            ),
        ],
      ),
    ),
  );

  Widget _noticeRow(Notice n) => _noticeLike(
    icon: Icons.send_outlined,
    title: 'To ${Fmt.join(n.to)}',
    text: n.note.isEmpty ? Fmt.plural(n.photos.length, 'photo', 'photos') : n.note,
    when:
        'Sent ${Fmt.longStamp(n.sentAt)}${n.photos.isEmpty ? '' : ', ${Fmt.plural(n.photos.length, 'photo', 'photos')}'}',
    include: _include(Extra.notify, n.id, Fmt.join(n.to), n.note, n.photos, n.sentAt),
    photos: n.photos,
    onTap: () => _noticeMenu(n),
  );

  Widget _noteRow(Note n) {
    final by = n.by.isEmpty || n.by == 'Me' ? '' : 'By ${n.by}';
    final pics = n.photos.isEmpty ? '' : Fmt.plural(n.photos.length, 'photo', 'photos');
    return _noticeLike(
      icon: Icons.edit_outlined,
      title: Fmt.longStamp(n.createdAt),
      text: n.text.isEmpty ? Fmt.plural(n.photos.length, 'photo', 'photos') : n.text,
      when: by.isEmpty || pics.isEmpty ? by + pics : '$by, $pics',
      include: _include(Extra.note, n.id, n.by, n.text, n.photos, 0),
      photos: n.photos,
      onTap: () => _openNote(n),
      onLongPress: () => _noteMenu(n),
      onDelete: () => _deleteNote(n),
    );
  }

  // ----- building the screen -----

  List<Widget> _rows(Project p) {
    switch (mode) {
      case ProjectTab.notify:
        return [
          for (final n in p.notices)
            if (query.isEmpty || n.note.toLowerCase().contains(query) || Fmt.join(n.to).toLowerCase().contains(query))
              _noticeRow(n),
        ];
      case ProjectTab.notes:
        return [
          for (final n in p.notes)
            if (query.isEmpty || n.text.toLowerCase().contains(query)) _noteRow(n),
        ];
      case ProjectTab.links:
        return [
          for (final l in store.links)
            if (query.isEmpty || l.title.toLowerCase().contains(query) || l.url.toLowerCase().contains(query))
              _linkRow(l),
        ];
      default:
        if (query.isEmpty) return [for (final l in sortedLogs(p)) _logRow(l)];
        // Searching: every day the word appears on, plus matching sea cans.
        final r = ProjectSearch.run(p, query);
        return [
          if (r.seaCans.isNotEmpty) _seaCanHits(r.seaCans),
          for (final d in r.days) _logRow(d.log, hits: d.lines),
        ];
    }
  }

  String _emptyText() {
    if (query.isNotEmpty) return 'Nothing matches “${search.text.trim()}”';
    switch (mode) {
      case ProjectTab.notify:
        return 'No notes sent yet.\nTap + to send a crew member a note or photo.\nTexts send straight away; with photos,\nMessages opens and you tap Send.';
      case ProjectTab.notes:
        return "No notes yet.\nTap + to write a note or add photos.\nTick a note to put it in the day's report.";
      case ProjectTab.links:
        return 'No links yet.\nTap + to save a web link to a tool or procedure.\nLinks show up in every project.';
      default:
        return "No daily logs yet.\nTap + to start today's.";
    }
  }

  String get _searchHint => switch (mode) {
    ProjectTab.notify => 'Search sent notes',
    ProjectTab.notes => 'Search notes',
    ProjectTab.links => 'Search links',
    _ => 'Search all days: fields, notes, sea cans',
  };

  String get _fabLabel => switch (mode) {
    ProjectTab.notify => 'New note to crew',
    ProjectTab.notes => 'New note',
    ProjectTab.links => 'Add link',
    _ => 'New daily log',
  };

  void _fab() {
    switch (mode) {
      case ProjectTab.notify:
        _push(NotifyScreen(projectId: widget.projectId));
      case ProjectTab.notes:
        _openNote(null);
      case ProjectTab.links:
        _editLink(null);
      default:
        _pickDate();
    }
  }

  Widget _pill(ProjectTab t, {required bool first}) => Padding(
    padding: EdgeInsets.only(left: first ? 0 : 4),
    child: Material(
      color: mode == t ? C.darkPill : Colors.white,
      shape: StadiumBorder(side: BorderSide(color: mode == t ? C.darkPill : C.cardStroke)),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: () {
          if (mode != t) setState(() => mode = t);
        },
        child: SizedBox(
          height: 46,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 6),
            child: Center(
              child: FittedBox(
                fit: BoxFit.scaleDown,
                child: Text(
                  _tabLabels[t]!,
                  maxLines: 1,
                  style: TextStyle(fontSize: 14, color: mode == t ? Colors.white : C.text),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );

  /// The tabs that aren't hidden, in rows of three (four on top when all seven show);
  /// Sea Can Movements gets a wider pill for its longer name.
  Widget _pills() {
    final shown = visibleTabs(Store.instance.hiddenTabs);
    final rows = tabRows(shown);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 14, 12, 0),
      child: Column(
        children: [
          for (var r = 0; r < rows.length; r++) ...[
            if (r > 0) const SizedBox(height: 6),
            Row(
              children: [
                for (var i = 0; i < rows[r].length; i++)
                  Expanded(
                    flex: rows[r][i] == ProjectTab.seaCans ? 16 : 10,
                    child: _pill(rows[r][i], first: i == 0),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _searchBox() => Container(
    height: 62,
    margin: const EdgeInsets.fromLTRB(20, 18, 20, 0),
    padding: const EdgeInsets.only(left: 20, right: 16),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(30),
      border: Border.all(color: C.cardStroke),
    ),
    child: Row(
      children: [
        const Icon(Icons.search, color: C.navy, size: 26),
        const SizedBox(width: 12),
        Expanded(
          child: TextField(
            controller: search,
            textInputAction: TextInputAction.search,
            style: const TextStyle(fontSize: 18, color: C.text),
            decoration: InputDecoration(
              hintText: _searchHint,
              hintStyle: const TextStyle(color: C.grey),
              border: InputBorder.none,
            ),
            onChanged: (s) => setState(() => query = s.trim().toLowerCase()),
          ),
        ),
      ],
    ),
  );

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) {
      final p = project;
      if (p == null) {
        // Deleted: go back to the project list.
        if (!_closing) {
          _closing = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) Navigator.of(context).maybePop();
          });
        }
        return const Scaffold();
      }
      // Overtime, Attendance and Sea Can Movements are full pages, not lists.
      // A tab hidden while it was open goes back to Checklists.
      if (!visibleTabs(Store.instance.hiddenTabs).contains(mode)) mode = ProjectTab.checklists;
      final ot = mode == ProjectTab.overtime || mode == ProjectTab.attendance || mode == ProjectTab.seaCans;
      final rows = ot ? const <Widget>[] : _rows(p);
      return Scaffold(
        body: SafeArea(
          child: Stack(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    height: 64,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                      child: Row(
                        children: [
                          IconButton(
                            iconSize: 28,
                            tooltip: 'Back',
                            icon: const Icon(Icons.arrow_back, color: C.text),
                            onPressed: () => Navigator.of(context).maybePop(),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              p.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(color: C.text, fontSize: 26),
                            ),
                          ),
                          IconButton(
                            iconSize: 28,
                            tooltip: 'Project options',
                            icon: const Icon(Icons.more_horiz, color: C.text),
                            onPressed: _projectMenu,
                          ),
                        ],
                      ),
                    ),
                  ),
                  if (p.location.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(68, 0, 20, 0),
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
                  _pills(),
                  if (!ot) _searchBox(),
                  if (mode == ProjectTab.notes && p.notes.isNotEmpty)
                    Align(
                      alignment: Alignment.centerRight,
                      child: Padding(
                        padding: const EdgeInsets.only(top: 6, right: 20),
                        child: TextButton(
                          onPressed: _clearNotes,
                          style: TextButton.styleFrom(minimumSize: const Size(0, 44)),
                          child: const Text(
                            'Clear all notes',
                            style: TextStyle(color: C.red, fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),
                    ),
                  Expanded(
                    child: mode == ProjectTab.seaCans
                        ? SeaCanPane(project: p, onExport: _exportSeaCans)
                        : mode == ProjectTab.attendance
                        ? AttendancePane(project: p, onExport: _exportAttendance)
                        : ot
                        ? OvertimePane(project: p, onExport: _exportOvertime)
                        : rows.isEmpty
                        ? Center(
                            child: SingleChildScrollView(
                              padding: const EdgeInsets.all(32),
                              child: Text(
                                _emptyText(),
                                textAlign: TextAlign.center,
                                style: const TextStyle(color: C.grey, fontSize: 18),
                              ),
                            ),
                          )
                        : ListView(padding: const EdgeInsets.fromLTRB(20, 20, 20, 140), children: rows),
                  ),
                ],
              ),
              if (!ot) Positioned(left: 0, right: 0, bottom: 20, child: Center(child: AddPill(_fabLabel, _fab))),
            ],
          ),
        ),
      );
    },
  );
}
