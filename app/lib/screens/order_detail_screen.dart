import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/app_theme.dart';
import '../models/order.dart';
import '../providers/order_provider.dart';
import '../widgets/catalog_image.dart';

/// Official VJoyKart Store share link; the backend `store` object is the source of truth.
const _storeMapsFallback = 'https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9';

class OrderDetailScreen extends StatefulWidget {
  final CustomerOrder order;
  const OrderDetailScreen({super.key, required this.order});

  @override
  State<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends State<OrderDetailScreen>
    with WidgetsBindingObserver {
  static const _pollInterval = Duration(seconds: 10);
  Timer? _timer;
  bool _refreshing = false;
  bool _offline = false;

  CustomerOrder get order => widget.order;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
    _startPolling();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _timer?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refresh();
      _startPolling();
    } else if (state == AppLifecycleState.paused) {
      _timer?.cancel();
    }
  }

  void _startPolling() {
    _timer?.cancel();
    if (order.isFinished) return;
    _timer = Timer.periodic(_pollInterval, (_) => _refresh());
  }

  Future<void> _refresh() async {
    if (_refreshing || !mounted) return;
    _refreshing = true;
    try {
      await context.read<OrderProvider>().refreshTracking(order);
      if (!mounted) return;
      setState(() => _offline = false);
      if (order.isFinished) _timer?.cancel();
    } catch (_) {
      if (mounted) setState(() => _offline = true);
    } finally {
      _refreshing = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    context.watch<OrderProvider>();
    return Scaffold(
      appBar: AppBar(
          title: Text('Order #${order.orderNumber}',
              style: const TextStyle(fontWeight: FontWeight.w900))),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            _summaryCard(),
            const SizedBox(height: 12),
            _trackingHeader(),
            const SizedBox(height: 10),
            _mapCard(),
            const SizedBox(height: 12),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 350),
              child: KeyedSubtree(
                  key: ValueKey(
                      '${order.assignmentStatus}-${order.deliveryPartner?.id}'),
                  child: _deliveryPartnerCard()),
            ),
            const SizedBox(height: 12),
            _progressCard(),
            const SizedBox(height: 12),
            _itemsCard(),
            const SizedBox(height: 12),
            _addressCard(),
          ],
        ),
      ),
    );
  }

  Widget _summaryCard() => Card(
      child: Padding(
          padding: const EdgeInsets.all(17),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Container(
                  padding: const EdgeInsets.all(11),
                  decoration: BoxDecoration(
                      color: const Color(0xffe9edff),
                      borderRadius: BorderRadius.circular(13)),
                  child: const Icon(Icons.receipt_long_rounded,
                      color: AppTheme.green)),
              const SizedBox(width: 12),
              Expanded(
                  child: Text('Order #${order.orderNumber}',
                      style: const TextStyle(
                          fontSize: 18, fontWeight: FontWeight.w900))),
              Text('₹${order.total.round()}',
                  style: const TextStyle(
                      fontSize: 18, fontWeight: FontWeight.w900)),
            ]),
            const SizedBox(height: 14),
            _info('Order date',
                DateFormat('dd MMM yyyy, hh:mm a').format(order.createdAt)),
            _info(
                'Payment',
                order.paymentMethod == 'ONLINE'
                    ? 'Online Payment'
                    : 'Cash on Delivery'),
            _info('Payment status', order.paymentStatus),
          ])));

  Widget _trackingHeader() {
    final cancelled = order.deliveryStatus == 'CANCELLED';
    final delivered = order.deliveryStatus == 'DELIVERED';
    final moving = order.deliveryStatus == 'ON_THE_WAY' ||
        order.deliveryStatus == 'ARRIVED';
    final color = cancelled
        ? Colors.red.shade700
        : delivered || moving
            ? Colors.green.shade700
            : Colors.orange.shade800;
    final subtitle = cancelled
        ? 'This order was cancelled.'
        : delivered
            ? 'Your order has been delivered. Enjoy!'
            : order.hasDeliveryPartner
                ? '${order.statusLabel} • updates automatically'
                : 'Finding a delivery partner near ${order.store?.name ?? 'VJoyKart Store'}…';
    return Card(
        child: Padding(
            padding: const EdgeInsets.all(17),
            child: Row(children: [
              Container(
                  padding: const EdgeInsets.all(11),
                  decoration: BoxDecoration(
                      color: color.withOpacity(.12), shape: BoxShape.circle),
                  child: Icon(
                      cancelled
                          ? Icons.cancel_outlined
                          : delivered
                              ? Icons.check_circle_rounded
                              : moving
                                  ? Icons.navigation_rounded
                                  : Icons.route_rounded,
                      color: color)),
              const SizedBox(width: 12),
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    const Text('Live order tracking',
                        style: TextStyle(
                            fontSize: 18, fontWeight: FontWeight.w900)),
                    const SizedBox(height: 3),
                    AnimatedSwitcher(
                        duration: const Duration(milliseconds: 300),
                        child: Text(subtitle, key: ValueKey(subtitle))),
                    if (_offline)
                      const Padding(
                          padding: EdgeInsets.only(top: 4),
                          child: Text('Reconnecting…',
                              style: TextStyle(
                                  fontSize: 12, color: Colors.black45))),
                  ])),
            ])));
  }

  Widget _mapCard() {
    final storeName = order.store?.name ?? 'VJoyKart Store';
    final moving = order.deliveryStatus == 'ON_THE_WAY' ||
        order.deliveryStatus == 'ARRIVED' ||
        order.deliveryStatus == 'DELIVERED';
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(children: [
        Container(
          height: 190,
          decoration: const BoxDecoration(
              gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xffedf2ee), Color(0xffdce7dc)])),
          child: Stack(children: [
            Positioned.fill(
                child: CustomPaint(painter: _MapPainter(active: moving))),
            Positioned(
                bottom: 18,
                left: 14,
                child: _mapLabel(Icons.storefront_rounded,
                    '$storeName • Pickup', Colors.deepOrange)),
            Positioned(
                top: 14,
                right: 14,
                child: _mapLabel(Icons.home_rounded, 'Your delivery address',
                    AppTheme.green)),
            if (moving)
              Positioned(
                  top: 70,
                  left: 0,
                  right: 0,
                  child: Center(
                      child: Container(
                          width: 46,
                          height: 46,
                          decoration: BoxDecoration(
                              color: Colors.white,
                              shape: BoxShape.circle,
                              boxShadow: [
                                BoxShadow(
                                    color: Colors.black.withOpacity(.15),
                                    blurRadius: 12)
                              ]),
                          child: const Icon(Icons.delivery_dining_rounded,
                              color: AppTheme.green, size: 28)))),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
          child: Row(children: [
            Expanded(
                child: OutlinedButton.icon(
                    onPressed: _openStoreInMaps,
                    icon: const Icon(Icons.storefront_rounded, size: 18),
                    label: const Text('Store in Maps',
                        overflow: TextOverflow.ellipsis))),
            const SizedBox(width: 10),
            Expanded(
                child: OutlinedButton.icon(
                    onPressed: _openDeliveryInMaps,
                    icon: const Icon(Icons.home_outlined, size: 18),
                    label: const Text('My address',
                        overflow: TextOverflow.ellipsis))),
          ]),
        ),
      ]),
    );
  }

  Widget _mapLabel(IconData icon, String text, Color iconColor) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          boxShadow: [
            BoxShadow(color: Colors.black.withOpacity(.10), blurRadius: 8)
          ]),
      child: Row(children: [
        Icon(icon, size: 19, color: iconColor),
        const SizedBox(width: 6),
        Text(text,
            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800))
      ]));

  Widget _deliveryPartnerCard() {
    if (order.deliveryStatus == 'CANCELLED') return const SizedBox.shrink();
    final partner = order.deliveryPartner;
    if (!order.hasDeliveryPartner || partner == null) {
      return Card(
          child: Padding(
              padding: const EdgeInsets.all(17),
              child: Row(children: [
                const SizedBox(
                    width: 26,
                    height: 26,
                    child: CircularProgressIndicator(strokeWidth: 2.6)),
                const SizedBox(width: 14),
                Expanded(
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                      const Text('Assigning a delivery boy',
                          style: TextStyle(
                              fontSize: 16, fontWeight: FontWeight.w900)),
                      const SizedBox(height: 3),
                      Text(
                          order.paymentMethod == 'ONLINE' &&
                                  order.paymentStatus != 'PAID'
                              ? 'A delivery boy is assigned once payment is confirmed.'
                              : 'We will show your delivery boy here as soon as one is assigned.',
                          style: const TextStyle(color: Colors.black54)),
                    ])),
              ])));
    }
    return Card(
        child: Padding(
            padding: const EdgeInsets.all(17),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Icon(Icons.verified_rounded,
                    color: Colors.green.shade700, size: 20),
                const SizedBox(width: 6),
                const Text('Delivery boy assigned',
                    style:
                        TextStyle(fontSize: 15, fontWeight: FontWeight.w900)),
                const Spacer(),
                if (order.assignedAt != null)
                  Text(DateFormat('hh:mm a').format(order.assignedAt!),
                      style:
                          const TextStyle(fontSize: 12, color: Colors.black54)),
              ]),
              const SizedBox(height: 14),
              Row(children: [
                CircleAvatar(
                    radius: 24,
                    backgroundColor: const Color(0xffe9edff),
                    child: Text(
                        partner.name.isEmpty
                            ? '?'
                            : partner.name.trim()[0].toUpperCase(),
                        style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                            color: AppTheme.green))),
                const SizedBox(width: 12),
                Expanded(
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                      Text(partner.name,
                          style: const TextStyle(
                              fontSize: 17, fontWeight: FontWeight.w900)),
                      const SizedBox(height: 2),
                      Text(partner.phone,
                          style: const TextStyle(
                              color: Colors.black54,
                              fontWeight: FontWeight.w600)),
                    ])),
              ]),
              if (partner.phone.isNotEmpty && !order.isFinished) ...[
                const SizedBox(height: 14),
                SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: FilledButton.icon(
                        onPressed: () => _call(partner.phone),
                        icon: const Icon(Icons.call_rounded),
                        label: const Text('Call delivery boy',
                            style: TextStyle(fontWeight: FontWeight.w800)))),
              ],
            ])));
  }

  Widget _progressCard() => Card(
      child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Delivery progress',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
            const SizedBox(height: 16),
            for (var i = 0; i < order.progress.length; i++)
              _timelineItem(order.progress[i], i == order.progress.length - 1),
          ])));

  Widget _timelineItem(DeliveryStep step, bool last) {
    final done = step.completed;
    final current = step.current && order.deliveryStatus != 'CANCELLED';
    final active = done || current;
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SizedBox(
          width: 34,
          child: Column(children: [
            AnimatedContainer(
                duration: const Duration(milliseconds: 350),
                width: 26,
                height: 26,
                decoration: BoxDecoration(
                    color: done ? AppTheme.green : Colors.white,
                    shape: BoxShape.circle,
                    border: Border.all(
                        color: active ? AppTheme.green : Colors.black26,
                        width: current ? 3 : 2),
                    boxShadow: current
                        ? [
                            BoxShadow(
                                color: AppTheme.green.withOpacity(.30),
                                blurRadius: 10,
                                spreadRadius: 1)
                          ]
                        : null),
                child: done
                    ? const Icon(Icons.check, color: Colors.white, size: 16)
                    : current
                        ? Center(
                            child: Container(
                                width: 9,
                                height: 9,
                                decoration: const BoxDecoration(
                                    color: AppTheme.green,
                                    shape: BoxShape.circle)))
                        : null),
            if (!last)
              AnimatedContainer(
                  duration: const Duration(milliseconds: 350),
                  width: 2,
                  height: 30,
                  color: done ? AppTheme.green : Colors.black12),
          ])),
      const SizedBox(width: 10),
      Expanded(
          child: Padding(
              padding: const EdgeInsets.only(top: 3, bottom: 16),
              child: Row(children: [
                Expanded(
                    child: Text(step.label,
                        style: TextStyle(
                            fontSize: 15,
                            fontWeight: current
                                ? FontWeight.w900
                                : done
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                            color: current
                                ? AppTheme.green
                                : done
                                    ? null
                                    : Colors.black38))),
                if (current)
                  Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                          color: const Color(0xffe9edff),
                          borderRadius: BorderRadius.circular(10)),
                      child: const Text('Now',
                          style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w800,
                              color: AppTheme.green)))
                else if (done && step.at != null)
                  Text(DateFormat('hh:mm a').format(step.at!),
                      style:
                          const TextStyle(fontSize: 12, color: Colors.black54)),
              ]))),
    ]);
  }

  Widget _itemsCard() => Card(
      child: Padding(
          padding: const EdgeInsets.all(17),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Items',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
            const SizedBox(height: 8),
            ...order.items.map((x) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: CatalogImage(
                    source: x.product.imageAsset, width: 48, height: 48),
                title: Text(x.product.name,
                    style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('${x.product.unit} × ${x.quantity}'),
                trailing: Text('₹${x.total.round()}'))),
            const Divider(),
            _money('Subtotal', order.subtotal),
            _money('Delivery', order.deliveryFee),
            _money('Discount', order.discount),
            _money('Total', order.total, bold: true),
          ])));

  Widget _addressCard() => Card(
      child: Padding(
          padding: const EdgeInsets.all(17),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('Pickup & delivery',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
            const SizedBox(height: 12),
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Icon(Icons.storefront_rounded, color: Colors.deepOrange),
              const SizedBox(width: 9),
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    Text('Pickup: ${order.store?.name ?? 'VJoyKart Store'}',
                        style: const TextStyle(fontWeight: FontWeight.w800)),
                    if ((order.store?.address ?? '').isNotEmpty)
                      Text(order.store!.address!,
                          style: const TextStyle(color: Colors.black54)),
                  ])),
            ]),
            const SizedBox(height: 12),
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Icon(Icons.location_on_outlined, color: AppTheme.green),
              const SizedBox(width: 9),
              Expanded(
                  child: Text(order.address,
                      style: const TextStyle(fontWeight: FontWeight.w600)))
            ]),
          ])));

  Widget _info(String label, String value) => Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(children: [
        Text('$label: ', style: const TextStyle(color: Colors.black54)),
        Expanded(
            child: Text(value,
                style: const TextStyle(fontWeight: FontWeight.w700)))
      ]));

  Widget _money(String label, double value, {bool bold = false}) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(children: [
        Text(label, style: TextStyle(fontWeight: bold ? FontWeight.w900 : null)),
        const Spacer(),
        Text('₹${value.round()}',
            style: TextStyle(fontWeight: bold ? FontWeight.w900 : null))
      ]));

  Future<void> _call(String phone) async {
    final uri = Uri(scheme: 'tel', path: phone.replaceAll(' ', ''));
    if (!await launchUrl(uri) && mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Call $phone')));
    }
  }

  /// Opens the fixed VJoyKart Store (never the customer's address).
  Future<void> _openStoreInMaps() async {
    final store = order.store;
    final url = store != null && store.mapsUrl.isNotEmpty
        ? store.mapsUrl
        : store != null
            ? 'https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}'
            : _storeMapsFallback;
    await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  }

  Future<void> _openDeliveryInMaps() async {
    final query = order.deliveryLatitude != null &&
            order.deliveryLongitude != null
        ? '${order.deliveryLatitude},${order.deliveryLongitude}'
        : Uri.encodeComponent(
            order.address.isEmpty ? 'Hyderabad' : order.address);
    await launchUrl(
        Uri.parse('https://www.google.com/maps/search/?api=1&query=$query'),
        mode: LaunchMode.externalApplication);
  }
}

