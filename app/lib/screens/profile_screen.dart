import 'package:flutter/material.dart';
import 'orders_screen.dart';
import 'saved_addresses_screen.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext c) => SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(18),
          children: [
            const Text('Profile',
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900)),
            const SizedBox(height: 18),
            const Card(
              child: ListTile(
                leading: CircleAvatar(
                    backgroundColor: Color(0xffe9edff),
                    child: Icon(Icons.shopping_bag, color: Color(0xff3454d1))),
                title: Text('Shop as guest',
                    style: TextStyle(fontWeight: FontWeight.w900)),
                subtitle: Text(
                    'No account is required to browse VJoyKart. Mobile OTP is requested only when you place an order.'),
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Column(
                children: [
                  ListTile(
                      leading: const Icon(Icons.receipt_long_outlined),
                      title: const Text('My Orders'),
                      onTap: () => Navigator.push(c,
                          MaterialPageRoute(builder: (_) => const OrdersScreen()))),
                  ListTile(
                      leading: const Icon(Icons.location_on_outlined),
                      title: const Text('Saved Addresses'),
                      onTap: () => Navigator.push(
                          c,
                          MaterialPageRoute(
                              builder: (_) => const SavedAddressesScreen()))),
                  const ListTile(
                      leading: Icon(Icons.help_outline),
                      title: Text('Help & Support')),
                  const ListTile(
                      leading: Icon(Icons.info_outline),
                      title: Text('About VJoyKart')),
                  const ListTile(
                      leading: Icon(Icons.gavel_outlined), title: Text('Terms')),
                  const ListTile(
                      leading: Icon(Icons.policy_outlined),
                      title: Text('Privacy Policy')),
                  const ListTile(
                      leading: Icon(Icons.verified_outlined),
                      title: Text('App Version'),
                      trailing: Text('2.1.0')),
                ],
              ),
            ),
          ],
        ),
      );
}
