// Report, overtime, attendance and sea can exports. The file is built in the background, then offered to
// share/email or save to the phone.
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../data/fmt.dart';
import '../data/models.dart';
import '../data/store.dart';
import '../ui/common.dart';
import 'attendance_xlsx.dart';
import 'overtime_xlsx.dart';
import 'report_docx.dart';
import 'report_pdf.dart';
import 'seacan_xlsx.dart';

class Export {
  Export._();

  static const pdf = 'application/pdf';
  static const docx = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  static const xlsx = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  static const csv = 'text/csv';

  /// The day's report as a PDF ([word] false) or Word document ([word] true), then the share sheet.
  static Future<void> report(BuildContext context, Project project, DailyLog log, {required bool word}) async {
    final f = await _buildReport(context, project, log, word);
    if (f == null || !context.mounted) return;
    final mime = word ? docx : pdf;
    await menu(context, '${_name(f)} is ready', [
      if (!word)
        SheetItem(
          Icons.send,
          project.emails.isEmpty ? 'Email to report list (set up)' : 'Email to report list (${project.emails.length})',
          () async {
            if (project.emails.isEmpty) {
              await editEmailList(context, project);
              if (project.emails.isEmpty || !context.mounted) return;
            }
            await _sendEmail(context, f, project, log);
          },
        ),
      SheetItem(Icons.share, 'Share or email', () => _share(context, f, mime)),
      SheetItem(Icons.download, 'Save to phone', () => _saveTo(context, f, mime)),
    ]);
  }

  /// Builds the PDF, then shares it addressed to the project's report list.
  static Future<void> email(BuildContext context, Project project, DailyLog log) async {
    if (project.emails.isEmpty) {
      await editEmailList(context, project);
      if (project.emails.isEmpty || !context.mounted) return;
    }
    final f = await _buildReport(context, project, log, false);
    if (f == null || !context.mounted) return;
    await _sendEmail(context, f, project, log);
  }

  /// Just the overtime sheet, as PDF or Excel.
  static Future<void> overtime(BuildContext context, Project project, {required bool excel}) async {
    final store = Store.instance;
    final by = store.me();
    final at = nowMs();
    final name =
        Fmt.fileSafe(
          'Weekend overtime ${Fmt.weekendDay(project.overtime.weekOf, 0)}'
          '${project.name.isEmpty ? '' : ' ${project.name}'}',
        ) +
        (excel ? '.xlsx' : '.pdf');
    final f = await _build(
      context,
      excel ? 'Building Excel sheet…' : 'Building PDF…',
      name,
      'Couldn\'t build the file. Please try again.',
      () => compute(_overtimeJob, _OtJob(_slim(project), by, at, excel)),
    );
    if (f == null || !context.mounted) return;
    final mime = excel ? xlsx : pdf;
    await menu(context, '${_name(f)} is ready', [
      SheetItem(Icons.share, 'Share or email', () => _share(context, f, mime)),
      SheetItem(Icons.download, 'Save to phone', () => _saveTo(context, f, mime)),
    ]);
  }

  /// Just the attendance sheet, as PDF or Excel.
  static Future<void> attendance(BuildContext context, Project project, {required bool excel}) async {
    final store = Store.instance;
    final by = store.me();
    final at = nowMs();
    final name =
        Fmt.fileSafe(
          'Attendance ${Fmt.logDate(project.attendance.date)}'
          '${project.name.isEmpty ? '' : ' ${project.name}'}',
        ) +
        (excel ? '.xlsx' : '.pdf');
    final f = await _build(
      context,
      excel ? 'Building Excel sheet…' : 'Building PDF…',
      name,
      'Couldn\'t build the file. Please try again.',
      () => compute(_attendanceJob, _OtJob(_slim(project), by, at, excel)),
    );
    if (f == null || !context.mounted) return;
    final mime = excel ? xlsx : pdf;
    await menu(context, '${_name(f)} is ready', [
      SheetItem(Icons.share, 'Share or email', () => _share(context, f, mime)),
      SheetItem(Icons.download, 'Save to phone', () => _saveTo(context, f, mime)),
    ]);
  }

  /// Just the sea can list: [format] is 'pdf', 'xlsx', 'csv' or 'docx'.
  static Future<void> seaCans(BuildContext context, Project project, {required String format}) async {
    final store = Store.instance;
    final by = store.me();
    final at = nowMs();
    final name = seaCanFileName(project, at, format);
    final f = await _build(
      context,
      switch (format) {
        'xlsx' => 'Building Excel sheet…',
        'csv' => 'Building CSV…',
        'docx' => 'Building Word document…',
        _ => 'Building PDF…',
      },
      name,
      'Couldn\'t build the file. Please try again.',
      () => compute(_seaCanJob, _CanJob(_slim(project), by, at, format)),
    );
    if (f == null || !context.mounted) return;
    final mime = switch (format) {
      'xlsx' => xlsx,
      'csv' => csv,
      'docx' => docx,
      _ => pdf,
    };
    await menu(context, '${_name(f)} is ready', [
      SheetItem(Icons.share, 'Share or email', () => _share(context, f, mime)),
      SheetItem(Icons.download, 'Save to phone', () => _saveTo(context, f, mime)),
    ]);
  }

