// STUB (owner: import agent). Photo / Excel / CSV import with the review screen.
import 'package:flutter/material.dart';

import '../data/models.dart';

/// How the import starts: a photo of a list, the North/South zone sheet photo, or a file.
enum ImportSource { camera, gallery, zoneCamera, zoneGallery, file }

class ImportScreen extends StatelessWidget {
  final Project project;
  final DailyLog log;
  final ImportSource source;

  const ImportScreen({super.key, required this.project, required this.log, required this.source});

  @override
  Widget build(BuildContext context) => const Scaffold();
}
