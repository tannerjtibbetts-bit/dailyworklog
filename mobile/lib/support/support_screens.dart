// Support: the user manual, with contents, search and one page per topic.
import 'package:flutter/material.dart';

import '../ui/theme.dart';
import 'manual.dart';

/// Opens the Support page.
void openSupport(BuildContext context) =>
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SupportScreen()));

/// A small "Support" button for the opening and sign-in screens.
class SupportButton extends StatelessWidget {
  final bool onDark;

  const SupportButton({super.key, this.onDark = false});

  @override
  Widget build(BuildContext context) => TextButton.icon(
    onPressed: () => openSupport(context),
    style: TextButton.styleFrom(
      foregroundColor: onDark ? Colors.white : C.deepBlue,
      minimumSize: const Size(0, 44),
      padding: const EdgeInsets.symmetric(horizontal: 14),
    ),
    icon: const Icon(Icons.help_outline, size: 22),
    label: const Text('Support', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
  );
}

class SupportScreen extends StatelessWidget {
  const SupportScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Support')),
    body: ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 40),
      children: [
        _tile(
          context,
          Icons.menu_book_outlined,
          'User manual',
          'Every part of the app: what it does and how to use it, with pictures. Search for what you need.',
          () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ManualScreen())),
        ),
        const SizedBox(height: 24),
        const Text(
          'App created by Tanner Tibbetts',
          textAlign: TextAlign.center,
          style: TextStyle(color: C.greyLight, fontSize: 12),
        ),
      ],
    ),
  );

  static Widget _tile(BuildContext context, IconData icon, String title, String sub, VoidCallback onTap) => Material(
    color: Colors.white,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(22),
      side: const BorderSide(color: C.cardStroke),
    ),
    child: InkWell(
      borderRadius: BorderRadius.circular(22),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(color: C.sky, borderRadius: BorderRadius.circular(16)),
              child: Icon(icon, color: C.deepBlue, size: 28),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: C.navy),
                  ),
                  const SizedBox(height: 4),
                  Text(sub, style: const TextStyle(fontSize: 15, color: C.grey)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: C.grey),
          ],
        ),
      ),
    ),
  );
}

/// Contents grouped by topic, with a search box at the top.
class ManualScreen extends StatefulWidget {
  final Manual? manual;

  const ManualScreen({super.key, this.manual});

  @override
  State<ManualScreen> createState() => _ManualScreenState();
}

class _ManualScreenState extends State<ManualScreen> {
  Manual? manual;
  String? error;
  String query = '';
  final _search = TextEditingController();