  /// e.g. "Sea can movements - B44 - 16-10-2026.csv" (the date written like the report titles).
  static String seaCanFileName(Project p, int at, String format) {
    final date = Fmt.logDate(Fmt.isoOf(DateTime.fromMillisecondsSinceEpoch(at)));
    return '${Fmt.fileSafe('Sea can movements${p.name.isEmpty ? '' : ' - ${p.name}'} - $date')}.$format';
  }

  /// The sea can list as CSV in the master sheet's columns: quoted where needed, CRLF line ends, UTF-8 with a BOM so Excel reads it right.
  static Uint8List seaCanCsv(Project p) {
    String field(String s) => s.contains(RegExp('[",\r\n]')) ? '"${s.replaceAll('"', '""')}"' : s;
    // The same columns as the master sheet (sea cans, then blue bins), so lines paste straight in.
    final b = StringBuffer('\uFEFFMoves,Can #,From Location,To Location,Weight,Bin #,From Location,To Location\r\n');
    final cans = p.filledSeaCans();
    for (var i = 0; i < cans.length; i++) {
      final m = cans[i];
      b.write(
        '${['${i + 1}', m.can.trim(), m.fromLocation(), m.toLocation(), m.weight.trim(), '', '', ''].map(field).join(',')}\r\n',
      );
    }
    return Uint8List.fromList(utf8.encode(b.toString()));
  }

  /// "Report email list" prompt: emails separated by commas.
  static Future<void> editEmailList(BuildContext context, Project p) async {
    final text = await prompt(
      context,
      'Report email list',
      'Emails, separated by commas',
      Fmt.join(p.emails),
      'Save',
      keyboard: TextInputType.emailAddress,
      caps: TextCapitalization.none,
    );
    if (text == null) return;
    p.emails = parseEmails(text);
    Store.instance.save();
    if (context.mounted) {
      toast(
        context,
        p.emails.isEmpty
            ? 'No valid email addresses found'
            : 'Saved ${Fmt.plural(p.emails.length, 'address', 'addresses')}',
      );
    }
  }

  /// Valid, distinct addresses from text separated by commas, semicolons or spaces.
  static List<String> parseEmails(String text) {
    final list = <String>[];
    final ok = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
    for (final e in text.split(RegExp(r'[\s,;]+'))) {
      final t = e.trim();
      if (ok.hasMatch(t) && !list.contains(t)) list.add(t);
    }
    return list;
  }

  /// Email subject for a report, e.g. "Daily work log 05/10/2026 - B44 Zone 2".
  static String emailSubject(Project p, DailyLog log) => log.displayTitle() + (p.name.isEmpty ? '' : ' - ${p.name}');

  /// Email body for a report.
  static String emailBody(Project p, DailyLog log, String me) =>
      '${emailSubject(p, log)}: ${log.doneCount()}/${log.tasks.length} fields completed.\nReport attached.\n\n$me';

  /// Report file name, e.g. "Daily work log 05-10-2026.pdf".
  static String reportFileName(DailyLog log, bool word) => Fmt.fileSafe(log.displayTitle()) + (word ? '.docx' : '.pdf');

  // ----- building -----

  /// Just what the exports print, so the background job doesn't copy every checklist.
  static Project _slim(Project p) => Project()
    ..name = p.name
    ..location = p.location
    ..overtime = p.overtime
    ..attendance = p.attendance
    ..seaCans = p.seaCans
    ..seaCanInclude = p.seaCanInclude;

  static Future<File?> _buildReport(BuildContext context, Project p, DailyLog log, bool word) {
    final store = Store.instance;
    final job = _ReportJob(_slim(p), log, store.me(), nowMs(), store.photoDir.path, word);
    return _build(
      context,
      word ? 'Building Word report…' : 'Building PDF report…',
      reportFileName(log, word),
      'Couldn\'t build the report. Please try again.',
      () => compute(_reportJob, job),
    );
  }

