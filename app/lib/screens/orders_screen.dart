import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../models/order.dart';
import '../providers/order_provider.dart';
import 'order_detail_screen.dart';

class OrdersScreen extends StatefulWidget {
  const OrdersScreen({super.key});
  @override
  State<OrdersScreen> createState() => _OrdersScreenState();
}

class _OrdersScreenState extends State<OrdersScreen> {
  int filter = 0;

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<OrderProvider>();
    final all = provider.orders;
    final active = all.where((o) => !_isFinished(o.status)).toList();
    final delivered = all.where((o) => _isFinished(o.status)).toList();
    final orders = filter == 0 ? all : filter == 1 ? active : delivered;

    return Container(
      color: AppTheme.bg,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 18, 12, 6),
              child: Row(children: [
                const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('My Orders', style: TextStyle(fontSize: 29, fontWeight: FontWeight.w900)),
                  SizedBox(height: 4),
                  Text('Track every order from pickup to delivery', style: TextStyle(color: Colors.black54)),
                ])),
                IconButton(onPressed: provider.refresh, icon: const Icon(Icons.refresh_rounded)),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
              child: Row(children: [
                _filter('All', 0, all.length),
                const SizedBox(width: 8),
                _filter('Active', 1, active.length),
                const SizedBox(width: 8),
                _filter('Completed', 2, delivered.length),
              ]),
            ),
            Expanded(
              child: orders.isEmpty
                  ? _empty(filter)
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(18, 0, 18, 110),
                      itemCount: orders.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 12),
                      itemBuilder: (_, i) => _orderCard(context, orders[i]),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _filter(String text, int value, int count) {
    final selected = filter == value;
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => filter = value),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(color: selected ? AppTheme.green : Colors.white, borderRadius: BorderRadius.circular(14)),
          child: Center(child: Text('$text  $count', style: TextStyle(color: selected ? Colors.white : Colors.black87, fontWeight: FontWeight.w800))),
        ),
      ),
    );
  }

  Widget _orderCard(BuildContext context, CustomerOrder order) {
    final progress = ((order.status.index + 1) / 12).clamp(0.08, 1.0);
    final finished = _isFinished(order.status);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => OrderDetailScreen(order: order))),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Container(padding: const EdgeInsets.all(10), decoration: BoxDecoration(color: const Color(0xffe9edff), borderRadius: BorderRadius.circular(12)), child: Icon(finished ? Icons.check_circle_outline : Icons.local_shipping_outlined, color: AppTheme.green)),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Order #${order.orderNumber}', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
                const SizedBox(height: 3),
                Text(DateFormat('dd MMM yyyy • hh:mm a').format(order.createdAt), style: const TextStyle(color: Colors.black54, fontSize: 12)),
              ])),
              Text('₹${order.total.round()}', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
            ]),
            const SizedBox(height: 14),
            Row(children: [
              _statusChip(order.status.label, finished),
              const Spacer(),
              Text('${order.items.length} ${order.items.length == 1 ? 'item' : 'items'}', style: const TextStyle(color: Colors.black54, fontWeight: FontWeight.w600)),
            ]),
            const SizedBox(height: 12),
            ClipRRect(borderRadius: BorderRadius.circular(10), child: LinearProgressIndicator(value: progress, minHeight: 7, backgroundColor: const Color(0xffeceef5), valueColor: const AlwaysStoppedAnimation(AppTheme.green))),
            const SizedBox(height: 9),
            Row(children: [
              Expanded(child: Text(order.status.label, style: const TextStyle(fontWeight: FontWeight.w700))),
              const Icon(Icons.arrow_forward_ios_rounded, size: 15, color: Colors.black45),
            ]),
          ]),
        ),
      ),
    );
  }

  Widget _statusChip(String text, bool finished) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    decoration: BoxDecoration(color: finished ? const Color(0xffe8f7ed) : const Color(0xfffff3d8), borderRadius: BorderRadius.circular(20)),
    child: Text(text, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: finished ? Colors.green.shade800 : Colors.orange.shade900)),
  );

  Widget _empty(int selected) => Center(child: Padding(padding: const EdgeInsets.all(28), child: Column(mainAxisSize: MainAxisSize.min, children: [
    Container(padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: const Color(0xffe9edff), borderRadius: BorderRadius.circular(24)), child: const Icon(Icons.receipt_long_outlined, size: 46, color: AppTheme.green)),
    const SizedBox(height: 16),
    Text(selected == 1 ? 'No active orders' : selected == 2 ? 'No completed orders' : 'No orders yet', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
    const SizedBox(height: 7),
    const Text('Your VJoyKart orders will appear here.', textAlign: TextAlign.center),
  ])));

  bool _isFinished(OrderStatus status) => status == OrderStatus.delivered || status == OrderStatus.cancelled || status == OrderStatus.returned || status == OrderStatus.deliveryFailed;
}
