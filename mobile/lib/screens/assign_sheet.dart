// "Assign Users" sheet: search the project crew, tick people, add new crew members.
// Also Manage crew: groups (Crew / Techs / Supervision) and who gets assignment texts.
import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/forms.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

/// Crew grouped Crew, Techs, Supervision, filtered by [query] (any part of the name, any case).
/// Groups with nobody in them are left out, unless [empty].
List<MapEntry<String, List<String>>> crewGroups(Project p, String query, {bool empty = false}) {
  final q = query.trim().toLowerCase();
  final out = <MapEntry<String, List<String>>>[];
  for (final role in p.allRoles) {
    final group = [
      for (final name in p.crew)
        if (p.roleOf(name) == role && (q.isEmpty || name.toLowerCase().contains(q))) name,
    ];
    if (group.isNotEmpty || empty) out.add(MapEntry(role, group));
  }
  return out;
}

/// True unless someone on the crew already has exactly this name (any case).
bool offerAdd(Project p, String query) {
  final q = query.trim().toLowerCase();
  return !p.crew.any((n) => n.toLowerCase() == q);
}

/// Saves a new crew member's details. Ticks them when [selected] is given.
void addCrewMember(Project p, CrewDetails d, Set<String>? selected) {
  if (!p.crew.contains(d.name)) p.crew.add(d.name);
  selected?.add(d.name);
  if (d.phone.isNotEmpty) p.phones[d.name] = d.phone;
  _setDetails(p, d);
  if (d.texts) {
    p.assignTexts.add(d.name);
  } else {
    p.assignTexts.remove(d.name);
  }
}

/// Saves edited details for [name]. Returns false (and changes nothing) if the new name is
/// someone else already on the crew.
bool updateCrewMember(Store store, Project p, String name, CrewDetails d) {
  if (d.name != name && p.crew.contains(d.name)) return false;
  p.renamePerson(name, d.name);
  if (d.phone.isEmpty) {
    p.phones.remove(d.name);
  } else {
    p.phones[d.name] = d.phone;
  }
  _setDetails(p, d);
  if (d.texts) {
    p.assignTexts.add(d.name);
  } else {
    p.assignTexts.remove(d.name);
  }
  if (store.userName == name) store.userName = d.name;
  return true;
}

void _setDetails(Project p, CrewDetails d) {
  p.roles[d.name] = d.role;
  for (final (m, v) in [
    (p.levels, d.level),
    (p.trades, d.trade),
    (p.unionLocals, d.local),
    (p.employeeNumbers, d.employeeNo),
    (p.bpNumbers, d.bp),
    (p.disnNumbers, d.disn),
  ]) {
    if (v.trim().isEmpty) {
      m.remove(d.name);
    } else {
      m[d.name] = v.trim();
    }
  }
}

/// The details saved for [name], for the crew box.
CrewDetails crewDetailsOf(Project p, String name) => CrewDetails(
      name,
      p.phoneOf(name),
      p.roleOf(name),
      p.assignTexts.contains(name),
      level: p.levels[name] ?? '',
      trade: p.trades[name] ?? '',
      local: p.unionLocals[name] ?? '',
      employeeNo: p.employeeNumbers[name] ?? '',
      bp: p.bpNumbers[name] ?? '',
      disn: p.disnNumbers[name] ?? '',
    );

/// "Assign Users" sheet. Returns the people ticked, or null if cancelled.
/// [selectAll] adds a "Select all" row (the Notify "Send to" list).
Future<List<String>?> showAssignSheet(BuildContext context, Project project, List<String> current, String title,
        {bool selectAll = false}) =>
    _show(context, project, current, title, false, selectAll: selectAll);

/// Manage crew: groups and who gets assignment texts. Saves as you go.
Future<void> showManageCrew(BuildContext context, Project project) async {
  await _show(context, project, const [], 'Manage Crew', true);
}

Future<List<String>?> _show(BuildContext context, Project project, List<String> current, String title, bool manage,
    {bool selectAll = false}) {
  final store = Store.instance;
  if (store.userName.isNotEmpty && !project.crew.contains(store.userName)) {
    project.crew.insert(0, store.userName);
    store.save();
  }
  return showModalBottomSheet<List<String>>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (c) =>
        _AssignSheet(project: project, current: current, title: title, manage: manage, selectAll: selectAll),
  );
}

class _AssignSheet extends StatefulWidget {
  final Project project;
  final List<String> current;
  final String title;
  final bool manage, selectAll;

