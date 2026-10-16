import 'dart:io';
import 'package:dailyworklog/data/models.dart';
import 'package:dailyworklog/data/store.dart';
import 'package:dailyworklog/screens/project_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final (w, h, r, n) in [(1080.0, 2220.0, 3.0, 'w360'), (1080.0, 2340.0, 2.75, 'w393')]) {
  testWidgets('shot $n', (tester) async {
    await tester.runAsync(() async {
      for (final (fam, files) in [('Roboto', ['Roboto-Regular.ttf', 'Roboto-Bold.ttf', 'Roboto-Medium.ttf']), ('MaterialIcons', ['MaterialIcons-Regular.otf'])]) {
        final l = FontLoader(fam);
        for (final f in files) { l.addFont(Future.value(ByteData.sublistView(File('/tmp/claude-0/-home-user-dailyworklog/67629185-8c91-55b1-bab9-fee74f7b6f5a/scratchpad/flutter/bin/cache/artifacts/material_fonts/$f').readAsBytesSync()))); }
        await l.load();
      }
    });
    tester.view.physicalSize = Size(w, h);
    tester.view.devicePixelRatio = r;
    addTearDown(tester.view.reset);
    late Store store;
    await tester.runAsync(() async => store = await Store.open(Directory.systemTemp.createTempSync('shot')));
    final p = Project()..name = 'B44'..seaCans = [
      SeaCanMove()..can = 'BPPU 201006-1'..fromLetter = 'D'..fromRow = '33'..fromLevel = '3'..toLetter = 'W'..toRow = '40'..toLevel = '1',
      SeaCanMove()..can = 'TCLU 317503'..fromRow = '12'..fromLevel = '2',
      SeaCanMove()..can = 'MSCU1234567'..toLetter = 'M'..toRow = '8',
    ];
    store.projects.add(p);
    await tester.pumpWidget(MaterialApp(theme: ThemeData(fontFamily: 'Roboto'), home: ProjectScreen(projectId: p.id, initialTab: ProjectTab.seaCans)));
    await tester.pumpAndSettle();
    await expectLater(find.byType(ProjectScreen), matchesGoldenFile('/tmp/claude-0/-home-user-dailyworklog/67629185-8c91-55b1-bab9-fee74f7b6f5a/scratchpad/$n.png'));
  });
  }
}
