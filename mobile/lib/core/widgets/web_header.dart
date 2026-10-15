import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../constants/app_colors.dart';

class WebHeader extends StatelessWidget {
  final String watermark;
  final String titlePrefix;
  final String titleHighlight;
  final List<Widget>? actions;

  const WebHeader({
    super.key,
    required this.watermark,
    required this.titlePrefix,
    required this.titleHighlight,
    this.actions,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.only(top: 8, bottom: 16),
      child: Stack(
        clipBehavior: Clip.none,
        alignment: Alignment.centerLeft,
        children: [
          // Watermark text in background
          Positioned(
            left: 0,
            top: -12,
            child: IgnorePointer(
              child: Text(
                watermark,
                style: GoogleFonts.nunito(
                  fontSize: 42,
                  fontWeight: FontWeight.w900,
                  letterSpacing: 2,
                  color: isDark
                      ? Colors.white.withOpacity(0.04)
                      : Colors.black.withOpacity(0.04),
                ),
              ),
            ),
          ),

          // Main Header Row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              RichText(
                text: TextSpan(
                  children: [
                    TextSpan(
                      text: '$titlePrefix ',
                      style: GoogleFonts.quicksand(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.5,
                        color: isDark ? Colors.white : const Color(0xFF1E293B),
                      ),
                    ),
                    TextSpan(
                      text: titleHighlight,
                      style: GoogleFonts.quicksand(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.5,
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
              if (actions != null) Row(mainAxisSize: MainAxisSize.min, children: actions!),
            ],
          ),
        ],
      ),
    );
  }
}
