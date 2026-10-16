// Backup and restore: everything (worklog.json and photos) in one .zip file, so your work can
// move to another phone, including between Android and iPhone. Photos are streamed to and from
// disk so a big backup doesn't run the phone out of memory.
import 'dart:convert';
import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:path_provider/path_provider.dart';

import 'fmt.dart';
import 'store.dart';

class Backup {
  Backup._();

  static const dataName = 'worklog.json';

  /// Writes the backup zip to the temp folder (or [into]) and returns it.
  static Future<File> create(Store store, {Directory? into}) async {
    await store.flush();
    final dir = into ?? await getTemporaryDirectory();
    final out = File('${dir.path}/DailyWorkLog-backup-${Fmt.todayIso()}.zip');
    final json = utf8.encode(jsonEncode(store.toJson()));
    final enc = ZipFileEncoder()..create(out.path);
    enc.addArchiveFile(ArchiveFile(dataName, json.length, json));
    if (await store.photoDir.exists()) {
      await for (final f in store.photoDir.list()) {
        // Photos are already compressed JPEGs: store them as they are.
        if (f is File) await enc.addFile(f, 'photos/${f.uri.pathSegments.last}', ZipFileEncoder.store);
      }
    }
    await enc.close();
    return out;
  }

  static Map<String, dynamic>? _data(Archive archive) {
    final f = archive.findFile(dataName);
    if (f == null) return null;
    return jsonDecode(utf8.decode(f.content)) as Map<String, dynamic>;
  }

  /// What's in a backup, before restoring it.
  static Future<BackupInfo?> inspect(File zip) async {
    final input = InputFileStream(zip.path);
    try {
      final archive = ZipDecoder().decodeStream(input);
      final o = _data(archive);
      if (o == null) return null;
      final projects = o['projects'] is List ? (o['projects'] as List).length : 0;
      final photos = archive.files.where((f) => f.isFile && f.name.startsWith('photos/')).length;
      return BackupInfo(projects, photos, (o['userName'] ?? '').toString());
    } catch (_) {
      return null;
    } finally {
      await input.close();
    }
  }

  /// Replaces everything on this phone with the backup. Returns false if the file isn't a backup.
  static Future<bool> restore(Store store, File zip) async {
    final input = InputFileStream(zip.path);
    try {
      final Archive archive;
      final Map<String, dynamic>? data;
      try {
        archive = ZipDecoder().decodeStream(input);
        data = _data(archive);
      } catch (_) {
        return false;
      }
      if (data == null) return false;
      await store.flush();
      // Keep the current data next to it, just in case.
      if (await store.dataFile.exists()) {
        await store.dataFile.copy('${store.dir.path}/worklog-before-restore-${DateTime.now().millisecondsSinceEpoch}.json');
      }
      await store.photoDir.create(recursive: true);
      for (final f in archive.files) {
        if (!f.isFile || !f.name.startsWith('photos/')) continue;
        final name = f.name.substring('photos/'.length);
        if (name.isEmpty || name.contains('/') || name.contains('..')) continue;
        final out = OutputFileStream(store.photoFile(name).path);
        f.writeContent(out);
        await out.close();
      }
      store.fromJson(data);
      store.save();
      await store.flush();
      return true;
    } finally {
      await input.close();
    }
  }
}

class BackupInfo {
  final int projects, photos;
  final String userName;

  const BackupInfo(this.projects, this.photos, this.userName);
}
