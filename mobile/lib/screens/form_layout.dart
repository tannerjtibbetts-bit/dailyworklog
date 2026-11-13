// "Crop the page" (after each photo of a form) and "Place the answers" (drag each answer to its blank on
// the form's own page pictures; the PDF writes them there).
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;

import '../data/forms.dart';
import '../data/store.dart';
import '../services/form_files.dart';
import '../ui/theme.dart';
import 'note_screen.dart' show bottomPill;

/// Crops a saved photo (in the photo folder) to the rectangle the person drags.
class CropScreen extends StatefulWidget {
  final String file;

  const CropScreen({super.key, required this.file});

  @override
  State<CropScreen> createState() => _CropScreenState();
}

class _CropScreenState extends State<CropScreen> {
  (int, int)? _size;

  /// The rectangle as fractions of the photo: left, top, right, bottom.
  double _l = 0.03, _t = 0.03, _r = 0.97, _b = 0.97;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    FormFiles.pictureSize(widget.file).then((s) {
      if (mounted) setState(() => _size = s);
    }, onError: (_) {
      if (mounted) Navigator.of(context).pop();
    });
  }

  Future<void> _crop() async {
    setState(() => _busy = true);
    final f = Store.instance.photoFile(widget.file);
    final rect = [_l, _t, _r, _b];
    try {
      final bytes = await f.readAsBytes();
      final out = await compute(_cropJpeg, (bytes, rect));
      if (out != null) await f.writeAsBytes(out, flush: true);
    } catch (_) {}
    imageCache.clear();
    if (mounted) Navigator.of(context).pop();
  }

  Widget _handle(double x, double y, void Function(Offset d, Size box) move, Size box) => Positioned(
        left: x * box.width - 22,
        top: y * box.height - 22,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onPanUpdate: (d) => setState(() => move(d.delta, box)),
          child: Container(
            width: 44,
            height: 44,
            alignment: Alignment.center,
            child: Container(
              width: 22,
              height: 22,
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.all(color: C.blue, width: 3),
              ),
            ),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final s = _size;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: const Text('Crop the page'),
        automaticallyImplyLeading: false,
      ),
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : Column(children: [
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 4, 16, 10),
                child: Text('Drag the corners to the edges of the form.',
                    style: TextStyle(color: Colors.white70, fontSize: 16)),
              ),
              Expanded(
                child: Center(
                  child: AspectRatio(
                    aspectRatio: s.$1 / s.$2,
                    child: LayoutBuilder(builder: (context, c) {
                      final box = Size(c.maxWidth, c.maxHeight);
                      const min = 0.08;
                      double dx(Offset d) => d.dx / box.width;
                      double dy(Offset d) => d.dy / box.height;
                      return Stack(clipBehavior: Clip.none, children: [
                        Positioned.fill(child: Image.file(Store.instance.photoFile(widget.file), fit: BoxFit.fill)),
                        // Shade outside the rectangle.
                        Positioned.fill(
                          child: IgnorePointer(
                            child: CustomPaint(painter: _Shade(Rect.fromLTRB(_l, _t, _r, _b))),
                          ),
                        ),
                        _handle(_l, _t, (d, _) {
                          _l = (_l + dx(d)).clamp(0.0, _r - min);
                          _t = (_t + dy(d)).clamp(0.0, _b - min);
                        }, box),
                        _handle(_r, _t, (d, _) {
                          _r = (_r + dx(d)).clamp(_l + min, 1.0);
                          _t = (_t + dy(d)).clamp(0.0, _b - min);
                        }, box),
                        _handle(_l, _b, (d, _) {
                          _l = (_l + dx(d)).clamp(0.0, _r - min);
                          _b = (_b + dy(d)).clamp(_t + min, 1.0);
                        }, box),
                        _handle(_r, _b, (d, _) {
                          _r = (_r + dx(d)).clamp(_l + min, 1.0);
                          _b = (_b + dy(d)).clamp(_t + min, 1.0);
                        }, box),
                      ]);
                    }),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(children: [
                  Expanded(
                    child: OutlinedButton(
                      style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          minimumSize: const Size(0, 54),
                          side: const BorderSide(color: Colors.white54)),
                      onPressed: _busy ? null : () => Navigator.of(context).pop(),
                      child: const Text('Use whole photo', style: TextStyle(fontSize: 17)),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton(
                      style: FilledButton.styleFrom(minimumSize: const Size(0, 54)),
                      onPressed: _busy ? null : _crop,
                      child: Text(_busy ? 'Cropping…' : 'Crop', style: const TextStyle(fontSize: 17)),
                    ),
                  ),
                ]),
              ),
            ]),
    );
  }
}

