// Finds the sea can's six-digit number in the text read from a photo, e.g. "BPPU 410018 5" -> "410018".
// Only answers when it's clear: if nothing fits, or two different numbers fit, it gives null and the
// subject is left for the person to type. Same rules as the classic app's CanNumber.java.

class CanNumber {
  CanNumber._();

  /// Owner code (3 letters + U, J or Z), six digits, maybe the check digit. OCR often reads 0 as O and 1 as I or L.
  static final _withPrefix =
      RegExp(r'(?<![A-Z0-9])([A-Z]{3}[UJZ])\s*([0-9OIL]{6})(?:\s*[-\s]?\s*([0-9OIL])(?![0-9OIL]))?(?![0-9])');

  /// Six digits standing alone (maybe followed by a separate check digit), not part of 30,480 or a longer number.
  static final _bare = RegExp(r'(?<![0-9.,:/A-Z])([0-9]{6})(?:\s*[-\s]\s*([0-9]))?(?![0-9.,:/A-Z])');

  /// The six digits, or null if there's no clear sea can number.
  static String? find(List<String>? lines) {
    if (lines == null || lines.isEmpty) return null;
    final texts = <String>[];
    final all = StringBuffer();
    for (final l in lines) {
      final u = l.toUpperCase().trim();
      texts.add(u);
      all
        ..write(u)
        ..write(' ');
    }
    // The owner code and the number are sometimes read as separate lines, so look at all the text joined too.
    texts.add(all.toString());

    final checked = <String>{}, prefixed = <String>{};
    for (final t in texts) {
      for (final m in _withPrefix.allMatches(t)) {
        final d = _digits(m.group(2)!);
        prefixed.add(d);
        final check = m.group(3);
        if (check != null && checkDigit(m.group(1)! + d) == int.parse(_digits(check))) checked.add(d);
      }
    }
    if (checked.length == 1) return checked.first;
    if (checked.length > 1) return null;
    if (prefixed.length == 1) return prefixed.first;
    if (prefixed.length > 1) return null;

    // No owner code read: a lone six-digit number, if there's exactly one.
    final bare = <String>{};
    for (var i = 0; i < texts.length - 1; i++) {
      for (final m in _bare.allMatches(texts[i])) {
        bare.add(m.group(1)!);
      }
    }
    return bare.length == 1 ? bare.first : null;
  }

  static String _digits(String s) => s.replaceAll('O', '0').replaceAll('I', '1').replaceAll('L', '1');

  /// ISO 6346 check digit for "BPPU410018" (4 letters + 6 digits), or -1.
  static int checkDigit(String code) {
    if (code.length != 10) return -1;
    var sum = 0;
    for (var i = 0; i < 10; i++) {
      final c = code.codeUnitAt(i);
      int v;
      if (c >= 48 && c <= 57) {
        v = c - 48;
      } else if (c >= 65 && c <= 90) {
        v = 10 + (c - 65);
        v += (v - 1) ~/ 10; // values skip 11, 22 and 33
      } else {
        return -1;
      }
      sum += v << i;
    }
    return sum % 11 % 10;
  }
}
