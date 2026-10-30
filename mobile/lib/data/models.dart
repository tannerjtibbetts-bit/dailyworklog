// Plain data classes. Everything is saved as one JSON file by Store, in the same
// format as the Android app (worklog.json), so data can move between the two.
import 'package:uuid/uuid.dart';

import 'fmt.dart';

const _uuid = Uuid();

String newId() => _uuid.v4();
int nowMs() => DateTime.now().millisecondsSinceEpoch;

String _s(Map<String, dynamic> o, String k, [String d = '']) {
  final v = o[k];
  return v == null ? d : v.toString();
}

int _i(Map<String, dynamic> o, String k) {
  final v = o[k];
  if (v is int) return v;
  if (v is num) return v.toInt();
  return int.tryParse('${v ?? ''}') ?? 0;
}

bool _b(Map<String, dynamic> o, String k) {
  final v = o[k];
  return v is bool ? v : v.toString() == 'true';
}

List<String> _strings(dynamic a) {
  if (a is! List) return [];
  return [for (final s in a) if (s != null && s.toString().isNotEmpty) s.toString()];
}

List<T> _objects<T>(dynamic a, T Function(Map<String, dynamic>) f) {
  if (a is! List) return [];
  return [for (final o in a) if (o is Map) f(Map<String, dynamic>.from(o))];
}

Map<String, String> _stringMap(dynamic m) {
  if (m is! Map) return {};
  final out = <String, String>{};
  m.forEach((k, v) {
    if (v != null) out[k.toString()] = v.toString();
  });
  return out;
}

class Photo {
  String file = '';
  String addedBy = '';
  int addedAt = 0;

  Photo({this.file = '', this.addedBy = '', this.addedAt = 0});

  Map<String, dynamic> toJson() => {'file': file, 'addedBy': addedBy, 'addedAt': addedAt};

  static Photo fromJson(Map<String, dynamic> o) =>
      Photo(file: _s(o, 'file'), addedBy: _s(o, 'addedBy'), addedAt: _i(o, 'addedAt'));
}

class Task {
  static const south = 'south', north = 'north', unzoned = 'unzoned';

  String id = newId();
  String title = '';
  String notes = '';
  bool done = false;
  String completedBy = '';
  int completedAt = 0;
  String createdBy = '';
  int createdAt = nowMs();
  bool photosRequired = false;

  /// Load securement confirmed. Only printed on reports when true.
  bool loadSecured = false;
  String securedBy = '';
  int securedAt = 0;

  /// Marked to carry over to the next shift's checklist; shown on the report.
  bool nextShift = false;

  /// south, north, unzoned or '' (not set). Reports list South, then North, then Unzoned.
  String side = '';
  List<String> assignees = [];
  List<Photo> photos = [];

  /// Load securement photos (taken in the Load securement pop-up and emailed).
  List<Photo> securedPhotos = [];

  /// Load securement photos go in the report too (the user can untick this).
  bool securedPhotosInReport = true;

  /// Who gets a text when this field is completed, and their number (as typed). Empty: nobody.
  String doneTextName = '';
  String doneTextPhone = '';

  static String sideLabel(String side) => side == south
      ? 'South'
      : side == north
          ? 'North'
          : side == unzoned
              ? 'Unzoned'
              : '';

  /// South first, then North, then Unzoned, then fields with no area (checklist and report order).
  static int areaRank(String side) => side == south
      ? 0
      : side == north
          ? 1
          : side == unzoned
              ? 2
              : 3;

  /// Fields grouped for the report: South, North, Unzoned, then any without an area.
  /// Returns one group with no heading if no areas are set.
  static List<MapEntry<String, List<Task>>> reportGroups(List<Task> tasks) {
    final s = <Task>[], n = <Task>[], u = <Task>[], other = <Task>[];
    for (final t in tasks) {
      (t.side == south
              ? s
              : t.side == north
                  ? n
                  : t.side == unzoned
                      ? u
                      : other)
          .add(t);
    }
    if (s.isEmpty && n.isEmpty && u.isEmpty) return [MapEntry('', other)];
    return [
      if (s.isNotEmpty) MapEntry('South', s),
      if (n.isNotEmpty) MapEntry('North', n),
      if (u.isNotEmpty) MapEntry('Unzoned', u),
      if (other.isNotEmpty) MapEntry('Other', other),
    ];
  }