class _MapPainter extends CustomPainter {
  final bool active;
  const _MapPainter({required this.active});
  @override
  void paint(Canvas canvas, Size size) {
    final grid = Paint()
      ..color = Colors.white.withOpacity(.65)
      ..strokeWidth = 1;
    for (double x = 0; x < size.width; x += 46) {
      canvas.drawLine(Offset(x, 0), Offset(x + 40, size.height), grid);
    }
    for (double y = 0; y < size.height; y += 42) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y + 8), grid);
    }
    final road = Paint()
      ..color = const Color(0xffaebdad)
      ..strokeWidth = 18
      ..strokeCap = StrokeCap.round;
    final route = Paint()
      ..color = AppTheme.green
      ..strokeWidth = 7
      ..strokeCap = StrokeCap.round;
    final points = [
      Offset(45, size.height - 35),
      Offset(size.width * .34, size.height * .72),
      Offset(size.width * .55, size.height * .48),
      Offset(size.width * .72, size.height * .60),
      Offset(size.width - 55, 45)
    ];
    for (var i = 0; i < points.length - 1; i++) {
      canvas.drawLine(points[i], points[i + 1], road);
    }
    if (active) {
      canvas.drawPath(
          Path()
            ..moveTo(points[0].dx, points[0].dy)
            ..lineTo(points[1].dx, points[1].dy)
            ..lineTo(points[2].dx, points[2].dy)
            ..lineTo(points[3].dx, points[3].dy),
          route);
    }
    // Pickup marker: VJoyKart Store (start of route).
    canvas.drawCircle(points.first, 10, Paint()..color = Colors.deepOrange);
    canvas.drawCircle(points.first, 4, Paint()..color = Colors.white);
    // Drop marker: customer's delivery address (end of route).
    canvas.drawCircle(points.last, 9, Paint()..color = AppTheme.green);
    canvas.drawCircle(points.last, 4, Paint()..color = Colors.white);
  }

  @override
  bool shouldRepaint(covariant _MapPainter oldDelegate) =>
      oldDelegate.active != active;
}
