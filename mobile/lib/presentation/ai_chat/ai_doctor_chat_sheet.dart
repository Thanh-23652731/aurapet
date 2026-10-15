import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants/app_colors.dart';
import '../pets/web_pet_list_screen.dart';
import '../state/pet_provider.dart';

// Notifiers for Gemini AI
class GeminiKeyNotifier extends Notifier<String> {
  @override
  String build() => '';
  void setKey(String key) => state = key;
}

final geminiApiKeyProvider = NotifierProvider<GeminiKeyNotifier, String>(GeminiKeyNotifier.new);

class GeminiModelNotifier extends Notifier<String> {
  @override
  String build() => 'gemini-1.5-flash';
  void setModel(String model) => state = model;
}

final geminiModelProvider = NotifierProvider<GeminiModelNotifier, String>(GeminiModelNotifier.new);

class ChatMessage {
  final bool isAI;
  final String text;
  final DateTime timestamp;

  ChatMessage({required this.isAI, required this.text, DateTime? timestamp})
      : timestamp = timestamp ?? DateTime.now();
}

class AIDoctorLauncherButton extends ConsumerWidget {
  const AIDoctorLauncherButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Positioned(
      bottom: 84, // Just above bottom navigation pill
      right: 16,
      child: GestureDetector(
        onTap: () => showModalBottomSheet(
          context: context,
          isScrollControlled: true,
          backgroundColor: Colors.transparent,
          builder: (_) => const AIDoctorChatModal(),
        ),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFFFF7A9E), Color(0xFFFF5277)],
            ),
            borderRadius: BorderRadius.circular(30),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFFFF7A9E).withOpacity(0.4),
                blurRadius: 14,
                offset: const Offset(0, 6),
              ),
            ],
            border: Border.all(color: Colors.white.withOpacity(0.4), width: 1.5),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 34,
                height: 34,
                decoration: const BoxDecoration(
                  color: Colors.white,
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.medical_services_rounded, color: AppColors.primary, size: 20),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Bác Sĩ AI Mèo',
                    style: GoogleFonts.quicksand(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                  Text(
                    'Giám sát 24/7',
                    style: GoogleFonts.nunito(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: Colors.white.withOpacity(0.9),
                    ),
                  ),
                ],
              ),
              const SizedBox(width: 6),
              // Pulse dot
              Container(
                width: 8,
                height: 8,
                decoration: const BoxDecoration(
                  color: Color(0xFF4ADE80),
                  shape: BoxShape.circle,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AIDoctorChatModal extends ConsumerStatefulWidget {
  const AIDoctorChatModal({super.key});

  @override
  ConsumerState<AIDoctorChatModal> createState() => _AIDoctorChatModalState();
}

class _AIDoctorChatModalState extends ConsumerState<AIDoctorChatModal> {
  final TextEditingController _textCtrl = TextEditingController();
  final ScrollController _scrollCtrl = ScrollController();
  final List<ChatMessage> _messages = [];
  bool _isTyping = false;

  @override
  void initState() {
    super.initState();
    final pet = ref.read(selectedPetProvider) ?? (ref.read(petListProvider).pets.isNotEmpty ? ref.read(petListProvider).pets.first : null);
    final petName = pet?.name ?? 'Meo';
    final petWeight = pet != null ? '${pet.weight} KG' : '2.3 KG';

    _messages.add(
      ChatMessage(
        isAI: true,
        text: 'Xin chào! Mình là Bác Sĩ AI chuyên chăm sóc và giám sát sức khỏe cho bé $petName. Dựa trên dữ liệu cảm biến đo được, bé hiện nặng $petWeight. Bạn cần mình tư vấn điều gì cho bé hôm nay?',
      ),
    );
  }

  @override
  void dispose() {
    _textCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _sendMessage(String query) async {
    final text = query.trim();
    if (text.isEmpty) return;

    _textCtrl.clear();
    setState(() {
      _messages.add(ChatMessage(isAI: false, text: text));
      _isTyping = true;
    });
    _scrollToBottom();

    final pet = ref.read(selectedPetProvider) ?? (ref.read(petListProvider).pets.isNotEmpty ? ref.read(petListProvider).pets.first : null);
    final apiKey = ref.read(geminiApiKeyProvider);
    final model = ref.read(geminiModelProvider);

    String aiReply = '';

    // If user configured a Gemini API Key, query Gemini API!
    if (apiKey.isNotEmpty) {
      try {
        final dio = Dio();
        final chosenModel = model == 'auto' ? 'gemini-1.5-flash' : model;
        final url = 'https://generativelanguage.googleapis.com/v1beta/models/$chosenModel:generateContent?key=$apiKey';

        final promptContext = '''
Bạn là Bác Sĩ Thú Y AI chuyên về loài mèo trong hệ sinh thái Smart Pet Care (AuraPet).
Thông tin bé mèo hiện tại:
- Tên: ${pet?.name ?? 'Meo'}
- Giống loài: ${pet?.breed ?? 'Mèo ta'}
- Cân nặng: ${pet?.weight ?? 2.3} kg
- Giới tính: ${pet?.gender ?? 'FEMALE'}
- Đã triệt sản: ${pet?.neutered ?? true}

Hãy trả lời ngắn gọn, ân cần, chuẩn y khoa thú y, đưa ra lời khuyên thực tế cho câu hỏi của chủ nuôi:
"$text"
''';

        final response = await dio.post(
          url,
          data: {
            "contents": [
              {
                "parts": [
                  {"text": promptContext}
                ]
              }
            ]
          },
        );

        if (response.statusCode == 200 && response.data != null) {
          final candidates = response.data['candidates'] as List?;
          if (candidates != null && candidates.isNotEmpty) {
            aiReply = candidates[0]['content']['parts'][0]['text'] as String;
          }
        }
      } catch (e) {
        // Fallback with warning
        aiReply = '⚠️ Lưu ý: Kết nối Gemini API đang gặp lỗi (${e.toString().replaceAll("Exception: ", "")}). Bác Sĩ AI chuyển sang hệ thống thú y nội bộ:\n\n' +
            _getVeterinaryFallbackAnswer(text, pet?.name ?? 'Meo', pet?.weight ?? 2.3);
      }
    }

    // Default intelligent veterinary system fallback if no API key or failed
    if (aiReply.isEmpty) {
      await Future.delayed(const Duration(milliseconds: 600));
      aiReply = _getVeterinaryFallbackAnswer(text, pet?.name ?? 'Meo', pet?.weight ?? 2.3);
    }

    if (mounted) {
      setState(() {
        _isTyping = false;
        _messages.add(ChatMessage(isAI: true, text: aiReply));
      });
      _scrollToBottom();
    }
  }

  String _getVeterinaryFallbackAnswer(String query, String petName, double weight) {
    final q = query.toLowerCase();
    if (q.contains('sức khỏe') || q.contains('tổng quan')) {
      return '🩺 Đánh giá sức khỏe bé $petName:\n- Cân nặng hiện tại: $weight kg.\n- Tình trạng: Các chỉ số sinh hiệu và khay cát ổn định.\n- Khuyến nghị: Duy trì chia 3 cữ ăn đúng giờ bằng khay ăn tự động và vệ sinh nước uống hàng ngày.';
    } else if (q.contains('khẩu phần') || q.contains('ăn') || q.contains('hạt')) {
      final grams = (weight * 60).round();
      final calories = (weight * 58).round();
      return '🍗 Khẩu phần ăn cho bé $petName ($weight kg):\n- Nhu cầu calo: Khoảng $calories kcal/ngày.\n- Định lượng hạt: $grams gram/ngày chia làm 3 bữa (Sáng 08:30, Trưa 13:00, Tối 19:30). Nên chọn hạt giàu protein >32% và đạm động vật.';
    } else if (q.contains('nước') || q.contains('uống')) {
      final ml = (weight * 55).round();
      return '💧 Nhu cầu nước của bé $petName:\n- Bé cần khoảng $ml ml nước sạch mỗi ngày.\n- Khuyên dùng máy lọc nước tuần hoàn có than hoạt tính giúp tạo dòng nước chuyển động kích thích bé uống nhiều hơn, phòng ngừa sỏi thận hiệu quả.';
    } else if (q.contains('cát') || q.contains('vệ sinh') || q.contains('tiêu hóa')) {
      return '🚽 Tình trạng khay cát & tiêu hóa:\n- Khay vệ sinh tự động đang hoạt động chuẩn xác.\n- Bé đi vệ sinh trung bình 2-3 lần/ngày là rất bình thường. Nếu phát hiện phân lỏng hoặc bé rặn lâu không ra, cần liên hệ phòng khám thú y ngay.';
    } else if (q.contains('mệt mỏi') || q.contains('bỏ ăn') || q.contains('bất thường')) {
      return '⚠️ Cảnh báo dấu hiệu bất thường:\n1. Kiểm tra thân nhiệt bằng cách sờ tai và đệm thịt bàn chân bé.\n2. Kiểm tra khay nước và hạt xem bé có ăn uống từ sáng không.\n3. Nếu bé bỏ ăn quá 24h hoặc nôn mửa, hãy đưa bé đến bệnh viện thú y gần nhất!';
    }
    return 'Bác Sĩ AI đã ghi nhận câu hỏi về bé $petName. Bé hiện có cân nặng $weight kg. Hãy đảm bảo cho bé ăn đúng giờ, duy trì nước sạch tuần hoàn và theo dõi khay cát thông minh thường xuyên nhé!';
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final pet = ref.watch(selectedPetProvider) ?? (ref.watch(petListProvider).pets.isNotEmpty ? ref.watch(petListProvider).pets.first : null);
    final petName = pet?.name ?? 'Meo';
    final petWeight = pet != null ? '${pet.weight} KG' : '2.3 KG';
    final apiKey = ref.watch(geminiApiKeyProvider);

    return Container(
      height: MediaQuery.of(context).size.height * 0.88,
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF141416) : Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: Column(
        children: [
          // Header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFE2E8F0),
                ),
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: AppColors.primary.withOpacity(0.15),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.medical_services_rounded, color: AppColors.primary, size: 22),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            'Bác Sĩ AI AuraPet',
                            style: GoogleFonts.quicksand(fontSize: 16, fontWeight: FontWeight.w800),
                          ),
                          const SizedBox(width: 8),
                          InkWell(
                            onTap: () => _openGeminiConfigModal(context),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                              decoration: BoxDecoration(
                                color: apiKey.isNotEmpty ? const Color(0xFF38BDF8) : Colors.grey.withOpacity(0.3),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.auto_awesome_rounded, size: 10, color: Colors.white),
                                  const SizedBox(width: 3),
                                  Text(
                                    apiKey.isNotEmpty ? 'Gemini AI' : 'Cấu hình AI',
                                    style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.white),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                      Text(
                        '🟢 Trực tuyến | Đang theo dõi: $petName',
                        style: GoogleFonts.nunito(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w600,
                          color: isDark ? const Color(0xFF9E9EA7) : const Color(0xFF64748B),
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.key_rounded, size: 20),
                  tooltip: 'Cài đặt Gemini API Key',
                  onPressed: () => _openGeminiConfigModal(context),
                ),
                IconButton(
                  icon: const Icon(Icons.refresh_rounded, size: 20),
                  tooltip: 'Làm mới',
                  onPressed: () {
                    setState(() {
                      _messages.clear();
                      _messages.add(
                        ChatMessage(
                          isAI: true,
                          text: 'Cuộc trò chuyện đã được làm mới. Bạn cần Bác Sĩ AI tư vấn điều gì cho bé $petName?',
                        ),
                      );
                    });
                  },
                ),
                IconButton(
                  icon: const Icon(Icons.close_rounded),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),

          // Telemetry Bar
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            color: isDark ? const Color(0xFF1E1E24) : const Color(0xFFFFF0F5),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _TelemetryChip(icon: Icons.monitor_weight_outlined, label: petWeight, color: const Color(0xFFF59E0B)),
                  _TelemetryChip(icon: Icons.local_fire_department_outlined, label: '135 Calo', color: const Color(0xFFEF4444)),
                  _TelemetryChip(icon: Icons.rice_bowl_outlined, label: '278g/ngày', color: const Color(0xFF38BDF8)),
                  _TelemetryChip(icon: Icons.cleaning_services_outlined, label: 'Vệ sinh tốt', color: const Color(0xFF10B981)),
                ],
              ),
            ),
          ),

          // Quick Question Chips
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _QuickPromptChip(label: '🩺 Đánh giá sức khỏe', onTap: () => _sendMessage('Đánh giá tổng quan sức khỏe của bé hôm nay')),
                  _QuickPromptChip(label: '🍗 Khẩu phần ăn', onTap: () => _sendMessage('Khẩu phần ăn hiện tại có đủ dinh dưỡng cho bé không?')),
                  _QuickPromptChip(label: '💧 Lượng nước uống', onTap: () => _sendMessage('Bé cần uống bao nhiêu nước mỗi ngày là chuẩn?')),
                  _QuickPromptChip(label: '🚽 Khay cát & tiêu hóa', onTap: () => _sendMessage('Phân tích tần suất và tình trạng khay cát vệ sinh của bé')),
                  _QuickPromptChip(label: '⚠️ Dấu hiệu bất thường', onTap: () => _sendMessage('Bé có dấu hiệu mệt mỏi, bỏ ăn thì chủ nuôi cần làm gì ngay?')),
                ],
              ),
            ),
          ),

          // Messages List
          Expanded(
            child: ListView.builder(
              controller: _scrollCtrl,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final msg = _messages[index];
                return _ChatBubble(message: msg);
              },
            ),
          ),

          // Typing Indicator
          if (_isTyping)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 6),
              child: Row(
                children: [
                  const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.primary),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    'Bác Sĩ AI đang phân tích dữ liệu...',
                    style: GoogleFonts.nunito(fontSize: 12, fontStyle: FontStyle.italic, color: AppColors.primary),
                  ),
                ],
              ),
            ),

          // Input Bar
          Container(
            padding: EdgeInsets.only(
              left: 14,
              right: 14,
              top: 8,
              bottom: MediaQuery.of(context).viewInsets.bottom + 12,
            ),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1E1E24) : Colors.white,
              border: Border(
                top: BorderSide(
                  color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFE2E8F0),
                ),
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _textCtrl,
                    onSubmitted: (val) => _sendMessage(val),
                    decoration: InputDecoration(
                      hintText: 'Hỏi Bác Sĩ AI về bé $petName...',
                      hintStyle: GoogleFonts.nunito(fontSize: 13, color: Colors.grey),
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.send_rounded, color: AppColors.primary),
                  onPressed: () => _sendMessage(_textCtrl.text),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _openGeminiConfigModal(BuildContext context) {
    final keyCtrl = TextEditingController(text: ref.read(geminiApiKeyProvider));
    String selectedModel = ref.read(geminiModelProvider);

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (dialogCtx, setDialogState) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: Row(
            children: [
              const Icon(Icons.auto_awesome_rounded, color: Color(0xFF38BDF8)),
              const SizedBox(width: 8),
              Text('Cấu hình Google Gemini AI', style: GoogleFonts.quicksand(fontWeight: FontWeight.bold, fontSize: 17)),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Tích hợp Gemini AI giúp Bác Sĩ trả lời trực tiếp mọi câu hỏi tự do, phân tích y khoa chuẩn xác theo thời gian thực.',
                style: GoogleFonts.nunito(fontSize: 12.5, color: Colors.grey[600]),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: keyCtrl,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Google Gemini API Key',
                  hintText: 'Dán mã AIzaSy...',
                  prefixIcon: Icon(Icons.key_rounded),
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: selectedModel,
                decoration: const InputDecoration(labelText: 'Mô hình AI'),
                items: const [
                  DropdownMenuItem(value: 'gemini-1.5-flash', child: Text('gemini-1.5-flash (Nhanh & Tối ưu)')),
                  DropdownMenuItem(value: 'gemini-2.0-flash', child: Text('gemini-2.0-flash (Mới nhất)')),
                  DropdownMenuItem(value: 'gemini-pro', child: Text('gemini-pro')),
                  DropdownMenuItem(value: 'auto', child: Text('⚡ Tự động tìm mô hình')),
                ],
                onChanged: (val) => setDialogState(() => selectedModel = val!),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                ref.read(geminiApiKeyProvider.notifier).setKey('');
                Navigator.of(ctx).pop();
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Đã xóa Key Gemini, dùng Bác Sĩ AI nội bộ.')));
              },
              child: const Text('Xóa Key', style: TextStyle(color: AppColors.danger)),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary, foregroundColor: Colors.white),
              onPressed: () {
                final key = keyCtrl.text.trim();
                ref.read(geminiApiKeyProvider.notifier).setKey(key);
                ref.read(geminiModelProvider.notifier).setModel(selectedModel);
                Navigator.of(ctx).pop();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Đã kích hoạt Google Gemini AI thành công!'), backgroundColor: AppColors.success),
                );
              },
              child: const Text('LƯU & KÍCH HOẠT'),
            ),
          ],
        ),
      ),
    );
  }
}

