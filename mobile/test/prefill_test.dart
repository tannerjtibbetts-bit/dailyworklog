// Ported from PrefillTest (Java), using the mock Observation & Coaching form page.
import 'dart:io';

import 'package:dailyworklog/services/form_prefill.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final html = File('test/fixtures/mockform.html').readAsStringSync();

  FormDetails details() {
    final d = FormDetails();
    const v = {
      'observation': 'Single', 'project': 'FCFR', 'site': 'B44 - Warehouse', 'unit': 'Unit 5', //
      'location': 'B44 zone 2', 'workGroup': 'Millwright / Boilermaker Mixed', 'position': 'FM', 'gang': '4888',
      'email': 'pat@example.com', 'interview': 'No', 'photos': 'No', 'actions': 'None', 'definitions': 'No',
      'rating': 'Positive Feedback', 'supervisor': 'No',
    };
    d.answers.addAll(v);
    d.name = 'Pat Example';
    return d;
  }

  test('all 17 filled', () {
    final r = FormPrefill.build('https://x.formstack.com/forms/obs', html, details(), DateTime(2026, 10, 9));
    expect(r.missed, isEmpty);
    expect(r.filled.length, 17);
    const want = [
      'field111001=Single%20Observation', 'field111002=FCFR', 'field111020=B44%20-%20Warehouse',
      'field111021=Unit%205', 'field111003=B44%20zone%202', 'field111004=Millwright%20%2F%20Boilermaker%20Mixed',
      'field111007=FM', 'field111008=FCFR%20-%20Justin', 'field111030=No', 'field111031=No', 'field111032=None',
      'field111033=No', 'field111034=Positive%20Feedback', 'field111035=No',
    ];
    for (final w in want) {
      expect(r.url, contains(w));
    }
    // Same link as the Android app builds.
    expect(
        r.url,
        'https://x.formstack.com/forms/obs?field111001=Single%20Observation%3A%20Single%20observer%20reviews%20the%20tasks%20and%20activities%20taking%20place.'
        '&field111002=FCFR&field111020=B44%20-%20Warehouse&field111021=Unit%205&field111003=B44%20zone%202'
        '&field111004=Millwright%20%2F%20Boilermaker%20Mixed&field111005=October%2009%2C%202026'
        '&field111006-first=Pat&field111006-last=Example&field111007=FM'
        '&field111008=FCFR%20-%20Justin%20Stanley%20-%204888%20-%20AECON%20-%20MCR%20FCFR%20B44%20%26%20B07'
        '&field111009=pat%40example.com&field111030=No&field111031=No&field111032=None&field111033=No'
        '&field111034=Positive%20Feedback&field111035=No');
  });

  test('empty check', () {
    final old = FormDetails()..answers['project'] = 'FCFR';
    expect(old.isEmpty, isFalse);
    expect(FormDetails().isEmpty, isTrue);
    expect((FormDetails()..answers['x'] = '  ').isEmpty, isTrue);
  });

  test('unmatched answers are listed as missed', () {
    final d = FormDetails()
      ..answers['project'] = 'Nothing like it'
      ..answers['unit'] = 'Unit 5';
    final r = FormPrefill.build('https://x.formstack.com/forms/obs?a=1', html, d, DateTime(2026, 10, 9));
    expect(r.missed, ['Bruce Power Project']);
    expect(r.filled, ['Unit Number', 'Observation Date']);
    expect(r.url, startsWith('https://x.formstack.com/forms/obs?a=1&field111021=Unit%205'));
  });

  test('no answers leaves the link alone', () {
    final r = FormPrefill.build('https://x.formstack.com/forms/obs', '<html></html>', FormDetails(), DateTime(2026, 1, 2));
    expect(r.url, 'https://x.formstack.com/forms/obs');
  });

  test('isFormstack and date', () {
    expect(FormPrefill.isFormstack('https://aecon.formstack.com/forms/observation'), isTrue);
    expect(FormPrefill.isFormstack('https://example.com'), isFalse);
    expect(FormPrefill.formDate(DateTime(2026, 3, 5)), 'March 05, 2026');
    expect(FormPrefill.questions.length, 15);
  });

  test('fetch reads the page and fails on errors', () async {
    final ok = MockClient((req) async => http.Response(html, 200));
    expect(await FormPrefill.fetch('https://x.formstack.com/forms/obs', client: ok), contains('fsCell111001'));
    final bad = MockClient((req) async => http.Response('nope', 404));
    expect(FormPrefill.fetch('https://x.formstack.com/forms/obs', client: bad), throwsA(isA<http.ClientException>()));
  });
}
