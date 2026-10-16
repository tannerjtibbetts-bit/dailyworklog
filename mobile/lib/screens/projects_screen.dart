// Home screen: the list of projects (e.g. "B44 Zone 2 Tool Readiness").
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../access/access.dart';
import '../data/backup.dart';
import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'approvals_screen.dart';
import 'intro_screen.dart';
import 'project_screen.dart';

/// 12265550123 -> 1-226-555-0123; 2265550123 -> 226-555-0123; anything else as typed.
String formatPhone(String phone) {
  final d = phone.replaceAll(RegExp(r'[^0-9]'), '');
  if (d.length == 11 && d.startsWith('1')) {
    return '1-${d.substring(1, 4)}-${d.substring(4, 7)}-${d.substring(7)}';
  }
  if (d.length == 10) {
    return '${d.substring(0, 3)}-${d.substring(3, 6)}-${d.substring(6)}';
  }
  return phone.trim();
}

/// Zone sheet task colours you can pick as yours (same ids as the zone sheet reader).
const myColourChoices = [
  ['blue', 'Dark blue'],
  ['orange', 'Orange (maintenance)'],
  ['pink', 'Pink (electrical)'],
];

/// Big dark "+" pill at the bottom of the list screens.
class AddPill extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const AddPill(this.label, this.onTap, {super.key});

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: label,
    child: Material(
      color: C.darkPill,
      elevation: 6,
      borderRadius: BorderRadius.circular(100),
      child: InkWell(
        borderRadius: BorderRadius.circular(100),
        onTap: onTap,
        child: SizedBox(
          width: 156,
          height: 72,
          child: Icon(
            Icons.add,
            color: Colors.white,
            size: 34,
            semanticLabel: label,
          ),
        ),
      ),
    ),
  );
}

class ProjectsScreen extends StatefulWidget {
  const ProjectsScreen({super.key});

  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  Store get store => Store.instance;