class _Shade extends CustomPainter {
  final Rect frac;

  _Shade(this.frac);

  @override
  void paint(Canvas canvas, Size size) {
    final r = Rect.fromLTRB(
        frac.left * size.width, frac.top * size.height, frac.right * size.width, frac.bottom * size.height);
    final outside = Path()
      ..addRect(Offset.zero & size)
      ..addRect(r)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(outside, Paint()..color = Colors.black54);
    canvas.drawRect(r, Paint()
      ..color = C.blue
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2);
  }

  @override
  bool shouldRepaint(_Shade old) => old.frac != frac;
}

/// Crops JPEG/PNG bytes to [rect] (fractions left, top, right, bottom). Runs in the background.
Uint8List? _cropJpeg((Uint8List, List<double>) a) {
  final im = img.decodeImage(a.$1);
  if (im == null) return null;
  final r = a.$2;
  final x = (r[0] * im.width).round(), y = (r[1] * im.height).round();
  final w = math.max(1, ((r[2] - r[0]) * im.width).round()), h = math.max(1, ((r[3] - r[1]) * im.height).round());
  return Uint8List.fromList(img.encodeJpg(img.copyCrop(im, x: x, y: y, width: w, height: h), quality: 92));
}

/// Drag each answer to where it's written on the form. With a [report], its answers are shown.
class PlaceScreen extends StatefulWidget {
  final String formId;
  final FilledReport? report;

  /// The form to edit when it isn't in the list any more (a report's own copy).
  final ReportForm? form;

  const PlaceScreen({super.key, required this.formId, this.report, this.form});

  @override
  State<PlaceScreen> createState() => _PlaceScreenState();
}

class _PlaceScreenState extends State<PlaceScreen> {
  Store get store => Store.instance;
  late final ReportForm? f = store.reportForms.where((x) => x.id == widget.formId).firstOrNull ?? widget.form;
  final Map<String, (int, int)> _sizes = {};

  @override
  void initState() {
    super.initState();
    final form = f;
    if (form == null) return;
    for (final p in form.pagePictures) {
      FormFiles.pictureSize(p).then((s) {
        if (mounted) setState(() => _sizes[p] = s);
      }, onError: (_) {});
    }
  }

  void _save() {
    store.save();
    setState(() {});
  }

  String _text(ReportQuestion q) {
    final r = widget.report, form = f!;
    if (r == null) return q.label;
    final v = FormFill.onForm(q, r, form);
    return v.isEmpty ? q.label : v;
  }

  Widget _page(int page, String file, double width) {
    final form = f!;
    final s = _sizes[file];
    if (s == null) return const SizedBox(height: 200, child: Center(child: CircularProgressIndicator()));
    final height = width * s.$2 / s.$1;
    final font = form.textSize * height * form.fontScale;
    return SizedBox(
      width: width,
      height: height,
      child: Stack(clipBehavior: Clip.none, children: [
        Positioned.fill(child: Image.file(File(store.photoFile(file).path), fit: BoxFit.fill)),
        for (final q in form.questions)
          if (q.place != null && q.place!.page == page) ..._answer(q, width, height, font),
      ]),
    );
  }

  static const _ink = Color(0xFF0B2E83);