  @override
  void initState() {
    super.initState();
    manual = widget.manual;
    if (manual == null) {
      Manual.load().then(
        (m) {
          if (mounted) setState(() => manual = m);
        },
        onError: (_) {
          if (mounted) setState(() => error = 'The manual couldn’t be opened.');
        },
      );
    }
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  void _open(ManualSection s) => Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) => SectionScreen(manual: manual!, section: s, highlight: query),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final m = manual;
    return Scaffold(
      appBar: AppBar(title: const Text('User manual')),
      body: m == null
          ? Center(
              child: error == null
                  ? const CircularProgressIndicator()
                  : Text(error!, style: const TextStyle(color: C.grey, fontSize: 17)),
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  height: 58,
                  margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                  padding: const EdgeInsets.only(left: 18, right: 6),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(30),
                    border: Border.all(color: C.cardStroke),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.search, color: C.navy),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextField(
                          controller: _search,
                          textInputAction: TextInputAction.search,
                          style: const TextStyle(fontSize: 18),
                          decoration: const InputDecoration(
                            border: InputBorder.none,
                            hintText: 'Search the manual (e.g. photos, PIN, sea can)',
                          ),
                          onChanged: (s) => setState(() => query = s.trim()),
                        ),
                      ),
                      if (query.isNotEmpty)
                        IconButton(
                          tooltip: 'Clear search',
                          icon: const Icon(Icons.close, color: C.grey),
                          onPressed: () {
                            _search.clear();
                            setState(() => query = '');
                          },
                        ),
                    ],
                  ),
                ),
                Expanded(child: query.isEmpty ? _contents(m) : _results(m)),
              ],
            ),
    );
  }

  Widget _contents(Manual m) => ListView(
    padding: const EdgeInsets.fromLTRB(16, 4, 16, 40),
    children: [
      for (final g in m.groups()) ...[
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 18, 8, 6),
          child: Text(
            g.key.toUpperCase(),
            style: const TextStyle(color: C.grey, fontSize: 13, fontWeight: FontWeight.bold, letterSpacing: 0.8),
          ),
        ),
        Container(
          decoration: cardDecoration(),
          child: Column(
            children: [
              for (var i = 0; i < g.value.length; i++) ...[
                if (i > 0) const Divider(height: 1, indent: 18, endIndent: 18, color: C.divider),
                _entry(g.value[i], g.value[i].summary),
              ],
            ],
          ),
        ),
      ],
    ],
  );

  Widget _results(Manual m) {
    final hits = m.search(query);
    if (hits.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(32),
        children: [
          Text(
            'Nothing matches “$query”.\nTry another word, like “photo”, “text” or “report”.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: C.grey, fontSize: 17),
          ),
        ],
      );
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 40),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
          child: Text(
            hits.length == 1 ? '1 topic found' : '${hits.length} topics found',
            style: const TextStyle(color: C.grey, fontSize: 14),
          ),
        ),
        Container(
          decoration: cardDecoration(),
          child: Column(
            children: [
              for (var i = 0; i < hits.length; i++) ...[
                if (i > 0) const Divider(height: 1, indent: 18, endIndent: 18, color: C.divider),
                _entry(hits[i].section, hits[i].snippet),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Widget _entry(ManualSection s, String sub) => InkWell(
    onTap: () => _open(s),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(18, 14, 10, 14),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _highlighted(s.title, query, const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: C.navy)),
                const SizedBox(height: 3),
                _highlighted(sub, query, const TextStyle(fontSize: 15, color: C.grey), maxLines: 3),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: C.grey),
        ],
      ),
    ),
  );
}

/// One topic of the manual.
class SectionScreen extends StatelessWidget {
  final Manual manual;
  final ManualSection section;

  /// Search words to mark in yellow.
  final String highlight;

  const SectionScreen({super.key, required this.manual, required this.section, this.highlight = ''});

