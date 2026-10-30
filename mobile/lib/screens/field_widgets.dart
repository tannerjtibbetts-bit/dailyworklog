// Small widgets shared by the checklist and field screens: the round check, the progress ring
// and the field card colours.
import 'dart:math';

import 'package:flutter/material.dart';

import '../data/models.dart';
import '../ui/theme.dart';

/// Card colours for a field: North pale blue, South pale pink, Unzoned pale yellow, no area white.
/// Done adds a green border all round (and pale green when no area is set).
BoxDecoration fieldCardDecoration(Task t) {
  final north = t.side == Task.north, south = t.side == Task.south, unzoned = t.side == Task.unzoned;
  final color = north
      ? C.northBg
      : south
      ? C.southBg
      : unzoned
      ? C.unzonedBg
      : t.done
      ? C.doneBg
      : Colors.white;
  final stroke = t.done
      ? C.green
      : north
      ? C.northStroke
      : south
      ? C.southStroke
      : unzoned
      ? C.unzonedStroke
      : C.cardStroke;
  return cardDecoration(color: color, stroke: stroke, width: t.done ? 2.5 : 1);
}

/// The coloured bar down the left of a field card.
Color fieldAccent(Task t) => t.done
    ? C.green
    : t.side == Task.north
    ? C.northAccent
    : t.side == Task.south
    ? C.southAccent
    : t.side == Task.unzoned
    ? C.unzonedAccent
    : C.sky;

/// The round completion check: grey ring when open, filled green with a tick when done.
/// Pops in when it changes.
class CheckCircle extends StatefulWidget {
  final bool checked;
  final double size;
  final VoidCallback? onTap;

  const CheckCircle({super.key, required this.checked, this.size = 38, this.onTap});

  @override
  State<CheckCircle> createState() => _CheckCircleState();
}

class _CheckCircleState extends State<CheckCircle> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 260),
    value: 1,
  );
  late final Animation<double> _scale = Tween(
    begin: 0.6,
    end: 1.0,
  ).animate(CurvedAnimation(parent: _c, curve: const _Overshoot(3)));

  @override
  void didUpdateWidget(CheckCircle old) {
    super.didUpdateWidget(old);
    if (old.checked != widget.checked) _c.forward(from: 0);
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: widget.checked ? 'Completed. Tap to reopen' : 'Not completed. Tap to complete',
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: widget.onTap,
      child: ScaleTransition(
        scale: _scale,
        child: CustomPaint(size: Size.square(widget.size), painter: _CheckPainter(widget.checked)),
      ),
    ),
  );
}

/// Android's OvershootInterpolator.
class _Overshoot extends Curve {
  final double tension;

  const _Overshoot(this.tension);

  @override
  double transformInternal(double t) {
    t -= 1;
    return t * t * ((tension + 1) * t + tension) + 1;
  }
}

class _CheckPainter extends CustomPainter {
  final bool checked;

  _CheckPainter(this.checked);

  @override
  void paint(Canvas canvas, Size s) {
    final size = min(s.width, s.height);
    final cx = s.width / 2, cy = s.height / 2;
    if (checked) {
      final r = size / 2;
      canvas.drawCircle(Offset(cx, cy), r, Paint()..color = C.green);
      final tick = Paint()
        ..color = Colors.white
        ..style = PaintingStyle.stroke
        ..strokeWidth = size * 0.09
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round;
      canvas.drawPath(
        Path()
          ..moveTo(cx - r * 0.40, cy + r * 0.02)
          ..lineTo(cx - r * 0.10, cy + r * 0.32)
          ..lineTo(cx + r * 0.42, cy - r * 0.28),
        tick,
      );
    } else {
      const w = 2.2;
      canvas.drawCircle(
        Offset(cx, cy),
        size / 2 - w,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = w
          ..color = const Color(0xFF9DB4CF),
      );
    }
  }

  @override
  bool shouldRepaint(_CheckPainter old) => old.checked != checked;
}

/// Percentage ring; turns into a green check at 100%.
/// [filledWhenComplete] true: solid green disc (list cards). false: green outline (checklist header).
class ProgressRing extends StatelessWidget {
  final double progress;
  final double size;
  final bool filledWhenComplete;

