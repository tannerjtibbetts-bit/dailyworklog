import 'package:flutter/material.dart';

import 'data/store.dart';
import 'screens/intro_screen.dart';
import 'ui/common.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Store.open();
  runApp(const DailyWorkLogApp());
}

class DailyWorkLogApp extends StatelessWidget {
  const DailyWorkLogApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'Daily Work Log',
        navigatorKey: appNavigator,
        debugShowCheckedModeBanner: false,
        theme: appTheme(),
        home: const IntroScreen(),
      );
}
