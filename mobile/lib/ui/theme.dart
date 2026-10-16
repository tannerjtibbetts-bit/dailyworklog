// Colours and theme, matching the Android app.
import 'package:flutter/material.dart';

class C {
  C._();
  // Text: near-black, with blue-grey for secondary text
  static const navy = Color(0xFF0F1A2B);
  static const text = Color(0xFF111827);
  static const grey = Color(0xFF55667D);
  static const greyLight = Color(0xFF8FA1B8);
  // Blues
  static const blue = Color(0xFF1268D6);
  static const deepBlue = Color(0xFF174A8B);
  static const sky = Color(0xFFDCEBFB);
  // Status
  static const green = Color(0xFF1DB04B);
  static const red = Color(0xFFE5484D);
  // Surfaces
  static const bg = Color(0xFFEAF2FB);
  static const cardStroke = Color(0xFFD3E1F1);
  // Field cards by area: North pale blue, South pale pink, Unzoned pale yellow
  static const northBg = Color(0xFFDDEBFC);
  static const northStroke = Color(0xFFA9CBF2);
  static const northAccent = Color(0xFF5E9BE6);
  static const southBg = Color(0xFFFCE8EF);
  static const southStroke = Color(0xFFF0BFD0);
  static const southAccent = Color(0xFFE07FA3);
  // Unzoned: pale yellow
  static const unzonedBg = Color(0xFFFFF5D1);
  static const unzonedStroke = Color(0xFFEED98C);
  static const unzonedAccent = Color(0xFFE0B92E);
  static const doneBg = Color(0xFFF3FBF6);
  static const chip = Color(0xFFDAE6F4);
  static const divider = Color(0xFFD3E1F1);
  static const darkPill = Color(0xFF174A8B);
  static const ringTrack = Color(0xFFD3E1F1);
  static const introBg = Color(0xFF0F1A2B);
  static const safetyYellow = Color(0xFFF2C200);
  static const introSub = Color(0xFF8FA1B8);
}

ThemeData appTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: C.blue,
    primary: C.blue,
    onPrimary: Colors.white,
    secondary: C.navy,
    surface: Colors.white,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: C.bg,
    appBarTheme: const AppBarTheme(
      backgroundColor: C.bg,
      foregroundColor: C.navy,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: true,
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
    ),
    dialogTheme: const DialogThemeData(backgroundColor: Colors.white),
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: C.blue,
      foregroundColor: Colors.white,
      shape: CircleBorder(),
    ),
  );
}

/// White card with a light border and big rounded corners (bg_card).
BoxDecoration cardDecoration({Color color = Colors.white, Color stroke = C.cardStroke, double width = 1}) =>
    BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(22),
      border: Border.all(color: stroke, width: width),
    );
