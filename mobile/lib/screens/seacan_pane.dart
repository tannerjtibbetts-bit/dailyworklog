// Sea Can Movements tab: one line per sea can. Type its identifier, then pick the row (1 to 40)
// and level (1 to 3) it's coming from and the row and level it's at now.
import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

const seaCanRows = 40, seaCanLevels = 3;

/// Lines shown to start with; more can be added (there's no limit).
const seaCanStartLines = 20;
const _blank = '–';
const _canFlex = 24, _cellFlex = 10;

/// Makes sure there are at least [seaCanStartLines] lines to type into.
void prepareSeaCans(Project p) {
  while (p.seaCans.length < seaCanStartLines) {
    p.seaCans.add(SeaCanMove());
  }
}

int seaCansEntered(Project p) => p.seaCans.where((m) => m.can.trim().isNotEmpty).length;

class SeaCanPane extends StatefulWidget {
  final Project project;

  /// The Export button: just this list, as PDF, Excel, CSV or Word.
  final VoidCallback onExport;

  const SeaCanPane({super.key, required this.project, required this.onExport});

  @override
  State<SeaCanPane> createState() => _SeaCanPaneState();
}

class _SeaCanPaneState extends State<SeaCanPane> {
  final Map<SeaCanMove, TextEditingController> _cans = {};
  final _scroll = ScrollController();

  Store get store => Store.instance;
  Project get p => widget.project;

  @override
  void dispose() {
    for (final c in _cans.values) {
      c.dispose();
    }
    _scroll.dispose();
    super.dispose();
  }

  TextEditingController _canFor(SeaCanMove m) => _cans.putIfAbsent(m, () => TextEditingController(text: m.can));

  void _addRows(int count) {
    for (var i = 0; i < count; i++) {
      p.seaCans.add(SeaCanMove());
    }
    store.save();
    // Bring the new lines into view.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  static final _cellDecoration = BoxDecoration(
    color: const Color(0xFFF2F7FD),
    border: Border.all(color: C.cardStroke),
    borderRadius: BorderRadius.circular(10),
  );

