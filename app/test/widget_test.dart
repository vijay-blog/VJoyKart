import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nexamart_customer/main.dart';

void main() {
  testWidgets('opens Home after splash without customer login', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const VJoyKartApp());
    expect(find.text('VJoyKart'), findsOneWidget);
    expect(find.text('Sign in'), findsNothing);
    expect(find.text('Create account'), findsNothing);

    await tester.pump(const Duration(milliseconds: 1100));
    await tester.pump();

    expect(find.text('Search products...'), findsOneWidget);
    expect(find.text('Sign in'), findsNothing);
    expect(find.text('Create account'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
