import 'package:aves/model/settings/settings.dart';
import 'package:aves/widgets/common/fx/borders.dart';
import 'package:aves/widgets/common/fx/colors.dart';
import 'package:aves_model/aves_model.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

class AvesLogo extends StatelessWidget {
  final double size;

  const new({
    super.key,
    required this.size,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    Widget child = CustomPaint(
      size: Size(size / 1.4, size / 1.4),
      painter: AvesLogoPainter(),
    );
    if (context.select<Settings, bool>((v) => v.themeColorMode == AvesThemeColorMode.monochrome)) {
      final tint = Color.lerp(theme.colorScheme.primary, Colors.white, .5)!;
      child = ColorFiltered(
        colorFilter: ColorFilter.mode(tint, BlendMode.modulate),
        child: ColorFiltered(
          colorFilter: MatrixColorFilters.greyscale,
          child: child,
        ),
      );
    }

    return CircleAvatar(
      backgroundColor: theme.dividerColor,
      radius: size / 2,
      child: CircleAvatar(
        backgroundColor: Colors.white,
        radius: size / 2 - AvesBorder.curvedBorderWidth(context),
        child: child,
      ),
    );
  }
}

/// Murai Gallery logo: a minimal murai (magpie-robin) perched on a rounded
/// photo frame, with an amber sun — geometry mirrors `assets/logo/murai_logo.svg`.
class AvesLogoPainter extends CustomPainter {
  // palette tuned for light in-app surfaces (white circle backdrop)
  static const teal = Color(0xFF0F766E);
  static const tealDark = Color(0xFF0C5F58);
  static const amber = Color(0xFFF59E0B);

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width / 512;

    // frame
    final frameRect = RRect.fromRectAndCorners(
      Rect.fromLTWH(104 * s, 104 * s, 304 * s, 304 * s),
      topLeft: Radius.circular(60 * s),
      topRight: Radius.circular(60 * s),
      bottomLeft: Radius.circular(60 * s),
      bottomRight: Radius.circular(60 * s),
    );
    canvas.drawRRect(frameRect, Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 24 * s
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = teal);

    // sun
    canvas.drawCircle(Offset(196 * s, 180 * s), 28 * s, Paint()..color = amber);

    // bird: tail (cocked up-left), body, head, beak, eye
    final tail = Path()
      ..moveTo(296 * s, 344 * s)
      ..lineTo(200 * s, 270 * s)
      ..lineTo(176 * s, 300 * s)
      ..lineTo(284 * s, 400 * s)
      ..close();
    canvas.drawPath(tail, Paint()..color = tealDark);

    canvas.drawOval(
      Rect.fromCenter(center: Offset(282 * s, 332 * s), width: 100 * s, height: 140 * s),
      Paint()..color = tealDark,
    );

    canvas.drawCircle(Offset(312 * s, 264 * s), 36 * s, Paint()..color = tealDark);

    final beak = Path()
      ..moveTo(344 * s, 250 * s)
      ..lineTo(382 * s, 264 * s)
      ..lineTo(342 * s, 282 * s)
      ..close();
    canvas.drawPath(beak, Paint()..color = amber);

    canvas.drawCircle(Offset(322 * s, 252 * s), 7 * s, Paint()..color = Colors.white);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