  const ProgressRing({super.key, required this.progress, this.size = 76, this.filledWhenComplete = true});

  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
    tween: Tween(end: progress.clamp(0.0, 1.0)),
    duration: const Duration(milliseconds: 420),
    curve: Curves.decelerate,
    builder: (c, v, _) => CustomPaint(
      size: Size.square(size),
      painter: _RingPainter(v, filledWhenComplete, DefaultTextStyle.of(c).style),
    ),
  );
}

class _RingPainter extends CustomPainter {
  final double shown;
  final bool filled;

  /// The app's text style, so the percentage uses the same font as the rest of the screen.
  final TextStyle base;

  _RingPainter(this.shown, this.filled, this.base);

  @override
  void paint(Canvas canvas, Size s) {
    final size = min(s.width, s.height);
    final cx = s.width / 2, cy = s.height / 2;
    final stroke = size * 0.1;
    if (shown >= 0.999) {
      final r = size / 2;
      final path = Path()
        ..moveTo(cx - r * 0.36, cy + r * 0.02)
        ..lineTo(cx - r * 0.09, cy + r * 0.29)
        ..lineTo(cx + r * 0.38, cy - r * 0.25);
      final tick = Paint()
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round;
      if (filled) {
        canvas.drawCircle(Offset(cx, cy), r, Paint()..color = C.green);
        tick
          ..color = Colors.white
          ..strokeWidth = size * 0.085;
      } else {
        canvas.drawCircle(
          Offset(cx, cy),
          r - stroke * 0.4,
          Paint()
            ..color = C.green
            ..style = PaintingStyle.stroke
            ..strokeWidth = stroke * 0.8,
        );
        tick
          ..color = C.green
          ..strokeWidth = size * 0.07;
      }
      canvas.drawPath(path, tick);
      return;
    }
    final oval = Rect.fromLTRB(
      cx - size / 2 + stroke / 2,
      cy - size / 2 + stroke / 2,
      cx + size / 2 - stroke / 2,
      cy + size / 2 - stroke / 2,
    );
    canvas.drawArc(
      oval,
      0,
      2 * pi,
      false,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke
        ..color = C.ringTrack,
    );
    if (shown > 0) {
      canvas.drawArc(
        oval,
        -pi / 2,
        2 * pi * shown,
        false,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = stroke
          ..strokeCap = StrokeCap.round
          ..color = C.blue,
      );
    }
    final tp = TextPainter(
      text: TextSpan(
        text: '${(shown * 100).round()}%',
        style: base.copyWith(color: C.navy, fontSize: size * 0.25, fontWeight: FontWeight.bold),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, Offset(cx - tp.width / 2, cy - tp.height / 2));
  }

  @override
  bool shouldRepaint(_RingPainter old) => old.shown != shown || old.filled != filled || old.base != base;
}

/// Pill-shaped label on a field card ("Load secured", "South", "Next shift", "Photos Required").
class FieldChip extends StatelessWidget {
  final String label;
  final Color bg, fg;
  final Color? border;
  final IconData? icon;

  const FieldChip(this.label, {super.key, required this.bg, required this.fg, this.border, this.icon});

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.fromLTRB(14, 6, 16, 6),
    decoration: BoxDecoration(
      color: bg,
      borderRadius: BorderRadius.circular(100),
      border: border == null ? null : Border.all(color: border!, width: 1.5),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[Icon(icon, size: 18, color: fg), const SizedBox(width: 8)],
        Flexible(
          child: Text(label, style: TextStyle(color: fg, fontSize: 15)),
        ),
      ],
    ),
  );
}

/// Dashed rounded box for the "add photo" tile.
class DashedBox extends StatelessWidget {
  final double size;
  final Widget child;

  const DashedBox({super.key, required this.size, required this.child});

  @override
  Widget build(BuildContext context) => CustomPaint(
    painter: _DashPainter(),
    child: SizedBox.square(
      dimension: size,
      child: Center(child: child),
    ),
  );
}

class _DashPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size s) {
    final rr = RRect.fromRectAndRadius(Offset.zero & s, const Radius.circular(12));
    canvas.drawRRect(rr, Paint()..color = const Color(0xFFF2F7FD));
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..color = const Color(0xFF9DB7D8);
    for (final m in (Path()..addRRect(rr.deflate(1))).computeMetrics()) {
      var d = 0.0;
      while (d < m.length) {
        canvas.drawPath(m.extractPath(d, min(d + 8, m.length)), paint);
        d += 14;
      }
    }
  }

  @override
  bool shouldRepaint(_DashPainter old) => false;
}
