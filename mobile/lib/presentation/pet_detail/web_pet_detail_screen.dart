import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants/app_colors.dart';
import '../../core/widgets/glass_card.dart';
import '../../core/widgets/web_header.dart';
import '../../data/models/pet.dart';
import '../pets/web_pet_list_screen.dart';
import '../state/pet_provider.dart';

class WebPetDetailScreen extends ConsumerStatefulWidget {
  final Function(int targetTab) onNavigateTab;

  const WebPetDetailScreen({super.key, required this.onNavigateTab});

  @override
  ConsumerState<WebPetDetailScreen> createState() => _WebPetDetailScreenState();
}

class _WebPetDetailScreenState extends ConsumerState<WebPetDetailScreen> {
  late TextEditingController _breakfastCtrl;
  late TextEditingController _lunchCtrl;
  late TextEditingController _dinnerCtrl;

  @override
  void initState() {
    super.initState();
    _breakfastCtrl = TextEditingController(text: '08:30 AM');
    _lunchCtrl = TextEditingController(text: '01:00 PM');
    _dinnerCtrl = TextEditingController(text: '07:30 PM');
  }

  @override
  void dispose() {
    _breakfastCtrl.dispose();
    _lunchCtrl.dispose();
    _dinnerCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final petState = ref.watch(petListProvider);
    var selectedPet = ref.watch(selectedPetProvider);

    // If no pet selected, default to the first pet
    if (selectedPet == null && petState.pets.isNotEmpty) {
      selectedPet = petState.pets.first;
    }

    if (selectedPet == null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.pets, size: 64, color: AppColors.primary),
            const SizedBox(height: 16),
            Text(
              'Chưa có bé mèo nào được chọn',
              style: GoogleFonts.quicksand(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: () => widget.onNavigateTab(1),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primary,
                foregroundColor: Colors.white,
              ),
              child: const Text('Xem Danh Sách Mèo'),
            ),
          ],
        ),
      );
    }

    final pet = selectedPet;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final avatarUrl = pet.imageUrl != null && pet.imageUrl!.isNotEmpty
        ? pet.imageUrl!
        : 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=500&auto=format&fit=crop&q=60';

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Watermark Web Header
          WebHeader(
            watermark: 'LOVE PET',
            titlePrefix: 'CHI TIẾT',
            titleHighlight: 'THÚ CƯNG',
            actions: [
              IconButton(
                icon: const Icon(Icons.switch_left_rounded, color: AppColors.primary),
                tooltip: 'Chọn bé mèo khác',
                onPressed: () => _showPetSelectorSheet(context, petState.pets),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Pet Profile Header (Avatar + Name + Edit + Delete)
          GlassCard(
            borderRadius: 24,
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                Container(
                  width: 76,
                  height: 76,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(color: AppColors.primary, width: 3),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primary.withOpacity(0.2),
                        blurRadius: 10,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: ClipOval(
                    child: Image.network(
                      avatarUrl,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => const Icon(Icons.pets, color: AppColors.primary, size: 40),
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        pet.name,
                        style: GoogleFonts.quicksand(
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          color: isDark ? Colors.white : const Color(0xFF1E293B),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          _ActionMiniButton(
                            icon: Icons.edit_note_rounded,
                            label: 'Chỉnh sửa',
                            color: AppColors.primary,
                            onTap: () => _editPet(context, pet),
                          ),
                          const SizedBox(width: 8),
                          _ActionMiniButton(
                            icon: Icons.delete_outline_rounded,
                            label: 'Xóa',
                            color: AppColors.danger,
                            onTap: () => _deletePet(context, pet),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 1. CARD: THÔNG TIN (Information)
          _WebPillCard(
            pillTitle: 'Thông Tin',
            child: Column(
              children: [
                _DetailRow(label: 'Tuổi', value: '${pet.age} tuổi'),
                _DetailRow(label: 'Giới tính', value: pet.gender == 'MALE' ? 'Đực' : 'Cái'),
                _DetailRow(label: 'Giống loài', value: pet.breed),
                _DetailRow(label: 'Triệt sản', value: pet.neutered ? 'Đã triệt sản' : 'Chưa triệt sản'),
                _DetailRow(label: 'Cân nặng', value: '${pet.weight} KG'),
                _DetailRow(label: 'Lượng calo cần thiết', value: '${(pet.weight * 58).round()} Calo'),
                _DetailRow(label: 'Khẩu phần ăn', value: '${(pet.weight * 60).round()} Gram'),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 2. CARD: CÀI ĐẶT GIỜ ĂN (Set Time Eat)
          _WebPillCard(
            pillTitle: 'Cài Đặt Giờ Ăn',
            child: Column(
              children: [
                _TimeInputRow(
                  label: 'Bữa sáng :',
                  controller: _breakfastCtrl,
                  onPickTime: () => _pickTime(context, _breakfastCtrl),
                ),
                _TimeInputRow(
                  label: 'Bữa trưa :',
                  controller: _lunchCtrl,
                  onPickTime: () => _pickTime(context, _lunchCtrl),
                ),
                _TimeInputRow(
                  label: 'Bữa tối :',
                  controller: _dinnerCtrl,
                  onPickTime: () => _pickTime(context, _dinnerCtrl),
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.primary,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        onPressed: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Đã lưu cài đặt giờ ăn cho bé mèo!'),
                              backgroundColor: AppColors.primary,
                            ),
                          );
                        },
                        child: Text('LƯU CÀI ĐẶT', style: GoogleFonts.quicksand(fontWeight: FontWeight.bold, fontSize: 13)),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.primary,
                          side: const BorderSide(color: AppColors.primary),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        onPressed: () {
                          setState(() {
                            _breakfastCtrl.text = '08:30 AM';
                            _lunchCtrl.text = '01:00 PM';
                            _dinnerCtrl.text = '07:30 PM';
                          });
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Đã khôi phục giờ ăn mặc định')),
                          );
                        },
                        child: Text('KHÔI PHỤC', style: GoogleFonts.quicksand(fontWeight: FontWeight.bold, fontSize: 13)),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 3. CARD: TỔNG HỢP (Synthetic)
          _WebPillCard(
            pillTitle: 'Tổng Hợp',
            child: Column(
              children: [
                _DetailRow(label: 'Lượng thức ăn mỗi tuần', value: '${(pet.weight * 60 * 7 * 0.5).toStringAsFixed(1)} Gram'),
                _DetailRow(label: 'Cân nặng trung bình mỗi tuần', value: '${pet.weight} KG'),
                _DetailRow(label: 'Lượng calo cần thiết mỗi ngày', value: '${(pet.weight * 58).round()} Calo'),
                _DetailRow(label: 'Lượng thức ăn trong một ngày', value: '${(pet.weight * 60).round()} Gram'),
                _DetailRow(label: 'Lượng thức ăn đã nạp hiện tại', value: '100 Gram'),
                _DetailRow(label: 'Lượng calo đã nạp hiện tại', value: '50 Calo'),
                _DetailRow(label: 'Từ khóa chăm sóc', value: 'Bổ sung calo, vận động thường xuyên, hạt dinh dưỡng'),
                _DetailRow(
                  label: 'Tình trạng cân nặng',
                  value: pet.weight < 3.0 ? 'Yếu (Weak)' : 'Khỏe mạnh (Healthy)',
                  valueColor: pet.weight < 3.0 ? const Color(0xFFF59E0B) : const Color(0xFF10B981),
                ),
                _DetailRow(
                  label: 'Tình trạng theo độ tuổi',
                  value: pet.weight < 3.0 ? 'Thiếu cân (Underweight)' : 'Chuẩn (Ideal)',
                  valueColor: pet.weight < 3.0 ? const Color(0xFFEF4444) : const Color(0xFF10B981),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // 4. BIỂU ĐỒ LƯỢNG THỨC ĂN (Food Weight)
          _WebPillCard(
            pillTitle: 'Lượng Thức Ăn (Gram)',
            child: SizedBox(
              height: 170,
              child: LineChart(
                LineChartData(
                  gridData: const FlGridData(show: false),
                  titlesData: FlTitlesData(
                    rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        getTitlesWidget: (val, meta) {
                          const days = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
                          if (val.toInt() >= 0 && val.toInt() < days.length) {
                            return Text(days[val.toInt()], style: const TextStyle(fontSize: 10, color: Colors.grey));
                          }
                          return const SizedBox();
                        },
                      ),
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  lineBarsData: [
                    LineChartBarData(
                      spots: const [
                        FlSpot(0, 170),
                        FlSpot(1, 205),
                        FlSpot(2, 140),
                        FlSpot(3, 190),
                        FlSpot(4, 160),
                        FlSpot(5, 220),
                        FlSpot(6, 185),
                      ],
                      isCurved: true,
                      color: AppColors.primary,
                      barWidth: 3.5,
                      belowBarData: BarAreaData(
                        show: true,
                        color: AppColors.primary.withOpacity(0.18),
                      ),
                      dotData: const FlDotData(show: true),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 5. BIỂU ĐỒ CÂN NẶNG THÚ CƯNG (Pet Weight)
          _WebPillCard(
            pillTitle: 'Cân Nặng Thú Cưng (KG)',
            child: SizedBox(
              height: 170,
              child: LineChart(
                LineChartData(
                  gridData: const FlGridData(show: false),
                  titlesData: FlTitlesData(
                    rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        getTitlesWidget: (val, meta) {
                          const weeks = ['Tuần 1', 'Tuần 2', 'Tuần 3', 'Tuần 4', 'Tuần 5'];
                          if (val.toInt() >= 0 && val.toInt() < weeks.length) {
                            return Text(weeks[val.toInt()], style: const TextStyle(fontSize: 10, color: Colors.grey));
                          }
                          return const SizedBox();
                        },
                      ),
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  lineBarsData: [
                    LineChartBarData(
                      spots: [
                        FlSpot(0, pet.weight - 0.22),
                        FlSpot(1, pet.weight - 0.15),
                        FlSpot(2, pet.weight - 0.08),
                        FlSpot(3, pet.weight - 0.04),
                        FlSpot(4, pet.weight),
                      ],
                      isCurved: true,
                      color: const Color(0xFF38BDF8),
                      barWidth: 3.5,
                      belowBarData: BarAreaData(
                        show: true,
                        color: const Color(0xFF38BDF8).withOpacity(0.18),
                      ),
                      dotData: const FlDotData(show: true),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 90),
        ],
      ),
    );
  }

  Future<void> _pickTime(BuildContext context, TextEditingController controller) async {
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.now(),
    );
    if (time != null) {
      final hour = time.hourOfPeriod.toString().padLeft(2, '0');
      final minute = time.minute.toString().padLeft(2, '0');
      final period = time.period == DayPeriod.am ? 'AM' : 'PM';
      setState(() {
        controller.text = '$hour:$minute $period';
      });
    }
  }

  void _showPetSelectorSheet(BuildContext context, List<Pet> pets) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Chọn bé mèo để xem chi tiết', style: GoogleFonts.quicksand(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            ...pets.map((p) => ListTile(
                  leading: CircleAvatar(
                    backgroundImage: NetworkImage(p.imageUrl ?? 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=500&auto=format&fit=crop&q=60'),
                  ),
                  title: Text(p.name, style: GoogleFonts.quicksand(fontWeight: FontWeight.bold)),
                  subtitle: Text('${p.breed} • ${p.weight} kg'),
                  onTap: () {
                    ref.read(selectedPetProvider.notifier).select(p);
                    Navigator.of(ctx).pop();
                  },
                )),
          ],
        ),
      ),
    );
  }

  void _editPet(BuildContext context, Pet pet) {
    // Open edit modal directly
    final nameCtrl = TextEditingController(text: pet.name);
    final weightCtrl = TextEditingController(text: '${pet.weight}');
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text('Chỉnh sửa ${pet.name}', style: GoogleFonts.quicksand(fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: 'Tên bé')),
            const SizedBox(height: 12),
            TextField(controller: weightCtrl, decoration: const InputDecoration(labelText: 'Cân nặng (kg)')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('HỦY')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary, foregroundColor: Colors.white),
            onPressed: () async {
              final newName = nameCtrl.text.trim();
              final newWeight = double.tryParse(weightCtrl.text.trim()) ?? pet.weight;
              if (newName.isNotEmpty) {
                final updated = pet.copyWith(name: newName, weight: newWeight);
                await ref.read(petListProvider.notifier).updatePet(pet.id, updated.toJson());
                ref.read(selectedPetProvider.notifier).select(updated);
                if (context.mounted) Navigator.of(ctx).pop();
              }
            },
            child: const Text('LƯU'),
          ),
        ],
      ),
    );
  }

  void _deletePet(BuildContext context, Pet pet) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Xác nhận xóa thú cưng'),
        content: Text('Bạn có chắc muốn xóa bé ${pet.name}?'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('HỦY')),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.danger, foregroundColor: Colors.white),
            onPressed: () async {
              Navigator.of(ctx).pop();
              await ref.read(petListProvider.notifier).deletePet(pet.id);
              ref.read(selectedPetProvider.notifier).select(null);
              widget.onNavigateTab(1); // back to pet list
            },
            child: const Text('XÓA'),
          ),
        ],
      ),
    );
  }
}

class _WebPillCard extends StatelessWidget {
  final String pillTitle;
  final Widget child;

  const _WebPillCard({required this.pillTitle, required this.child});

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 22,
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.primary,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              pillTitle,
              style: GoogleFonts.quicksand(
                fontSize: 13,
                fontWeight: FontWeight.w800,
                color: Colors.white,
              ),
            ),
          ),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;

  const _DetailRow({required this.label, required this.value, this.valueColor});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '$label : ',
            style: GoogleFonts.nunito(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
            ),
          ),
          Expanded(
            child: Text(
              value,
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

class _TimeInputRow extends StatelessWidget {
  final String label;
  final TextEditingController controller;
  final VoidCallback onPickTime;

  const _TimeInputRow({
    required this.label,
    required this.controller,
    required this.onPickTime,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(
              label,
              style: GoogleFonts.quicksand(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: isDark ? Colors.white : const Color(0xFF1E293B),
              ),
            ),
          ),
          Expanded(
            child: GestureDetector(
              onTap: onPickTime,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF141416) : const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFCBD5E1),
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      controller.text,
                      style: GoogleFonts.nunito(
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                        color: AppColors.primary,
                      ),
                    ),
                    const Icon(Icons.access_time_rounded, size: 18, color: AppColors.primary),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionMiniButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ActionMiniButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: color.withOpacity(0.12),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withOpacity(0.4)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: color),
            const SizedBox(width: 4),
            Text(
              label,
              style: GoogleFonts.quicksand(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
