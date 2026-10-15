import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:aurapet_mobile/presentation/main_layout.dart';

void main() {
  group('Smart Pet Care Mobile UI Tests', () {
    testWidgets('MainLayout and WebHomeScreen render successfully', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: MainLayout(),
          ),
        ),
      );

      // Verify Welcome to Smart pet care text
      expect(find.text('Chào mừng đến với '), findsOneWidget);
      expect(find.text('Smart pet care'), findsOneWidget);
    });
  });
}
