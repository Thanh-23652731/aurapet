import 'package:aurapet_mobile/core/api.dart';
import 'package:aurapet_mobile/state/providers.dart';
import 'package:aurapet_mobile/ui/login_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Login screen shows sign-in form and server field', (tester) async {
    final client = ApiClient()..baseUrl = 'http://localhost:8080';
    await tester.pumpWidget(ProviderScope(
      overrides: [apiClientProvider.overrideWithValue(client)],
      child: const MaterialApp(home: LoginScreen()),
    ));
    await tester.pump();

    expect(find.text('AuraPet'), findsOneWidget);
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('Mật khẩu'), findsOneWidget);
    expect(find.text('Máy chủ'), findsOneWidget);

    await tester.tap(find.text('Tạo tài khoản').first);
    await tester.pump();
    expect(find.text('Họ tên'), findsOneWidget);
  });
}
