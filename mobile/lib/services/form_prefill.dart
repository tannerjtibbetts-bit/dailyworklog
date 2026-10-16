// STUB (owner: import agent). Opens links; Formstack forms open with your usual answers filled in.
import 'package:flutter/material.dart';

import '../data/models.dart';

class FormPrefill {
  FormPrefill._();

  static bool isFormstack(String url) => url.toLowerCase().contains('formstack.com/forms/');

  /// Opens a saved link in the browser (prefilling Formstack forms).
  static Future<void> openLink(BuildContext context, Link link) async {}

  /// The "Form details" screen where you set your usual answers.
  static Future<void> editDetails(BuildContext context) async {}
}
