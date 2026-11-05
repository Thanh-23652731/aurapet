String two(int n) => n.toString().padLeft(2, '0');

String hhmm(DateTime d) => '${two(d.hour)}:${two(d.minute)}';

String dayMonth(DateTime d) => '${two(d.day)}/${two(d.month)}';

String timeLabel(DateTime? d) {
  if (d == null) return '–';
  final now = DateTime.now();
  final sameDay = d.year == now.year && d.month == now.month && d.day == now.day;
  return sameDay ? hhmm(d) : '${hhmm(d)} ${dayMonth(d)}';
}

String ago(DateTime? d) {
  if (d == null) return '–';
  final s = DateTime.now().difference(d).inSeconds;
  if (s < 60) return 'vừa xong';
  if (s < 3600) return '${s ~/ 60} phút trước';
  if (s < 86400) return '${s ~/ 3600} giờ trước';
  return timeLabel(d);
}

const _weekdays = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
String weekday(DateTime d) => _weekdays[d.weekday - 1];
