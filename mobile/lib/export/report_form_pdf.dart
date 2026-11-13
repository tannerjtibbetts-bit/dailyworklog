// A filled-in report form (Apprentice reports, Disciplinary action) as a PDF to print, sign and hand in:
// the answers, the ratings table with the point system, and the form's signature lines.
import 'dart:typed_data';

import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../data/forms.dart';

class ReportFormPdf {
  ReportFormPdf._();

  static const _navy = PdfColor.fromInt(0xFF14233A);
  static const _grey = PdfColor.fromInt(0xFF6B7685);
  static const _line = PdfColor.fromInt(0xFFB8C0CA);

  /// Text the built-in PDF font can draw: curly quotes, dashes and the like made plain.
  static String plain(String s) {
    final t = s
        .replaceAll(RegExp('[‘’‛′]'), "'")
        .replaceAll(RegExp('[“”‟″]'), '"')
        .replaceAll(RegExp('[–—−]'), '-')
        .replaceAll('…', '...')
        .replaceAll('•', '-')
        .replaceAll(' ', ' ');
    return String.fromCharCodes(t.runes.where((r) => r == 10 || (r >= 32 && r <= 0xFF)));
  }

  static String fileName(FilledReport r, DateTime when) {
    final d = '${when.year}-${when.month.toString().padLeft(2, '0')}-${when.day.toString().padLeft(2, '0')}';
    final base = '${r.formName.isEmpty ? 'Report' : r.formName} - ${r.person} $d';
    return '${base.replaceAll(RegExp(r'[\\/:*?"<>|]'), '-')}.pdf';
  }

  static const _ink = PdfColor.fromInt(0xFF0B2E83);

  /// The form's own pages with the answers written on their blanks (see Place the answers). [pictures] are the
  /// page pictures' bytes, in page order. Answers that have no place go on a last page, "More answers".
  static Future<Uint8List> writeOnForm(ReportForm form, FilledReport r, List<Uint8List> pictures) async {
    final doc = pw.Document(title: plain(form.name), author: 'Daily Work Log');
    final font = PdfFont.helvetica(doc.document);
    double at1(String t) => t.isEmpty ? 0 : font.stringMetrics(t).advanceWidth;
    for (var page = 0; page < pictures.length; page++) {
      final image = pw.MemoryImage(pictures[page]);
      const w = 612.0;
      final h = w * (image.height ?? 792) / (image.width ?? 612);
      final base = form.textSize * h * form.fontScale;
      // Text is placed by its baseline: the top of the text box is this far above it.
      pw.Widget at(double x, double baseline, double size, String text) => pw.Positioned(
            left: x,
            top: baseline - size * 0.78,
            child: pw.Text(text, style: pw.TextStyle(fontSize: size, color: _ink)),
          );
      final writing = <pw.Widget>[];
      for (final q in form.questions) {
        final p = q.place;
        if (p == null || p.page != page) continue;
        final text = plain(FormFill.onForm(q, r, form)).trim();
        if (text.isEmpty) continue;
        final width = p.width * w;
        if (q.kind == ReportQuestion.long) {
          final ys = [p.y, ...p.rows];
          final (size, lines) = Fit.rows(text, width, ys.length, base, at1);
          final gap = ys.length > 1 ? ys[ys.length - 1] - ys[ys.length - 2] : 1.7 * size / h;
          for (var i = 0; i < lines.length; i++) {
            final y = i < ys.length ? ys[i] : ys.last + gap * (i - ys.length + 1);
            writing.add(at(p.x * w, y * h, size, lines[i]));
          }
        } else {
          final one = text.replaceAll('\n', ' ');
          writing.add(at(p.x * w, p.y * h, Fit.oneLine(one, width, base, at1), one));
        }
      }
      doc.addPage(pw.Page(
        pageFormat: PdfPageFormat(w, h, marginAll: 0),
        build: (c) => pw.Stack(children: [
          pw.Image(image, width: w, height: h, fit: pw.BoxFit.fill),
          ...writing,
        ]),
      ));
    }
    final more = [
      for (final q in form.questions)
        if ((q.place == null || q.place!.page >= pictures.length) && FormFill.shown(q, r, form).trim().isNotEmpty) q,
    ];
    if (more.isNotEmpty) {
      doc.addPage(pw.MultiPage(
        pageFormat: PdfPageFormat.letter.copyWith(marginLeft: 43, marginRight: 43, marginTop: 43, marginBottom: 43),
        build: (c) => [
          pw.Text('More answers', style: pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold, color: _navy)),
          pw.SizedBox(height: 4),
          pw.Text(plain('${form.name} - ${r.person}'), style: const pw.TextStyle(fontSize: 10, color: _grey)),
          pw.SizedBox(height: 10),
          for (final q in more)
            pw.Padding(
              padding: const pw.EdgeInsets.only(bottom: 6),
              child: pw.RichText(
                text: pw.TextSpan(children: [
                  pw.TextSpan(
                      text: '${plain(q.label)}: ',
                      style: pw.TextStyle(fontSize: 10.5, fontWeight: pw.FontWeight.bold, color: _navy)),
                  pw.TextSpan(
                      text: plain(FormFill.shown(q, r, form)), style: const pw.TextStyle(fontSize: 10.5, color: _navy)),
                ]),
              ),
            ),
        ],
      ));
    }
    return doc.save();
  }