  /// Dropdown of 1 to [max], with a dash for not picked yet.
  Widget _dropdown(int max, String current, ValueChanged<String> onPick) {
    final items = [_blank, for (var i = 1; i <= max; i++) '$i'];
    final value = items.contains(current) ? current : _blank;
    TextStyle style(String v) => TextStyle(
      fontSize: 16,
      color: v == _blank ? C.greyLight : C.deepBlue,
      fontWeight: v == _blank ? FontWeight.normal : FontWeight.bold,
    );
    return Container(
      constraints: const BoxConstraints(minHeight: 44),
      decoration: _cellDecoration,
      padding: const EdgeInsets.only(left: 4),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          value: value,
          isExpanded: true,
          isDense: true,
          iconSize: 16,
          menuMaxHeight: 360,
          icon: const Icon(Icons.arrow_drop_down, color: C.grey),
          items: [
            for (final v in items)
              DropdownMenuItem(
                value: v,
                child: Center(child: Text(v, style: style(v))),
              ),
          ],
          selectedItemBuilder: (_) => [
            for (final v in items)
              Container(
                alignment: Alignment.center,
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Text(v, maxLines: 1, style: style(v)),
              ),
          ],
          onChanged: (v) {
            if (v == null || v == value) return;
            onPick(v == _blank ? '' : v);
            store.save();
          },
        ),
      ),
    );
  }

  Widget _canBox(SeaCanMove m) => TextField(
    controller: _canFor(m),
    textCapitalization: TextCapitalization.characters,
    autocorrect: false,
    enableSuggestions: false,
    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: C.text),
    decoration: InputDecoration(
      hintText: 'Sea can #',
      hintStyle: const TextStyle(color: C.greyLight, fontWeight: FontWeight.normal),
      isDense: true,
      filled: true,
      fillColor: const Color(0xFFF2F7FD),
      contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 13),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: C.cardStroke),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: C.blue),
      ),
    ),
    onChanged: (s) {
      final before = m.can.trim().isEmpty;
      m.can = s.trim();
      store.save();
      if (before != m.can.isEmpty) setState(() {}); // the count changes
    },
  );

  /// Sea can box, then four cells; [cells] has five widgets.
  Widget _row(List<Widget> cells) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: Row(
      children: [
        for (var i = 0; i < cells.length; i++)
          Expanded(
            flex: i == 0 ? _canFlex : _cellFlex,
            child: Padding(padding: const EdgeInsets.only(right: 4), child: cells[i]),
          ),
      ],
    ),
  );

  static Widget _label(String s) => Text(
    s,
    textAlign: TextAlign.center,
    style: const TextStyle(fontSize: 13, color: C.grey, fontWeight: FontWeight.bold),
  );

  Widget _line(SeaCanMove m) => KeyedSubtree(
    key: ObjectKey(m),
    child: _row([
      _canBox(m),
      _dropdown(seaCanRows, m.fromRow, (v) => m.fromRow = v),
      _dropdown(seaCanLevels, m.fromLevel, (v) => m.fromLevel = v),
      _dropdown(seaCanRows, m.toRow, (v) => m.toRow = v),
      _dropdown(seaCanLevels, m.toLevel, (v) => m.toLevel = v),
    ]),
  );

  Widget _action(String s, Color color, VoidCallback onTap) => Align(
    alignment: Alignment.centerLeft,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(8, 16, 8, 4),
        child: Text(s, style: TextStyle(fontSize: 18, color: color)),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    prepareSeaCans(p);
    // Lines removed elsewhere drop their boxes.
    _cans.removeWhere((m, c) {
      if (p.seaCans.contains(m)) return false;
      c.dispose();
      return true;
    });
    final n = seaCansEntered(p);
    final lines = p.seaCans;
    // Header, the lines, then the actions: a lazy list so 100+ sea cans stay smooth.
    return ListView.builder(
      controller: _scroll,
      padding: const EdgeInsets.fromLTRB(12, 18, 12, 120),
      itemCount: lines.length + 2,
      itemBuilder: (c, i) {
        if (i == 0) {
          return Container(
            decoration: const BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
              border: Border(
                top: BorderSide(color: C.cardStroke),
                left: BorderSide(color: C.cardStroke),
                right: BorderSide(color: C.cardStroke),
              ),
            ),
            padding: const EdgeInsets.fromLTRB(12, 16, 8, 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Title, with an Export button for sending just this list
                Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'Sea can movements',
                        style: TextStyle(fontSize: 22, color: C.navy, fontWeight: FontWeight.bold),
                      ),
                    ),
                    Semantics(
                      label: 'Export sea cans to PDF, Excel, CSV or Word',
                      button: true,
                      child: Material(
                        color: C.sky,
                        borderRadius: BorderRadius.circular(100),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(100),
                          onTap: widget.onExport,
                          child: const Padding(
                            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 9),
                            child: Text(
                              'Export',
                              style: TextStyle(fontSize: 15, color: C.deepBlue, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: InkWell(
                    onTap: () {
                      p.seaCanInclude = !p.seaCanInclude;
                      store.save();
                    },
                    child: Row(
                      children: [
                        Checkbox(
                          value: p.seaCanInclude,
                          onChanged: (v) {
                            p.seaCanInclude = v ?? false;
                            store.save();
                          },
                        ),
                        const Expanded(
                          child: Text('Include in my reports (PDF, Word and email)', style: TextStyle(fontSize: 16)),
                        ),
                      ],
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 4, bottom: 10),
                  child: Text(
                    n == 0
                        ? 'Type a sea can number, then pick where it came from and where it is now.'
                        : '${Fmt.plural(n, 'sea can', 'sea cans')} entered',
                    style: const TextStyle(fontSize: 15, color: C.grey),
                  ),
                ),
                // The three columns, then Row / Level under each location
                Row(
                  children: [
                    Expanded(flex: _canFlex, child: _label('Sea can')),
                    Expanded(flex: _cellFlex * 2, child: _label('From location')),
                    Expanded(flex: _cellFlex * 2, child: _label('Current location')),
                  ],
                ),
                const SizedBox(height: 4),
                _row([_label('Identifier'), _label('Row'), _label('Level'), _label('Row'), _label('Level')]),
              ],
            ),
          );
        }
        if (i <= lines.length) {
          final last = i == lines.length;
          return Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: last ? const BorderRadius.vertical(bottom: Radius.circular(22)) : null,
              border: Border(
                left: const BorderSide(color: C.cardStroke),
                right: const BorderSide(color: C.cardStroke),
                bottom: last ? const BorderSide(color: C.cardStroke) : BorderSide.none,
              ),
            ),
            padding: EdgeInsets.fromLTRB(12, 0, 8, last ? 12 : 0),
            child: _line(lines[i - 1]),
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _action('+  Add 10 rows', C.blue, () => _addRows(10)),
            _action('Remove empty rows', C.deepBlue, () {
              final before = p.seaCans.length;
              p.seaCans.removeWhere((m) => m.isEmpty);
              if (p.seaCans.length == before) {
                toast(context, 'No empty rows to remove');
                return;
              }
              store.save();
            }),
            _action('Clear all', C.red, () {
              confirm(
                context,
                'Clear all sea cans?',
                'Every sea can number and location on this tab is cleared. This can’t be undone.',
                'Clear all',
                () {
                  p.seaCans.clear();
                  store.save();
                },
              );
            }),
          ],
        );
      },
    );
  }
}
