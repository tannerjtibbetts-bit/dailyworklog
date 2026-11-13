// Files for documents and report forms: copying them into the app (the photo folder, so backups include
// them), turning PDF pages into pictures, reading their text, and speaking answers.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:printing/printing.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'package:uuid/uuid.dart';

import '../data/form_lines.dart';
import '../data/forms.dart';
import '../data/store.dart';
import '../ui/common.dart';

class FormFiles {
  FormFiles._();

  static String newName(String ext) => 'doc-${const Uuid().v4()}.${ext.toLowerCase()}';

  static String extOf(String name) => name.contains('.') ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : '';

  /// The file name without its folder and extension ("Lift plan" from ".../Lift plan.pdf").
  static String baseName(String path) {
    final n = path.split(RegExp(r'[\\/]')).last;
    return n.contains('.') ? n.substring(0, n.lastIndexOf('.')) : n;
  }

  /// Lets the person pick a file and copies it into the app. Returns (saved name, original name) or null.
  static Future<(String, String)?> pickFile(BuildContext context, {bool formsOnly = false}) async {
    List<PlatformFile> r;
    try {
      r = await FilePicker.pickFiles(
        type: formsOnly ? FileType.custom : FileType.any,
        allowedExtensions: formsOnly ? const ['pdf', 'docx', 'xlsx', 'jpg', 'jpeg', 'png', 'heic'] : null,
      );
    } catch (_) {
      if (context.mounted) toast(context, "Couldn't open the file picker", long: true);
      return null;
    }
    final path = r.isEmpty ? null : r.first.path;
    if (path == null) return null;
    final ext = extOf(path);
    final name = newName(ext.isEmpty ? 'bin' : ext);
    try {
      final out = Store.instance.photoFile(name);
      await out.parent.create(recursive: true);
      await File(path).copy(out.path);
    } catch (_) {
      if (context.mounted) toast(context, "Couldn't copy that file", long: true);
      return null;
    }
    return (name, baseName(path));
  }

  /// Each page of a PDF in the app as a picture saved next to it. Returns the picture names.
  static Future<List<String>> pdfPages(String pdfName) async {
    final bytes = await Store.instance.photoFile(pdfName).readAsBytes();
    final names = <String>[];
    await for (final page in Printing.raster(bytes, dpi: 200)) {
      final n = newName('png');
      await Store.instance.photoFile(n).writeAsBytes(await page.toPng(), flush: true);
      names.add(n);
    }
    return names;
  }

  /// A picture's width and height in pixels.
  static Future<(int, int)> pictureSize(String name) async {
    final buf = await ui.ImmutableBuffer.fromFilePath(Store.instance.photoFile(name).path);
    final d = await ui.ImageDescriptor.encoded(buf);
    final size = (d.width, d.height);
    d.dispose();
    buf.dispose();
    return size;
  }

  /// The text lines in pictures (in page order), read on the phone, with where each line is on its page.
  static Future<List<FormLine>> readPictures(List<String> names) async {
    final rec = TextRecognizer(script: TextRecognitionScript.latin);
    final lines = <FormLine>[];
    try {
      for (var page = 0; page < names.length; page++) {
        final (w, h) = await pictureSize(names[page]);
        final t = await rec.processImage(InputImage.fromFilePath(Store.instance.photoFile(names[page]).path));
        for (final b in t.blocks) {
          for (final l in b.lines) {
            final r = l.boundingBox;
            lines.add(FormLine(l.text,
                page: page, left: r.left / w, top: r.top / h, right: r.right / w, bottom: r.bottom / h));
          }
        }
      }
    } finally {
      await rec.close();
    }
    return lines;
  }

  /// Reads a form's text into questions. [files] are in the app; [kind] is the source's kind.
  /// For a PDF, the page pictures are added to the form's files so it can be viewed.
  static Future<ReadForm> readForm(ReportForm form) async {
    switch (form.kind) {
      case Doc.pdf:
        final pages = await pdfPages(form.files.first);
        form.files = [form.files.first, ...pages];
        return FormReader.readLines(await readPictures(pages), segments: await findLines(pages));
      case Doc.docx:
        return FormReader.read(FormReader.docxLines(await Store.instance.photoFile(form.files.first).readAsBytes()));
      case Doc.xlsx:
        return FormReader.read(FormReader.xlsxLines(await Store.instance.photoFile(form.files.first).readAsBytes()));
      default:
        return FormReader.readLines(await readPictures(form.files), segments: await findLines(form.files));
    }
  }

  /// The fill-in lines on each page picture (worked out in the background).
  static Future<List<Segment>> findLines(List<String> pictures) async {
    final out = <Segment>[];
    for (var page = 0; page < pictures.length; page++) {
      try {
        final bytes = await Store.instance.photoFile(pictures[page]).readAsBytes();
        out.addAll(await compute(_findLines, (bytes, page)));
      } catch (_) {
        // no lines found on this page; answers go beside their labels
      }
    }
    return out;
  }

  /// The pictures to show for a form or document: photos as they are, a PDF's pages.
  static Future<List<String>> pictures(List<String> files, String kind) async {
    if (kind == Doc.photo) return files;
    if (kind == Doc.pdf) {
      final pngs = files.where((f) => extOf(f) == 'png').toList();
      if (pngs.isNotEmpty) return pngs;
      return pdfPages(files.first);
    }
    return const [];
  }

  static void deleteFiles(Iterable<String> names) {
    for (final n in names) {
      try {
        Store.instance.photoFile(n).deleteSync();
      } catch (_) {}
    }
  }
}

/// Speaking an answer: turns speech into text with the phone's speech recognition.
class Mic {
  Mic._();

  static final _speech = SpeechToText();
  static bool? _ready;

  static bool get listening => _speech.isListening;

  /// Listens once; [onText] gets the words heard so far (the final words when [done]).
  static Future<bool> listen(BuildContext context, void Function(String words, bool done) onText,
      {VoidCallback? onStop}) async {
    try {
      _ready ??= await _speech.initialize(
        onStatus: (s) {
          if ((s == 'done' || s == 'notListening') && onStop != null) onStop();
        },
        onError: (_) => onStop?.call(),
      );
    } catch (_) {
      _ready = false;
    }
    if (_ready != true) {
      if (context.mounted) toast(context, "Speech isn't available on this phone. Use the mic on your keyboard.", long: true);
      return false;
    }
    await _speech.listen(
      onResult: (r) => onText(r.recognizedWords, r.finalResult),
      listenOptions: SpeechListenOptions(
        partialResults: true,
        cancelOnError: true,
        pauseFor: const Duration(seconds: 4),
        listenFor: const Duration(minutes: 1),
      ),
    );
    return true;
  }

  static Future<void> stop() async {
    try {
      await _speech.stop();
    } catch (_) {}
  }
}

List<Segment> _findLines((Uint8List, int) a) => FormLines.find(a.$1, a.$2);