  /// Same field with a clean slate: for templates, copies and duplicates.
  Task freshCopy() => Task()
    ..title = title
    ..photosRequired = photosRequired
    ..assignees = List.of(assignees)
    ..side = side
    ..doneTextName = doneTextName
    ..doneTextPhone = doneTextPhone;

  /// Unfinished field rolled into the next day: keeps the notes too.
  Task carryCopy() => freshCopy()..notes = notes;

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'notes': notes,
        'done': done,
        'completedBy': completedBy,
        'completedAt': completedAt,
        'createdBy': createdBy,
        'createdAt': createdAt,
        'photosRequired': photosRequired,
        'loadSecured': loadSecured,
        'securedBy': securedBy,
        'securedAt': securedAt,
        'nextShift': nextShift,
        'side': side,
        'assignees': assignees,
        'photos': [for (final p in photos) p.toJson()],
        'securedPhotos': [for (final p in securedPhotos) p.toJson()],
        'securedPhotosInReport': securedPhotosInReport,
        'doneTextName': doneTextName,
        'doneTextPhone': doneTextPhone,
      };

  static Task fromJson(Map<String, dynamic> o) => Task()
    ..id = _s(o, 'id', newId())
    ..title = _s(o, 'title')
    ..notes = _s(o, 'notes')
    ..done = _b(o, 'done')
    ..completedBy = _s(o, 'completedBy')
    ..completedAt = _i(o, 'completedAt')
    ..createdBy = _s(o, 'createdBy')
    ..createdAt = _i(o, 'createdAt')
    ..photosRequired = _b(o, 'photosRequired')
    ..loadSecured = _b(o, 'loadSecured')
    ..securedBy = _s(o, 'securedBy')
    ..securedAt = _i(o, 'securedAt')
    ..nextShift = _b(o, 'nextShift')
    ..side = _s(o, 'side')
    ..assignees = _strings(o['assignees'])
    ..photos = _objects(o['photos'], Photo.fromJson)
    ..securedPhotos = _objects(o['securedPhotos'], Photo.fromJson)
    ..securedPhotosInReport = o['securedPhotosInReport'] is bool ? o['securedPhotosInReport'] as bool : true
    ..doneTextName = _s(o, 'doneTextName')
    ..doneTextPhone = _s(o, 'doneTextPhone');
}

/// A link used, note sent or template used, that can be included in the day's report.
class Extra {
  static const link = 'link', notify = 'notify', template = 'template', note = 'note';

  String id = newId();
  String type = link;

  /// id of the link, sent note or template it came from
  String refId = '';
  String title = '';
  String detail = '';
  String by = '';
  int at = 0;
  bool include = false;
  List<Photo> photos = [];

  String label() {
    switch (type) {
      case notify:
        return 'Note sent to $title';
      case template:
        return 'Template used: $title';
      case note:
        return title.isEmpty || title == 'Me' ? 'Note' : 'Note by $title';
      default:
        return 'Link used: $title';
    }
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'type': type,
        'refId': refId,
        'title': title,
        'detail': detail,
        'by': by,
        'at': at,
        'include': include,
        'photos': [for (final p in photos) p.toJson()],
      };

  static Extra fromJson(Map<String, dynamic> o) => Extra()
    ..id = _s(o, 'id', newId())
    ..type = _s(o, 'type', link)
    ..refId = _s(o, 'refId')
    ..title = _s(o, 'title')
    ..detail = _s(o, 'detail')
    ..by = _s(o, 'by')
    ..at = _i(o, 'at')
    ..include = _b(o, 'include')
    ..photos = _objects(o['photos'], Photo.fromJson);
}

class DailyLog {
  String id = newId();

  /// ISO date, yyyy-MM-dd.
  String date = '';

  /// Custom name; empty means "Daily work log dd/MM/yyyy".
  String name = '';
  int createdAt = nowMs();
  List<String> crew = [];
  List<Task> tasks = [];
  List<Extra> extras = [];

  Extra? findExtra(String refId) {
    for (final e in extras) {
      if (e.refId == refId) return e;
    }
    return null;
  }

  List<Extra> includedExtras() => extras.where((e) => e.include).toList()..sort((a, b) => a.at.compareTo(b.at));

  String displayTitle() => name.isEmpty ? 'Daily work log ${Fmt.logDate(date)}' : name;

  int doneCount() => tasks.where((t) => t.done).length;

