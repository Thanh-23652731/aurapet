import 'package:flutter/material.dart';

class AppColors {
  // Primary Pink & Rose Accents (Matching Web Theme)
  static const Color primary = Color(0xFFFF7A9E);
  static const Color primaryDark = Color(0xFFFF6088);
  static const Color primaryLight = Color(0xFFFFE4EC);
  static const Color primaryGlow = Color(0x4DFF7A9E);

  static const Color pinkSoft = Color(0xFFFFE4EC);
  static const Color pinkGradientStart = Color(0xFFFFA8C0);
  static const Color pinkGradientEnd = Color(0xFFFF8FAD);

  static const Color cyan = Color(0xFF38BDF8);
  static const Color aqua = Color(0xFF38BDF8);
  static const Color indigo = Color(0xFF818CF8);

  // Backgrounds & Surfaces (Matching Web Theme)
  static const Color bgLight = Color(0xFFF8FAFC);
  static const Color bgDark = Color(0xFF141416);

  static const Color cardBgLight = Color(0xFFFFFFFF);
  static const Color cardBgDark = Color(0xFF222226);

  static const Color glassLight = Color(0xFFFFFFFF);
  static const Color glassBorderLight = Color(0xFFE2E8F0);

  static const Color glassDark = Color(0xFF222226);
  static const Color glassBorderDark = Color(0xFF2E2E34);

  // Neutrals
  static const Color textDark = Color(0xFF1E293B);
  static const Color textMutedLight = Color(0xFF64748B);
  static const Color textLight = Color(0xFFFFFFFF);
  static const Color textMutedDark = Color(0xFF9E9EA7);

  // Status & Telemetry
  static const Color success = Color(0xFF10B981);
  static const Color warning = Color(0xFFF59E0B);
  static const Color danger = Color(0xFFEF4444);
  static const Color info = Color(0xFF38BDF8);

  // Gradient Presets
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [Color(0xFFFF7A9E), Color(0xFFFF6088)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient petCardGradient = LinearGradient(
    colors: [Color(0xFFFFA8C0), Color(0xFFFF8FAD)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient glassGradient = LinearGradient(
    colors: [Color(0xFFFFFFFF), Color(0xFFF8FAFC)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient glassDarkGradient = LinearGradient(
    colors: [Color(0xFF222226), Color(0xFF1A1A1D)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}