  @override
  void initState() {
    super.initState();
    if (store.userName.isEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _askName();
      });
    }
  }

  String _subtitle() => store.userName.isEmpty
      ? 'Tap the person icon to set your name'
      : 'Signed in as ${store.userName}'
            '${store.contactPhone.isEmpty ? '' : '  ·  ${formatPhone(store.contactPhone)}'}';

  /// Your name, plus the number crew should reach you on (added to texts sent from this phone).
  Future<void> _askName() async {
    final name = TextEditingController(text: store.userName);
    final phone = TextEditingController(text: store.contactPhone);
    final picked = {
      for (final c in myColourChoices) c[0]: store.myColours.contains(c[0]),
    };
    String? error;
    final result = await showDialog<String>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: const Text('Your profile'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextField(
                  controller: name,
                  autofocus: true,
                  textCapitalization: TextCapitalization.words,
                  decoration: InputDecoration(
                    hintText: 'Your name',
                    errorText: error,
                  ),
                ),
                TextField(
                  controller: phone,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    hintText: 'Number crew should call or text you on',
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Your name is shown on completed fields and photos. The number is added under your name in texts.',
                  style: TextStyle(color: C.grey, fontSize: 14),
                ),
                const Padding(
                  padding: EdgeInsets.only(top: 16, bottom: 2),
                  child: Text(
                    'My task colours on the zone sheet',
                    style: TextStyle(color: C.navy, fontSize: 16),
                  ),
                ),
                for (final col in myColourChoices)
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    controlAffinity: ListTileControlAffinity.leading,
                    dense: true,
                    value: picked[col[0]],
                    onChanged: (v) => set(() => picked[col[0]] = v ?? false),
                    title: Text(col[1], style: const TextStyle(fontSize: 16)),
                  ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(c, 'more'),
              child: const Text('More…'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(c),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                if (name.text.trim().isEmpty) {
                  set(() => error = 'Enter your name');
                  return;
                }
                Navigator.pop(c, 'save');
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
    if (!mounted) return;
    if (result == 'more') {
      _profileMore();
    } else if (result == 'save') {
      store.userName = name.text.trim();
      store.contactPhone = phone.text.trim();
      store.myColours
        ..clear()
        ..addAll([
          for (final c in myColourChoices)
            if (picked[c[0]] == true) c[0],
        ]);
      store.save();
    }
  }

  /// Approvals (app owner) and Sign out.
  Future<void> _profileMore() async {
    final owner = await Access.isOwner();
    final pin =
        await Access.hasPin(); // Sign out only means something with a PIN set
    if (!mounted) return;
    await menu(context, null, [
      SheetItem(
        Icons.person_add_alt_1,
        owner ? 'Approvals / hand over' : 'App owner / Approvals',
        () {
          Navigator.of(context)
              .push(MaterialPageRoute(builder: (_) => const ApprovalsScreen()));
        },
      ),
      SheetItem(Icons.save_alt, 'Back up all my work', _backup),
      SheetItem(Icons.restore, 'Restore from a backup', _restore),
      if (pin)
        SheetItem(Icons.logout, 'Sign out', () async {
          await Access.signOut();
          if (!mounted) return;
          Navigator.of(context).pushAndRemoveUntil(
            MaterialPageRoute(builder: (_) => const IntroScreen()),
            (_) => false,
          );
        }),
    ]);
  }

  /// Everything (projects and photos) in one .zip, shared so it can be saved or sent to a new phone.
  Future<void> _backup() async {
    final zip = await _busy(
      'Making your backup\u2026',
      () => Backup.create(store),
    );
    if (!mounted) return;
    if (zip == null) {
      toast(context, "Couldn't make the backup. Please try again.", long: true);
      return;
    }
    final box = context.findRenderObject() as RenderBox?;
    await SharePlus.instance.share(
      ShareParams(
        files: [XFile(zip.path, mimeType: 'application/zip')],
        subject: 'Daily Work Log backup',
        text: 'Daily Work Log backup. Save it somewhere safe, or send it to your new phone.',
        sharePositionOrigin: box == null
            ? null
            : box.localToGlobal(Offset.zero) & box.size,
      ),
    );
  }

  /// Replaces everything on this phone with a backup .zip, after showing what's in it.
  Future<void> _restore() async {
    List<PlatformFile> picked;
    try {
      picked = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['zip'],
      );
    } catch (_) {
      picked = [];
    }
    final path = picked.isEmpty ? null : picked.first.path;
    if (path == null || !mounted) return;
    final zip = File(path);
    final info = await Backup.inspect(zip);
    if (!mounted) return;
    if (info == null) {
      toast(context, "That file isn't a Daily Work Log backup", long: true);
      return;
    }
    final from = info.userName.isEmpty ? '' : ' from ${info.userName}';
    await confirm(
      context,
      'Restore this backup?',
      'Backup$from: ${Fmt.plural(info.projects, 'project', 'projects')}, '
          '${Fmt.plural(info.photos, 'photo', 'photos')}.\n\n'
          'This replaces everything on this phone with the backup. It can\'t be undone.',
      'Restore',
      () async {
        final ok = await _busy(
          'Restoring\u2026',
          () => Backup.restore(store, zip),
        );
        if (!mounted) return;
        toast(
          context,
          ok == true ? 'Backup restored' : "Couldn't restore that backup",
          long: true,
        );
      },
    );
  }

  /// Runs [job] behind a "please wait" box. Null if it failed.
  Future<T?> _busy<T>(String message, Future<T> Function() job) async {
    final nav = Navigator.of(context, rootNavigator: true);
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      useRootNavigator: true,
      builder: (_) =>
          PopScope(canPop: false, child: AlertDialog(content: Text(message))),
    );
    T? result;
    try {
      result = await job();
    } catch (_) {
      result = null;
    }
    nav.pop();
    return result;
  }

  Future<void> _editProject(Project? existing) async {
    final name = TextEditingController(text: existing?.name ?? '');
    final loc = TextEditingController(text: existing?.location ?? '');
    String? error;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) {
          void submit() {
            if (name.text.trim().isEmpty) {
              set(() => error = 'Enter a project name');
              return;
            }
            Navigator.pop(c, true);
          }

          return AlertDialog(
            title: Text(existing == null ? 'New project' : 'Edit project'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: name,
                  autofocus: true,
                  textCapitalization: TextCapitalization.words,
                  decoration: InputDecoration(
                    hintText: 'Project name, e.g. B44 Zone 2 Tool Readiness',
                    errorText: error,
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: loc,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(
                    hintText: 'Location, e.g. Kincardine, ON',
                  ),
                  onSubmitted: (_) => submit(),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(c, false),
                child: const Text('Cancel'),
              ),
              TextButton(
                onPressed: submit,
                child: Text(existing == null ? 'Create' : 'Save'),
              ),
            ],
          );
        },
      ),
    );
    if (ok != true || !mounted) return;
    var p = existing;
    if (p == null) {
      p = Project();
      store.projects.insert(0, p);
    }
    p.name = name.text.trim();
    p.location = loc.text.trim();
    store.save();
    if (existing == null) _open(p);
  }

  void _open(Project p) => Navigator.of(context)
      .push(MaterialPageRoute(builder: (_) => ProjectScreen(projectId: p.id)));

  void _projectMenu(Project p) {
    menu(context, p.name, [
      SheetItem(
        Icons.edit_outlined,
        'Edit name and location',
        () => _editProject(p),
      ),
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

  Widget _card(Project p) {
    final latest = p.latestLog();
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Material(
        color: Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(22),
          side: const BorderSide(color: C.cardStroke),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: () => _open(p),
          onLongPress: () => _projectMenu(p),
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Row(
              children: [
                Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: C.sky,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Icon(
                    Icons.folder_outlined,
                    color: C.deepBlue,
                    size: 26,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        p.name,
                        style: const TextStyle(
                          color: C.navy,
                          fontSize: 21,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      if (p.location.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Row(
                            children: [
                              const Icon(
                                Icons.place_outlined,
                                size: 16,
                                color: C.grey,
                              ),
                              const SizedBox(width: 4),
                              Flexible(
                                child: Text(
                                  p.location,
                                  style: const TextStyle(
                                    color: C.grey,
                                    fontSize: 15,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          latest == null
                              ? 'No daily logs yet'
                              : '${Fmt.plural(p.logs.length, 'daily log', 'daily logs')}, latest ${Fmt.logDate(latest.date)}',
                          style: const TextStyle(color: C.grey, fontSize: 15),
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right, color: C.navy, size: 28),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: ListenableBuilder(
        listenable: store,
        builder: (context, _) => Stack(
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 20, 12, 0),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Text(
                          'Projects',
                          style: TextStyle(
                            color: C.navy,
                            fontSize: 34,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      SizedBox(
                        width: 52,
                        height: 52,
                        child: Material(
                          color: C.chip,
                          shape: const CircleBorder(),
                          child: IconButton(
                            tooltip: 'Your name',
                            icon: const Icon(
                              Icons.person_outline,
                              color: C.blue,
                            ),
                            onPressed: _askName,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 2, 24, 0),
                  child: Text(
                    _subtitle(),
                    style: const TextStyle(color: C.grey, fontSize: 16),
                  ),
                ),
                Expanded(
                  child: store.projects.isEmpty
                      ? const Center(
                          child: Padding(
                            padding: EdgeInsets.all(32),
                            child: Text(
                              'No projects yet.\nTap + to create your first one.',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: C.grey, fontSize: 18),
                            ),
                          ),
                        )
                      : ListView(
                          padding: const EdgeInsets.fromLTRB(20, 20, 20, 140),
                          children: [for (final p in store.projects) _card(p)],
                        ),
                ),
              ],
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 20,
              child: Center(
                child: AddPill('New project', () => _editProject(null)),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
