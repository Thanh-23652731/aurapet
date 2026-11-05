import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/format.dart';
import '../core/theme.dart';
import '../data/models.dart';

void showMessage(BuildContext context, String text, {bool error = false}) {
  final messenger = ScaffoldMessenger.maybeOf(context);
  messenger?.hideCurrentSnackBar();
  messenger?.showSnackBar(SnackBar(
    content: Text(text),
    behavior: SnackBarBehavior.floating,
    backgroundColor: error ? AppColors.stop : null,
    duration: Duration(seconds: error ? 4 : 2),
  ));
}

/// Khung nội dung có viền mảnh (giống bản web).
class Panel extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;

  const Panel({super.key, required this.child, this.padding = const EdgeInsets.all(14), this.radius = 12});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: context.cs.surface,
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(color: context.line),
      ),
      child: child,
    );
  }
}

class PanelTitle extends StatelessWidget {
  final String text;
  final IconData? icon;
  final Widget? trailing;

  const PanelTitle(this.text, {super.key, this.icon, this.trailing});

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      if (icon != null) ...[Icon(icon, size: 18, color: context.cs.primary), const SizedBox(width: 6)],
      Expanded(child: Text(text, style: context.tt.titleSmall?.copyWith(fontWeight: FontWeight.w600))),
      if (trailing != null) trailing!,
    ]);
  }
}

/// Dòng có công tắc: tiêu đề + mô tả ngắn + Switch.
class SwitchLine extends StatelessWidget {
  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool value;
  final ValueChanged<bool>? onChanged;

  const SwitchLine({super.key, required this.title, this.subtitle, this.icon, required this.value, this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PanelTitle(title, icon: icon),
          if (subtitle != null)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(subtitle!, style: context.tt.bodySmall?.copyWith(color: context.muted)),
            ),
        ]),
      ),
      const SizedBox(width: 8),
      Switch(value: value, onChanged: onChanged, activeTrackColor: AppColors.leaf),
    ]);
  }
}

/// Ô thông số nhỏ.
class Fact extends StatelessWidget {
  final String label;
  final String value;

  const Fact(this.label, this.value, {super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(color: context.soft, borderRadius: BorderRadius.circular(8)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label, style: context.tt.labelSmall?.copyWith(color: context.muted)),
        const SizedBox(height: 2),
        Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
      ]),
    );
  }
}

/// Biểu đồ cột 7 ngày (tự vẽ, không cần thư viện).
class WeekBars extends StatelessWidget {
  final List<DailyFood> days;
  final double height;

  const WeekBars({super.key, required this.days, this.height = 120});

  @override
  Widget build(BuildContext context) {
    if (days.isEmpty) {
      return SizedBox(height: height, child: Center(child: Text('Chưa có dữ liệu', style: TextStyle(color: context.muted))));
    }
    final maxG = math.max(1, days.map((d) => d.gram).reduce(math.max));
    return SizedBox(
      height: height,
      child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        for (var i = 0; i < days.length; i++)
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 3),
              child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
                Text(days[i].gram == 0 ? '' : '${days[i].gram}',
                    style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, fontFeatures: [FontFeature.tabularFigures()])),
                const SizedBox(height: 3),
                Flexible(
                  child: FractionallySizedBox(
                    alignment: Alignment.bottomCenter,
                    heightFactor: math.max(0.03, days[i].gram / maxG),
                    child: Container(
                      decoration: BoxDecoration(
                        color: i == days.length - 1 ? context.cs.primary : AppColors.kibB,
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(6), bottom: Radius.circular(3)),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 4),
                Text(i == days.length - 1 ? 'Nay' : weekday(days[i].date),
                    style: TextStyle(fontSize: 11, color: context.muted)),
              ]),
            ),
          ),
      ]),
    );
  }
}

/// Đường cân nặng.
class WeightLine extends StatelessWidget {
  final List<WeightPoint> points;

  const WeightLine({super.key, required this.points});

  @override
  Widget build(BuildContext context) {
    if (points.length < 2) {
      return Text('Cần ít nhất 2 lần cân để vẽ biểu đồ.', style: TextStyle(color: context.muted));
    }
    final ws = points.map((p) => p.weight).toList();
    final minW = ws.reduce(math.min), maxW = ws.reduce(math.max);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SizedBox(
        height: 80,
        width: double.infinity,
        child: CustomPaint(painter: _LinePainter(ws, context.cs.primary, AppColors.kibB.withValues(alpha: 0.45))),
      ),
      const SizedBox(height: 4),
      Text('Hiện tại ${ws.last.toStringAsFixed(2)} kg, thấp nhất ${minW.toStringAsFixed(2)}, cao nhất ${maxW.toStringAsFixed(2)}',
          style: context.tt.bodySmall?.copyWith(color: context.muted)),
    ]);
  }
}

class _LinePainter extends CustomPainter {
  final List<double> values;
  final Color line;
  final Color fill;

  _LinePainter(this.values, this.line, this.fill);

  @override
  void paint(Canvas canvas, Size size) {
    final minV = values.reduce(math.min);
    final span = math.max(0.05, values.reduce(math.max) - minV);
    Offset pt(int i) => Offset(
          size.width * i / (values.length - 1),
          size.height - 6 - (values[i] - minV) / span * (size.height - 12),
        );
    final path = Path()..moveTo(pt(0).dx, pt(0).dy);
    for (var i = 1; i < values.length; i++) {
      path.lineTo(pt(i).dx, pt(i).dy);
    }
    final area = Path.from(path)
      ..lineTo(size.width, size.height)
      ..lineTo(0, size.height)
      ..close();
    canvas.drawPath(area, Paint()..color = fill);
    canvas.drawPath(path, Paint()
      ..color = line
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5
      ..strokeJoin = StrokeJoin.round);
    canvas.drawCircle(pt(values.length - 1), 4, Paint()..color = line);
  }

  @override
  bool shouldRepaint(covariant _LinePainter old) => old.values != values || old.line != line;
}

/// Ảnh đại diện thú cưng (ảnh lỗi -> chữ cái đầu).
class PetAvatar extends StatelessWidget {
  final Pet pet;
  final double size;

  const PetAvatar(this.pet, {super.key, this.size = 44});

  @override
  Widget build(BuildContext context) {
    final fallback = Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: context.soft, shape: BoxShape.circle),
      child: Text(pet.name.isEmpty ? '?' : pet.name.characters.first.toUpperCase(),
          style: TextStyle(fontWeight: FontWeight.w700, color: context.muted, fontSize: size * 0.4)),
    );
    if (pet.imageUrl == null) return fallback;
    return ClipOval(
      child: Image.network(pet.imageUrl!, width: size, height: size, fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => fallback),
    );
  }
}
