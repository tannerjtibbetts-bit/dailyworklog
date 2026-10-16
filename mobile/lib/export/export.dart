// STUB (owner: export agent). Report and overtime exports, shared/emailed with the share sheet.
import 'package:flutter/material.dart';

import '../data/models.dart';

class Export {
  Export._();

  /// The day's report as a PDF ([word] false) or Word document ([word] true), then the share sheet.
  static Future<void> report(BuildContext context, Project project, DailyLog log, {required bool word}) async {}

  /// Just the overtime sheet, as PDF or Excel.
  static Future<void> overtime(BuildContext context, Project project, {required bool excel}) async {}
}
