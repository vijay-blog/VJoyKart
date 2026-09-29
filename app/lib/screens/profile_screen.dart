import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'saved_addresses_screen.dart';
import 'wallet_screen.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});
  static const supportNumber = '9959095202';

  @override
  Widget build(BuildContext c) => SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 18, 18, 110),
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xff3454d1), Color(0xff182451)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Row(
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: Colors.white24,
                    child: Icon(Icons.person_rounded, color: Colors.white, size: 30),
                  ),
                  SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('VJoyKart Customer', style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w900)),
                        SizedBox(height: 4),
                        Text('Shop locally. Order easily. Get it delivered.', style: TextStyle(color: Colors.white70)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            _sectionTitle('Shopping'),
            Card(
              child: Column(
                children: [
                  _tile(
                    c,
                    Icons.account_balance_wallet_rounded,
                    'Wallet & Rewards',
                    'View wallet credits, rewards and eligible refunds',
                    () => Navigator.push(c, MaterialPageRoute(builder: (_) => const WalletScreen())),
                  ),
                  _tile(
                    c,
                    Icons.location_on_outlined,
                    'Saved Addresses',
                    'Manage your delivery locations',
                    () => Navigator.push(c, MaterialPageRoute(builder: (_) => const SavedAddressesScreen())),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            _sectionTitle('Need Help?'),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  children: [
                    const Row(
                      children: [
                        Icon(Icons.support_agent_rounded, color: Color(0xff3454d1)),
                        SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'We can help with orders, payments, deliveries and shopping.',
                            style: TextStyle(fontWeight: FontWeight.w700),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () => _call(c),
                            icon: const Icon(Icons.call_outlined),
                            label: const Text('Call Us'),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: FilledButton.icon(
                            onPressed: () => _whatsapp(c),
                            icon: const Icon(Icons.chat_outlined),
                            label: const Text('WhatsApp'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text('Support: 9959095202', style: TextStyle(color: Colors.black54, fontWeight: FontWeight.w600)),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 14),
            _sectionTitle('VJoyKart'),
            Card(
              child: Column(
                children: [
                  _tile(
                    c,
                    Icons.info_outline,
                    'About VJoyKart',
                    'How VJoyKart helps customers and local shops',
                    () => _about(c),
                  ),
                  _tile(
                    c,
                    Icons.gavel_outlined,
                    'Terms',
                    'Simple rules for using VJoyKart',
                    () => _terms(c),
                  ),
                  _tile(
                    c,
                    Icons.verified_outlined,
                    'App Version',
                    'Current customer app version',
                    null,
                    trailing: const Text('2.1.2', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.black54)),
                  ),
                ],
              ),
            ),
          ],
        ),
      );

  Widget _sectionTitle(String title) => Padding(
        padding: const EdgeInsets.only(left: 4, bottom: 8),
        child: Text(
          title.toUpperCase(),
          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w900, letterSpacing: 1.1, color: Colors.black54),
        ),
      );

  Widget _tile(
    BuildContext c,
    IconData icon,
    String title,
    String subtitle,
    VoidCallback? onTap, {
    Widget? trailing,
  }) => ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
        leading: Container(
          padding: const EdgeInsets.all(9),
          decoration: BoxDecoration(color: const Color(0xfff1f3fa), borderRadius: BorderRadius.circular(12)),
          child: Icon(icon, color: const Color(0xff4c4f59)),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
        subtitle: Text(subtitle),
        trailing: trailing ?? const Icon(Icons.chevron_right_rounded, color: Colors.black38),
        onTap: onTap,
      );

  Future<void> _call(BuildContext c) async {
    final uri = Uri(scheme: 'tel', path: supportNumber);
    if (!await launchUrl(uri)) _error(c, 'Unable to open the phone dialer.');
  }

  Future<void> _whatsapp(BuildContext c) async {
    final uri = Uri.parse(
      'https://wa.me/91$supportNumber?text=${Uri.encodeComponent('Hi VJoyKart Support, I need help with my order.')}',
    );
    if (!await launchUrl(uri, mode: LaunchMode.externalApplication)) {
      _error(c, 'WhatsApp is not available on this device.');
    }
  }

  void _error(BuildContext c, String text) => ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(text)));

  void _about(BuildContext c) => showDialog(
        context: c,
        builder: (_) => AlertDialog(
          title: const Row(children: [Icon(Icons.favorite_rounded, color: Color(0xff3454d1)), SizedBox(width: 8), Text('About VJoyKart')]),
          content: const Text(
            'VJoyKart is built to make everyday shopping easier while helping neighbourhood kirana stores and small local shops reach more customers.\n\n'
            'Customers get convenient product discovery, ordering and delivery, while local shops get another digital channel to serve nearby customers.\n\n'
            'The goal is simple: make local shopping more convenient without losing the personal connection of neighbourhood stores.',
          ),
          actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('Close'))],
        ),
      );

  void _terms(BuildContext c) => showDialog(
        context: c,
        builder: (_) => AlertDialog(
          title: const Text('VJoyKart Terms'),
          content: const SingleChildScrollView(
            child: Text(
              'By using VJoyKart, you agree to use the app for genuine shopping and delivery requests.\n\n'
              '• Product availability and prices may change.\n'
              '• Orders are subject to store availability and service coverage.\n'
              '• Customers should provide accurate contact and delivery information.\n'
              '• Cash on Delivery orders should be accepted only when the customer is ready to receive them.\n'
              '• Online payments are processed through the configured payment gateway.\n'
              '• Delivery times are estimates and may vary due to store preparation, traffic or other operational conditions.\n'
              '• Refunds, cancellations and payment issues are handled according to the applicable order and payment process.\n\n'
              'VJoyKart may update these terms as the service evolves.',
            ),
          ),
          actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('Close'))],
        ),
      );
}
