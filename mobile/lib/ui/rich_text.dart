// Showing and editing formatted notes: bold, underline, colour on chosen words, and the automatic
// red "Delay" and yellow-highlighted "on hold".
import 'package:flutter/material.dart';

import '../data/rich.dart';

/// Screen colours for the typing colours (dark yellow so it can be read on white).
const richColourValues = {
  'yellow': Color(0xFFC99A00),
  'orange': Color(0xFFE8730C),
  'red': Color(0xFFD93B3F),
};
const richHighlight = Color(0xFFFFE45C);

const _colourNames = {'': 'Black', 'yellow': 'Yellow', 'orange': 'Orange', 'red': 'Red'};

TextStyle richTextStyle(RichStyle s, TextStyle base) => base.copyWith(
      fontWeight: s.bold ? FontWeight.bold : base.fontWeight,
      decoration: s.underline ? TextDecoration.underline : base.decoration,
      color: s.colour.isEmpty ? base.color : richColourValues[s.colour],
      backgroundColor: s.highlight ? richHighlight : base.backgroundColor,
    );

/// The text with its formatting (and Delay / on hold) as a span for Text.rich.
TextSpan richSpan(String text, List<Run> runs, TextStyle base, {bool keywords = true}) => TextSpan(
      style: base,
      children: [
        for (final s in Rich.segments(text, runs, keywords: keywords)) TextSpan(text: s.text, style: richTextStyle(s.style, base)),
      ],
    );

/// Shows formatted text (or just the Delay / on hold highlighting when there are no runs).
class RichView extends StatelessWidget {
  final String text;
  final List<Run> runs;
  final TextStyle style;
  final int? maxLines;
  final TextOverflow? overflow;

  const RichView(this.text, {super.key, this.runs = const [], required this.style, this.maxLines, this.overflow});

  @override
  Widget build(BuildContext context) =>
      Text.rich(richSpan(text, runs, style), maxLines: maxLines, overflow: overflow ?? TextOverflow.clip);
}

/// A text box controller that keeps the formatting in step with the typing and draws it.
/// With [formatting] false it only shows the Delay / on hold highlighting (for titles).
class RichController extends TextEditingController {
  List<Run> runs;
  final bool formatting;

  /// Style for what's typed next, set by the toolbar when nothing is selected.
  RichStyle? typing;

  RichController({String text = '', List<Run>? runs, this.formatting = true})
      : runs = List.of(runs ?? const []),
        super(text: text);

  @override
  set value(TextEditingValue v) {
    final old = super.value;
    if (formatting) {
      if (v.text != old.text) {
        runs = Rich.afterEdit(old.text, v.text, runs, typing);
      } else if (v.selection != old.selection) {
        typing = null; // moved the cursor: type in the style of the text there
      }
    }
    super.value = v;
  }

  /// The style that B, U and the colour dots show as on: the selection's, or the typing style.
  RichStyle current() {
    final sel = selection;
    if (typing != null && sel.isCollapsed) return typing!;
    if (!sel.isValid) return RichStyle.plain;
    if (sel.isCollapsed) return Rich.styleAt(text, runs, sel.start > 0 ? sel.start - 1 : 0);
    final bold = Rich.allHave(text, runs, sel.start, sel.end, underline: false);
    final under = Rich.allHave(text, runs, sel.start, sel.end, underline: true);
    return RichStyle(bold: bold, underline: under, colour: Rich.styleAt(text, runs, sel.start).colour);
  }

  void toggle({required bool underline}) {
    final sel = selection;
    if (sel.isValid && !sel.isCollapsed) {
      runs = Rich.applyToggle(text, runs, sel.start, sel.end, underline: underline);
    } else {
      final c = current();
      typing = underline ? c.copyWith(underline: !c.underline) : c.copyWith(bold: !c.bold);
    }
    notifyListeners();
  }

  void colour(String c) {
    final sel = selection;
    if (sel.isValid && !sel.isCollapsed) {
      runs = Rich.applyColour(text, runs, sel.start, sel.end, c);
    } else {
      typing = current().copyWith(colour: c);
    }
    notifyListeners();
  }

  @override
  TextSpan buildTextSpan({required BuildContext context, TextStyle? style, required bool withComposing}) =>
      richSpan(text, formatting ? runs : const [], style ?? const TextStyle());
}

/// B, U and the four colour dots, shown above a notes box. [onChanged] saves the formatting.
class FormatBar extends StatelessWidget {
  final RichController controller;
  final VoidCallback onChanged;

  const FormatBar({super.key, required this.controller, required this.onChanged});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final cur = controller.current();
          Widget toggle(String label, String tip, bool on, TextStyle style, VoidCallback tap) => Padding(
                padding: const EdgeInsets.only(right: 8),
                child: Tooltip(
                  message: tip,
                  child: Material(
                    color: on ? const Color(0xFF174A8B) : const Color(0xFFDAE6F4),
                    borderRadius: BorderRadius.circular(10),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(10),
                      onTap: tap,
                      child: SizedBox(
                        width: 44,
                        height: 40,
                        child: Center(
                          child: Text(label, style: style.copyWith(fontSize: 19, color: on ? Colors.white : const Color(0xFF111827))),
                        ),
                      ),
                    ),
                  ),
                ),
              );
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                toggle('B', 'Bold', cur.bold, const TextStyle(fontWeight: FontWeight.bold), () {
                  controller.toggle(underline: false);
                  onChanged();
                }),
                toggle('U', 'Underline', cur.underline, const TextStyle(decoration: TextDecoration.underline), () {
                  controller.toggle(underline: true);
                  onChanged();
                }),
                const SizedBox(width: 6),
                for (final c in richColours)
                  Tooltip(
                    message: _colourNames[c]!,
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      onTap: () {
                        controller.colour(c);
                        onChanged();
                      },
                      child: Container(
                        width: 40,
                        height: 40,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: cur.colour == c ? const Color(0xFF174A8B) : Colors.transparent,
                            width: 2.5,
                          ),
                        ),
                        child: Container(
                          width: 28,
                          height: 28,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: c.isEmpty ? const Color(0xFF111827) : richColourValues[c],
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      );
}
