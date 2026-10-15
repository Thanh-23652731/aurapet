import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants/app_colors.dart';

class MascotWidget extends StatelessWidget {
  const MascotWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: 280,
          height: 160,
          child: CustomPaint(
            painter: _MascotPainter(),
          ),
        ),
        const SizedBox(height: 8),
        RichText(
          text: TextSpan(
            children: [
              TextSpan(
                text: 'SMART ',
                style: GoogleFonts.quicksand(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 2,
                  color: Theme.of(context).brightness == Brightness.dark ? Colors.white : const Color(0xFF1E293B),
                ),
              ),
              TextSpan(
                text: 'PET CARE',
                style: GoogleFonts.quicksand(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 2,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _MascotPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    // Center heart
    final heartPaint = Paint()..color = const Color(0xFFFF7A9E)..style = PaintingStyle.fill;
    final heartPath = Path();
    heartPath.moveTo(size.width * 0.5, size.height * 0.52);
    heartPath.cubicTo(
      size.width * 0.44, size.height * 0.42,
      size.width * 0.38, size.height * 0.48,
      size.width * 0.5, size.height * 0.65,
    );
    heartPath.moveTo(size.width * 0.5, size.height * 0.52);
    heartPath.cubicTo(
      size.width * 0.56, size.height * 0.42,
      size.width * 0.62, size.height * 0.48,
      size.width * 0.5, size.height * 0.65,
    );
    canvas.drawPath(heartPath, heartPaint);

    // Left Corgi Dog
    final dogPaint = Paint()..color = const Color(0xFFF59E0B)..style = PaintingStyle.fill;
    final whitePaint = Paint()..color = Colors.white..style = PaintingStyle.fill;
    final darkPaint = Paint()..color = const Color(0xFF1F2937)..style = PaintingStyle.fill;
    final pinkEarPaint = Paint()..color = const Color(0xFFFECDD3)..style = PaintingStyle.fill;

    // Dog Ears
    final dogLeftEar = Path()
      ..moveTo(size.width * 0.28, size.height * 0.45)
      ..lineTo(size.width * 0.18, size.height * 0.15)
      ..lineTo(size.width * 0.35, size.height * 0.32)
      ..close();
    canvas.drawPath(dogLeftEar, dogPaint);
    final dogLeftEarIn = Path()
      ..moveTo(size.width * 0.27, size.height * 0.42)
      ..lineTo(size.width * 0.20, size.height * 0.20)
      ..lineTo(size.width * 0.33, size.height * 0.33)
      ..close();
    canvas.drawPath(dogLeftEarIn, pinkEarPaint);

    final dogRightEar = Path()
      ..moveTo(size.width * 0.40, size.height * 0.45)
      ..lineTo(size.width * 0.48, size.height * 0.15)
      ..lineTo(size.width * 0.36, size.height * 0.32)
      ..close();
    canvas.drawPath(dogRightEar, dogPaint);
    final dogRightEarIn = Path()
      ..moveTo(size.width * 0.39, size.height * 0.42)
      ..lineTo(size.width * 0.46, size.height * 0.20)
      ..lineTo(size.width * 0.37, size.height * 0.33)
      ..close();
    canvas.drawPath(dogRightEarIn, pinkEarPaint);

    // Dog Head
    canvas.drawCircle(Offset(size.width * 0.34, size.height * 0.55), 36, dogPaint);
    // Dog Face White Patch
    canvas.drawOval(
      Rect.fromCenter(center: Offset(size.width * 0.34, size.height * 0.60), width: 34, height: 38),
      whitePaint,
    );
    // Dog Eyes
    canvas.drawCircle(Offset(size.width * 0.30, size.height * 0.52), 4, darkPaint);
    canvas.drawCircle(Offset(size.width * 0.38, size.height * 0.52), 4, darkPaint);
    canvas.drawCircle(Offset(size.width * 0.31, size.height * 0.51), 1.5, whitePaint);
    canvas.drawCircle(Offset(size.width * 0.39, size.height * 0.51), 1.5, whitePaint);
    // Dog Nose & Mouth
    canvas.drawOval(
      Rect.fromCenter(center: Offset(size.width * 0.34, size.height * 0.60), width: 8, height: 6),
      darkPaint,
    );

    // Right Cat
    final catPaint = Paint()..color = Colors.white..style = PaintingStyle.fill;
    final catOrangePatch = Paint()..color = const Color(0xFFF59E0B)..style = PaintingStyle.fill;

    // Cat Ears
    final catLeftEar = Path()
      ..moveTo(size.width * 0.60, size.height * 0.45)
      ..lineTo(size.width * 0.54, size.height * 0.18)
      ..lineTo(size.width * 0.67, size.height * 0.33)
      ..close();
    canvas.drawPath(catLeftEar, catPaint);
    final catLeftEarIn = Path()
      ..moveTo(size.width * 0.61, size.height * 0.42)
      ..lineTo(size.width * 0.56, size.height * 0.23)
      ..lineTo(size.width * 0.65, size.height * 0.34)
      ..close();
    canvas.drawPath(catLeftEarIn, pinkEarPaint);

    final catRightEar = Path()
      ..moveTo(size.width * 0.72, size.height * 0.45)
      ..lineTo(size.width * 0.78, size.height * 0.18)
      ..lineTo(size.width * 0.65, size.height * 0.33)
      ..close();
    canvas.drawPath(catRightEar, catOrangePatch);

    // Cat Head
    final catBorder = Paint()
      ..color = const Color(0xFFE2E8F0)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    canvas.drawCircle(Offset(size.width * 0.66, size.height * 0.55), 34, catPaint);
    canvas.drawCircle(Offset(size.width * 0.66, size.height * 0.55), 34, catBorder);

    // Cat Patch
    final catPatchPath = Path()
      ..moveTo(size.width * 0.66, size.height * 0.35)
      ..quadraticBezierTo(size.width * 0.76, size.height * 0.35, size.width * 0.77, size.height * 0.55)
      ..quadraticBezierTo(size.width * 0.70, size.height * 0.60, size.width * 0.66, size.height * 0.35)
      ..close();
    canvas.drawPath(catPatchPath, catOrangePatch);

    // Cat Eyes & Blush
    canvas.drawCircle(Offset(size.width * 0.61, size.height * 0.52), 4, darkPaint);
    canvas.drawCircle(Offset(size.width * 0.71, size.height * 0.52), 4, darkPaint);
    canvas.drawCircle(Offset(size.width * 0.62, size.height * 0.51), 1.5, whitePaint);
    canvas.drawCircle(Offset(size.width * 0.72, size.height * 0.51), 1.5, whitePaint);

    // Blush
    canvas.drawCircle(Offset(size.width * 0.58, size.height * 0.57), 5, pinkEarPaint);
    canvas.drawCircle(Offset(size.width * 0.74, size.height * 0.57), 5, pinkEarPaint);

    // Nose
    final roseNose = Paint()..color = const Color(0xFFF43F5E)..style = PaintingStyle.fill;
    final catNosePath = Path()
      ..moveTo(size.width * 0.66, size.height * 0.58)
      ..lineTo(size.width * 0.645, size.height * 0.56)
      ..lineTo(size.width * 0.675, size.height * 0.56)
      ..close();
    canvas.drawPath(catNosePath, roseNose);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
