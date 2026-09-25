import 'package:flutter/material.dart';

import '../models/order.dart';
import 'order_detail_screen.dart';

class OrderSuccessScreen extends StatelessWidget {
  final CustomerOrder order;
  const OrderSuccessScreen({super.key, required this.order});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(22),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Spacer(),
              const Center(
                child: Icon(Icons.check_circle,
                    color: Color(0xff3454d1), size: 88),
              ),
              const SizedBox(height: 18),
              const Center(
                child: Text('Order placed successfully',
                    style:
                        TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
              ),
              const SizedBox(height: 24),
              _item('Order Number', order.id),
              _item('Total Amount', '₹${order.total.round()}'),
              _item(
                  'Payment',
                  order.paymentMethod == 'ONLINE'
                      ? 'Online Payment'
                      : 'Cash on Delivery'),
              _item('Delivery Address', order.address),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: OutlinedButton(
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => OrderDetailScreen(order: order)),
                  ),
                  child: const Text('VIEW ORDER'),
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton(
                  onPressed: () => Navigator.popUntil(
                    context,
                    (route) => route.isFirst,
                  ),
                  child: const Text('CONTINUE SHOPPING'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _item(String label, String value) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(color: Colors.black54)),
            const SizedBox(height: 2),
            Text(value, style: const TextStyle(fontWeight: FontWeight.w800)),
          ],
        ),
      );
}
