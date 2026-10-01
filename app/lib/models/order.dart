import 'cart_item.dart';
import 'product.dart';

enum OrderStatus {
  created,
  paymentPending,
  partnerSearching,
  partnerAssigned,
  partnerAccepted,
  picking,
  packed,
  deliverySearching,
  deliveryAssigned,
  pickedUp,
  outForDelivery,
  arrived,
  delivered,
  cancelled,
  outOfStock,
  deliveryFailed,
  returnRequested,
  returned
}

extension OrderStatusX on OrderStatus {
  String get label => switch (this) {
        OrderStatus.created => 'Finding a delivery partner',
        OrderStatus.paymentPending => 'Awaiting payment',
        OrderStatus.partnerSearching => 'Finding a delivery partner',
        OrderStatus.partnerAssigned => 'Finding a delivery partner',
        OrderStatus.partnerAccepted => 'Finding a delivery partner',
        OrderStatus.picking => 'Packing',
        OrderStatus.packed => 'Packed',
        OrderStatus.deliverySearching => 'Finding delivery partner',
        OrderStatus.deliveryAssigned => 'Delivery boy assigned',
        OrderStatus.pickedUp => 'Picked up',
        OrderStatus.outForDelivery => 'On the way',
        OrderStatus.arrived => 'Arrived',
        OrderStatus.delivered => 'Delivered',
        OrderStatus.cancelled => 'Cancelled',
        OrderStatus.outOfStock => 'Out of stock',
        OrderStatus.deliveryFailed => 'Delivery failed',
        OrderStatus.returnRequested => 'Return requested',
        OrderStatus.returned => 'Returned'
      };
}

/// Customer-facing delivery steps returned by the backend, in order.
const deliveryStepOrder = [
  'DELIVERY_ASSIGNED',
  'PACKING',
  'ON_THE_WAY',
  'ARRIVED',
  'DELIVERED',
];

const deliveryStepLabels = {
  'DELIVERY_ASSIGNED': 'Delivery boy assigned',
  'PACKING': 'Packing',
  'ON_THE_WAY': 'On the way',
  'ARRIVED': 'Arrived',
  'DELIVERED': 'Delivered',
};

double? _toDouble(dynamic v) =>
    v is num ? v.toDouble() : (v == null ? null : double.tryParse('$v'));

Map<String, dynamic>? _map(dynamic v) =>
    v is Map ? Map<String, dynamic>.from(v) : null;

/// Delivery boy contact exposed by the backend (name + phone only).
class DeliveryPartnerInfo {
  final String id, name, phone;
  const DeliveryPartnerInfo(
      {required this.id, required this.name, required this.phone});

  static DeliveryPartnerInfo? fromJson(dynamic raw) {
    final json = _map(raw);
    if (json == null) return null;
    final name = json['name']?.toString() ?? '';
    if (name.isEmpty && json['id'] == null) return null;
    return DeliveryPartnerInfo(
        id: json['id']?.toString() ?? '',
        name: name,
        phone: json['phone']?.toString() ?? '');
  }

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'phone': phone};
}

/// The fixed VJoyKart Store (pickup) location, owned by the backend.
class StoreInfo {
  final String name;
  final double latitude, longitude;
  final String mapsUrl;
  final String? address;
  const StoreInfo(
      {required this.name,
      required this.latitude,
      required this.longitude,
      required this.mapsUrl,
      this.address});

  static StoreInfo? fromJson(dynamic raw) {
    final json = _map(raw);
    final lat = _toDouble(json?['latitude']);
    final lng = _toDouble(json?['longitude']);
    if (json == null || lat == null || lng == null) return null;
    return StoreInfo(
        name: json['name']?.toString() ?? 'VJoyKart Store',
        latitude: lat,
        longitude: lng,
        mapsUrl: json['mapsUrl']?.toString() ?? '',
        address: json['address']?.toString());
  }

  Map<String, dynamic> toJson() => {
        'name': name,
        'latitude': latitude,
        'longitude': longitude,
        'mapsUrl': mapsUrl,
        'address': address,
      };
}

class DeliveryStep {
  final String status, label, state;
  final DateTime? at;
  const DeliveryStep(
      {required this.status, required this.label, required this.state, this.at});

  bool get completed => state == 'COMPLETED';
  bool get current => state == 'CURRENT';

  static DeliveryStep fromJson(Map<String, dynamic> json) => DeliveryStep(
        status: json['status']?.toString() ?? '',
        label: json['label']?.toString() ??
            deliveryStepLabels[json['status']] ??
            '',
        state: json['state']?.toString() ?? 'PENDING',
        at: DateTime.tryParse(json['timestamp']?.toString() ?? '')?.toLocal(),
      );

