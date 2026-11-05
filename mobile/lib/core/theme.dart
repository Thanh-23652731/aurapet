import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Bảng màu giống bản web: màu hạt thức ăn + xanh lá cho trạng thái tốt.
class AppColors {
  static const kibble = Color(0xFF9A6516);
  static const kibbleDark = Color(0xFFD49A3A);
  static const kibA = Color(0xFFB9822E);
  static const kibB = Color(0xFFE2C08A);
  static const leaf = Color(0xFF2F8F6B);
  static const warn = Color(0xFFC98A06);
  static const stop = Color(0xFFB4232C);
  static const bgLight = Color(0xFFF2F5F3);
  static const bgDark = Color(0xFF141C19);
  static const surfaceDark = Color(0xFF1C2622);
  static const inkLight = Color(0xFF1E2A33);
}

ThemeData buildTheme(Brightness b) {
  final dark = b == Brightness.dark;
  final scheme = ColorScheme.fromSeed(
    seedColor: AppColors.kibble,
    brightness: b,
    primary: dark ? AppColors.kibbleDark : AppColors.kibble,
    onPrimary: dark ? const Color(0xFF1B140A) : Colors.white,
    error: AppColors.stop,
    surface: dark ? AppColors.surfaceDark : Colors.white,
  );
  final base = ThemeData(useMaterial3: true, colorScheme: scheme, brightness: b);
  return base.copyWith(
    scaffoldBackgroundColor: dark ? AppColors.bgDark : AppColors.bgLight,
    textTheme: GoogleFonts.beVietnamProTextTheme(base.textTheme),
    visualDensity: VisualDensity.compact,
  );
}

extension ThemeX on BuildContext {
  ColorScheme get cs => Theme.of(this).colorScheme;
  TextTheme get tt => Theme.of(this).textTheme;
  bool get isDark => Theme.of(this).brightness == Brightness.dark;
  Color get muted => cs.onSurfaceVariant;
  Color get line => cs.outlineVariant;
  Color get soft => isDark ? const Color(0xFF24312C) : const Color(0xFFE8EEEA);
}