  Task? findTask(String? id) {
    if (id == null) return null;
    for (final t in tasks) {
      if (t.id == id) return t;
    }
    return null;
  }

  /// Crew on the checklist, or everyone assigned to a field if no crew is set.
  List<String> people() {
    if (crew.isNotEmpty) return crew;
    final s = <String>{};
    for (final t in tasks) {
      s.addAll(t.assignees);
    }
    return s.toList();
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'date': date,
        'name': name,
        'createdAt': createdAt,
        'crew': crew,
        'tasks': [for (final t in tasks) t.toJson()],
        'extras': [for (final e in extras) e.toJson()],
      };

  static DailyLog fromJson(Map<String, dynamic> o) => DailyLog()
    ..id = _s(o, 'id', newId())
    ..date = _s(o, 'date')
    ..name = _s(o, 'name')
    ..createdAt = _i(o, 'createdAt')
    ..crew = _strings(o['crew'])
    ..tasks = _objects(o['tasks'], Task.fromJson)
    ..extras = _objects(o['extras'], Extra.fromJson);
}

class Template {
  String id = newId();
  String name = '';
  List<Task> tasks = [];

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'tasks': [for (final t in tasks) t.toJson()]};

  static Template fromJson(Map<String, dynamic> o) => Template()
    ..id = _s(o, 'id', newId())
    ..name = _s(o, 'name')
    ..tasks = _objects(o['tasks'], Task.fromJson);
}

/// A saved web link (tool portals, procedures, etc.). Shared across all projects.
class Link {
  String id = newId();
  String title = '';
  String url = '';

  Map<String, dynamic> toJson() => {'id': id, 'title': title, 'url': url};

  static Link fromJson(Map<String, dynamic> o) => Link()
    ..id = _s(o, 'id', newId())
    ..title = _s(o, 'title')
    ..url = _s(o, 'url');
}

/// A note (and photos) sent to crew from the Notify tab.
class Notice {
  String id = newId();
  List<String> to = [];
  String note = '';
  int sentAt = 0;
  List<Photo> photos = [];

  Map<String, dynamic> toJson() => {
        'id': id,
        'to': to,
        'note': note,
        'sentAt': sentAt,
        'photos': [for (final p in photos) p.toJson()],
      };

  static Notice fromJson(Map<String, dynamic> o) => Notice()
    ..id = _s(o, 'id', newId())
    ..to = _strings(o['to'])
    ..note = _s(o, 'note')
    ..sentAt = _i(o, 'sentAt')
    ..photos = _objects(o['photos'], Photo.fromJson);
}

/// A note with optional photos from the Notes tab. Can be ticked into a day's report.
class Note {
  String id = newId();
  String text = '';
  String by = '';
  int createdAt = nowMs();
  late int updatedAt = createdAt;
  List<Photo> photos = [];

  bool get isEmpty => text.trim().isEmpty && photos.isEmpty;

  Map<String, dynamic> toJson() => {
        'id': id,
        'text': text,
        'by': by,
        'createdAt': createdAt,
        'updatedAt': updatedAt,
        'photos': [for (final p in photos) p.toJson()],
      };

  static Note fromJson(Map<String, dynamic> o) {
    final n = Note()
      ..id = _s(o, 'id', newId())
      ..text = _s(o, 'text')
      ..by = _s(o, 'by')
      ..createdAt = _i(o, 'createdAt')
      ..photos = _objects(o['photos'], Photo.fromJson);
    n.updatedAt = o.containsKey('updatedAt') ? _i(o, 'updatedAt') : n.createdAt;
    return n;
  }
}

class OtRow {
  String name = '', fri = '', sat = '', sun = '';

  /// Hours willing to work, 1 to 10 (blank if not given).
  String hours = '';

  String day(int d) => d == 0 ? fri : d == 1 ? sat : sun;

  void setDay(int d, String v) {
    if (d == 0) {
      fri = v;
    } else if (d == 1) {
      sat = v;
    } else {
      sun = v;
    }
  }

  Map<String, dynamic> toJson() => {'name': name, 'fri': fri, 'sat': sat, 'sun': sun, 'hours': hours};

  static OtRow fromJson(Map<String, dynamic> o) => OtRow()
    ..name = _s(o, 'name')
    ..fri = _s(o, 'fri')
    ..sat = _s(o, 'sat')
    ..sun = _s(o, 'sun')
    ..hours = _s(o, 'hours');
}

