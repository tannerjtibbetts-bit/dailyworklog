// Rename areas: a project's own names for South, North and Unzoned (e.g. "Garage 1"), so the checklist
// fits the work front. The colours stay the same; reports use the new names.
import 'package:flutter/material.dart';

import '../data/models.dart';
import '../data/store.dart';
import '../ui/theme.dart';

/// The card colour of each area, as on the checklist.
const areaColours = {Task.south: C.southBg, Task.north: C.northBg, Task.unzoned: C.unzonedBg};
const _areaStrokes = {Task.south: C.southStroke, Task.north: C.northStroke, Task.unzoned: C.unzonedStroke};

/// Opens the Rename areas box for [p]. Returns true if the names changed (and saved).
Future<bool> renameAreas(BuildContext context, Project p) async {
  final boxes = {
    for (final a in Task.areas) a: TextEditingController(text: p.areaNames[a] ?? ''),
  };
  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: const Text('Rename areas'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Give the areas names that fit your work, like Garage 1 or Location 2. '
              'The colours stay the same. Leave a box empty to keep the usual name.',
              style: TextStyle(color: C.grey, fontSize: 15),
            ),
            for (final a in Task.areas)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Row(children: [
                  Container(
                    width: 28,
                    height: 28,
                    decoration: BoxDecoration(
                      color: areaColours[a],
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: _areaStrokes[a]!, width: 2),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: boxes[a],
                      maxLength: 24,
                      textCapitalization: TextCapitalization.words,
                      decoration: InputDecoration(
                        labelText: '${Task.sideLabel(a)} area',
                        hintText: Task.sideLabel(a),
                        counterText: '',
                      ),
                    ),
                  ),
                ]),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () {
            for (final b in boxes.values) {
              b.clear();
            }
          },
          child: const Text('Use usual names'),
        ),
        TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
        TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Save')),
      ],
    ),
  );
  if (ok != true) return false;
  final names = {
    for (final a in Task.areas)
      if (boxes[a]!.text.trim().isNotEmpty) a: boxes[a]!.text.trim(),
  };
  p.areaNames = names;
  Store.instance.save();
  return true;
}

/// A small "Rename areas" button for next to the area choices.
Widget renameAreasButton(BuildContext context, Project p, {VoidCallback? changed}) => TextButton.icon(
  style: TextButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6), minimumSize: const Size(0, 36)),
  onPressed: () async {
    if (await renameAreas(context, p)) changed?.call();
  },
  icon: const Icon(Icons.edit_outlined, size: 18),
  label: const Text('Rename areas', style: TextStyle(fontSize: 15)),
);
