import 'package:flutter/material.dart';
import '../constants/app_colors.dart';

class StatusBadge extends StatelessWidget {
  final String label;
  final Color color;
  final IconData? icon;

  const StatusBadge({
    super.key,
    required this.label,
    required this.color,
    this.icon,
  });

  factory StatusBadge.online() => const StatusBadge(
        label: 'ONLINE',
        color: AppColors.success,
        icon: Icons.wifi_rounded,
      );

  factory StatusBadge.offline() => const StatusBadge(
        label: 'OFFLINE',
        color: AppColors.danger,
        icon: Icons.wifi_off_rounded,
      );

  factory StatusBadge.busy() => const StatusBadge(
        label: 'BUSY',
        color: AppColors.warning,
        icon: Icons.hourglass_top_rounded,
      );

  factory StatusBadge.alert(String level) {
    Color col;
    IconData ic;
    switch (level.toUpperCase()) {
      case 'CRITICAL':
        col = AppColors.danger;
        ic = Icons.error_outline_rounded;
        break;
      case 'WARNING':
        col = AppColors.warning;
        ic = Icons.warning_amber_rounded;
        break;
      default:
        col = AppColors.info;
        ic = Icons.info_outline_rounded;
    }
    return StatusBadge(label: level, color: col, icon: ic);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: color),
            const SizedBox(width: 4),
          ] else ...[
            Container(
              width: 6,
              height: 6,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: color,
              ),
            ),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w700,
              fontSize: 11,
              letterSpacing: 0.5,
            ),
          ),
        ],
      ),
    );
  }
}
