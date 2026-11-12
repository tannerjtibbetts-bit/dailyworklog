// Formatting for notes: bold, underline and colour on chosen words, kept as "runs" next to the
// plain text, plus the automatic highlighting of "Delay" (red, bold) and "on hold" (bold, yellow
// highlight). The same rules as the classic app's Rich.java.

/// Colours a person can give their typing ('' is the normal black).
const richColours = ['', 'yellow', 'orange', 'red'];

class RichStyle {
  final bool bold, underline, highlight;

  /// '', 'yellow', 'orange' or 'red'.
  final String colour;

  const RichStyle({this.bold = false, this.underline = false, this.colour = '', this.highlight = false});

  static const plain = RichStyle();

  bool get isPlain => !bold && !underline && colour.isEmpty && !highlight;

  RichStyle copyWith({bool? bold, bool? underline, String? colour, bool? highlight}) => RichStyle(
        bold: bold ?? this.bold,
        underline: underline ?? this.underline,
        colour: colour ?? this.colour,
        highlight: highlight ?? this.highlight,
      );

  @override
  bool operator ==(Object other) =>
      other is RichStyle &&
      other.bold == bold &&
      other.underline == underline &&
      other.colour == colour &&
      other.highlight == highlight;

  @override
  int get hashCode => Object.hash(bold, underline, colour, highlight);

  @override
  String toString() => [if (bold) 'b', if (underline) 'u', if (colour.isNotEmpty) colour, if (highlight) 'hl'].join(',');
}

/// User formatting on [start, end).
class Run {
  final int start, end;
  final RichStyle style;

  const Run(this.start, this.end, this.style);

  Map<String, dynamic> toJson() => {
        's': start,
        'e': end,
        if (style.bold) 'b': true,
        if (style.underline) 'u': true,
        if (style.colour.isNotEmpty) 'c': style.colour,
      };

  @override
  bool operator ==(Object other) => other is Run && other.start == start && other.end == end && other.style == style;

  @override
  int get hashCode => Object.hash(start, end, style);

  @override
  String toString() => '{$start,$end,$style}';
}

/// A piece of text in one style.
class Segment {
  final String text;
  final int start;
  final RichStyle style;

  const Segment(this.text, this.start, this.style);

  int get end => start + text.length;

  @override
  String toString() => '"$text" $style';
}

class Rich {
  Rich._();

  /// "Delay cleared: when (by, go-ahead from X, delayed 2 h)" for reports, with "Delay cleared: when" green and bold.
  /// Green is only used by the app itself (not one of the colours a person can pick).
  static (String, List<Run>) clearedLine(String when, String by, String goAhead, String length) {
    final head = 'Delay cleared: $when';
    final extra = [
      by,
      if (goAhead.isNotEmpty) 'go-ahead from $goAhead',
      if (length.isNotEmpty) 'delayed $length',
    ].where((x) => x.isNotEmpty).join(', ');
    return (
      extra.isEmpty ? head : '$head ($extra)',
      [Run(0, head.length, const RichStyle(bold: true, colour: 'green'))],
    );
  }

  /// "Delayed: reason (by, when)" for reports, with the "Delayed: reason" part red and bold.
  static (String, List<Run>) delayLine(String reason, String by, String when) {
    final head = 'Delayed: $reason';
    return (
      by.isEmpty ? head : '$head ($by, $when)',
      [Run(0, head.length, const RichStyle(bold: true, colour: 'red'))],
    );
  }

  /// [text] with [tags] in front: "Safety concern, Training: text", the tag names orange and bold.
  static (String, List<Run>) tagged(String text, List<Run> runs, List<String> tags) {
    if (tags.isEmpty) return (text, runs);
    final names = tags.join(', ');
    final prefix = text.isEmpty ? names : '$names: ';
    return (
      prefix + text,
      [
        Run(0, names.length, const RichStyle(bold: true, colour: 'orange')),
        for (final r in runs) Run(r.start + prefix.length, r.end + prefix.length, r.style),
      ],
    );
  }

  static final _delay = RegExp(r'\bdelay[a-z]*', caseSensitive: false);
  static final _onHold = RegExp(r'\bon\s+hold\b', caseSensitive: false);

  /// Runs from worklog.json; anything invalid is dropped, the rest clipped to [length] and normalized.
  static List<Run> fromJson(dynamic list, int length) {
    final out = <Run>[];
    if (list is! List) return out;
    for (final o in list) {
      if (o is! Map) continue;
      final s = o['s'], e = o['e'];
      if (s is! int || e is! int) continue;
      final c = (o['c'] ?? '').toString();
      if (!richColours.contains(c)) continue;
      final start = s < 0 ? 0 : s, end = e > length ? length : e;
      if (start >= end) continue;
      out.add(Run(start, end, RichStyle(bold: o['b'] == true, underline: o['u'] == true, colour: c)));
    }
    out.sort((a, b) => a.start.compareTo(b.start));
    // Overlaps: a later run is cut so it starts after the one before.
    final fixed = <Run>[];
    for (final r in out) {
      final from = fixed.isEmpty ? r.start : (r.start < fixed.last.end ? fixed.last.end : r.start);
      if (from < r.end) fixed.add(Run(from, r.end, r.style));
    }
    return _fromStyles(_styles(length, fixed));
  }

