import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/format.dart';
import '../core/theme.dart';
import '../state/providers.dart';
import 'account_screen.dart';
import 'ai_screen.dart';
import 'control_screen.dart';
import 'history_screen.dart';
import 'pets_screen.dart';

/// Khung chính sau khi đăng nhập: thanh trạng thái máy + 5 tab.
class Shell extends ConsumerStatefulWidget {
  const Shell({super.key});

  @override
  ConsumerState<Shell> createState() => _ShellState();
}

class _ShellState extends ConsumerState<Shell> {
  int _tab = 0;

  static const _titles = ['Điều khiển', 'Thú cưng', 'Lịch sử', 'Bác sĩ AI', 'Tài khoản'];

  @override
  Widget build(BuildContext context) {
    final view = ref.watch(deviceProvider);
    final wide = MediaQuery.of(context).size.width >= 840;
    final pages = const [ControlScreen(), PetsScreen(), HistoryScreen(), AiScreen(), AccountScreen()];
    final destinations = const [
      (Icons.rice_bowl_outlined, Icons.rice_bowl, 'Điều khiển'),
      (Icons.pets_outlined, Icons.pets, 'Thú cưng'),
      (Icons.history, Icons.history, 'Lịch sử'),
      (Icons.chat_bubble_outline, Icons.chat_bubble, 'Bác sĩ AI'),
      (Icons.person_outline, Icons.person, 'Tài khoản'),
    ];

    final body = IndexedStack(index: _tab, children: pages);

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 12,
        title: Text(_titles[_tab], style: const TextStyle(fontWeight: FontWeight.w700)),
        actions: [
          _ConnChip(view: view),
          IconButton(
            tooltip: 'Đổi giao diện sáng/tối',
            icon: Icon(context.isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined),
            onPressed: () => ref.read(themeModeProvider.notifier).toggle(Theme.of(context).brightness),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: wide
          ? Row(children: [
              NavigationRail(
                selectedIndex: _tab,
                labelType: NavigationRailLabelType.all,
                onDestinationSelected: (i) => setState(() => _tab = i),
                destinations: [
                  for (final d in destinations)
                    NavigationRailDestination(icon: Icon(d.$1), selectedIcon: Icon(d.$2), label: Text(d.$3)),
                ],
              ),
              const VerticalDivider(width: 1),
              Expanded(child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 760), child: body))),
            ])
          : body,
      bottomNavigationBar: wide
          ? null
          : NavigationBar(
              height: 64,
              selectedIndex: _tab,
              onDestinationSelected: (i) => setState(() => _tab = i),
              destinations: [
                for (final d in destinations) NavigationDestination(icon: Icon(d.$1), selectedIcon: Icon(d.$2), label: d.$3),
              ],
            ),
    );
  }
}

class _ConnChip extends StatelessWidget {
  final DeviceView view;
  const _ConnChip({required this.view});

  @override
  Widget build(BuildContext context) {
    final d = view.device;
    final (color, text) = d == null
        ? (view.error != null ? AppColors.stop : context.muted, view.error != null ? 'Mất kết nối' : 'Đang kết nối')
        : !d.brokerConnected
            ? (AppColors.stop, 'Chưa có MQTT')
            : d.online
                ? (AppColors.leaf, 'Máy online')
                : (AppColors.warn, d.lastSeen == null ? 'Máy offline' : 'Offline ${ago(d.lastSeen)}');
    return Tooltip(
      message: view.error ?? text,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(color: color.withValues(alpha: 0.14), borderRadius: BorderRadius.circular(99)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          CircleAvatar(radius: 4, backgroundColor: color),
          const SizedBox(width: 6),
          Text(text, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color)),
        ]),
      ),
    );
  }
}
