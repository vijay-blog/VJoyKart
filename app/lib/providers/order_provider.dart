import 'package:flutter/foundation.dart';
import 'dart:convert';
import '../core/api_client.dart';
import '../models/order.dart';
import '../models/cart_item.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/address.dart';
import '../models/payment.dart';
import '../services/api_service.dart';
import '../services/customer_session.dart';

class OrderProvider extends ChangeNotifier {
  final List<CustomerOrder> orders = [];
  bool initialized = false;
  final ApiService _api = ApiService();
  final CustomerSession _session = CustomerSession();

  OrderProvider() {
    _load();
  }

  Future<void> _load() async {
    final authenticated = await _session.isAuthenticated();
    if (!authenticated) {
      initialized = true;
      notifyListeners();
      return;
    }
    await refresh();
  }

  Future<void> refresh() async {
    final authenticated = await _session.isAuthenticated();
    if (!authenticated) {
      orders.clear();
      initialized = true;
      notifyListeners();
      return;
    }
    try {
      final data = await _api.get('/customer/orders');
      final content = data is Map<String, dynamic> ? data['content'] : data;
      if (content is List) {
        orders
          ..clear()
          ..addAll(content
              .whereType<Map<String, dynamic>>()
              .map(CustomerOrder.fromJson));
        await _persist();
      }
    } on ApiException {
      // Keep persisted orders while the backend is temporarily unavailable.
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getStringList('zp.orders') ?? const [];
      if (orders.isEmpty) {
        orders.addAll(raw.map((x) =>
            CustomerOrder.fromJson(jsonDecode(x) as Map<String, dynamic>)));
      }
    }
    initialized = true;
    notifyListeners();
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      'zp.orders',
      orders.map((x) => jsonEncode(x.toJson())).toList(),
    );
  }

  Future<CustomerOrder> create(
    List<CartItem> items,
    Address address, {
    String paymentMethod = 'COD',
  }) async {
    await _session.ensureCustomerId();
    final response = await _api.post('/customer/orders', {
      'address': address.toJson(),
      'paymentMethod': paymentMethod,
      'items': items
          .map((x) => {'productId': x.product.id, 'quantity': x.quantity})
          .toList(),
    });
    final order = CustomerOrder.fromJson(response);
    orders.removeWhere((o) => o.id == order.id);
    orders.insert(0, order);
    await _persist();
    notifyListeners();
    return order;
  }

  Future<PaymentOrder> createPaymentOrder(CustomerOrder order) async {
    final response = await _api.post('/payments/create-order', {
      'orderId': int.parse(order.id),
    });
    return PaymentOrder.fromJson(response);
  }

  Future<CustomerOrder> verifyPayment({
    required int orderId,
    required String gatewayOrderId,
    required String gatewayPaymentId,
    required String gatewaySignature,
  }) async {
    final response = await _api.post('/payments/verify', {
      'orderId': orderId,
      'gatewayOrderId': gatewayOrderId,
      'gatewayPaymentId': gatewayPaymentId,
      'gatewaySignature': gatewaySignature,
    });
    final order = CustomerOrder.fromJson(response);
    orders.removeWhere((o) => o.id == order.id);
    orders.insert(0, order);
    await _persist();
    notifyListeners();
    return order;
  }
}
