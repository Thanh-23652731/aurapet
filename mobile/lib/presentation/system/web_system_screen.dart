import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants/app_colors.dart';
import '../../core/widgets/glass_card.dart';
import '../../core/widgets/web_header.dart';
import '../state/system_provider.dart';

class WebSystemScreen extends ConsumerStatefulWidget {
  const WebSystemScreen({super.key});

  @override
  ConsumerState<WebSystemScreen> createState() => _WebSystemScreenState();
}

class _WebSystemScreenState extends ConsumerState<WebSystemScreen> {
  // Local hardware interactive simulation states matching web
  bool _foodLed = false;
  bool _foodMotor = false;
  bool _foodAuto = true;
  int _foodCalories = 50;
  final TextEditingController _caloriesCtrl = TextEditingController(text: '50');

  bool _waterLed = false;
  bool _waterPump = true;
  bool _waterAuto = true;

  bool _litterCleaning = false;
  bool _litterAuto = true;

  @override
  void dispose() {
    _caloriesCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final systemState = ref.watch(systemStatusProvider);
    final status = systemState.status;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Watermark Web Header
          WebHeader(
            watermark: 'LOVE PET',
            titlePrefix: 'QUẢN LÝ',
            titleHighlight: 'HỆ THỐNG',
            actions: [
              IconButton(
                icon: const Icon(Icons.sync_rounded, color: AppColors.primary),
                tooltip: 'Đồng bộ phần cứng',
                onPressed: () {
                  ref.read(systemStatusProvider.notifier).loadStatus();
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Đang đồng bộ dữ liệu cảm biến IoT...')),
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 8),

          // 1. CHO ĂN (FEEDING) CARD
          _SystemCardFrame(
            child: Column(
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _IconIllustrationBox(
                      icon: Icons.restaurant_rounded,
                      color: const Color(0xFF38BDF8),
                      accentColor: const Color(0xFFFECDD3),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Cho Ăn (Feeding)',
                            style: GoogleFonts.quicksand(
                              fontSize: 18,
                              fontWeight: FontWeight.w800,
                              color: AppColors.primary,
                            ),
                          ),
                          const SizedBox(height: 8),
                          _StatusTextRow(label: 'Đèn xanh', value: _foodAuto ? 'Bật' : 'Tắt', isHighlight: _foodAuto),
                          _StatusTextRow(label: 'Đèn đỏ', value: _foodLed ? 'Bật' : 'Tắt', isHighlight: _foodLed),
                          _StatusTextRow(label: 'Động cơ nhả', value: _foodMotor ? 'Đang quay' : 'Tắt', isHighlight: _foodMotor),
                          _StatusTextRow(label: 'Mức thức ăn', value: '${status.foodLevelPercent}%', isHighlight: true),
                          const SizedBox(height: 10),

                          // Calories Input
                          Text(
                            'Lượng calo (calo/100g)',
                            style: GoogleFonts.nunito(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
                            ),
                          ),
                          const SizedBox(height: 6),
                          Row(
                            children: [
                              SizedBox(
                                width: 80,
                                height: 38,
                                child: TextField(
                                  controller: _caloriesCtrl,
                                  keyboardType: TextInputType.number,
                                  textAlign: TextAlign.center,
                                  style: GoogleFonts.nunito(
                                    fontSize: 14,
                                    fontWeight: FontWeight.w800,
                                    color: isDark ? Colors.white : const Color(0xFF1E293B),
                                  ),
                                  decoration: InputDecoration(
                                    filled: true,
                                    fillColor: isDark ? const Color(0xFF141416) : const Color(0xFFF1F5F9),
                                    contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                                    border: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(10),
                                      borderSide: BorderSide(color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFCBD5E1)),
                                    ),
                                    enabledBorder: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(10),
                                      borderSide: BorderSide(color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFCBD5E1)),
                                    ),
                                    focusedBorder: OutlineInputBorder(
                                      borderRadius: BorderRadius.circular(10),
                                      borderSide: const BorderSide(color: AppColors.primary, width: 1.8),
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 8),
                              InkWell(
                                onTap: () {
                                  final cal = int.tryParse(_caloriesCtrl.text.trim()) ?? 50;
                                  setState(() => _foodCalories = cal);
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Đã lưu lượng calo hạt: $_foodCalories calo/100g'), backgroundColor: AppColors.primary),
                                  );
                                },
                                borderRadius: BorderRadius.circular(10),
                                child: Container(
                                  height: 38,
                                  padding: const EdgeInsets.symmetric(horizontal: 12),
                                  decoration: BoxDecoration(
                                    color: AppColors.primary,
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: const Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Icon(Icons.cloud_upload_rounded, color: Colors.white, size: 16),
                                      SizedBox(width: 4),
                                      Text(
                                        'Lưu',
                                        style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12.5),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                const Divider(),
                const SizedBox(height: 8),

                // 3 Actuator Buttons
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _RoundActuatorButton(
                      icon: Icons.lightbulb_outline_rounded,
                      isActive: _foodLed,
                      tooltip: 'Bật/Tắt Đèn LED',
                      onTap: () => setState(() => _foodLed = !_foodLed),
                    ),
                    _RoundActuatorButton(
                      icon: Icons.settings_rounded,
                      isActive: _foodMotor,
                      tooltip: 'Kích hoạt nhả hạt ngay',
                      onTap: () async {
                        setState(() => _foodMotor = true);
                        ref.read(systemStatusProvider.notifier).dispenseFood(portionGram: 30);
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Động cơ servo đang quay: Đã nhả 30g hạt!'), backgroundColor: AppColors.success),
                        );
                        await Future.delayed(const Duration(seconds: 2));
                        if (mounted) setState(() => _foodMotor = false);
                      },
                    ),
                    _RoundActuatorButton(
                      icon: Icons.bolt_rounded,
                      isActive: _foodAuto,
                      tooltip: 'Chế độ Tự động',
                      onTap: () => setState(() => _foodAuto = !_foodAuto),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),

          // 2. NƯỚC UỐNG (DRINKING) CARD
          _SystemCardFrame(
            child: Column(
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _IconIllustrationBox(
                      icon: Icons.water_drop_rounded,
                      color: const Color(0xFF0284C7),
                      accentColor: const Color(0xFFBAE6FD),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Nước Uống (Drinking)',
                            style: GoogleFonts.quicksand(
                              fontSize: 18,
                              fontWeight: FontWeight.w800,
                              color: AppColors.primary,
                            ),
                          ),
                          const SizedBox(height: 8),
                          _StatusTextRow(label: 'Đèn xanh', value: _waterAuto ? 'Bật' : 'Tắt', isHighlight: _waterAuto),
                          _StatusTextRow(label: 'Đèn đỏ', value: _waterLed ? 'Bật' : 'Tắt', isHighlight: _waterLed),
                          _StatusTextRow(label: 'Máy bơm nước', value: _waterPump ? 'Đang chạy' : 'Tắt', isHighlight: _waterPump),
                          _StatusTextRow(label: 'Mức nước bình', value: '${status.waterLevelPercent}%', isHighlight: true),
                          const SizedBox(height: 8),
                          Text(
                            'Bơm tuần hoàn lọc than hoạt tính',
                            style: GoogleFonts.nunito(
                              fontSize: 12,
                              fontStyle: FontStyle.italic,
                              color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                const Divider(),
                const SizedBox(height: 8),

                // 3 Actuator Buttons
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _RoundActuatorButton(
                      icon: Icons.lightbulb_outline_rounded,
                      isActive: _waterLed,
                      tooltip: 'Bật/Tắt Đèn LED',
                      onTap: () => setState(() => _waterLed = !_waterLed),
                    ),
                    _RoundActuatorButton(
                      icon: Icons.water_drop_rounded,
                      isActive: _waterPump,
                      tooltip: 'Bật/Tắt Máy Bơm',
                      onTap: () {
                        setState(() => _waterPump = !_waterPump);
                        ref.read(systemStatusProvider.notifier).toggleWater(_waterPump);
                      },
                    ),
                    _RoundActuatorButton(
                      icon: Icons.bolt_rounded,
                      isActive: _waterAuto,
                      tooltip: 'Chế độ Tự động',
                      onTap: () => setState(() => _waterAuto = !_waterAuto),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),

          // 3. DỌN CÁT (AUTO LITTER) CARD
          _SystemCardFrame(
            child: Column(
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _IconIllustrationBox(
                      icon: Icons.cleaning_services_rounded,
                      color: const Color(0xFFD97706),
                      accentColor: const Color(0xFFFED7AA),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Dọn Cát (Auto Litter)',
                            style: GoogleFonts.quicksand(
                              fontSize: 18,
                              fontWeight: FontWeight.w800,
                              color: AppColors.primary,
                            ),
                          ),
                          const SizedBox(height: 8),
                          _StatusTextRow(
                            label: 'Trạng thái',
                            value: _litterCleaning ? 'Đang dọn (Cleaning)' : 'Đang chờ (Idle)',
                            isHighlight: _litterCleaning,
                          ),
                          _StatusTextRow(label: 'Khay chứa thải', value: '${status.litterWastePercent}%', isHighlight: false),
                          _StatusTextRow(label: 'Lần dọn gần nhất', value: '18:45 Hôm nay', isHighlight: false),
                          const SizedBox(height: 8),
                          Text(
                            'Cảm biến nhận diện mèo & tự động cào chất thải',
                            style: GoogleFonts.nunito(
                              fontSize: 12,
                              fontStyle: FontStyle.italic,
                              color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                const Divider(),
                const SizedBox(height: 8),

                // 2 Actuator Buttons
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _RoundActuatorButton(
                      icon: Icons.cleaning_services_rounded,
                      isActive: _litterCleaning,
                      tooltip: 'Dọn phân & cào cát ngay',
                      onTap: () async {
                        setState(() => _litterCleaning = true);
                        ref.read(systemStatusProvider.notifier).cleanLitter();
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Chu trình cào cát vệ sinh tự động đã kích hoạt!'), backgroundColor: AppColors.success),
                        );
                        await Future.delayed(const Duration(seconds: 3));
                        if (mounted) setState(() => _litterCleaning = false);
                      },
                    ),
                    _RoundActuatorButton(
                      icon: Icons.bolt_rounded,
                      isActive: _litterAuto,
                      tooltip: 'Chế độ Tự động dọn',
                      onTap: () => setState(() => _litterAuto = !_litterAuto),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 90),
        ],
      ),
    );
  }
}

class _SystemCardFrame extends StatelessWidget {
  final Widget child;

  const _SystemCardFrame({required this.child});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 24,
      padding: const EdgeInsets.all(20),
      child: child,
    );
  }
}

class _IconIllustrationBox extends StatelessWidget {
  final IconData icon;
  final Color color;
  final Color accentColor;

  const _IconIllustrationBox({
    required this.icon,
    required this.color,
    required this.accentColor,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 72,
      height: 84,
      decoration: BoxDecoration(
        color: accentColor.withOpacity(0.3),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: color.withOpacity(0.4), width: 2),
      ),
      child: Center(
        child: Icon(icon, size: 36, color: color),
      ),
    );
  }
}

class _StatusTextRow extends StatelessWidget {
  final String label;
  final String value;
  final bool isHighlight;

  const _StatusTextRow({
    required this.label,
    required this.value,
    required this.isHighlight,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Text(
            '$label : ',
            style: GoogleFonts.nunito(
              fontSize: 12.5,
              fontWeight: FontWeight.w600,
              color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
            ),
          ),
          Text(
            value,
            style: GoogleFonts.nunito(
              fontSize: 12.5,
              fontWeight: FontWeight.w800,
              color: isHighlight
                  ? AppColors.primary
                  : (isDark ? Colors.white : const Color(0xFF1E293B)),
            ),
          ),
        ],
      ),
    );
  }
}

class _RoundActuatorButton extends StatelessWidget {
  final IconData icon;
  final bool isActive;
  final String tooltip;
  final VoidCallback onTap;

  const _RoundActuatorButton({
    required this.icon,
    required this.isActive,
    required this.tooltip,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Tooltip(
      message: tooltip,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(50),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: 52,
          height: 52,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isActive
                ? AppColors.primary
                : (isDark ? const Color(0xFF1A1A1D) : const Color(0xFFF1F5F9)),
            border: Border.all(
              color: isActive
                  ? AppColors.primary
                  : (isDark ? const Color(0xFF383840) : const Color(0xFFCBD5E1)),
              width: 1.5,
            ),
            boxShadow: isActive
                ? [
                    BoxShadow(
                      color: AppColors.primary.withOpacity(0.4),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ]
                : [],
          ),
          child: Icon(
            icon,
            size: 22,
            color: isActive
                ? Colors.white
                : (isDark ? const Color(0xFFA1A1AA) : const Color(0xFF475569)),
          ),
        ),
      ),
    );
  }
}