  /// One answer drawn as the PDF will: fitted to its line, a long one wrapped over its ruled lines. Each piece
  /// can be dragged; the whole answer moves together.
  List<Widget> _answer(ReportQuestion q, double width, double height, double font) {
    final p = q.place!;
    final text = _text(q).trim();
    final lineWidth = p.width * width;
    // Measured at size 100 and scaled: tiny sizes measure badly.
    double at1(String t) {
      final tp = TextPainter(
          text: TextSpan(text: t, style: const TextStyle(fontSize: 100, color: _ink)), textDirection: TextDirection.ltr)
        ..layout();
      return tp.width / 100;
    }

    final pieces = <(double, double, double, String)>[]; // x, baseline y, size, text
    if (q.kind == ReportQuestion.long) {
      final ys = [p.y, ...p.rows];
      final start = p.rowFit == null ? font : math.min(font, p.rowFit! * height);
      final (size, lines) = Fit.rows(text, lineWidth, ys.length, start, at1);
      final gap = ys.length > 1 ? ys[ys.length - 1] - ys[ys.length - 2] : 1.7 * size / height;
      for (var i = 0; i < lines.length; i++) {
        pieces.add((p.x * width, (i < ys.length ? ys[i] : ys.last + gap * (i - ys.length + 1)) * height, size, lines[i]));
      }
    } else {
      final one = text.replaceAll('\n', ' ');
      pieces.add((p.x * width, p.y * height, Fit.oneLine(one, lineWidth, font, at1), one));
    }
    return [
      for (final (x, y, size, t) in pieces)
        Positioned(
          left: x,
          top: y - size * 1.0,
          child: GestureDetector(
            onPanUpdate: (d) => setState(() => q.place = q.place!.moved(d.delta.dx / width, d.delta.dy / height)),
            onPanEnd: (_) => _save(),
            child: Container(
              decoration: BoxDecoration(
                color: const Color(0x55FFE082),
                border: Border.all(color: const Color(0xFFE0A800), width: 1),
                borderRadius: BorderRadius.circular(3),
              ),
              child: Text(t,
                  maxLines: 1,
                  softWrap: false,
                  style: TextStyle(fontSize: math.max(5, size), height: 1.15, color: _ink)),
            ),
          ),
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final form = f;
    if (form == null) return const Scaffold();
    final pages = form.pagePictures;
    final notPlaced = form.questions.where((q) => q.place == null).toList();
    return Scaffold(
      backgroundColor: C.bg,
      appBar: AppBar(
        backgroundColor: C.bg,
        title: const Text('Place the answers'),
        actions: [
          TextButton(
            onPressed: () {
              form.fontScale = (form.fontScale - 0.1).clamp(0.6, 2.0);
              _save();
            },
            child: const Text('A-', style: TextStyle(fontSize: 18)),
          ),
          TextButton(
            onPressed: () {
              form.fontScale = (form.fontScale + 0.1).clamp(0.6, 2.0);
              _save();
            },
            child: const Text('A+', style: TextStyle(fontSize: 22)),
          ),
        ],
      ),
      body: Column(children: [
        Expanded(
          child: LayoutBuilder(
            builder: (context, c) => ListView(padding: const EdgeInsets.fromLTRB(8, 0, 8, 20), children: [
              const Padding(
                padding: EdgeInsets.fromLTRB(8, 4, 8, 10),
                child: Text('Press and drag an answer to move it onto its line. A- and A+ change the size of all '
                    'the answers.',
                    style: TextStyle(color: C.grey, fontSize: 15)),
              ),
              for (var i = 0; i < pages.length; i++)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: _page(i, pages[i], c.maxWidth - 16),
                ),
              if (notPlaced.isNotEmpty) ...[
                const Padding(
                  padding: EdgeInsets.fromLTRB(8, 10, 8, 6),
                  child: Text('NOT ON THE FORM YET',
                      style: TextStyle(color: C.grey, fontSize: 13, letterSpacing: 0.8, fontWeight: FontWeight.bold)),
                ),
                for (final q in notPlaced)
                  ListTile(
                    title: Text(q.label),
                    subtitle: const Text('Tap to put it on the first page, then drag it'),
                    leading: const Icon(Icons.add_circle_outline, color: C.blue),
                    onTap: () {
                      q.place = const Place(0, 0.4, 0.5);
                      _save();
                    },
                  ),
              ],
            ]),
          ),
        ),
        bottomPill('Done', () => Navigator.of(context).pop()),
      ]),
    );
  }
}