  Map<String, dynamic> toJson() => {
        'status': status,
        'label': label,
        'state': state,
        'timestamp': at?.toUtc().toIso8601String(),
      };

  /// Builds the five steps locally from a delivery status (used for cached orders).
  static List<DeliveryStep> fromStatus(String deliveryStatus) {
    final current = deliveryStepOrder.indexOf(deliveryStatus);
    return [
      for (var i = 0; i < deliveryStepOrder.length; i++)
        DeliveryStep(
          status: deliveryStepOrder[i],
          label: deliveryStepLabels[deliveryStepOrder[i]]!,
          state: current < 0
              ? 'PENDING'
              : (i < current ||
                      (i == current && deliveryStepOrder[i] == 'DELIVERED'))
                  ? 'COMPLETED'
                  : i == current
                      ? 'CURRENT'
                      : 'PENDING',
        )
    ];
  }
}

/// Maps the backend delivery status to the app's order status enum.
OrderStatus orderStatusFromDelivery(String? deliveryStatus, String? legacy) {
  switch (deliveryStatus) {
    case 'ORDER_PLACED':
      return OrderStatus.created;
    case 'DELIVERY_ASSIGNED':
      return OrderStatus.deliveryAssigned;
    case 'PACKING':
      return OrderStatus.picking;
    case 'ON_THE_WAY':
      return OrderStatus.outForDelivery;
    case 'ARRIVED':
      return OrderStatus.arrived;
    case 'DELIVERED':
      return OrderStatus.delivered;
    case 'CANCELLED':
      return OrderStatus.cancelled;
  }
  return parseOrderStatus(legacy);
}

class CustomerOrder {
  final String id;
  final String orderNumber;
  final DateTime createdAt;
  final List<CartItem> items;
  final double subtotal, deliveryFee, discount, total;
  final String address, paymentMethod;
  String paymentStatus;
  OrderStatus status;
  String deliveryStatus;
  String assignmentStatus;
  DateTime? assignedAt;
  DeliveryPartnerInfo? deliveryPartner;
  StoreInfo? store;
  double? deliveryLatitude, deliveryLongitude;
  List<DeliveryStep> progress;
  CustomerOrder(
      {required this.id,
      String? orderNumber,
      required this.createdAt,
      required this.items,
      required this.subtotal,
      required this.deliveryFee,
      required this.discount,
      required this.total,
      required this.address,
      required this.paymentMethod,
      this.paymentStatus = 'CREATED',
      this.status = OrderStatus.created,
      this.deliveryStatus = 'ORDER_PLACED',
      this.assignmentStatus = 'AWAITING_ASSIGNMENT',
      this.assignedAt,
      this.deliveryPartner,
      this.store,
      this.deliveryLatitude,
      this.deliveryLongitude,
      List<DeliveryStep>? progress})
      : orderNumber = orderNumber ?? id,
        progress = progress ?? DeliveryStep.fromStatus(deliveryStatus);

  bool get isFinished =>
      deliveryStatus == 'DELIVERED' ||
      deliveryStatus == 'CANCELLED' ||
      status == OrderStatus.delivered ||
      status == OrderStatus.cancelled ||
      status == OrderStatus.returned ||
      status == OrderStatus.deliveryFailed;

  bool get hasDeliveryPartner =>
      deliveryPartner != null && assignmentStatus != 'AWAITING_ASSIGNMENT';

  /// 0..1 progress across the five delivery steps.
  double get deliveryFraction {
    final done = progress.where((s) => s.completed).length;
    final current = progress.any((s) => s.current) ? 0.5 : 0.0;
    return ((done + current) / deliveryStepOrder.length)
        .clamp(0.05, 1.0)
        .toDouble();
  }

  String get statusLabel {
    if (deliveryStatus == 'CANCELLED') return 'Cancelled';
    if (deliveryStatus == 'ORDER_PLACED' || !hasDeliveryPartner) {
      return paymentMethod == 'ONLINE' && paymentStatus != 'PAID'
          ? 'Awaiting payment'
          : 'Finding a delivery partner';
    }
    return deliveryStepLabels[deliveryStatus] ?? status.label;
  }

