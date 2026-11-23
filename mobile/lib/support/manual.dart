// The user manual: loaded from assets/manual/manual.json (the same file the classic Android app
// shows), with a simple search over titles, keywords and text.
import 'dart:convert';

import 'package:flutter/services.dart';

class ManualBlock {
  /// text, steps, bullets, tip, image or see
  final String type;
  final String text;
  final List<String> items;
  final String file;
  final String caption;
  final String id;

  const ManualBlock(
    this.type, {
    this.text = '',
    this.items = const [],
    this.file = '',
    this.caption = '',
    this.id = '',
  });

  static ManualBlock fromJson(Map<String, dynamic> o) => ManualBlock(
    (o['type'] ?? 'text').toString(),
    text: (o['text'] ?? '').toString(),
    items: [for (final i in (o['items'] as List? ?? const [])) i.toString()],
    file: (o['file'] ?? '').toString(),
    caption: (o['caption'] ?? '').toString(),
    id: (o['id'] ?? '').toString(),
  );

  /// Words in this block that search looks at.
  String get searchText => [text, caption, ...items].join(' ');
}

class ManualSection {
  final String id, title, group, summary;
  final List<String> keywords;
  final List<ManualBlock> blocks;

  const ManualSection(this.id, this.title, this.group, this.summary, this.keywords, this.blocks);

  static ManualSection fromJson(Map<String, dynamic> o) => ManualSection(
    (o['id'] ?? '').toString(),
    (o['title'] ?? '').toString(),
    (o['group'] ?? '').toString(),
    (o['summary'] ?? '').toString(),
    [for (final k in (o['keywords'] as List? ?? const [])) k.toString()],
    [
      for (final b in (o['blocks'] as List? ?? const []))
        if (b is Map) ManualBlock.fromJson(Map<String, dynamic>.from(b)),
    ],
  );
}

class SearchHit {
  final ManualSection section;
  final int score;

  /// A short piece of text around the first match, or the summary.
  final String snippet;

  const SearchHit(this.section, this.score, this.snippet);
}

class Manual {
  final String title, updated;
  final List<ManualSection> sections;

  const Manual(this.title, this.updated, this.sections);

  static const asset = 'assets/manual/manual.json';
  static const imageDir = 'assets/manual/';

  /// Reads the manual, leaving out sections that are only for the classic Android app.
  static Manual parse(String json) {
    final o = jsonDecode(json) as Map<String, dynamic>;
    final sections = <ManualSection>[];
    for (final s in (o['sections'] as List? ?? const [])) {
      if (s is! Map || s['classicOnly'] == true) continue;
      sections.add(ManualSection.fromJson(Map<String, dynamic>.from(s)));
    }
    return Manual((o['title'] ?? 'User manual').toString(), (o['updated'] ?? '').toString(), sections);
  }

  static Future<Manual> load([AssetBundle? bundle]) async => parse(await (bundle ?? rootBundle).loadString(asset));

  ManualSection? byId(String id) {
    for (final s in sections) {
      if (s.id == id) return s;
    }
    return null;
  }

  /// Groups in the order they first appear, each with its sections.
  List<MapEntry<String, List<ManualSection>>> groups() {
    final out = <String, List<ManualSection>>{};
    for (final s in sections) {
      out.putIfAbsent(s.group, () => []).add(s);
    }
    return out.entries.toList();
  }

  /// Sections matching every word typed, best first: title words count most, then keywords,
  /// then the summary and the text.
  List<SearchHit> search(String query) {
    final words = query.toLowerCase().split(RegExp(r'\s+')).where((w) => w.isNotEmpty).toList();
    if (words.isEmpty) return [];
    final hits = <SearchHit>[];
    for (final s in sections) {
      final title = s.title.toLowerCase();
      final keys = s.keywords.join(' ').toLowerCase();
      final summary = s.summary.toLowerCase();
      final body = s.blocks.map((b) => b.searchText).join(' ');
      final lowerBody = body.toLowerCase();
      var score = 0;
      var all = true;
      for (final w in words) {
        var ws = 0;
        if (title.contains(w)) ws += 10;
        if (keys.contains(w)) ws += 6;
        if (summary.contains(w)) ws += 3;
        if (lowerBody.contains(w)) ws += 1;
        if (ws == 0) {
          all = false;
          break;
        }
        score += ws;
      }
      if (!all) continue;
      hits.add(SearchHit(s, score, _snippet(body, lowerBody, words, s.summary)));
    }
    hits.sort((a, b) => b.score.compareTo(a.score));
    return hits;
  }

  static String _snippet(String body, String lowerBody, List<String> words, String fallback) {
    for (final w in words) {
      final i = lowerBody.indexOf(w);
      if (i < 0) continue;
      var start = i - 50, end = i + w.length + 70;
      if (start < 0) start = 0;
      if (end > body.length) end = body.length;
      final cut = body.substring(start, end).trim();
      return '${start > 0 ? '…' : ''}$cut${end < body.length ? '…' : ''}';
    }
    return fallback;
  }
}
