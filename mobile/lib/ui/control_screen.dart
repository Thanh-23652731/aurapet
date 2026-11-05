import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/format.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../state/providers.dart';
import 'widgets.dart';

/// Màn hình chính: cho ăn, xem khay & thùng chứa, chế độ tự động, lịch hẹn giờ, mực nước bình bơm.
class ControlScreen extends ConsumerStatefulWidget {
  const ControlScreen({super.key});

  @override
  ConsumerState<ControlScreen> createState() => _ControlScreenState();
}

class _ControlScreenState extends ConsumerState<ControlScreen> {
  int? _gram; // khẩu phần đang chỉnh (null = theo cài đặt)
  DateTime _gramEditedAt = DateTime(2000);

  Future<void> _do(Future<String?> Function() action, [String? ok]) async {
    final err = await action();
    if (!mounted) return;
    if (err != null) {
      showMessage(context, err, error: true);
    } else if (ok != null) {
      showMessage(context, ok);
    }
  }

  void _changeGram(int g, DeviceSettings s) {
    final v = g.clamp(5, 150).toInt();
    setState(() {
      _gram = v;
      _gramEditedAt = DateTime.now();
    });
    Future.delayed(const Duration(milliseconds: 600), () {
      if (mounted && _gram == v && DateTime.now().difference(_gramEditedAt).inMilliseconds >= 550) {
        ref.read(deviceProvider.notifier).settings({'targetGram': v});
      }
    });
  }

