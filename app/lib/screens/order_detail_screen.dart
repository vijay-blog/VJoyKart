import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/app_theme.dart';
import '../models/order.dart';
import '../widgets/catalog_image.dart';

class OrderDetailScreen extends StatelessWidget {
  final CustomerOrder order;
  const OrderDetailScreen({super.key, required this.order});

  @override
  Widget build(BuildContext context) {
    final states = [
      OrderStatus.created, OrderStatus.partnerSearching, OrderStatus.partnerAssigned,
      OrderStatus.partnerAccepted, OrderStatus.picking, OrderStatus.packed,
      OrderStatus.deliverySearching, OrderStatus.deliveryAssigned, OrderStatus.pickedUp,
      OrderStatus.outForDelivery, OrderStatus.delivered,
    ];
    final current = states.contains(order.status) ? order.status : OrderStatus.created;
    final currentIndex = states.indexOf(current);
    final deliveryStarted = currentIndex >= states.indexOf(OrderStatus.deliveryAssigned);
    return Scaffold(
      appBar: AppBar(title: Text('Order #${order.orderNumber}', style: const TextStyle(fontWeight: FontWeight.w900))),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
        children: [
          _summaryCard(),
          const SizedBox(height: 12),
          _trackingHeader(deliveryStarted),
          const SizedBox(height: 10),
          _mapCard(context, deliveryStarted),
          const SizedBox(height: 12),
          Card(child: Padding(padding: const EdgeInsets.fromLTRB(18, 18, 18, 10), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Delivery progress', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
            const SizedBox(height: 16),
            ...List.generate(states.length, (i) => _timelineItem(states[i], i, currentIndex)),
          ]))),
          const SizedBox(height: 12),
          _itemsCard(),
          const SizedBox(height: 12),
          _addressCard(),
        ],
      ),
    );
  }

  Widget _summaryCard() => Card(child: Padding(padding: const EdgeInsets.all(17), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Row(children: [
      Container(padding: const EdgeInsets.all(11), decoration: BoxDecoration(color: const Color(0xffe9edff), borderRadius: BorderRadius.circular(13)), child: const Icon(Icons.receipt_long_rounded, color: AppTheme.green)),
      const SizedBox(width: 12),
      const Expanded(child: Text('Order summary', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900))),
      Text('₹${order.total.round()}', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
    ]),
    const SizedBox(height: 14),
    _info('Order date', DateFormat('dd MMM yyyy, hh:mm a').format(order.createdAt)),
    _info('Payment', order.paymentMethod == 'ONLINE' ? 'Online Payment' : 'Cash on Delivery'),
    _info('Payment status', order.paymentStatus),
  ])));

  Widget _trackingHeader(bool deliveryStarted) => Card(child: Padding(padding: const EdgeInsets.all(17), child: Row(children: [
    Container(padding: const EdgeInsets.all(11), decoration: BoxDecoration(color: deliveryStarted ? const Color(0xffe8f7ed) : const Color(0xfffff3d8), shape: BoxShape.circle), child: Icon(deliveryStarted ? Icons.navigation_rounded : Icons.route_rounded, color: deliveryStarted ? Colors.green.shade700 : Colors.orange.shade800)),
    const SizedBox(width: 12),
    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('Live order tracking', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
      const SizedBox(height: 3),
      Text(deliveryStarted ? 'Your delivery is on the way.' : 'We are preparing your order. Tracking will update automatically.'),
    ])),
  ])));

  Widget _mapCard(BuildContext context, bool deliveryStarted) => Card(
    clipBehavior: Clip.antiAlias,
    child: Container(
      height: 205,
      decoration: const BoxDecoration(gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(0xffedf2ee), Color(0xffdce7dc)])),
      child: Stack(children: [
        Positioned.fill(child: CustomPaint(painter: _MapPainter(active: deliveryStarted))),
        Positioned(top: 16, left: 16, child: _mapLabel(Icons.storefront_rounded, 'VJoyKart Store', Colors.white)),
        Positioned(bottom: 20, right: 16, child: _mapLabel(Icons.home_rounded, 'Your delivery address', AppTheme.green)),
        if (deliveryStarted) Positioned(top: 84, left: 0, right: 0, child: Center(child: Container(width: 48, height: 48, decoration: BoxDecoration(color: Colors.white, shape: BoxShape.circle, boxShadow: [BoxShadow(color: Colors.black.withOpacity(.15), blurRadius: 12)]), child: const Icon(Icons.delivery_dining_rounded, color: AppTheme.green, size: 28)))),
        Positioned(bottom: 10, left: 10, child: TextButton.icon(onPressed: () => _openMaps(), style: TextButton.styleFrom(backgroundColor: Colors.white), icon: const Icon(Icons.map_outlined, size: 18), label: const Text('Open in Google Maps'))),
      ]),
    ),
  );

  Widget _mapLabel(IconData icon, String text, Color iconColor) => Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(14), boxShadow: [BoxShadow(color: Colors.black.withOpacity(.10), blurRadius: 8)]), child: Row(children: [Icon(icon, size: 19, color: iconColor), const SizedBox(width: 6), Text(text, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800))]));

  Widget _timelineItem(OrderStatus state, int index, int currentIndex) {
    final done = index <= currentIndex;
    final current = index == currentIndex;
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SizedBox(width: 34, child: Column(children: [
        Container(width: 25, height: 25, decoration: BoxDecoration(color: done ? AppTheme.green : Colors.white, shape: BoxShape.circle, border: Border.all(color: done ? AppTheme.green : Colors.black26, width: 2)), child: done ? const Icon(Icons.check, color: Colors.white, size: 16) : null),
        if (index < 10) Container(width: 2, height: 36, color: index < currentIndex ? AppTheme.green : Colors.black12),
      ])),
      const SizedBox(width: 10),
      Expanded(child: Padding(padding: const EdgeInsets.only(top: 2, bottom: 18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(state.label, style: TextStyle(fontSize: 15, fontWeight: current ? FontWeight.w900 : done ? FontWeight.w700 : FontWeight.w500, color: current ? AppTheme.green : null)),
        if (current) const Padding(padding: EdgeInsets.only(top: 3), child: Text('Current status', style: TextStyle(fontSize: 12, color: Colors.black54))),
      ]))),
    ]);
  }

  Widget _itemsCard() => Card(child: Padding(padding: const EdgeInsets.all(17), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const Text('Items', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
    const SizedBox(height: 8),
    ...order.items.map((x) => ListTile(contentPadding: EdgeInsets.zero, leading: CatalogImage(source: x.product.imageAsset, width: 48, height: 48), title: Text(x.product.name, style: const TextStyle(fontWeight: FontWeight.w700)), subtitle: Text('${x.product.unit} × ${x.quantity}'), trailing: Text('₹${x.total.round()}'))),
    const Divider(),
    _money('Subtotal', order.subtotal), _money('Delivery', order.deliveryFee), _money('Discount', order.discount), _money('Total', order.total, bold: true),
  ])));

  Widget _addressCard() => Card(child: Padding(padding: const EdgeInsets.all(17), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    const Text('Delivery address', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
    const SizedBox(height: 9),
    Row(crossAxisAlignment: CrossAxisAlignment.start, children: [const Icon(Icons.location_on_outlined, color: AppTheme.green), const SizedBox(width: 9), Expanded(child: Text(order.address, style: const TextStyle(fontWeight: FontWeight.w600)))]),
  ])));

  Widget _info(String label, String value) => Padding(padding: const EdgeInsets.only(bottom: 6), child: Row(children: [Text('$label: ', style: const TextStyle(color: Colors.black54)), Expanded(child: Text(value, style: const TextStyle(fontWeight: FontWeight.w700)))]));
  Widget _money(String label, double value, {bool bold = false}) => Padding(padding: const EdgeInsets.symmetric(vertical: 4), child: Row(children: [Text(label, style: TextStyle(fontWeight: bold ? FontWeight.w900 : null)), const Spacer(), Text('₹${value.round()}', style: TextStyle(fontWeight: bold ? FontWeight.w900 : null))]));

  Future<void> _openMaps() async {
    final query = Uri.encodeComponent(order.address.isEmpty ? 'Hyderabad' : order.address);
    final uri = Uri.parse('https://www.google.com/maps/search/?api=1&query=$query');
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }
}