/// Weekend overtime sign-up sheet: one row per crew member, Yes/No for Fri, Sat, Sun.
class Overtime {
  static const yes = 'Yes', no = 'No';

  /// ISO date of the Friday this sheet covers.
  String weekOf = '';
  bool include = false;
  List<OtRow> rows = [];

  /// Free-text notes shown under the table and printed with it.
  String notes = '';

  /// Rows that have someone picked, in order.
  List<OtRow> filled() => rows.where((r) => r.name.isNotEmpty).toList();

  int yesCount(int day) => filled().where((r) => r.day(day) == yes).length;

  /// Drops rows with no name and no answers.
  void removeEmptyRows() => rows.removeWhere(
      (r) => r.name.isEmpty && r.fri.isEmpty && r.sat.isEmpty && r.sun.isEmpty && r.hours.isEmpty);

  /// Fresh weekend: keep the names, clear the Yes/No answers and hours.
  void startWeekend(String friday) {
    weekOf = friday;
    for (final r in rows) {
      r.fri = '';
      r.sat = '';
      r.sun = '';
      r.hours = '';
    }
  }

  Map<String, dynamic> toJson() => {
        'weekOf': weekOf,
        'include': include,
        'notes': notes,
        'rows': [for (final r in rows) r.toJson()],
      };

  static Overtime fromJson(dynamic m) {
    if (m is! Map) return Overtime();
    final o = Map<String, dynamic>.from(m);
    return Overtime()
      ..weekOf = _s(o, 'weekOf')
      ..include = _b(o, 'include')
      ..notes = _s(o, 'notes')
      ..rows = _objects(o['rows'], OtRow.fromJson);
  }
}

class AttRow {
  String name = '';

  /// One of [Attendance.statuses], or '' if not answered.
  String status = '';

  /// A short note about this person today, e.g. "Left at 1pm for a doctor's appointment".
  String comment = '';

  bool get isEmpty => name.isEmpty && status.isEmpty && comment.trim().isEmpty;

  Map<String, dynamic> toJson() => {'name': name, 'status': status, 'comment': comment};

  static AttRow fromJson(Map<String, dynamic> o) => AttRow()
    ..name = _s(o, 'name')
    ..status = _s(o, 'status')
    ..comment = _s(o, 'comment');
}

/// Attendance sheet: one row per crew member with whether they're here today.
class Attendance {
  static const yes = 'Yes', no = 'No';
  static const statuses = [yes, no, 'AWOL', 'Vacation', 'Off', 'Training', 'D72', 'Step up', 'Late', 'Left early'];

  /// ISO date this sheet is for.
  String date = '';
  bool include = false;
  String notes = '';
  List<AttRow> rows = [];

  /// Rows that have someone picked, in order.
  List<AttRow> filled() => rows.where((r) => r.name.isNotEmpty).toList();

  int count(String status) => filled().where((r) => r.status == status).length;

  /// "Yes 5 · Vacation 1": the answers given, in the dropdown's order. Empty if none.
  String summary() => [
        for (final s in statuses)
          if (count(s) > 0) '$s ${count(s)}',
      ].join(' · ');

  /// Drops rows with no name, answer or comment.
  void removeEmptyRows() => rows.removeWhere((r) => r.isEmpty);

  /// A new day: keep the names and notes, clear the answers and comments.
  void startDay(String iso) {
    date = iso;
    for (final r in rows) {
      r.status = '';
      r.comment = '';
    }
  }

  Map<String, dynamic> toJson() => {
        'date': date,
        'include': include,
        'notes': notes,
        'rows': [for (final r in rows) r.toJson()],
      };

  static Attendance fromJson(dynamic m) {
    if (m is! Map) return Attendance();
    final o = Map<String, dynamic>.from(m);
    return Attendance()
      ..date = _s(o, 'date')
      ..include = _b(o, 'include')
      ..notes = _s(o, 'notes')
      ..rows = _objects(o['rows'], AttRow.fromJson);
  }
}

/// One sea can on the Sea Can Movements tab: where it was (letter, row and level), where it is now,
/// and its weight.
class SeaCanMove {
  String can = '', fromLetter = '', fromRow = '', fromLevel = '', toLetter = '', toRow = '', toLevel = '';
  String weight = '';

