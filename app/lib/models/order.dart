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
  delivered,
  cancelled,
  outOfStock,
  deliveryFailed,
  returnRequested,
  returned
}

extension OrderStatusX on OrderStatus {
  String get label => switch (this) {
        OrderStatus.created => 'Order placed',
        OrderStatus.paymentPending => 'Awaiting payment',
        OrderStatus.partnerSearching => 'Finding nearby store',
        OrderStatus.partnerAssigned => 'Store assigned',
        OrderStatus.partnerAccepted => 'Store accepted',
        OrderStatus.picking => 'Packing your order',
        OrderStatus.packed => 'Packed',
        OrderStatus.deliverySearching => 'Finding delivery partner',
        OrderStatus.deliveryAssigned => 'Delivery partner assigned',
        OrderStatus.pickedUp => 'Picked up',
        OrderStatus.outForDelivery => 'Out for delivery',
        OrderStatus.delivered => 'Delivered',
        OrderStatus.cancelled => 'Cancelled',
        OrderStatus.outOfStock => 'Out of stock',
        OrderStatus.deliveryFailed => 'Delivery failed',
        OrderStatus.returnRequested => 'Return requested',
        OrderStatus.returned => 'Returned'
      };
}

class CustomerOrder {
  final String id;
  final String orderNumber;
  final DateTime createdAt;
  final List<CartItem> items;
  final double subtotal, deliveryFee, discount, total;
  final String address, paymentMethod, paymentStatus;
  OrderStatus status;
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
      this.status = OrderStatus.created})
      : orderNumber = orderNumber ?? id;

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
    return CustomerOrder(
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