  static Future<Uint8List> write(ReportForm form, FilledReport r, DateTime made) async {
    final doc = pw.Document(title: plain(form.name), author: 'Daily Work Log');
    pw.TextStyle st(double size, {bool bold = false, PdfColor color = _navy}) =>
        pw.TextStyle(fontSize: size, fontWeight: bold ? pw.FontWeight.bold : pw.FontWeight.normal, color: color);
    String a(ReportQuestion q) {
      final v = FormFill.shown(q, r, form).trim();
      return v.isEmpty ? '-' : plain(v);
    }

    final body = <pw.Widget>[];
    final ratings = form.questions.where((q) => q.kind == ReportQuestion.rating).toList();
    final average = form.questions.where((q) => q.kind == ReportQuestion.average).toList();
    var ratingsDone = false;

    pw.Widget ratingTable() {
      final rows = <pw.TableRow>[
        for (var i = 0; i < ratings.length; i++)
          pw.TableRow(children: [
            pw.Padding(padding: const pw.EdgeInsets.all(4), child: pw.Text('${i + 1}. ${plain(ratings[i].label)}', style: st(10))),
            pw.Padding(padding: const pw.EdgeInsets.all(4), child: pw.Text(a(ratings[i]), style: st(10))),
          ]),
        if (average.isNotEmpty)
          pw.TableRow(children: [
            pw.Padding(padding: const pw.EdgeInsets.all(4), child: pw.Text('Overall average', style: st(10, bold: true))),
            pw.Padding(padding: const pw.EdgeInsets.all(4), child: pw.Text(a(average.first), style: st(10, bold: true))),
          ]),
      ];
      final scale = form.scale();
      return pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
        pw.Table(
          border: pw.TableBorder.all(color: _line, width: 0.6),
          columnWidths: const {0: pw.FlexColumnWidth(3), 1: pw.FlexColumnWidth(1.4)},
          children: rows,
        ),
        if (scale.isNotEmpty)
          pw.Padding(
            padding: const pw.EdgeInsets.only(top: 4),
            child: pw.Text('Point system: ${plain(scale.map((c) => c.legend).join(' · ').replaceAll('·', '-'))}',
                style: st(8.5, color: _grey)),
          ),
        pw.SizedBox(height: 10),
      ]);
    }

    for (final q in form.questions) {
      if (q.kind == ReportQuestion.rating || q.kind == ReportQuestion.average) {
        if (!ratingsDone && (ratings.isNotEmpty || average.isNotEmpty)) {
          ratingsDone = true;
          body.add(ratingTable());
        }
        continue;
      }
      if (q.kind == ReportQuestion.long) {
        body.add(pw.Padding(
          padding: const pw.EdgeInsets.only(top: 4, bottom: 8),
          child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
            pw.Text(plain(q.label), style: st(10.5, bold: true)),
            pw.SizedBox(height: 3),
            pw.Text(a(q), style: st(10.5)),
          ]),
        ));
      } else {
        body.add(pw.Padding(
          padding: const pw.EdgeInsets.only(bottom: 5),
          child: pw.RichText(
            text: pw.TextSpan(children: [
              pw.TextSpan(text: '${plain(q.label)}: ', style: st(10.5, bold: true)),
              pw.TextSpan(text: a(q), style: st(10.5)),
            ]),
          ),
        ));
      }
    }

    final sigs = form.signatures.isEmpty ? ReportForm.defaultSignatures(form.group) : form.signatures;
    pw.Widget sig(String label) => pw.Expanded(
          child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
            pw.SizedBox(height: 30),
            pw.Container(height: 0.8, color: _navy),
            pw.SizedBox(height: 3),
            pw.Text(plain(label), style: st(9)),
            pw.SizedBox(height: 14),
            pw.Text('Date: ______________________', style: st(9, color: _grey)),
          ]),
        );
    final sigRows = <pw.Widget>[
      for (var i = 0; i < sigs.length; i += 2)
        pw.Padding(
          padding: const pw.EdgeInsets.only(bottom: 18),
          child: pw.Row(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
            sig(sigs[i]),
            pw.SizedBox(width: 36),
            if (i + 1 < sigs.length) sig(sigs[i + 1]) else pw.Expanded(child: pw.SizedBox()),
          ]),
        ),
    ];

    final made0 = FormFill.date(made);
    doc.addPage(pw.MultiPage(
      pageFormat: PdfPageFormat.letter.copyWith(
          marginLeft: 43, marginRight: 43, marginTop: 43, marginBottom: 43),
      footer: (c) => pw.Row(mainAxisAlignment: pw.MainAxisAlignment.spaceBetween, children: [
        pw.Text('Filled in with Daily Work Log on $made0', style: st(8, color: _grey)),
        pw.Text('Page ${c.pageNumber} of ${c.pagesCount}', style: st(8, color: _grey)),
      ]),
      build: (c) => [
        pw.Text(plain(form.name.isEmpty ? r.formName : form.name), style: st(18, bold: true)),
        pw.SizedBox(height: 4),
        pw.Text(
          plain('${ReportForm.personWord(form.group)}: ${r.person}'
              '${r.project.isEmpty ? '' : '     Project: ${r.project}'}'
              '     Date: ${FormFill.date(DateTime.fromMillisecondsSinceEpoch(r.createdAt))}'),
          style: st(10, color: _grey),
        ),
        pw.Divider(color: _line, thickness: 0.8, height: 18),
        ...body,
        pw.SizedBox(height: 10),
        // Keep the signature lines together.
        pw.Column(children: sigRows),
      ],
    ));
    return doc.save();
  }
}