  static List<Map<String, dynamic>> toJson(List<Run> runs) => [for (final r in runs) r.toJson()];

  /// Each character's user style.
  static List<RichStyle> _styles(int length, List<Run> runs) {
    final s = List<RichStyle>.filled(length, RichStyle.plain);
    for (final r in runs) {
      for (var i = r.start; i < r.end && i < length; i++) {
        if (i >= 0) s[i] = r.style;
      }
    }
    return s;
  }

  /// Normalized runs from per-character styles (highlight is never stored).
  static List<Run> _fromStyles(List<RichStyle> styles) {
    final out = <Run>[];
    var i = 0;
    while (i < styles.length) {
      final st = styles[i].copyWith(highlight: false);
      var j = i + 1;
      while (j < styles.length && styles[j].copyWith(highlight: false) == st) {
        j++;
      }
      if (!st.isPlain) out.add(Run(i, j, st));
      i = j;
    }
    return out;
  }

  /// The text in pieces of one style each: user formatting, plus Delay / on hold when [keywords].
  static List<Segment> segments(String text, List<Run> runs, {bool keywords = true}) {
    if (text.isEmpty) return const [];
    final st = _styles(text.length, runs);
    if (keywords) {
      for (final m in _delay.allMatches(text)) {
        for (var i = m.start; i < m.end; i++) {
          // The app's own green ("Delay cleared") stays green.
          if (st[i].colour != 'green') st[i] = st[i].copyWith(bold: true, colour: 'red');
        }
      }
      for (final m in _onHold.allMatches(text)) {
        for (var i = m.start; i < m.end; i++) {
          st[i] = st[i].copyWith(bold: true, highlight: true);
        }
      }
    }
    final out = <Segment>[];
    var i = 0;
    while (i < text.length) {
      var j = i + 1;
      while (j < text.length && st[j] == st[i]) {
        j++;
      }
      out.add(Segment(text.substring(i, j), i, st[i]));
      i = j;
    }
    return out;
  }

  /// True if every character in [start, end) has bold (or underline when [underline]).
  static bool allHave(String text, List<Run> runs, int start, int end, {required bool underline}) {
    if (start >= end) return false;
    final st = _styles(text.length, runs);
    for (var i = start; i < end; i++) {
      if (!(underline ? st[i].underline : st[i].bold)) return false;
    }
    return true;
  }

  /// Bold or underline on [start, end): off if it's all on already, otherwise on.
  static List<Run> applyToggle(String text, List<Run> runs, int start, int end, {required bool underline}) {
    if (start >= end) return runs;
    final on = !allHave(text, runs, start, end, underline: underline);
    final st = _styles(text.length, runs);
    for (var i = start; i < end; i++) {
      st[i] = underline ? st[i].copyWith(underline: on) : st[i].copyWith(bold: on);
    }
    return _fromStyles(st);
  }

  /// Sets the colour of [start, end) ('' is black).
  static List<Run> applyColour(String text, List<Run> runs, int start, int end, String colour) {
    if (start >= end) return runs;
    final st = _styles(text.length, runs);
    for (var i = start; i < end; i++) {
      st[i] = st[i].copyWith(colour: colour);
    }
    return _fromStyles(st);
  }

  /// The user style of the character at [i] (plain outside the text).
  static RichStyle styleAt(String text, List<Run> runs, int i) {
    for (final r in runs) {
      if (i >= r.start && i < r.end) return r.style;
    }
    return RichStyle.plain;
  }

  /// Keeps the runs in step when the text changes from [oldText] to [newText]. Typed text gets [typing], or
  /// else the style of the character before it.
  static List<Run> afterEdit(String oldText, String newText, List<Run> runs, RichStyle? typing) {
    if (oldText == newText) return runs;
    var p = 0;
    final maxP = oldText.length < newText.length ? oldText.length : newText.length;
    while (p < maxP && oldText.codeUnitAt(p) == newText.codeUnitAt(p)) {
      p++;
    }
    var q = 0;
    while (q < maxP - p &&
        oldText.codeUnitAt(oldText.length - 1 - q) == newText.codeUnitAt(newText.length - 1 - q)) {
      q++;
    }
    final old = _styles(oldText.length, runs);
    final inserted = newText.length - q - p;
    final fill = typing ??
        (p > 0
            ? old[p - 1]
            : old.isNotEmpty
                ? old[0]
                : RichStyle.plain);
    final st = [
      ...old.sublist(0, p),
      for (var i = 0; i < inserted; i++) fill,
      ...old.sublist(oldText.length - q),
    ];
    return _fromStyles(st);
  }
}