  bool get isEmpty =>
      can.trim().isEmpty &&
      fromLetter.isEmpty &&
      fromRow.isEmpty &&
      fromLevel.isEmpty &&
      toLetter.isEmpty &&
      toRow.isEmpty &&
      toLevel.isEmpty &&
      weight.trim().isEmpty;

  /// A location as one piece of text: letter and row, then " L" and the level,
  /// e.g. ("D", "33", "3") -> "D33 L3", ("", "12", "2") -> "12 L2", ("", "", "3") -> "L3".
  static String location(String letter, String row, String level) {
    final place = '$letter$row';
    if (level.isEmpty) return place;
    return place.isEmpty ? 'L$level' : '$place L$level';
  }

  /// Where it came from, e.g. "D33 L3" ("" when nothing is picked).
  String fromLocation() => location(fromLetter, fromRow, fromLevel);

  /// Where it is now, e.g. "E12 L1" ("" when nothing is picked).
  String toLocation() => location(toLetter, toRow, toLevel);

  Map<String, dynamic> toJson() => {
    'can': can,
    'fromLetter': fromLetter,
    'fromRow': fromRow,
    'fromLevel': fromLevel,
    'toLetter': toLetter,
    'toRow': toRow,
    'toLevel': toLevel,
    'weight': weight,
  };

  static SeaCanMove fromJson(Map<String, dynamic> o) => SeaCanMove()
    ..can = _s(o, 'can')
    ..fromLetter = _s(o, 'fromLetter')
    ..fromRow = _s(o, 'fromRow')
    ..fromLevel = _s(o, 'fromLevel')
    ..toLetter = _s(o, 'toLetter')
    ..toRow = _s(o, 'toRow')
    ..toLevel = _s(o, 'toLevel')
    ..weight = _s(o, 'weight');
}

class Project {
  static const crewRole = 'crew', techRole = 'tech', mgmtRole = 'mgmt';
  static const allRoles = [crewRole, techRole, mgmtRole];

  String id = newId();
  String name = '';
  String location = '';
  int createdAt = nowMs();
  List<String> crew = [];

  /// Crew phone numbers by name, used for texting.
  Map<String, String> phones = {};
  List<DailyLog> logs = [];
  List<Template> templates = [];
  List<Notice> notices = [];

  /// Notes tab, newest first.
  List<Note> notes = [];

  /// Who gets the emailed report.
  List<String> emails = [];
  Overtime overtime = Overtime();
  Attendance attendance = Attendance();

  /// Crew, Techs or Supervision, by name. Anyone not listed is Crew.
  Map<String, String> roles = {};

  /// People who get a text when assigned to a field. Nobody does unless ticked.
  Set<String> assignTexts = {};

  /// Sea Can Movements tab, one line per sea can.
  List<SeaCanMove> seaCans = [];

  /// Sea can list goes in the daily report (PDF, Word and email).
  bool seaCanInclude = false;

  /// Sea cans with a number typed in, in order.
  List<SeaCanMove> filledSeaCans() => seaCans.where((m) => m.can.trim().isNotEmpty).toList();

  static String roleLabel(String role) =>
      role == techRole ? 'Techs' : role == mgmtRole ? 'Supervision' : 'Crew';

  /// Renames someone everywhere they appear: crew, checklists, fields, notes sent, overtime and attendance.
  void renamePerson(String from, String to) {
    if (from == to) return;
    _replaceIn(crew, from, to);
    final ph = phones.remove(from);
    if (ph != null) phones[to] = ph;
    final r = roles.remove(from);
    if (r != null) roles[to] = r;
    if (assignTexts.remove(from)) assignTexts.add(to);
    for (final l in logs) {
      _replaceIn(l.crew, from, to);
      for (final t in l.tasks) {
        _replaceIn(t.assignees, from, to);
      }
    }
    for (final x in templates) {
      for (final t in x.tasks) {
        _replaceIn(t.assignees, from, to);
      }
    }
    for (final n in notices) {
      _replaceIn(n.to, from, to);
    }
    for (final row in overtime.rows) {
      if (row.name == from) row.name = to;
    }
    for (final row in attendance.rows) {
      if (row.name == from) row.name = to;
    }
  }

  static void _replaceIn(List<String> list, String from, String to) {
    final i = list.indexOf(from);
    if (i < 0) return;
    if (list.contains(to)) {
      list.removeAt(i);
    } else {
      list[i] = to;
    }
  }

