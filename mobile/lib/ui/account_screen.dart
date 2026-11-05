import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../state/providers.dart';
import 'widgets.dart';

class AccountScreen extends ConsumerStatefulWidget {
  const AccountScreen({super.key});

  @override
  ConsumerState<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends ConsumerState<AccountScreen> {
  late final _name = TextEditingController(text: ref.read(sessionProvider).user?.name);
  final _current = TextEditingController();
  final _new = TextEditingController();
  bool _busy = false;

  @override
  void dispose() {
    _name.dispose();
    _current.dispose();
    _new.dispose();
    super.dispose();
  }

  Future<void> _update({String? name, String? current, String? next}) async {
    setState(() => _busy = true);
    try {
      final u = await ref.read(apiProvider).updateProfile(name: name, currentPassword: current, newPassword: next);
      ref.read(sessionProvider.notifier).setUser(u);
      if (next != null) {
        _current.clear();
        _new.clear();
      }
      if (mounted) showMessage(context, next != null ? 'Đã đổi mật khẩu' : 'Đã lưu tên');
    } catch (e) {
      if (mounted) showMessage(context, e.toString(), error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(sessionProvider).user;
    final d = ref.watch(deviceProvider).device;
    final pets = ref.watch(petsProvider).pets;
    final client = ref.read(apiClientProvider);
    final feederId = d?.settings.feederPetId;

    return ListView(padding: const EdgeInsets.fromLTRB(12, 8, 12, 24), children: [
      Panel(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const PanelTitle('Hồ sơ', icon: Icons.person_outline),
          const SizedBox(height: 4),
          Text(user?.email ?? '', style: TextStyle(color: context.muted)),
          const SizedBox(height: 10),
          TextField(controller: _name, decoration: const InputDecoration(labelText: 'Họ tên', isDense: true)),
          const SizedBox(height: 10),
          FilledButton(onPressed: _busy ? null : () => _update(name: _name.text.trim()), child: const Text('Lưu tên')),
        ]),
      ),
      const SizedBox(height: 12),
      Panel(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const PanelTitle('Đổi mật khẩu', icon: Icons.lock_outline),
          TextField(controller: _current, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu hiện tại', isDense: true)),
          TextField(controller: _new, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu mới (tối thiểu 6 ký tự)', isDense: true)),
          const SizedBox(height: 10),
          OutlinedButton(
            onPressed: _busy
                ? null
                : () {
                    if (_new.text.length < 6) {
                      showMessage(context, 'Mật khẩu mới cần ít nhất 6 ký tự', error: true);
                      return;
                    }
                    _update(current: _current.text, next: _new.text);
                  },
            child: const Text('Đổi mật khẩu'),
          ),
        ]),
      ),
      const SizedBox(height: 12),
      Panel(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const PanelTitle('Máy cho ăn', icon: Icons.rice_bowl_outlined),
          const SizedBox(height: 6),
          if (pets.isNotEmpty)
            DropdownButtonFormField<String>(
              initialValue: pets.any((p) => p.id == feederId) ? feederId : null,
              decoration: const InputDecoration(labelText: 'Bé được ghi nhật ký khi máy nhả hạt', isDense: true),
              items: [for (final p in pets) DropdownMenuItem(value: p.id, child: Text(p.name))],
              onChanged: (v) async {
                if (v == null) return;
                final err = await ref.read(deviceProvider.notifier).settings({'feederPetId': v});
                if (context.mounted) showMessage(context, err ?? 'Đã lưu', error: err != null);
              },
            ),
          const SizedBox(height: 8),
          Text(
            d == null
                ? 'Đang tải…'
                : d.online
                    ? 'ESP32 tại ${d.ip ?? '?'}, đã nhả ${d.feedCountSinceBoot} lần từ lúc bật nguồn.'
                    : 'Máy cho ăn đang offline.',
            style: TextStyle(color: context.muted),
          ),
        ]),
      ),
      const SizedBox(height: 12),
      Panel(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const PanelTitle('Ứng dụng', icon: Icons.settings_outlined),
          const SizedBox(height: 4),
          Text('Máy chủ: ${client.baseUrl}', style: TextStyle(color: context.muted)),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            onPressed: () => ref.read(sessionProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
            label: const Text('Đăng xuất'),
          ),
        ]),
      ),
    ]);
  }
}