  Future<void> _addTime(DeviceSettings s) async {
    final t = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 7, minute: 0),
      helpText: 'Chọn giờ cho ăn',
      builder: (c, child) => MediaQuery(data: MediaQuery.of(c).copyWith(alwaysUse24HourFormat: true), child: child!),
    );
    if (t == null) return;
    final v = '${two(t.hour)}:${two(t.minute)}';
    final times = {...s.scheduleTimes, v}.toList()..sort();
    await _do(() => ref.read(deviceProvider.notifier).settings({'scheduleTimes': times, 'scheduleEnabled': true}), 'Đã thêm bữa $v');
  }

  Future<void> _tare() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Trừ bì cân'),
        content: const Text('Hãy lấy hết hạt ra khỏi khay trước. Đưa cân về 0 g ngay bây giờ?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Huỷ')),
          FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Trừ bì')),
        ],
      ),
    );
    if (ok == true) await _do(ref.read(deviceProvider.notifier).tare, 'Đã trừ bì cân');
  }

  @override
  Widget build(BuildContext context) {
    final view = ref.watch(deviceProvider);
    final stats = ref.watch(statsProvider);
    final d = view.device;
    final s = d?.settings ?? const DeviceSettings();
    if (DateTime.now().difference(_gramEditedAt).inSeconds > 3) _gram = null;
    final gram = _gram ?? s.targetGram;
    final online = d?.online ?? false;
    final feeding = online && (d?.feeding ?? false);
    final pending = online && view.pending;
    final dev = ref.read(deviceProvider.notifier);

    return RefreshIndicator(
      onRefresh: () async {
        await dev.refresh();
        await ref.read(statsProvider.notifier).refresh();
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 20),
        children: [
          // ---------------- Khay ăn + nút cho ăn
          Panel(
            radius: 18,
            child: Column(children: [
              Row(children: [
                Expanded(
                  child: Text.rich(TextSpan(children: [
                    TextSpan(text: 'Khay ăn ', style: context.tt.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                    if (s.feederPetName != null)
                      TextSpan(text: 'của ${s.feederPetName}', style: context.tt.titleMedium?.copyWith(color: context.muted)),
                  ])),
                ),
                _StatePill(online: online, feeding: feeding, pending: pending),
              ]),
              const SizedBox(height: 6),
              _Bowl(gram: online ? d!.bowlWeightGram : null, target: s.targetGram, feeding: feeding),
              const SizedBox(height: 8),
              Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                Text('Khẩu phần', style: TextStyle(color: context.muted)),
                const SizedBox(width: 8),
                IconButton.outlined(onPressed: () => _changeGram(gram - 5, s), icon: const Icon(Icons.remove), tooltip: 'Giảm 5 g'),
                SizedBox(
                  width: 70,
                  child: Text('$gram g',
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, fontFeatures: [FontFeature.tabularFigures()])),
                ),
                IconButton.outlined(onPressed: () => _changeGram(gram + 5, s), icon: const Icon(Icons.add), tooltip: 'Tăng 5 g'),
              ]),
              Wrap(spacing: 6, children: [
                for (final g in const [15, 25, 40, 60])
                  ChoiceChip(label: Text('$g g'), selected: gram == g, onSelected: (_) => _changeGram(g, s)),
              ]),
              const SizedBox(height: 10),
              Row(children: [
                Expanded(
                  child: SizedBox(
                    height: 54,
                    child: FilledButton.icon(
                      onPressed: (!online || feeding || pending) ? null : () => _do(() => dev.feed(gram), 'Đã gửi lệnh nhả $gram g'),
                      icon: const Icon(Icons.rice_bowl_outlined),
                      label: Text(feeding ? 'Đang nhả hạt…' : 'Cho ăn $gram g', style: const TextStyle(fontSize: 17)),
                    ),
                  ),
                ),
                if (feeding) ...[
                  const SizedBox(width: 8),
                  SizedBox(
                    height: 54,
                    child: FilledButton.icon(
                      style: FilledButton.styleFrom(backgroundColor: AppColors.stop, foregroundColor: Colors.white),
                      onPressed: () => _do(dev.stop, 'Đã dừng động cơ'),
                      icon: const Icon(Icons.stop_rounded),
                      label: const Text('Dừng'),
                    ),
                  ),
                ],
              ]),
              const SizedBox(height: 6),
              Text(_hint(d, view.error), textAlign: TextAlign.center, style: context.tt.bodySmall?.copyWith(color: context.muted)),
            ]),
          ),
          const SizedBox(height: 12),

          // ---------------- Thùng chứa + thông số
          Panel(
            child: Column(children: [
              Row(children: [
                _Hopper(percent: online ? d!.foodLevelPercent : -1, low: d?.lowFood ?? false),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const PanelTitle('Thùng chứa hạt'),
                    Text(online && d!.foodLevelPercent >= 0 ? '${d.foodLevelPercent}%' : '–',
                        style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
                    Text(!online ? 'Chưa có dữ liệu' : d!.lowFood ? 'Sắp hết hạt, hãy châm thêm' : 'Còn đủ hạt',
                        style: TextStyle(color: (d?.lowFood ?? false) ? AppColors.warn : context.muted, fontSize: 13)),
                  ]),
                ),
              ]),
              const SizedBox(height: 10),
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: 2.9,
                children: [
                  Fact('Mèo ở gần', !online ? '–' : d!.catPresent ? 'Có mèo' : 'Không'),
                  Fact('Lần ăn gần nhất', d?.lastFeedAt == null ? '–' : '${d!.lastFeedGram ?? '?'} g, ${ago(d.lastFeedAt)}'),
                  Fact('Hôm nay đã ăn', stats == null ? '–' : '${stats.todayFoodGram} g (${stats.feedingSessionsCount} bữa)'),
                  Fact('Wi-Fi máy', !online ? '–' : _signal(d!.wifiRssi)),
                ],
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: (!online || feeding) ? null : _tare,
                  icon: const Icon(Icons.scale_outlined),
                  label: const Text('Trừ bì cân về 0 g'),
                ),
              ),
            ]),
          ),
          const SizedBox(height: 12),

          // ---------------- Chế độ tự động + lịch
          Panel(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SwitchLine(
                title: 'Tự cho ăn khi mèo đến',
                subtitle: 'Chỉ nhả khi khay còn dưới 10 g, cách nhau tối thiểu 1 phút.',
                icon: Icons.bolt,
                value: s.autoMode,
                onChanged: d == null ? null : (v) => _do(() => dev.settings({'autoMode': v})),
              ),
              const Divider(height: 20),
              SwitchLine(
                title: 'Lịch hẹn giờ',
                subtitle: 'Đến giờ, máy chỉ nhả nếu khay gần hết.',
                icon: Icons.schedule,
                value: s.scheduleEnabled,
                onChanged: d == null ? null : (v) => _do(() => dev.settings({'scheduleEnabled': v})),
              ),
              const SizedBox(height: 8),
              Wrap(spacing: 6, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
                for (final t in s.scheduleTimes)
                  InputChip(
                    label: Text(t, style: const TextStyle(fontWeight: FontWeight.w600)),
                    onDeleted: () => _do(() => dev.settings({'scheduleTimes': s.scheduleTimes.where((x) => x != t).toList()})),
                    deleteButtonTooltipMessage: 'Xoá giờ $t',
                  ),
                ActionChip(avatar: const Icon(Icons.add, size: 18), label: const Text('Thêm giờ'), onPressed: d == null ? null : () => _addTime(s)),
              ]),
            ]),
          ),
          const SizedBox(height: 12),

          // ---------------- Bình nước (bơm tuần hoàn chạy liên tục, chỉ theo dõi mực nước)
          _WaterPanel(d: d),
          const SizedBox(height: 12),

          // ---------------- 7 ngày + hoạt động
          Panel(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const PanelTitle('7 ngày qua'),
              const SizedBox(height: 10),
              WeekBars(days: stats?.last7Days ?? const []),
            ]),
          ),
          const SizedBox(height: 12),
          Panel(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const PanelTitle('Hoạt động'),
              const SizedBox(height: 4),
              if ((stats?.latestAlerts ?? const []).isEmpty)
                Padding(padding: const EdgeInsets.all(12), child: Text('Chưa có hoạt động nào.', style: TextStyle(color: context.muted))),
              for (final a in stats?.latestAlerts ?? const <AlertItem>[])
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Padding(
                      padding: const EdgeInsets.only(top: 6, right: 10),
                      child: CircleAvatar(
                        radius: 4,
                        backgroundColor: a.level == 'WARNING'
                            ? AppColors.warn
                            : a.level == 'CRITICAL'
                                ? AppColors.stop
                                : AppColors.leaf,
                      ),
                    ),
                    Expanded(child: Text(a.message)),
                    const SizedBox(width: 8),
                    Text(timeLabel(a.createdAt), style: TextStyle(fontSize: 12, color: context.muted)),
                  ]),
                ),
            ]),
          ),
        ],
      ),
    );
  }

  static String _signal(int? rssi) {
    if (rssi == null) return 'Đã kết nối';
    if (rssi > -60) return 'Sóng mạnh';
    if (rssi > -75) return 'Sóng khá';
    return 'Sóng yếu';
  }

  static String _hint(DeviceState? d, String? error) {
    if (d == null) return error ?? 'Đang kết nối máy chủ…';
    if (!d.brokerConnected) return 'Máy chủ chưa kết nối được MQTT broker — kiểm tra MQTT_* trong file .env.';
    if (!d.online) return 'Máy cho ăn đang offline. Kiểm tra nguồn điện và Wi-Fi của ESP32.';
    if (d.lastFeedAt != null) return 'Lần gần nhất: ${d.lastFeedGram ?? '?'} g, ${ago(d.lastFeedAt)} (${d.lastFeedSource ?? 'máy'})';
    return '';
  }
}