  @override
  Widget build(BuildContext context) {
    final s = section;
    final i = manual.sections.indexOf(s);
    final prev = i > 0 ? manual.sections[i - 1] : null;
    final next = i >= 0 && i < manual.sections.length - 1 ? manual.sections[i + 1] : null;
    return Scaffold(
      appBar: AppBar(title: Text(s.group, style: const TextStyle(fontSize: 17))),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 40),
        children: [
          _highlighted(s.title, highlight, const TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: C.navy)),
          if (s.summary.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 6, bottom: 4),
              child: _highlighted(s.summary, highlight, const TextStyle(fontSize: 17, color: C.grey)),
            ),
          for (final b in s.blocks) _block(context, b),
          const SizedBox(height: 24),
          Row(
            children: [
              if (prev != null)
                Expanded(child: _nav(context, Icons.chevron_left, prev, alignEnd: false))
              else
                const Spacer(),
              const SizedBox(width: 10),
              if (next != null)
                Expanded(child: _nav(context, Icons.chevron_right, next, alignEnd: true))
              else
                const Spacer(),
            ],
          ),
        ],
      ),
    );
  }

  Widget _nav(BuildContext context, IconData icon, ManualSection to, {required bool alignEnd}) => OutlinedButton(
    style: OutlinedButton.styleFrom(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
      side: const BorderSide(color: C.cardStroke),
      backgroundColor: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    onPressed: () => Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (_) => SectionScreen(manual: manual, section: to),
      ),
    ),
    child: Row(
      mainAxisAlignment: alignEnd ? MainAxisAlignment.end : MainAxisAlignment.start,
      children: [
        if (!alignEnd) Icon(icon, color: C.deepBlue),
        Flexible(
          child: Column(
            crossAxisAlignment: alignEnd ? CrossAxisAlignment.end : CrossAxisAlignment.start,
            children: [
              Text(alignEnd ? 'Next' : 'Back', style: const TextStyle(fontSize: 12, color: C.grey)),
              Text(
                to.title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                textAlign: alignEnd ? TextAlign.end : TextAlign.start,
                style: const TextStyle(fontSize: 15, color: C.deepBlue, fontWeight: FontWeight.bold),
              ),
            ],
          ),
        ),
        if (alignEnd) Icon(icon, color: C.deepBlue),
      ],
    ),
  );

  Widget _block(BuildContext context, ManualBlock b) {
    const body = TextStyle(fontSize: 17, color: C.text, height: 1.35);
    switch (b.type) {
      case 'steps':
        return Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Column(
            children: [
              for (var i = 0; i < b.items.length; i++)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 30,
                        height: 30,
                        alignment: Alignment.center,
                        decoration: const BoxDecoration(color: C.blue, shape: BoxShape.circle),
                        child: Text(
                          '${i + 1}',
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.only(top: 3),
                          child: _highlighted(b.items[i], highlight, body),
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        );
      case 'bullets':
        return Padding(
          padding: const EdgeInsets.only(top: 10),
          child: Column(
            children: [
              for (final it in b.items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Padding(
                        padding: EdgeInsets.only(top: 9, left: 6, right: 12),
                        child: CircleAvatar(radius: 3.5, backgroundColor: C.deepBlue),
                      ),
                      Expanded(child: _highlighted(it, highlight, body)),
                    ],
                  ),
                ),
            ],
          ),
        );
      case 'tip':
        return Container(
          margin: const EdgeInsets.only(top: 14),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: const Color(0xFFFFF5D1),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFEED98C)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.lightbulb_outline, color: Color(0xFFB8860B)),
              const SizedBox(width: 10),
              Expanded(child: _highlighted(b.text, highlight, body)),
            ],
          ),
        );
      case 'image':
        return Padding(
          padding: const EdgeInsets.only(top: 16),
          child: Column(
            children: [
              GestureDetector(
                onTap: () => _zoom(context, b),
                child: Container(
                  constraints: const BoxConstraints(maxHeight: 520),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: C.cardStroke, width: 2),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: Image.asset(
                    '${Manual.imageDir}${b.file}',
                    fit: BoxFit.contain,
                    errorBuilder: (_, _, _) => const SizedBox(height: 0),
                  ),
                ),
              ),
              if (b.caption.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(
                    b.caption,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 14, color: C.grey),
                  ),
                ),
            ],
          ),
        );
      case 'see':
        final to = manual.byId(b.id);
        if (to == null) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(top: 12),
          child: InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => SectionScreen(manual: manual, section: to),
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  const Icon(Icons.arrow_forward, color: C.blue, size: 20),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      b.text.isEmpty ? to.title : b.text,
                      style: const TextStyle(fontSize: 17, color: C.blue, fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      default:
        return Padding(padding: const EdgeInsets.only(top: 12), child: _highlighted(b.text, highlight, body));
    }
  }

  void _zoom(BuildContext context, ManualBlock b) => Navigator.of(context).push(
    MaterialPageRoute(
      builder: (c) => Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(backgroundColor: Colors.black, foregroundColor: Colors.white, title: Text(b.caption)),
        body: InteractiveViewer(maxScale: 4, child: Center(child: Image.asset('${Manual.imageDir}${b.file}'))),
      ),
    ),
  );
}

/// Text with the search words marked in yellow.
Widget _highlighted(String text, String query, TextStyle style, {int? maxLines}) {
  final words = query.toLowerCase().split(RegExp(r'\s+')).where((w) => w.length > 1).toList();
  if (words.isEmpty) {
    return Text(text, style: style, maxLines: maxLines, overflow: maxLines == null ? null : TextOverflow.ellipsis);
  }
  final lower = text.toLowerCase();
  final spans = <TextSpan>[];
  var i = 0;
  while (i < text.length) {
    var at = -1, len = 0;
    for (final w in words) {
      final j = lower.indexOf(w, i);
      if (j >= 0 && (at < 0 || j < at)) {
        at = j;
        len = w.length;
      }
    }
    if (at < 0) {
      spans.add(TextSpan(text: text.substring(i)));
      break;
    }
    if (at > i) spans.add(TextSpan(text: text.substring(i, at)));
    spans.add(
      TextSpan(
        text: text.substring(at, at + len),
        style: const TextStyle(backgroundColor: Color(0xFFFFE680)),
      ),
    );
    i = at + len;
  }
  return Text.rich(
    TextSpan(style: style, children: spans),
    maxLines: maxLines,
    overflow: maxLines == null ? null : TextOverflow.ellipsis,
  );
}