class _TelemetryChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;

  const _TelemetryChip({required this.icon, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(right: 8),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 4),
          Text(
            label,
            style: GoogleFonts.nunito(fontSize: 11.5, fontWeight: FontWeight.w800, color: color),
          ),
        ],
      ),
    );
  }
}

class _QuickPromptChip extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const _QuickPromptChip({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(right: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF222228) : const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppColors.primary.withOpacity(0.3)),
        ),
        child: Text(
          label,
          style: GoogleFonts.nunito(fontSize: 12, fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class _ChatBubble extends StatelessWidget {
  final ChatMessage message;

  const _ChatBubble({required this.message});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isAI = message.isAI;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: isAI ? MainAxisAlignment.start : MainAxisAlignment.end,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (isAI)
            Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(right: 8),
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.15),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.medical_services_rounded, color: AppColors.primary, size: 16),
            ),
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: isAI
                    ? (isDark ? const Color(0xFF222228) : const Color(0xFFF8FAFC))
                    : AppColors.primary,
                borderRadius: BorderRadius.circular(18).copyWith(
                  topLeft: isAI ? const Radius.circular(4) : const Radius.circular(18),
                  topRight: isAI ? const Radius.circular(18) : const Radius.circular(4),
                ),
                border: isAI
                    ? Border.all(color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFE2E8F0))
                    : null,
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.04),
                    blurRadius: 6,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: Text(
                message.text,
                style: GoogleFonts.nunito(
                  fontSize: 13.5,
                  height: 1.45,
                  fontWeight: FontWeight.w600,
                  color: isAI
                      ? (isDark ? Colors.white : const Color(0xFF1E293B))
                      : Colors.white,
                ),
              ),
            ),
          ),
          if (!isAI) const SizedBox(width: 8),
        ],
      ),
    );
  }
}