  const _AssignSheet({
    required this.project,
    required this.current,
    required this.title,
    required this.manage,
    this.selectAll = false,
  });

  @override
  State<_AssignSheet> createState() => _AssignSheetState();
}

class _AssignSheetState extends State<_AssignSheet> {
  late final Set<String> selected = {...widget.current};
  final search = TextEditingController();
  final keys = <String, GlobalKey>{};
  String? justAdded;

  Project get p => widget.project;
  Store get store => Store.instance;

  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  void _render() => setState(() {});

  Future<void> _add(String q) async {
    final d = await promptCrew(context, p, Fmt.capitalize(q));
    if (d == null) return;
    addCrewMember(p, d, widget.manage ? null : selected);
    store.save();
    justAdded = d.name;
    search.clear();
    _render();
    // Bring a newly added person into view, wherever their group is.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final k = keys[justAdded];
      justAdded = null;
      final ctx = k?.currentContext;
      if (ctx != null) Scrollable.ensureVisible(ctx, duration: const Duration(milliseconds: 200));
    });
  }

  Future<void> _remove(String name) => confirm(
    context,
    'Remove $name from the crew?',
    "They'll stay on fields they're already assigned to.",
    'Remove',
    () {
      p.crew.remove(name);
      selected.remove(name);
      store.save();
      _render();
    },
  );

  Future<void> _editPhone(String name) async {
    final n = await promptPhone(context, 'Phone number for $name', p.phoneOf(name));
    if (n == null) return;
    p.phones[name] = n;
    store.save();
    _render();
  }

  Future<void> _editPerson(String name) async {
    final d = await crewDialog(context, p, name, 'Save', crewDetailsOf(p, name), () => _remove(name));
    if (d == null || !mounted) return;
    if (!updateCrewMember(store, p, name, d)) {
      await showDialog<void>(
        context: context,
        builder: (c) => AlertDialog(
          content: Text('${d.name} is already on the crew'),
          actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('OK'))],
        ),
      );
      return;
    }
    store.save();
    _render();
  }

  // ----- pasting employee, BP and DISN numbers -----

  Future<void> _pasteNumbers() async {
    final box = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Paste crew numbers'),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Text(
              'Copy the rows from your crew list (for example in Excel) and paste them here. Each row needs the '
              "person's name and their numbers. The first row can be the headings.",
              style: TextStyle(color: C.grey, fontSize: 15),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: box,
              minLines: 6,
              maxLines: 12,
              decoration: const InputDecoration(
                hintText: 'Name | Employee # | BP # | DISN #',
                border: OutlineInputBorder(),
              ),
            ),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Add numbers')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final rows = CrewNumbers.parse(box.text);
    final result = CrewNumbers.apply(p, rows);
    store.save();
    _render();
    final n = result.updated.length;
    final add = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        content: Text(
          'Numbers added for $n ${n == 1 ? 'person' : 'people'}.'
          '${result.missing.isEmpty ? '' : "\n\nNot on this project's crew: ${result.missing.map((r) => r.name).join(', ')}."}',
        ),
        actions: [
          if (result.missing.isNotEmpty)
            TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Add them to the crew')),
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('OK')),
        ],
      ),
    );
    if (add != true) return;
    CrewNumbers.addMissing(p, result.missing);
    store.save();
    _render();
  }

  // ----- groups -----

  Future<void> _addGroup() async {
    final n = await prompt(context, 'New group', 'e.g. Night shift', null, 'Add',
        caps: TextCapitalization.words,
        validate: (s) => s.trim().isEmpty || p.groupNameTaken(s) ? 'Enter a new group name' : null);
    if (n == null || n.trim().isEmpty) return;
    p.addGroup(n);
    store.save();
    _render();
  }

  Future<void> _renameGroup(String id) async {
    final g = p.findGroup(id);
    if (g == null) return;
    final n = await prompt(context, 'Rename group', 'Group name', g.name, 'Save',
        caps: TextCapitalization.words,
        validate: (s) => s.trim().isEmpty || p.groupNameTaken(s, except: id) ? 'Enter a new group name' : null);
    if (n == null || n.trim().isEmpty) return;
    g.name = n.trim();
    store.save();
    _render();
  }

  void _deleteGroup(String id) {
    final g = p.findGroup(id);
    if (g == null || p.groups.length < 2) return;
    final to = p.groups.firstWhere((x) => x.id != id).name;
    confirm(context, 'Delete the ${g.name} group?', 'Everyone in it moves to $to.', 'Delete', () {
      p.deleteGroup(id);
      store.save();
      _render();
    });
  }

  void _moveTo(String name, String groupId) {
    if (p.roleOf(name) == groupId) return;
    p.roles[name] = groupId;
    store.save();
    _render();
  }

  /// One group in Manage Crew: people can be dragged onto it.
  Widget _dropGroup(String id, List<Widget> children) => DragTarget<String>(
        onWillAcceptWithDetails: (d) => p.roleOf(d.data) != id,
        onAcceptWithDetails: (d) => _moveTo(d.data, id),
        builder: (c, hovering, _) => AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          color: hovering.isNotEmpty ? C.sky : Colors.transparent,
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            ...children,
            if (children.length == 1)
              const Padding(
                padding: EdgeInsets.fromLTRB(24, 4, 24, 12),
                child: Text('Nobody in this group yet', style: TextStyle(color: C.greyLight, fontSize: 15)),
              ),
          ]),
        ),
      );

  Widget _manageHeading(String id) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 8, 8, 0),
        child: Row(children: [
          Expanded(
            child: Text(
              p.roleLabel(id).toUpperCase(),
              style: const TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.8, fontWeight: FontWeight.bold),
            ),
          ),
          IconButton(
            tooltip: 'Rename group',
            icon: const Icon(Icons.edit_outlined, color: C.grey, size: 22),
            onPressed: () => _renameGroup(id),
          ),
          if (p.groups.length > 1)
            IconButton(
              tooltip: 'Delete group',
              icon: const Icon(Icons.delete_outline, color: C.red, size: 22),
              onPressed: () => _deleteGroup(id),
            ),
        ]),
      );

  /// Notify: a group heading with its own "Select all".
  Widget _selectHeading(String id, List<String> names) {
    final all = names.every(selected.contains);
    void flip() => setState(() {
          if (all) {
            selected.removeAll(names);
          } else {
            selected.addAll(names);
          }
        });
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 12, 0),
      child: Row(children: [
        Expanded(
          child: Text(
            p.roleLabel(id).toUpperCase(),
            style: const TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.8, fontWeight: FontWeight.bold),
          ),
        ),
        InkWell(
          key: ValueKey('select-$id'),
          onTap: flip,
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Checkbox(value: all, onChanged: (_) => flip()),
            const Text('Select all', style: TextStyle(color: C.grey, fontSize: 15)),
          ]),
        ),
      ]),
    );
  }

  Widget _heading(String label) => Padding(
    padding: const EdgeInsets.fromLTRB(24, 16, 24, 4),
    child: Text(
      label.toUpperCase(),
      style: const TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.8, fontWeight: FontWeight.bold),
    ),
  );

  Widget _row(String name) {
    final number = p.phoneOf(name);
    final manage = widget.manage;
    final on = selected.contains(name);
    final Widget phone = manage
        ? Text(
            (number.isEmpty ? 'No phone number' : number) +
                (p.assignTexts.contains(name) ? '  ·  texted when assigned' : ''),
            style: const TextStyle(color: C.grey, fontSize: 15),
          )
        : InkWell(
            onTap: () => _editPhone(name),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Text(
                number.isEmpty ? '+ Add phone number' : '$number  ·  edit',
                style: TextStyle(color: number.isEmpty ? C.blue : C.grey, fontSize: 15),
              ),
            ),
          );
    return InkWell(
      key: keys.putIfAbsent(name, GlobalKey.new),
      onTap: manage
          ? () => _editPerson(name)
          : () => setState(() {
              if (!selected.remove(name)) selected.add(name);
            }),
      onLongPress: () => _remove(name),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 72),
        child: Padding(
          padding: EdgeInsets.fromLTRB(manage ? 20 : 16, 0, 20, 0),
          child: Row(
            children: [
              if (!manage)
                IgnorePointer(
                  child: Checkbox(value: on, onChanged: (_) {}),
                ),
              Container(
                width: 44,
                height: 44,
                margin: const EdgeInsets.only(left: 8),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                  border: Border.all(color: C.cardStroke),
                ),
                child: Text(Fmt.initials(name), style: const TextStyle(color: C.navy, fontSize: 14)),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(name, style: const TextStyle(color: C.text, fontSize: 20)),
                      phone,
                      if (manage && p.tradeLine(name).isNotEmpty)
                        Text(p.tradeLine(name), style: const TextStyle(color: C.grey, fontSize: 15)),
                      if (manage && p.numbersLine(name).isNotEmpty)
                        Text(p.numbersLine(name), style: const TextStyle(color: C.grey, fontSize: 14)),
                    ],
                  ),
                ),
              ),
              if (manage)
                Draggable<String>(
                  data: name,
                  feedback: Material(
                    elevation: 6,
                    borderRadius: BorderRadius.circular(24),
                    color: Colors.white,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
                      child: Text(name, style: const TextStyle(color: C.navy, fontSize: 19)),
                    ),
                  ),
                  child: const Tooltip(
                    message: 'Drag to another group',
                    child: Padding(
                      padding: EdgeInsets.all(10),
                      child: Icon(Icons.drag_indicator, color: C.grey, size: 28),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final q = search.text.trim();
    final rows = <Widget>[];
    final groups = crewGroups(p, q, empty: widget.manage);
    final shown = [for (final g in groups) ...g.value];
    if (widget.selectAll && !widget.manage && shown.isNotEmpty) {
      final all = shown.every(selected.contains);
      void flip() => setState(() {
            if (all) {
              selected.removeAll(shown);
            } else {
              selected.addAll(shown);
            }
          });
      rows.add(InkWell(
        onTap: flip,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          child: Row(children: [
            Checkbox(value: all, onChanged: (_) => flip()),
            const SizedBox(width: 4),
            const Text('Select all', style: TextStyle(color: C.navy, fontSize: 19, fontWeight: FontWeight.bold)),
          ]),
        ),
      ));
    }
    for (final g in groups) {
      if (widget.manage) {
        rows.add(_dropGroup(g.key, [_manageHeading(g.key), for (final name in g.value) _row(name)]));
      } else {
        rows.add(widget.selectAll ? _selectHeading(g.key, g.value) : _heading(p.roleLabel(g.key)));
        for (final name in g.value) {
          rows.add(_row(name));
        }
      }
    }
    if (widget.manage && q.isEmpty) {
      rows.add(InkWell(
        onTap: _addGroup,
        child: const Padding(
          padding: EdgeInsets.symmetric(horizontal: 24, vertical: 18),
          child: Text('+  Add group', style: TextStyle(color: C.blue, fontSize: 19)),
        ),
      ));
      rows.add(InkWell(
        onTap: _pasteNumbers,
        child: const Padding(
          padding: EdgeInsets.symmetric(horizontal: 24, vertical: 18),
          child: Text('Paste crew numbers', style: TextStyle(color: C.blue, fontSize: 19)),
        ),
      ));
    }
    if (offerAdd(p, q)) {
      rows.add(
        InkWell(
          onTap: () => _add(q),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
            child: Text(
              q.isEmpty ? '+  Add crew member' : '+  Add “$q” to crew',
              style: const TextStyle(color: C.blue, fontSize: 19),
            ),
          ),
        ),
      );
    }
    final bold = const TextStyle(fontSize: 19, fontWeight: FontWeight.bold);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.92),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 44,
              height: 5,
              margin: const EdgeInsets.only(top: 14),
              decoration: BoxDecoration(color: C.cardStroke, borderRadius: BorderRadius.circular(3)),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 16, 12, 8),
              child: Row(
                children: [
                  Visibility(
                    visible: !widget.manage,
                    maintainSize: true,
                    maintainAnimation: true,
                    maintainState: true,
                    child: TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: Text('Cancel', style: bold.copyWith(color: C.navy)),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      widget.title,
                      textAlign: TextAlign.center,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: C.navy, fontSize: 22, fontWeight: FontWeight.bold),
                    ),
                  ),
                  TextButton(
                    onPressed: () => Navigator.pop(context, selected.toList()),
                    child: Text(widget.manage ? 'Done' : 'Assign', style: bold.copyWith(color: C.blue)),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 6, 20, 10),
              child: TextField(
                controller: search,
                textCapitalization: TextCapitalization.words,
                onChanged: (_) => _render(),
                style: const TextStyle(fontSize: 18),
                decoration: InputDecoration(
                  hintText: 'Search Users',
                  prefixIcon: const Icon(Icons.search, color: C.text),
                  filled: true,
                  fillColor: C.bg,
                  contentPadding: const EdgeInsets.symmetric(vertical: 18),
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
                ),
              ),
            ),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.only(bottom: 20),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: rows),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
