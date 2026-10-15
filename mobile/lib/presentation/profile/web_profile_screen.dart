import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants/app_colors.dart';
import '../../core/widgets/glass_card.dart';
import '../../core/widgets/web_header.dart';
import '../auth/login_screen.dart';
import '../state/auth_provider.dart';

class WebProfileScreen extends ConsumerWidget {
  const WebProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final user = authState.user;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Watermark Web Header
          const WebHeader(
            watermark: 'USER PROFILE',
            titlePrefix: 'HỒ SƠ',
            titleHighlight: 'NGƯỜI DÙNG',
          ),
          const SizedBox(height: 8),

          // Main Profile Card
          GlassCard(
            borderRadius: 24,
            padding: const EdgeInsets.all(22),
            child: Column(
              children: [
                // Avatar with pink circle ring
                Container(
                  width: 88,
                  height: 88,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(color: AppColors.primary, width: 3.5),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primary.withOpacity(0.25),
                        blurRadius: 14,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: ClipOval(
                    child: Image.network(
                      'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80',
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        color: AppColors.primary,
                        child: const Icon(Icons.person, size: 48, color: Colors.white),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 14),

                // Name & Edit Icon
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      user?.name ?? 'Quản Trị Viên AuraPet',
                      style: GoogleFonts.quicksand(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                        color: isDark ? Colors.white : const Color(0xFF1E293B),
                      ),
                    ),
                    const SizedBox(width: 8),
                    InkWell(
                      onTap: () => _showEditUserModal(context, ref, user?.name, user?.email),
                      borderRadius: BorderRadius.circular(10),
                      child: Container(
                        padding: const EdgeInsets.all(6),
                        decoration: BoxDecoration(
                          color: AppColors.primary.withOpacity(0.12),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(Icons.edit_note_rounded, size: 18, color: AppColors.primary),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                // Admin Badge
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
                  decoration: BoxDecoration(
                    color: AppColors.primary.withOpacity(0.14),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppColors.primary.withOpacity(0.4)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.shield_rounded, size: 14, color: AppColors.primary),
                      const SizedBox(width: 6),
                      Text(
                        'Quản Trị Hệ Thống (Admin)',
                        style: GoogleFonts.quicksand(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: AppColors.primary,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                const Divider(),
                const SizedBox(height: 10),

                // Info Rows
                _ProfileInfoRow(icon: Icons.email_outlined, label: 'Email', value: user?.email ?? 'admin@aurapet.com'),
                _ProfileInfoRow(icon: Icons.phone_outlined, label: 'Số điện thoại', value: '+84 987 654 321'),
                _ProfileInfoRow(icon: Icons.vpn_key_outlined, label: 'Vai trò', value: user?.role ?? 'ROLE_ADMIN'),
                _ProfileInfoRow(icon: Icons.wifi_tethering_rounded, label: 'Trạng thái', value: '🟢 Trực tuyến 24/7', valueColor: const Color(0xFF10B981)),
              ],
            ),
          ),
          const SizedBox(height: 18),

          // Connected Hardware Section
          GlassCard(
            borderRadius: 24,
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Thiết Bị Phần Cứng Liên Kết',
                  style: GoogleFonts.quicksand(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    color: AppColors.primary,
                  ),
                ),
                const SizedBox(height: 14),
                _HardwareNodeItem(title: 'Khay ăn thông minh ESP32', status: 'Online', icon: Icons.developer_board_rounded),
                const SizedBox(height: 10),
                _HardwareNodeItem(title: 'Camera AI Pi 5 Giám Sát', status: 'Online', icon: Icons.videocam_outlined),
                const SizedBox(height: 10),
                _HardwareNodeItem(title: 'Khay Dọn Cát Tự Động', status: 'Online', icon: Icons.cleaning_services_rounded),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Sign Out Button
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.danger,
                side: const BorderSide(color: AppColors.danger, width: 1.5),
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
              onPressed: () {
                ref.read(authProvider.notifier).logout();
                Navigator.of(context).pushReplacement(
                  MaterialPageRoute(builder: (_) => const LoginScreen()),
                );
              },
              icon: const Icon(Icons.logout_rounded, size: 18),
              label: Text('ĐĂNG XUẤT HỆ THỐNG', style: GoogleFonts.quicksand(fontWeight: FontWeight.w800)),
            ),
          ),
          const SizedBox(height: 90),
        ],
      ),
    );
  }

  void _showEditUserModal(BuildContext context, WidgetRef ref, String? curName, String? curEmail) {
    final nameCtrl = TextEditingController(text: curName ?? '');
    final emailCtrl = TextEditingController(text: curEmail ?? '');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text('Chỉnh sửa thông tin hồ sơ', style: GoogleFonts.quicksand(fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Tên hiển thị')),
            const SizedBox(height: 12),
            TextField(controller: emailCtrl, decoration: const InputDecoration(labelText: 'Email')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('HỦY')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary, foregroundColor: Colors.white),
            onPressed: () {
              final newName = nameCtrl.text.trim();
              final newEmail = emailCtrl.text.trim();
              if (newName.isNotEmpty) {
                ref.read(authProvider.notifier).updateProfile(name: newName, email: newEmail);
                Navigator.of(ctx).pop();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Đã cập nhật hồ sơ thành công!'), backgroundColor: AppColors.primary),
                );
              }
            },
            child: const Text('LƯU'),
          ),
        ],
      ),
    );
  }
}

class _ProfileInfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;

  const _ProfileInfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.valueColor,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 18, color: AppColors.primary),
          const SizedBox(width: 10),
          Text(
            '$label: ',
            style: GoogleFonts.nunito(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
            ),
          ),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: GoogleFonts.nunito(
                fontSize: 13,
                fontWeight: FontWeight.w800,
                color: valueColor ?? (isDark ? Colors.white : const Color(0xFF1E293B)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HardwareNodeItem extends StatelessWidget {
  final String title;
  final String status;
  final IconData icon;

  const _HardwareNodeItem({
    required this.title,
    required this.status,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF141416) : const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFE2E8F0)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 20, color: AppColors.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              style: GoogleFonts.quicksand(fontSize: 13, fontWeight: FontWeight.w700),
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: const Color(0xFF10B981).withOpacity(0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              status,
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF10B981)),
            ),
          ),
        ],
      ),
    );
  }
}
