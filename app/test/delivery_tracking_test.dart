import 'package:flutter_test/flutter_test.dart';
import 'package:nexamart_customer/models/order.dart';

Map<String, dynamic> _tracking(String status, {bool partner = true}) => {
      'orderId': '18',
      'orderStatus': 'ASSIGNED',
      'paymentMethod': 'COD',
      'paymentStatus': 'PENDING',
      'totalAmount': '210.00',
      'deliveryStatus': status,
      'assignmentStatus': partner ? 'ASSIGNED' : 'AWAITING_ASSIGNMENT',
      'assignedAt': partner ? '2026-01-01T10:00:00Z' : null,
      'deliveryPartner':
          partner ? {'id': '7', 'name': 'Ravi Kumar', 'phone': '9876543210'} : null,
      'store': {
        'name': 'VJoyKart Store',
        'latitude': 17.3899091,
        'longitude': 78.383089,
        'mapsUrl': 'https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9',
      },
      'deliveryLocation': {'latitude': 17.40, 'longitude': 78.40},
    };

CustomerOrder _order() => CustomerOrder(
    id: '18',
    createdAt: DateTime(2026),
    items: const [],
    subtotal: 180,
    deliveryFee: 30,
    discount: 0,
    total: 210,
    address: 'Customer street',
    paymentMethod: 'COD');

void main() {
  test('tracking shows delivery boy name, phone and the fixed store', () {
    final order = _order()..applyTracking(_tracking('DELIVERY_ASSIGNED'));
    expect(order.hasDeliveryPartner, isTrue);
    expect(order.deliveryPartner!.name, 'Ravi Kumar');
    expect(order.deliveryPartner!.phone, '9876543210');
    expect(order.store!.name, 'VJoyKart Store');
    expect(order.store!.mapsUrl, 'https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9');
    expect(order.store!.latitude, isNot(order.deliveryLatitude));
    expect(order.statusLabel, 'Delivery boy assigned');
  });

  test('progress is exactly the five delivery steps', () {
    final order = _order()..applyTracking(_tracking('ON_THE_WAY'));
    expect(order.progress.map((s) => s.label).toList(), [
      'Delivery boy assigned',
      'Packing',
      'On the way',
      'Arrived',
      'Delivered',
    ]);
    expect(order.progress.map((s) => s.state).toList(),
        ['COMPLETED', 'COMPLETED', 'CURRENT', 'PENDING', 'PENDING']);
  });

  test('no delivery boy is shown while awaiting assignment', () {
    final order = _order()
      ..applyTracking(_tracking('ORDER_PLACED', partner: false));
    expect(order.hasDeliveryPartner, isFalse);
    expect(order.statusLabel, 'Finding a delivery partner');
    expect(order.progress.every((s) => s.state == 'PENDING'), isTrue);
  });

  test('delivered orders are finished and survive the local cache', () {
    final order = _order()..applyTracking(_tracking('DELIVERED'));
    expect(order.isFinished, isTrue);
    final cached = CustomerOrder.fromJson(order.toJson());
    expect(cached.deliveryStatus, 'DELIVERED');
    expect(cached.deliveryPartner!.phone, '9876543210');
    expect(cached.progress.every((s) => s.completed), isTrue);
  });
}
