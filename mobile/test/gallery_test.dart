import 'package:dailyworklog/services/photos.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('dailyworklog/gallery');

  test('a photo is copied to the Daily Work Log album in the gallery', () async {
    MethodCall? got;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (c) async {
      got = c;
      return true;
    });
    expect(await Photos.saveToGallery('/data/photos/IMG_1.jpg'), true);
    expect(got!.method, 'save');
    expect(got!.arguments, {'path': '/data/photos/IMG_1.jpg', 'album': 'Daily Work Log'});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('no gallery (or not allowed) is quietly skipped', () async {
    expect(await Photos.saveToGallery('/x.jpg'), false);
  });
}
