// Sea Can Movements tab: one line per sea can. Type its identifier, then pick the letter (A to Z),
// row (1 to 40) and level (1 to 3) it's coming from and the letter, row and level it's at now,
// with a blue arrow between the two, then its weight. The lines are wider than a phone, so the
// whole table (headings and lines together) scrolls sideways; the title and actions don't.
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

const seaCanRows = 40, seaCanLevels = 3;

/// Letters A to Z for the Letter dropdowns.
final seaCanLetters = [for (var c = 0x41; c <= 0x5A; c++) String.fromCharCode(c)];

/// Lines shown to start with; more can be added (there's no limit).
const seaCanStartLines = 20;
const _blank = '–';

/// Widths on a line: identifier, then Letter / Row / Level for each location, then weight.
const _canFlex = 30, _letterFlex = 7, _rowFlex = 9, _levelFlex = 7, _weightFlex = 10;
const _locFlex = _letterFlex + _rowFlex + _levelFlex;
const _cellFlexes = [_canFlex, _letterFlex, _rowFlex, _levelFlex, _letterFlex, _rowFlex, _levelFlex, _weightFlex];

/// The table is at least this wide (logical px) and scrolls sideways on narrower screens.
const seaCanTableMinWidth = 560.0;

/// Gap after each box.
const _cellGap = 3.0;

