import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/discovery.dart';
import '../core/theme.dart';
import '../state/providers.dart';
import 'widgets.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  bool _register = false;
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _pass = TextEditingController();
  late final _server = TextEditingController(text: ref.read(apiClientProvider).baseUrl);
  bool _checking = false;
  String? _localError;

  @override
  void dispose() {
    for (final c in [_name, _email, _pass, _server]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<bool> _ensureServer() async {
    final client = ref.read(apiClientProvider);
    final url = _server.text.trim();
    if (url.isEmpty) {
      setState(() => _localError = 'Vui lòng nhập địa chỉ máy chủ');
      return false;
    }
    if (url == client.baseUrl && client.hasServer) return true;
    setState(() => _checking = true);
    final ok = await client.ping(url);
    if (!mounted) return false;
    setState(() => _checking = false);
    if (!ok) {
      setState(() => _localError = 'Không tìm thấy máy chủ AuraPet tại $url');
      return false;
    }
    await client.setBaseUrl(url);
    return true;
  }

  Future<void> _submit({String? email, String? pass}) async {
    setState(() => _localError = null);
    final e = email ?? _email.text.trim();
    final p = pass ?? _pass.text;
    if (e.isEmpty || p.isEmpty) {
      setState(() => _localError = 'Vui lòng nhập email và mật khẩu');
      return;
    }
    if (_register && email == null && _name.text.trim().isEmpty) {
      setState(() => _localError = 'Vui lòng nhập họ tên');
      return;
    }
    if (!await _ensureServer()) return;
    final s = ref.read(sessionProvider.notifier);
    if (_register && email == null) {
      await s.register(_name.text.trim(), e, p);
    } else {
      await s.login(e, p);
    }
  }

  Future<void> _discover() async {
    setState(() {
      _checking = true;
      _localError = null;
    });
    final url = await ServerDiscovery.find();
    if (!mounted) return;
    setState(() => _checking = false);
    if (url == null) {
      showMessage(context, 'Không tìm thấy máy chủ trong mạng Wi-Fi này. Hãy nhập địa chỉ thủ công.', error: true);
    } else {
      _server.text = url;
      showMessage(context, 'Đã tìm thấy máy chủ $url');
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionProvider);
    final busy = session.busy || _checking;
    final error = _localError ?? session.error;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Panel(
                radius: 18,
                padding: const EdgeInsets.all(20),
                child: AutofillGroup(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    Row(children: [
                      Icon(Icons.rice_bowl_outlined, color: context.cs.primary, size: 28),
                      const SizedBox(width: 8),
                      Text('AuraPet', style: context.tt.titleLarge?.copyWith(fontWeight: FontWeight.w700, color: context.cs.primary)),
                    ]),
                    const SizedBox(height: 10),
                    Text('Máy cho ăn của bé đang chờ bạn', style: context.tt.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 14),
                    SegmentedButton<bool>(
                      segments: const [
                        ButtonSegment(value: false, label: Text('Đăng nhập')),
                        ButtonSegment(value: true, label: Text('Tạo tài khoản')),
                      ],
                      selected: {_register},
                      onSelectionChanged: (s) => setState(() {
                        _register = s.first;
                        _localError = null;
                      }),
                    ),
                    const SizedBox(height: 12),
                    if (_register) ...[
                      TextField(controller: _name, decoration: const InputDecoration(labelText: 'Họ tên'), autofillHints: const [AutofillHints.name]),
                      const SizedBox(height: 8),
                    ],
                    TextField(
                      controller: _email,
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.email],
                      decoration: const InputDecoration(labelText: 'Email'),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _pass,
                      obscureText: true,
                      autofillHints: [_register ? AutofillHints.newPassword : AutofillHints.password],
                      decoration: const InputDecoration(labelText: 'Mật khẩu'),
                      onSubmitted: (_) => _submit(),
                    ),
                    if (error != null) ...[
                      const SizedBox(height: 10),
                      Text(error, style: const TextStyle(color: AppColors.stop)),
                    ],
                    const SizedBox(height: 14),
                    SizedBox(
                      height: 48,
                      child: FilledButton(
                        onPressed: busy ? null : () => _submit(),
                        child: Text(busy ? 'Đang xử lý…' : _register ? 'Tạo tài khoản' : 'Đăng nhập'),
                      ),
                    ),
                    if (!_register) ...[
                      const SizedBox(height: 8),
                      Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 4, children: [
                        Text('Dùng thử:', style: TextStyle(color: context.muted, fontSize: 13)),
                        TextButton(onPressed: busy ? null : () => _submit(email: 'admin@aurapet.com', pass: 'Admin@123'), child: const Text('Quản trị')),
                        TextButton(onPressed: busy ? null : () => _submit(email: 'user@aurapet.com', pass: 'User@123'), child: const Text('Người dùng')),
                      ]),
                    ],
                    const Divider(height: 24),
                    Text('Máy chủ', style: TextStyle(color: context.muted, fontSize: 13)),
                    const SizedBox(height: 6),
                    Row(children: [
                      Expanded(
                        child: TextField(
                          controller: _server,
                          keyboardType: TextInputType.url,
                          decoration: const InputDecoration(hintText: 'http://192.168.1.10:8080', isDense: true, border: OutlineInputBorder()),
                        ),
                      ),
                      if (!kIsWeb) ...[
                        const SizedBox(width: 8),
                        IconButton.outlined(onPressed: busy ? null : _discover, icon: const Icon(Icons.wifi_find), tooltip: 'Tự tìm trong mạng Wi-Fi'),
                      ],
                    ]),
                    const SizedBox(height: 4),
                    Text('Máy tính chạy backend và điện thoại phải cùng mạng Wi-Fi, hoặc dùng địa chỉ cloud/Cloudflare Tunnel.',
                        style: TextStyle(color: context.muted, fontSize: 12)),
                  ]),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