class _MapPainter extends CustomPainter {
  final bool active;
  const _MapPainter({required this.active});
  @override
  void paint(Canvas canvas, Size size) {
    final grid = Paint()..color = Colors.white.withOpacity(.65)..strokeWidth = 1;
    for (double x = 0; x < size.width; x += 46) canvas.drawLine(Offset(x, 0), Offset(x + 40, size.height), grid);
    for (double y = 0; y < size.height; y += 42) canvas.drawLine(Offset(0, y), Offset(size.width, y + 8), grid);
    final road = Paint()..color = const Color(0xffaebdad)..strokeWidth = 18..strokeCap = StrokeCap.round;
    final route = Paint()..color = AppTheme.green..strokeWidth = 7..strokeCap = StrokeCap.round;
    final points = [Offset(45, size.height - 35), Offset(size.width * .34, size.height * .72), Offset(size.width * .55, size.height * .48), Offset(size.width * .72, size.height * .60), Offset(size.width - 55, 45)];
    for (var i = 0; i < points.length - 1; i++) canvas.drawLine(points[i], points[i + 1], road);
    if (active) canvas.drawPath(Path()..moveTo(points[0].dx, points[0].dy)..lineTo(points[1].dx, points[1].dy)..lineTo(points[2].dx, points[2].dy)..lineTo(points[3].dx, points[3].dy), route);
    final pin = Paint()..color = AppTheme.green;
    canvas.drawCircle(points.last, 9, pin);
    canvas.drawCircle(points.last, 4, Paint()..color = Colors.white);
  }
  @override
  bool shouldRepaint(covariant _MapPainter oldDelegate) => oldDelegate.active != active;
}