  String roleOf(String name) => roles[name] ?? crewRole;

  String phoneOf(String name) => phones[name] ?? '';

  Note? findNote(String? id) {
    if (id == null) return null;
    for (final n in notes) {
      if (n.id == id) return n;
    }
    return null;
  }

  /// Keeps report entries for this note in step with its latest text and photos.
  void syncNote(Note n) {
    for (final l in logs) {
      final e = l.findExtra(n.id);
      if (e == null) continue;
      e.title = n.by;
      e.detail = n.text;
      e.photos = List.of(n.photos);
    }
  }

  /// Takes a deleted item off every checklist's report list.
  void forgetExtras(String refId) {
    for (final l in logs) {
      l.extras.removeWhere((e) => e.refId == refId);
    }
  }

  DailyLog? findLog(String? id) {
    if (id == null) return null;
    for (final l in logs) {
      if (l.id == id) return l;
    }
    return null;
  }

  /// The checklist for today's date (newest if there are several), else the most recent one.
  DailyLog? todayLog(String todayIso) {
    DailyLog? best;
    for (final l in logs) {
      if (l.date != todayIso) continue;
      if (best == null || l.createdAt > best.createdAt) best = l;
    }
    return best ?? latestLog();
  }

  /// Notes that something was used, on the given log (or today's).
  /// Keeps one entry per item per checklist; using it again updates the time.
  Extra? record(DailyLog? target, String todayIso, String type, String refId, String title, String detail,
      List<Photo>? photos, String by, int at) {
    final l = target ?? todayLog(todayIso);
    if (l == null) return null;
    var e = l.findExtra(refId);
    if (e == null) {
      e = Extra()..refId = refId;
      l.extras.add(e);
    }
    e
      ..type = type
      ..title = title
      ..detail = detail
      ..by = by
      ..at = at
      ..photos = photos == null ? [] : List.of(photos);
    return e;
  }

  /// Most recent log by date (ties broken by creation time).
  DailyLog? latestLog() {
    DailyLog? best;
    for (final l in logs) {
      if (best == null) {
        best = l;
        continue;
      }
      final c = l.date.compareTo(best.date);
      if (c > 0 || (c == 0 && l.createdAt > best.createdAt)) best = l;
    }
    return best;
  }

  /// Copy a checklist's fields (not its progress or photos) into a new log.
  DailyLog duplicate(DailyLog src, String isoDate, String me) {
    final l = DailyLog()
      ..date = isoDate
      ..crew = List.of(src.crew);
    for (final t in src.tasks) {
      l.tasks.add(t.freshCopy()..createdBy = me);
    }
    logs.add(l);
    return l;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'location': location,
        'createdAt': createdAt,
        'crew': crew,
        'logs': [for (final l in logs) l.toJson()],
        'templates': [for (final t in templates) t.toJson()],
        'phones': phones,
        'notices': [for (final n in notices) n.toJson()],
        'emails': emails,
        'overtime': overtime.toJson(),
        'attendance': attendance.toJson(),
        'roles': roles,
        'assignTexts': assignTexts.toList(),
        'notes': [for (final n in notes) n.toJson()],
        'seaCans': [for (final m in seaCans) m.toJson()],
        'seaCanInclude': seaCanInclude,
      };

  static Project fromJson(Map<String, dynamic> o) => Project()
    ..id = _s(o, 'id', newId())
    ..name = _s(o, 'name')
    ..location = _s(o, 'location')
    ..createdAt = _i(o, 'createdAt')
    ..crew = _strings(o['crew'])
    ..logs = _objects(o['logs'], DailyLog.fromJson)
    ..templates = _objects(o['templates'], Template.fromJson)
    ..phones = (_stringMap(o['phones'])..removeWhere((k, v) => v.isEmpty))
    ..emails = _strings(o['emails'])
    ..overtime = Overtime.fromJson(o['overtime'])
    ..attendance = Attendance.fromJson(o['attendance'])
    ..roles = _stringMap(o['roles'])
    ..assignTexts = _strings(o['assignTexts']).toSet()
    ..notes = _objects(o['notes'], Note.fromJson)
    ..notices = _objects(o['notices'], Notice.fromJson)
    ..seaCans = _objects(o['seaCans'], SeaCanMove.fromJson)
    ..seaCanInclude = _b(o, 'seaCanInclude');
}
