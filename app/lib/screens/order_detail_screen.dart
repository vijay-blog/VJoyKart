import 'package:flutter/material.dart';
import '../widgets/catalog_image.dart';
import 'package:intl/intl.dart';

import '../models/order.dart';

class OrderDetailScreen extends StatelessWidget {
  final CustomerOrder order;

  const OrderDetailScreen({super.key, required this.order});

  @override
  Widget build(BuildContext context) {
    final states = [
      OrderStatus.created,
      OrderStatus.partnerSearching,
      OrderStatus.partnerAssigned,
      OrderStatus.partnerAccepted,
      OrderStatus.picking,
      OrderStatus.packed,
      OrderStatus.deliverySearching,
      OrderStatus.deliveryAssigned,
      OrderStatus.pickedUp,
      OrderStatus.outForDelivery,
      OrderStatus.delivered,
    ];

    return Scaffold(
      appBar: AppBar(
        title: Text(
          order.id,
          style: const TextStyle(fontWeight: FontWeight.w900),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(18),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Order Number: ${order.id}',
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                  const SizedBox(height: 6),
                  Text(
                    'Order Date: ${DateFormat('dd MMM yyyy, hh:mm a').format(order.createdAt)}',
                  ),
                  const SizedBox(height: 6),
                  Text(
                      'Payment Method: ${order.paymentMethod == 'ONLINE' ? 'Online Payment' : 'Cash on Delivery'}'),
                  const SizedBox(height: 6),
                  Text('Payment Status: ${order.paymentStatus}'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Order tracking',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 12),
                  ...states.map((s) {
                    final active = s.index <= order.status.index;
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(
                        active
                            ? Icons.check_circle
                            : Icons.radio_button_unchecked,
                        color: active ? const Color(0xff3454d1) : Colors.grey,
                      ),
                      title: Text(
                        s.label,
                        style: TextStyle(
                          fontWeight: active ? FontWeight.w800 : null,
                        ),
                      ),
                    );
                  }),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Items',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 8),
                  ...order.items.map((x) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: CatalogImage(
                          source: x.product.imageAsset,
                          width: 48,
                          height: 48,
                        ),
                        title: Text(x.product.name),
                        subtitle: Text('${x.product.unit} × ${x.quantity}'),
                        trailing: Text('₹${x.total.round()}'),
                      )),
                  const Divider(),
                  _summaryRow('Subtotal', order.subtotal),
                  _summaryRow('Delivery', order.deliveryFee),
                  _summaryRow('Discount', order.discount),
                  _summaryRow('Total', order.total, bold: true),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Delivery address',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 8),
                  Text(order.address),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      const Icon(Icons.payments_outlined),
                      const SizedBox(width: 8),
                      Text(order.paymentMethod == 'ONLINE'
                          ? 'Online Payment'
                          : 'Cash on Delivery'),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _summaryRow(String label, double value, {bool bold = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Text(
            label,
            style: TextStyle(fontWeight: bold ? FontWeight.w900 : null),
          ),
          const Spacer(),
          Text(
            '₹${value.round()}',
            style: TextStyle(fontWeight: bold ? FontWeight.w900 : null),
          ),
        ],
      ),
    );
  }
}
