import 'package:flutter/material.dart';

import '../core/app_theme.dart';

class WalletScreen extends StatelessWidget {
  const WalletScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('VJoyKart Wallet', style: TextStyle(fontWeight: FontWeight.w900)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(18, 8, 18, 30),
        children: [
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppTheme.green, AppTheme.dark],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(24),
              boxShadow: const [
                BoxShadow(blurRadius: 18, offset: Offset(0, 8), color: Color(0x22000000)),
              ],
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.account_balance_wallet_rounded, color: Colors.white, size: 34),
                SizedBox(height: 18),
                Text('Wallet balance', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.w700)),
                SizedBox(height: 4),
                Text('₹0.00', style: TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w900)),
                SizedBox(height: 8),
                Text(
                  'Wallet credits and eligible refunds will be shown here.',
                  style: TextStyle(color: Colors.white70),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.auto_awesome_rounded, color: AppTheme.green),
                      SizedBox(width: 10),
                      Text('Wallet & rewards', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'VJoyKart will show wallet credits, promotional rewards and eligible refund credits in one place. No money is added or charged from this screen.',
                    style: TextStyle(color: Colors.grey.shade700, height: 1.45),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 14),
          const Text('Recent activity', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 18),
              child: Column(
                children: [
                  Container(
                    padding: const EdgeInsets.all(15),
                    decoration: BoxDecoration(
                      color: const Color(0xffe9edff),
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: const Icon(Icons.receipt_long_outlined, size: 32, color: AppTheme.green),
                  ),
                  const SizedBox(height: 12),
                  const Text('No wallet activity yet', style: TextStyle(fontWeight: FontWeight.w900)),
                  const SizedBox(height: 5),
                  const Text(
                    'Your wallet history will appear here when VJoyKart adds a credit or eligible refund.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.black54),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