class _StatePill extends StatelessWidget {
  final bool online;
  final bool feeding;
  final bool pending;

  const _StatePill({required this.online, required this.feeding, required this.pending});

  @override
  Widget build(BuildContext context) {
    final (text, color) = feeding
        ? ('Đang nhả hạt…', context.cs.primary)
        : pending
            ? ('Đang gửi lệnh…', context.cs.primary)
            : online
                ? ('Sẵn sàng', AppColors.leaf)
                : ('Offline', AppColors.stop);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(99)),
      child: Text(text, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 12)),
    );
  }
}

/// Bát ăn: mức hạt dâng theo số gram thật trên cân.
class _Bowl extends StatelessWidget {
  final double? gram;
  final int target;
  final bool feeding;

  const _Bowl({required this.gram, required this.target, required this.feeding});

  @override
  Widget build(BuildContext context) {
    final cap = math.max(60, target * 2).toDouble();
    final ratio = gram == null ? 0.0 : (gram! / cap).clamp(0.0, 1.0);
    return Column(children: [
      TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: ratio),
        duration: const Duration(milliseconds: 700),
        curve: Curves.easeOutCubic,
        builder: (_, r, __) => SizedBox(
          width: 240,
          height: 112,
          child: CustomPaint(
            painter: _BowlPainter(
              ratio: r,
              feeding: feeding,
              rim: context.cs.onSurface,
              empty: context.soft,
            ),
          ),
        ),
      ),
      Text.rich(TextSpan(children: [
        TextSpan(
          text: gram == null ? '–' : '${gram!.round()}',
          style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w700, fontFeatures: [FontFeature.tabularFigures()]),
        ),
        TextSpan(text: ' g trong khay', style: TextStyle(color: context.muted)),
      ])),
    ]);
  }
}

class _BowlPainter extends CustomPainter {
  final double ratio;
  final bool feeding;
  final Color rim;
  final Color empty;

  _BowlPainter({required this.ratio, required this.feeding, required this.rim, required this.empty});

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width, h = size.height;
    final top = h * 0.16;
    final bowl = Path()
      ..moveTo(w * 0.04, top)
      ..lineTo(w * 0.96, top)
      ..cubicTo(w * 0.96, h * 0.75, w * 0.78, h * 0.98, w * 0.5, h * 0.98)
      ..cubicTo(w * 0.22, h * 0.98, w * 0.04, h * 0.75, w * 0.04, top)
      ..close();