  /// Shows a progress dialog while [make] runs (in the background), then writes the file.
  static Future<File?> _build(
    BuildContext context,
    String message,
    String fileName,
    String failed,
    Future<Uint8List> Function() make,
  ) async {
    final nav = Navigator.of(context, rootNavigator: true);
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      useRootNavigator: true,
      builder: (c) => PopScope(
        canPop: false,
        child: AlertDialog(
          content: Row(
            children: [
              const SizedBox(width: 36, height: 36, child: CircularProgressIndicator()),
              const SizedBox(width: 24),
              Expanded(child: Text(message, style: const TextStyle(fontSize: 17))),
            ],
          ),
        ),
      ),
    );
    File? out;
    try {
      final bytes = await make();
      final dir = Directory('${(await getTemporaryDirectory()).path}/exports');
      await dir.create(recursive: true);
      out = File('${dir.path}/$fileName');
      await out.writeAsBytes(bytes, flush: true);
    } catch (e) {
      debugPrint('Export failed: $e');
      out = null;
    }
    nav.pop();
    if (out == null && context.mounted) toast(context, failed, long: true);
    return out;
  }

  // ----- sending -----

  static String _name(File f) => f.uri.pathSegments.last;

  static Rect? _origin(BuildContext context) {
    // iPad share sheets need somewhere to point at.
    final box = context.findRenderObject();
    if (box is RenderBox && box.hasSize) return box.localToGlobal(Offset.zero) & box.size;
    return null;
  }

  static Future<void> _share(BuildContext context, File f, String mime) async {
    try {
      await SharePlus.instance.share(
        ShareParams(
          files: [XFile(f.path, mimeType: mime)],
          subject: _name(f).replaceFirst(RegExp(r'\.[a-z]+$'), ''),
          sharePositionOrigin: _origin(context),
        ),
      );
    } catch (_) {
      if (context.mounted) toast(context, 'Couldn\'t open the share menu', long: true);
    }
  }

  /// The share sheet can't fill in who it's to, so the addresses are copied for pasting into "To"
  /// and listed at the top of the message.
  static Future<void> _sendEmail(BuildContext context, File f, Project p, DailyLog log) async {
    final to = p.emails.join(', ');
    await Clipboard.setData(ClipboardData(text: to));
    if (context.mounted) toast(context, 'Email addresses copied. Paste them into "To".', long: true);
    try {
      await SharePlus.instance.share(
        ShareParams(
          files: [XFile(f.path, mimeType: pdf)],
          subject: emailSubject(p, log),
          text: 'To: $to\n\n${emailBody(p, log, Store.instance.me())}',
          sharePositionOrigin: context.mounted ? _origin(context) : null,
        ),
      );
    } catch (_) {
      if (context.mounted) toast(context, 'Couldn\'t open the share menu', long: true);
    }
  }

  static Future<void> _saveTo(BuildContext context, File f, String mime) async {
    bool saved;
    try {
      final uri = await FilePicker.saveFile(fileName: _name(f), bytes: await f.readAsBytes(), mimeType: mime);
      if (uri == null) return; // cancelled
      saved = true;
    } catch (_) {
      saved = false;
    }
    if (context.mounted) toast(context, saved ? 'Saved' : 'Couldn\'t save the file');
  }
}

class _ReportJob {
  final Project project;
  final DailyLog log;
  final String by;
  final int at;
  final String photoDir;
  final bool word;

  _ReportJob(this.project, this.log, this.by, this.at, this.photoDir, this.word);
}

class _OtJob {
  final Project project;
  final String by;
  final int at;
  final bool excel;

  _OtJob(this.project, this.by, this.at, this.excel);
}

class _CanJob {
  final Project project;
  final String by;
  final int at;
  final String format;

  _CanJob(this.project, this.by, this.at, this.format);
}

Future<Uint8List> _reportJob(_ReportJob j) async => j.word
    ? ReportDocx.write(j.project, j.log, j.by, j.at, j.photoDir)
    : await ReportPdf.write(j.project, j.log, j.by, j.at, j.photoDir);

Future<Uint8List> _overtimeJob(_OtJob j) async =>
    j.excel ? OvertimeXlsx.write(j.project, j.by, j.at) : await ReportPdf.writeOvertime(j.project, j.by, j.at);

Future<Uint8List> _attendanceJob(_OtJob j) async =>
    j.excel ? AttendanceXlsx.write(j.project, j.by, j.at) : await ReportPdf.writeAttendance(j.project, j.by, j.at);

Future<Uint8List> _seaCanJob(_CanJob j) async => switch (j.format) {
  'xlsx' => SeaCanXlsx.write(j.project, j.by, j.at),
  'csv' => Export.seaCanCsv(j.project),
  'docx' => ReportDocx.writeSeaCans(j.project, j.by, j.at),
  _ => await ReportPdf.writeSeaCans(j.project, j.by, j.at),
};
