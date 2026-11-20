// Who owns Daily Work Log, shown in the app and hidden in every report file, and the app's own
// mark. The mark is a made-up code that exists only in this app: if it turns up in someone else's
// app or files, they came from this one.
import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';

class Ownership {
  Ownership._();

  static const owner = 'Tanner Tibbetts';
  static const year = 2026;
  static const notice = '© $year $owner. All rights reserved.';

  /// Shown at the bottom of the opening, sign-in and Support screens.
  static const credit = 'App created by $owner\n$notice';

  /// The app's own mark. Never change it: old reports carry it.
  static const mark = 'DWL-PG9H-XMEU-PBZ4-HNFK';

  /// Hidden in every report file's properties (and as invisible text in PDFs).
  static const tag = 'Made with Daily Work Log. $notice $mark';

  /// A short, readable fingerprint of an owner key (its DER bytes), e.g. "3F9A-0C21-77BE-D410".
  static String fingerprint(List<int> keyDer) {
    final h = sha256.convert(keyDer).bytes.take(8).map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    final u = h.toUpperCase();
    return [for (var i = 0; i < u.length; i += 4) u.substring(i, i + 4)].join('-');
  }

  /// Word and Excel files: the docProps parts that carry the tag. [title] is the file's title.
  static String coreXml(String title, String by, DateTime at) {
    String esc(String s) => const HtmlEscape(HtmlEscapeMode.element).convert(s);
    final when = at.toUtc().toIso8601String().split('.').first;
    return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
        'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
        '<dc:title>${esc(title)}</dc:title><dc:creator>${esc(by)}</dc:creator>'
        '<cp:keywords>Daily Work Log $mark</cp:keywords><dc:description>${esc(tag)}</dc:description>'
        '<dcterms:created xsi:type="dcterms:W3CDTF">${when}Z</dcterms:created></cp:coreProperties>';
  }

  static const appXml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
      '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">'
      '<Application>Daily Work Log</Application><Company>$notice</Company></Properties>';

  /// Lines to add to [Content_Types].xml and _rels/.rels for the two parts above.
  static const contentTypes = '<Override PartName="/docProps/core.xml" '
      'ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
      '<Override PartName="/docProps/app.xml" '
      'ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>';
  static const rels = '<Relationship Id="rIdCore" '
      'Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" '
      'Target="docProps/core.xml"/><Relationship Id="rIdApp" '
      'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" '
      'Target="docProps/app.xml"/>';

  /// Puts the hidden tag into a Word or Excel file's properties, then zips it.
  static Uint8List tagged(Archive zip, {String title = 'Daily Work Log', String by = ''}) {
    String text(String name) => utf8.decode(zip.files.firstWhere((f) => f.name == name).content);
    zip
      ..add(ArchiveFile.string('[Content_Types].xml', text('[Content_Types].xml').replaceFirst('</Types>', '$contentTypes</Types>')))
      ..add(ArchiveFile.string('_rels/.rels', text('_rels/.rels').replaceFirst('</Relationships>', '$rels</Relationships>')))
      ..add(ArchiveFile.string('docProps/core.xml', coreXml(title, by, DateTime.now())))
      ..add(ArchiveFile.string('docProps/app.xml', appXml));
    return ZipEncoder().encodeBytes(zip);
  }
}
