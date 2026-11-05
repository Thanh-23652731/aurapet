import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/api.dart';
import 'core/theme.dart';
import 'state/providers.dart';
import 'ui/login_screen.dart';
import 'ui/shell.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final client = ApiClient();
  await client.init(); // đọc địa chỉ máy chủ + token đã lưu
  runApp(ProviderScope(
    overrides: [apiClientProvider.overrideWithValue(client)],
    child: const AuraPetApp(),
  ));
}

class AuraPetApp extends ConsumerWidget {
  const AuraPetApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    return MaterialApp(
      title: 'AuraPet',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ref.watch(themeModeProvider),
      home: session.restoring
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : session.loggedIn
              ? const Shell()
              : const LoginScreen(),
    );
  }
}