    canvas.save();
    canvas.clipPath(bowl);
    canvas.drawRect(Offset.zero & size, Paint()..color = empty);
    final fillTop = h * 0.98 - ratio * (h * 0.98 - top);
    canvas.drawRect(Rect.fromLTRB(0, fillTop, w, h), Paint()..color = AppColors.kibB);
    final kib = Paint()..color = AppColors.kibA;
    for (double y = fillTop + 5; y < h; y += 8) {
      final shift = ((y - fillTop) ~/ 8).isEven ? 0.0 : 7.0;
      for (double x = shift; x < w; x += 14) {
        canvas.drawOval(Rect.fromCenter(center: Offset(x, y), width: 6, height: 4.5), kib);
      }
    }
    canvas.restore();

    canvas.drawPath(bowl, Paint()
      ..color = rim
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3);

    if (feeding) {
      final p = Paint()..color = AppColors.kibA;
      for (final o in const [Offset(-10, -2), Offset(4, -14), Offset(14, -6)]) {
        canvas.drawOval(Rect.fromCenter(center: Offset(w / 2 + o.dx, top + o.dy), width: 6, height: 4.5), p);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _BowlPainter old) =>
      old.ratio != ratio || old.feeding != feeding || old.rim != rim || old.empty != empty;
}

/// Thanh đứng thể hiện thùng chứa hạt.
class _Hopper extends StatelessWidget {
  final int percent; // -1 = chưa đo
  final bool low;

  const _Hopper({required this.percent, required this.low});

  @override
  Widget build(BuildContext context) {
    final p = percent < 0 ? 0.0 : percent / 100.0;
    return Container(
      width: 34,
      height: 86,
      decoration: BoxDecoration(
        color: context.soft,
        border: Border.all(color: context.cs.onSurface, width: 2),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(6), bottom: Radius.circular(12)),
      ),
      clipBehavior: Clip.antiAlias,
      alignment: Alignment.bottomCenter,
      child: AnimatedFractionallySizedBox(
        duration: const Duration(milliseconds: 700),
        alignment: Alignment.bottomCenter,
        heightFactor: p,
        widthFactor: 1,
        child: Container(color: low ? AppColors.warn : AppColors.kibA),
      ),
    );
  }
}

/// Bình nước bơm tuần hoàn: mực nước từ cảm biến siêu âm trên ESP32 máy dọn cát, báo khi sắp cạn.
/// Bơm chạy liên tục bằng nguồn riêng nên app không có nút bật/tắt bơm.
class _WaterPanel extends StatelessWidget {
  final DeviceState? d;

  const _WaterPanel({required this.d});

  @override
  Widget build(BuildContext context) {
    final w = d;
    final on = w?.waterOnline ?? false;
    final ok = on && (w?.waterSensorOk ?? false) && (w?.waterPercent ?? -1) >= 0;
    final low = on && (w?.lowWater ?? false);
    final int? pct = ok ? w?.waterPercent : null;
    final everSeen = w?.waterLastSeen != null;

    final (String label, Color color) = !on
        ? (everSeen ? ('Mất tín hiệu', AppColors.stop) : ('Chưa kết nối', context.muted))
        : !ok
            ? ('Lỗi cảm biến', AppColors.warn)
            : low
                ? ('Sắp hết nước', AppColors.warn)
                : ('Đủ nước', AppColors.leaf);
    final note = !on
        ? (everSeen
            ? 'ESP32 máy dọn cát mất kết nối ${ago(w?.waterLastSeen)}. Kiểm tra nguồn điện và Wi-Fi.'
            : 'Chưa nhận mực nước. Nạp firmware esp32_litter_water cho ESP32 máy dọn cát.')
        : !ok
            ? 'Cảm biến siêu âm không đo được mặt nước, kiểm tra dây TRIG/ECHO.'
            : low
                ? 'Hãy đổ thêm nước để bơm không chạy khô và bé luôn có nước uống.'
                : 'Bơm tuần hoàn chạy liên tục · cập nhật ${ago(w?.waterLastSeen)}';

    return Panel(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        PanelTitle('Bình nước',
            icon: Icons.water_drop_outlined,
            trailing: Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 13))),
        const SizedBox(height: 10),
        Row(children: [
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: (pct ?? 0) / 100,
                minHeight: 10,
                backgroundColor: context.soft,
                color: low ? AppColors.warn : const Color(0xFF2B7BB9),
                semanticsLabel: 'Mực nước',
                semanticsValue: pct == null ? 'không rõ' : '$pct%',
              ),
            ),
          ),
          const SizedBox(width: 12),
          Text(pct == null ? '–' : '$pct%', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
        ]),
        const SizedBox(height: 6),
        Text(note, style: TextStyle(color: low ? AppColors.warn : context.muted, fontSize: 13)),
      ]),
    );
  }
}