/// Width of the arrow between From and Current location.
const _arrowW = 24.0;

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
  final Map<SeaCanMove, TextEditingController> _cans = {}, _weights = {};
  final _scroll = ScrollController();

  /// The sideways scroll of the table.
  final _across = ScrollController();

  Store get store => Store.instance;
  Project get p => widget.project;

  @override
  void dispose() {
    for (final c in [..._cans.values, ..._weights.values]) {
      c.dispose();
    }
    _scroll.dispose();
    _across.dispose();
    super.dispose();
  }

  TextEditingController _canFor(SeaCanMove m) => _cans.putIfAbsent(m, () => TextEditingController(text: m.can));
  TextEditingController _weightFor(SeaCanMove m) =>
      _weights.putIfAbsent(m, () => TextEditingController(text: m.weight));

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

  /// Dropdown of [choices], with a dash first for not picked yet.
  Widget _dropdown(List<String> choices, String current, ValueChanged<String> onPick) {
    final items = [_blank, ...choices];
    final value = items.contains(current) ? current : _blank;
    TextStyle style(String v) => TextStyle(
      fontSize: 15,
      color: v == _blank ? C.greyLight : C.deepBlue,
      fontWeight: v == _blank ? FontWeight.normal : FontWeight.bold,
    );
    return Container(
      constraints: const BoxConstraints(minHeight: 44),
      decoration: _cellDecoration,
      padding: const EdgeInsets.symmetric(horizontal: 1),
      child: DropdownButtonHideUnderline(
        // No chevron: the boxes are narrow, and the whole box opens the list.
        child: DropdownButton<String>(
          value: value,
          isExpanded: true,
          isDense: true,
          iconSize: 0,
          menuMaxHeight: 360,
          icon: const SizedBox.shrink(),
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
                child: Text(v, maxLines: 1, softWrap: false, overflow: TextOverflow.clip, style: style(v)),
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
    // Very long numbers wrap onto a second line rather than being cut off.
    minLines: 1,
    maxLines: 2,
    keyboardType: TextInputType.text,
    textInputAction: TextInputAction.done,
    textCapitalization: TextCapitalization.characters,
    autocorrect: false,
    enableSuggestions: false,
    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: C.text),
    decoration: InputDecoration(
      hintText: 'Sea can #',
      hintStyle: const TextStyle(color: C.greyLight, fontWeight: FontWeight.normal),
      isDense: true,
      filled: true,
      fillColor: const Color(0xFFF2F7FD),
      contentPadding: const EdgeInsets.symmetric(horizontal: 6, vertical: 12),
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

  static OutlineInputBorder _border(Color c) => OutlineInputBorder(
    borderRadius: BorderRadius.circular(10),
    borderSide: BorderSide(color: c),
  );

  /// Weight: a number (decimals allowed), saved as it's typed.
  Widget _weightBox(SeaCanMove m) => TextField(
    controller: _weightFor(m),
    keyboardType: const TextInputType.numberWithOptions(decimal: true),
    inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
    textInputAction: TextInputAction.done,
    textAlign: TextAlign.center,
    maxLines: 1,
    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: C.deepBlue),
    decoration: InputDecoration(
      hintText: _blank,
      hintStyle: const TextStyle(color: C.greyLight, fontWeight: FontWeight.normal),
      isDense: true,
      filled: true,
      fillColor: const Color(0xFFF2F7FD),
      contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 12),
      enabledBorder: _border(C.cardStroke),
      focusedBorder: _border(C.blue),
    ),
    onChanged: (s) {
      m.weight = s.trim();
      store.save();
    },
  );

  static List<String> _numbers(int max) => [for (var i = 1; i <= max; i++) '$i'];

  /// Identifier box, six cells (Letter / Row / Level for From, then Current location), then weight.
  /// With [arrow], a blue arrow sits between the two locations ("came from here, went to here");
  /// without, just the gap.
  Widget _row(List<Widget> cells, {bool arrow = false}) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: Row(
      children: [
        for (var i = 0; i < cells.length; i++) ...[
          if (i == 4) arrow ? const _MoveArrow() : const SizedBox(width: _arrowW),
          Expanded(
            flex: _cellFlexes[i],
            child: Padding(
              padding: const EdgeInsets.only(right: _cellGap),
              child: cells[i],
            ),
          ),
        ],
      ],
    ),
  );

  /// A column heading; shrinks a little rather than cut off when the boxes are narrow.
  static Widget _label(String s) => FittedBox(
    fit: BoxFit.scaleDown,
    child: Text(
      s,
      maxLines: 1,
      style: const TextStyle(fontSize: 12, color: C.grey, fontWeight: FontWeight.bold),
    ),
  );

  Widget _line(SeaCanMove m) => KeyedSubtree(
    key: ObjectKey(m),
    child: _row([
      _canBox(m),
      _dropdown(seaCanLetters, m.fromLetter, (v) => m.fromLetter = v),
      _dropdown(_numbers(seaCanRows), m.fromRow, (v) => m.fromRow = v),
      _dropdown(_numbers(seaCanLevels), m.fromLevel, (v) => m.fromLevel = v),
      _dropdown(seaCanLetters, m.toLetter, (v) => m.toLetter = v),
      _dropdown(_numbers(seaCanRows), m.toRow, (v) => m.toRow = v),
      _dropdown(_numbers(seaCanLevels), m.toLevel, (v) => m.toLevel = v),
      _weightBox(m),
    ], arrow: true),
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
    for (final boxes in [_cans, _weights]) {
      boxes.removeWhere((m, c) {
        if (p.seaCans.contains(m)) return false;
        c.dispose();
        return true;
      });
    }
    final n = seaCansEntered(p);
    final lines = p.seaCans;
    const side = BorderSide(color: C.cardStroke);
    // The card's top (title, Export, tick, count), then the table that scrolls sideways, then the actions.
    return ListView(
      controller: _scroll,
      padding: const EdgeInsets.fromLTRB(12, 18, 12, 120),
      children: [
        Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
            border: Border(top: side, left: side, right: side),
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
            ],
          ),
        ),
        // The table: headings and every line scroll sideways together.
        Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(bottom: Radius.circular(22)),
            border: Border(left: side, right: side, bottom: side),
          ),
          padding: const EdgeInsets.fromLTRB(12, 0, 8, 12),
          child: LayoutBuilder(
            builder: (c, box) {
              final width = box.maxWidth < seaCanTableMinWidth ? seaCanTableMinWidth : box.maxWidth;
              return Scrollbar(
                controller: _across,
                thumbVisibility: width > box.maxWidth,
                child: SingleChildScrollView(
                  key: const Key('seaCanTable'),
                  controller: _across,
                  scrollDirection: Axis.horizontal,
                  padding: EdgeInsets.only(bottom: width > box.maxWidth ? 10 : 0),
                  child: SizedBox(
                    width: width,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // The columns, then Letter / Row / Level under each location
                        Row(
                          children: [
                            Expanded(flex: _canFlex, child: _label('Sea can')),
                            Expanded(flex: _locFlex, child: _label('From location')),
                            const SizedBox(width: _arrowW),
                            Expanded(flex: _locFlex, child: _label('Current location')),
                            Expanded(flex: _weightFlex, child: _label('Weight')),
                          ],
                        ),
                        const SizedBox(height: 4),
                        _row([
                          _label('Identifier'),
                          for (var k = 0; k < 2; k++) ...[_label('Letter'), _label('Row'), _label('Level')],
                          const SizedBox(),
                        ]),
                        for (final m in lines) _line(m),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
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
  }
}

/// The little arrow between From and Current location on each line.
class _MoveArrow extends StatelessWidget {
  const _MoveArrow();

  @override
  Widget build(BuildContext context) => SizedBox(
    width: _arrowW,
    child: Center(
      child: Container(
        width: 20,
        height: 20,
        decoration: const BoxDecoration(
          shape: BoxShape.circle,
          gradient: LinearGradient(colors: [C.blue, C.deepBlue]),
        ),
        child: const Icon(Icons.arrow_forward_rounded, size: 14, color: Colors.white, semanticLabel: 'moved to'),
      ),
    ),
  );
}
