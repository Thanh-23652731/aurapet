import 'package:aurapet_mobile/core/api.dart';
import 'package:aurapet_mobile/state/providers.dart';
import 'package:aurapet_mobile/ui/login_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// Máy chủ giả: trả lời ngay, không gọi mạng thật (gọi mạng thật để lại Timer của Dio -> test lỗi).
class _FakeClient extends ApiClient {
  final Map<String, dynamic>? info;
  _FakeClient(this.info);

  @override
  Future<Map<String, dynamic>?> serverInfo(String url, {Duration timeout = const Duration(seconds: 3)}) async => info;
}

Future<void> _pumpLogin(WidgetTester tester, ApiClient client) async {
  await tester.pumpWidget(ProviderScope(
    overrides: [apiClientProvider.overrideWithValue(client)],
    child: const MaterialApp(home: LoginScreen()),
  ));
  await tester.pump();
}

void main() {
  testWidgets('Login screen shows sign-in form and server field', (tester) async {
    await _pumpLogin(tester, _FakeClient({'app': 'aurapet', 'demoData': false})..baseUrl = 'http://localhost:8080');

    expect(find.text('AuraPet'), findsOneWidget);
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('Mật khẩu'), findsOneWidget);
    expect(find.text('Máy chủ'), findsOneWidget);
    expect(find.text('Dùng thử:'), findsNothing); // máy chủ không bật dữ liệu mẫu

    await tester.tap(find.text('Tạo tài khoản').first);
    await tester.pump();
    expect(find.text('Họ tên'), findsOneWidget);
  });

  testWidgets('Demo buttons appear only when the server has demo data', (tester) async {
    await _pumpLogin(tester, _FakeClient({'app': 'aurapet', 'demoData': true})..baseUrl = 'http://localhost:8080');
    await tester.pump(); // chờ kết quả serverInfo được vẽ lại
    expect(find.text('Dùng thử:'), findsOneWidget);
  });
}