  /// Applies a `/customer/orders/{id}/tracking` (or full order) response.
  void applyTracking(Map<String, dynamic> json) {
    deliveryStatus = json['deliveryStatus']?.toString() ?? deliveryStatus;
    status = orderStatusFromDelivery(deliveryStatus, json['orderStatus']?.toString() ?? json['status']?.toString());
    assignmentStatus = json['assignmentStatus']?.toString() ?? assignmentStatus;
    paymentStatus = json['paymentStatus']?.toString() ?? paymentStatus;
    assignedAt = DateTime.tryParse(json['assignedAt']?.toString() ?? '')?.toLocal() ?? assignedAt;
    deliveryPartner = DeliveryPartnerInfo.fromJson(json['deliveryPartner']);
    store = StoreInfo.fromJson(json['store']) ?? store;
    final loc = _map(json['deliveryLocation']);
    deliveryLatitude = _toDouble(loc?['latitude']) ?? deliveryLatitude;
    deliveryLongitude = _toDouble(loc?['longitude']) ?? deliveryLongitude;
    final steps = json['progress'] ?? json['deliveryProgress'];
    progress = steps is List
        ? steps.whereType<Map>().map((s) => DeliveryStep.fromJson(Map<String, dynamic>.from(s))).toList()
        : DeliveryStep.fromStatus(deliveryStatus);
  }

  factory CustomerOrder.fromJson(Map<String, dynamic> json) {
    final totals = json['totals'] is Map<String, dynamic>
        ? json['totals'] as Map<String, dynamic>
        : const <String, dynamic>{};
    final payment = json['payment'] is Map<String, dynamic>
        ? json['payment'] as Map<String, dynamic>
        : const <String, dynamic>{};
    double amount(dynamic value) =>
        value is num ? value.toDouble() : double.tryParse('$value') ?? 0;
    final itemList = (json['items'] as List? ?? const []).map((raw) {
      final item = raw as Map<String, dynamic>;
      if (item['product'] is Map<String, dynamic>) {
        return CartItem.fromJson(item);
      }
      return CartItem(
        product: Product.fromJson({
          'id': item['productId'],
          'sku': item['productSku'],
          'name': item['productName'],
          'brand': item['productBrand'],
          'imageUrl': item['productImageUrl'],
          'unit': item['productUnit'],
          'sellingPrice': item['unitPrice'],
          'mrp': item['unitPrice'],
          'available': true,
          'stockQuantity': 1,
        }),
        quantity: (item['quantity'] as num? ?? 1).toInt(),
      );
    }).toList();
    final order = CustomerOrder(
      id: json['id']?.toString() ?? json['orderId']?.toString() ?? '',
      orderNumber:
          json['orderNumber']?.toString() ?? json['orderId']?.toString(),
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'].toString())
          : DateTime.now(),
      items: itemList,
      subtotal: amount(json['subtotal'] ?? totals['subtotal']),
      deliveryFee: amount(json['deliveryFee'] ?? totals['deliveryFee']),
      discount: amount(
          json['discountAmount'] ?? json['discount'] ?? totals['discount']),
      total:
          amount(json['totalAmount'] ?? json['total'] ?? totals['grandTotal']),
      address: json['addressSnapshot']?.toString() ??
          json['address']?.toString() ??
          '',
      paymentMethod: json['paymentMethod']?.toString() ??
          payment['method']?.toString() ??
          'COD',
      paymentStatus: json['paymentStatus']?.toString() ??
          payment['status']?.toString() ??
          'CREATED',
      status: parseOrderStatus(json['status']?.toString()),
    );
    if (json['deliveryStatus'] != null) order.applyTracking(json);
    return order;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'orderNumber': orderNumber,
        'createdAt': createdAt.toIso8601String(),
        'items': items.map((i) => i.toJson()).toList(),
        'subtotal': subtotal,
        'deliveryFee': deliveryFee,
        'discount': discount,
        'total': total,
        'address': address,
        'paymentMethod': paymentMethod,
        'paymentStatus': paymentStatus,
        'status': status.name,
        'deliveryStatus': deliveryStatus,
        'assignmentStatus': assignmentStatus,
        'assignedAt': assignedAt?.toUtc().toIso8601String(),
        'deliveryPartner': deliveryPartner?.toJson(),
        'store': store?.toJson(),
        'deliveryLocation': deliveryLatitude == null || deliveryLongitude == null
            ? null
            : {'latitude': deliveryLatitude, 'longitude': deliveryLongitude},
        'progress': progress.map((s) => s.toJson()).toList(),
      };
}

OrderStatus parseOrderStatus(String? value) {
  final normalized = (value ?? '').toLowerCase().replaceAll('_', '');
  for (final status in OrderStatus.values) {
    if (status.name.toLowerCase() == normalized) return status;
  }
  if (normalized == 'placed') return OrderStatus.created;
  if (normalized == 'packing') return OrderStatus.picking;
  return OrderStatus.created;
}
