// Keeps all data in memory and writes it to one JSON file (worklog.json, the same format as
// the Android app). Photos live in a photos folder next to it.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import 'models.dart';

class Store extends ChangeNotifier {
  Store._(this.dir);

  static late Store instance;

  final Directory dir;
  final List<Project> projects = [];
  final List<Link> links = [];
  String userName = '';

  /// Number the crew should call or text you on; added under your name in texts.
  String contactPhone = '';

  /// Your profile: the task colours on the zone sheet that are your trade's (dark blue by default).
  final Set<String> myColours = {'blue'};

  /// Your usual answers for the Observation & Coaching form (Formstack links), by question id.
  final Map<String, String> formAnswers = {};

  /// Project tabs this person has hidden ("notify", "links", "overtime", "notes", "seacans").
  final Set<String> hiddenTabs = {};

  File get dataFile => File('${dir.path}/worklog.json');
  Directory get photoDir => Directory('${dir.path}/photos');

  Future<void>? _writing;
  bool _again = false;

  /// Loads the saved data. [base] is only given in tests.
  ///
  /// On Android this is the app's "files" folder, the same place the earlier Android app kept
  /// worklog.json and photos, so installing this version as an update keeps everything.
  static Future<Store> open([Directory? base]) async {
    final d = base ?? (Platform.isAndroid ? await getApplicationSupportDirectory() : await getApplicationDocumentsDirectory());
    final s = Store._(d);
    await s.photoDir.create(recursive: true);
    await s._load();
    instance = s;
    return s;
  }

  /// Name stamped on completions and photos.
  String me() => userName.isEmpty ? 'Me' : userName;

  File photoFile(String name) => File('${photoDir.path}/$name');

  Project? project(String? id) {
    if (id == null) return null;
    for (final p in projects) {
      if (p.id == id) return p;
    }
    return null;
  }

  void deletePhoto(Photo p) {
    try {
      photoFile(p.file).deleteSync();
    } catch (_) {}
  }

  void deletePhotos(Iterable<Photo> photos) {
    for (final p in photos) {
      deletePhoto(p);
    }
  }

  void deleteTaskPhotos(Task t) => deletePhotos(t.photos);

  void deleteLogPhotos(DailyLog l) {
    for (final t in l.tasks) {
      deleteTaskPhotos(t);
    }
  }

  void deleteProjectPhotos(Project p) {
    for (final l in p.logs) {
      deleteLogPhotos(l);
    }
    for (final n in p.notices) {
      deletePhotos(n.photos);
    }
    for (final n in p.notes) {
      deletePhotos(n.photos);
    }
  }

  Future<void> _load() async {
    final f = dataFile;
    if (!await f.exists()) return;
    try {
      fromJson(jsonDecode(await f.readAsString()) as Map<String, dynamic>);
    } catch (_) {
      // Keep a copy of an unreadable file instead of overwriting it.
      await f.rename('${dir.path}/worklog-unreadable-${nowMs()}.json');
    }
  }

  /// Replaces everything with the data from a worklog.json (also used for restoring a backup).
  void fromJson(Map<String, dynamic> o) {
    projects.clear();
    links.clear();
    userName = (o['userName'] ?? '').toString();
    contactPhone = (o['contactPhone'] ?? '').toString();
    final mc = o['myColours'];
    if (mc is List) {
      myColours
        ..clear()
        ..addAll(mc.map((e) => e.toString()));
    }
    hiddenTabs
      ..clear()
      ..addAll([for (final t in (o['hiddenTabs'] as List? ?? const [])) t.toString()]);
    formAnswers.clear();
    final fd = o['formDetails'];
    if (fd is Map) fd.forEach((k, v) => formAnswers[k.toString()] = (v ?? '').toString());
    final a = o['projects'];
    if (a is List) {
      for (final p in a) {
        if (p is Map) projects.add(Project.fromJson(Map<String, dynamic>.from(p)));
      }
    }
    final ls = o['links'];
    if (ls is List) {
      for (final l in ls) {
        if (l is Map) links.add(Link.fromJson(Map<String, dynamic>.from(l)));
      }
    }
  }

  Map<String, dynamic> toJson() => {
        'version': 2,
        'userName': userName,
        'contactPhone': contactPhone,
        'myColours': myColours.toList(),
        'formDetails': formAnswers,
        'hiddenTabs': hiddenTabs.toList(),
        'projects': [for (final p in projects) p.toJson()],
        'links': [for (final l in links) l.toJson()],
      };

  /// Saves in the background and tells the screens to redraw.
  void save() {
    notifyListeners();
    final json = jsonEncode(toJson());
    if (_writing != null) {
      _again = true;
      _pending = json;
      return;
    }
    _writing = _write(json);
  }

  String? _pending;

  Future<void> _write(String json) async {
    try {
      final tmp = File('${dataFile.path}.tmp');
      await tmp.writeAsString(json, flush: true);
      await tmp.rename(dataFile.path);
    } catch (_) {}
    if (_again && _pending != null) {
      final next = _pending!;
      _again = false;
      _pending = null;
      await _write(next);
    }
    _writing = null;
  }

  /// Waits for any save in progress (used before backups and in tests).
  Future<void> flush() async {
    while (_writing != null) {
      await _writing;
    }
  }

  /// Redraw without saving.
  void changed() => notifyListeners();
}
