// Opening screen: logo, app name and an Enter button.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../support/support_screens.dart';
import '../access/access.dart';
import '../ui/theme.dart';

class IntroScreen extends StatefulWidget {
  const IntroScreen({super.key});

  @override
  State<IntroScreen> createState() => _IntroScreenState();
}

class _IntroScreenState extends State<IntroScreen> with SingleTickerProviderStateMixin {
  // Logo pops in, then the name, underline, tagline and button follow.
  late final AnimationController _a = AnimationController(vsync: this, duration: const Duration(milliseconds: 1350))
    ..forward();
  bool _entering = false;

  @override
  void dispose() {
    _a.dispose();
    super.dispose();
  }

  Animation<double> _part(int startMs, int ms, Curve curve) => CurvedAnimation(
    parent: _a,
    curve: Interval(startMs / 1350, (startMs + ms) / 1350, curve: curve),
  );

  Widget _rise(int delay, Widget child) {
    final t = _part(delay, 450, Curves.decelerate);
    return AnimatedBuilder(
      animation: t,
      builder: (_, c) => Opacity(
        opacity: t.value.clamp(0.0, 1.0),
        child: Transform.translate(offset: Offset(0, 28 * (1 - t.value)), child: c),
      ),
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final logo = _part(0, 700, Curves.easeOutBack);
    final bar = _part(520, 400, Curves.decelerate);
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        backgroundColor: C.introBg,
        body: SafeArea(
          child: LayoutBuilder(
            builder: (context, box) => SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: box.maxHeight),
                child: IntrinsicHeight(
                  child: Column(
                    children: [
                      const Spacer(),
                      AnimatedBuilder(
                        animation: logo,
                        builder: (_, c) => Opacity(
                          opacity: (logo.value * 1.4).clamp(0.0, 1.0),
                          child: Transform.rotate(
                            angle: -20 * (1 - logo.value) * 3.14159265 / 180,
                            child: Transform.scale(scale: 0.6 + 0.4 * logo.value, child: c),
                          ),
                        ),
                        child: const SizedBox(width: 230, height: 230, child: CustomPaint(painter: LogoPainter())),
                      ),
                      const SizedBox(height: 26),
                      _rise(
                        280,
                        const Text(
                          'Daily Work Log',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 42,
                            fontWeight: FontWeight.w900,
                            letterSpacing: -0.42,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      AnimatedBuilder(
                        animation: bar,
                        builder: (_, c) => Transform.scale(scaleX: bar.value, child: c),
                        child: Container(width: 64, height: 4, color: C.safetyYellow),
                      ),
                      const SizedBox(height: 16),
                      _rise(
                        600,
                        const Text(
                          'B44 TOOL READINESS',
                          style: TextStyle(color: C.introSub, fontSize: 14, letterSpacing: 2.8),
                        ),
                      ),
                      const SizedBox(height: 32),
                      _rise(
                        740,
                        Column(
                          children: [
                            const Text(
                              'OUR MISSION',
                              style: TextStyle(color: C.safetyYellow, fontSize: 12, letterSpacing: 2.4),
                            ),
                            const SizedBox(height: 8),
                            ConstrainedBox(
                              constraints: const BoxConstraints(maxWidth: 340),
                              child: const Text(
                                'Increasing efficiency and flow in your workday, while maintaining coverage and documenting '
                                'every task with accuracy and precision.',
                                textAlign: TextAlign.center,
                                style: TextStyle(color: Color(0xFFD3DCE7), fontSize: 17, height: 1.4),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const Spacer(),
                      _rise(
                        900,
                        Padding(
                          padding: const EdgeInsets.only(bottom: 22),
                          child: SizedBox(
                            width: 300,
                            height: 64,
                            child: Material(
                              color: C.safetyYellow,
                              elevation: 6,
                              borderRadius: BorderRadius.circular(100),
                              child: InkWell(
                                borderRadius: BorderRadius.circular(100),
                                onTap: _enter,
                                child: const Center(
                                  child: Text(
                                    'Enter',
                                    style: TextStyle(
                                      color: Color(0xFF14233A),
                                      fontSize: 21,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      // Support: the user manual, before or after signing in
                      _rise(
                        1000,
                        const Padding(padding: EdgeInsets.only(bottom: 8), child: SupportButton(onDark: true)),
                      ),
                      const Padding(
                        padding: EdgeInsets.only(bottom: 12),
                        child: Opacity(
                          opacity: 0.7,
                          child: Text(
                            'App created by Tanner Tibbetts',
                            style: TextStyle(color: C.introSub, fontSize: 11),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _enter() async {
    if (_entering) return;
    _entering = true;
    HapticFeedback.selectionClick();
    await enterApp(context); // straight in, or sign in / ask for access first
    _entering = false;
  }
}

/// The app logo (ic_logo.xml): navy disc, yellow ring, dot grid, faint signature and a check mark.
class LogoPainter extends CustomPainter {
  const LogoPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / 200, size.height / 200);
    canvas.drawCircle(const Offset(100, 100), 96, Paint()..color = const Color(0xFF1A2F4D));
    canvas.drawCircle(
      const Offset(100, 100),
      89,
      Paint()
        ..color = C.safetyYellow
        ..style = PaintingStyle.stroke
        ..strokeWidth = 5,
    );
    final dot = Paint()..color = const Color(0xFF4F6E96).withValues(alpha: 0.55);
    for (var x = 58; x <= 142; x += 14) {
      final edge = x == 58 || x == 142;
      for (var y = edge ? 72 : 58; y <= (edge ? 128 : 142); y += 14) {
        canvas.drawCircle(Offset(x.toDouble(), y.toDouble()), 3.3, dot);
      }
    }
    // Signature
    canvas.save();
    canvas.translate(77.8, 141);
    canvas.scale(0.22);
    final sig = Path()
      ..moveTo(26, 70)
      ..cubicTo(50, 57, 80, 52, 104, 56)
      ..moveTo(72, 56)
      ..cubicTo(68, 84, 62, 112, 53, 132)
      ..cubicTo(48, 143, 38, 146, 35, 137)
      ..moveTo(96, 94)
      ..cubicTo(102, 86, 111, 85, 112, 93)
      ..cubicTo(113, 110, 106, 134, 96, 152)
      ..cubicTo(87, 168, 66, 168, 65, 156)
      ..cubicTo(64, 143, 98, 137, 132, 133)
      ..cubicTo(150, 131, 164, 125, 176, 115)
      ..moveTo(118, 74)
      ..cubicTo(136, 64, 156, 61, 174, 66)
      ..moveTo(154, 63)
      ..cubicTo(150, 88, 144, 110, 135, 127)
      ..cubicTo(131, 135, 137, 140, 146, 133)
      ..moveTo(46, 161)
      ..cubicTo(80, 169, 120, 169, 156, 158);
    canvas.drawPath(
      sig,
      Paint()
        ..color = const Color(0xFFB4C0CF).withValues(alpha: 0.5)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 10
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
    canvas.restore();
    // Check mark
    final check = Path()
      ..moveTo(66, 96)
      ..lineTo(88, 118)
      ..lineTo(132, 72);
    for (final (color, width) in [(const Color(0xFF14233A), 19.0), (C.safetyYellow, 10.5)]) {
      canvas.drawPath(
        check,
        Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeWidth = width
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round,
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(LogoPainter oldDelegate) => false;
}
