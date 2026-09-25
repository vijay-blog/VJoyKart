import 'package:flutter_test/flutter_test.dart';
import 'package:nexamart_customer/models/cart_item.dart';
import 'package:nexamart_customer/models/order.dart';
import 'package:nexamart_customer/models/payment.dart';
import 'package:nexamart_customer/models/product.dart';

void main() {
  test('Product parses catalog API fields and preserves server image URL', () {
    final product = Product.fromJson({
      'productId': '42',
      'sku': 'ZP-MOB-001',
      'name': 'Mobile Phone',
      'brand': 'SmartTech',
      'categoryId': 15,
      'categoryName': 'Mobile Phones',
      'imageUrl': 'https://cdn.example.com/mobile.png',
      'price': '16999',
      'discountedPrice': '14999',
      'discountPercent': '11.76',
      'unit': '1 unit',
      'availability': 'IN_STOCK',
      'stock': 8,
      'deliveryType': 'MEDIUM',
    });

    expect(product.id, 42);
    expect(product.categoryId, '15');
    expect(product.imageAsset, 'https://cdn.example.com/mobile.png');
    expect(product.available, isTrue);
  });

  test('Product with zero backend stock is unavailable', () {
    final product = Product.fromJson({
      'productId': '43',
      'name': 'Sold out product',
      'status': 'ACTIVE',
      'availability': 'AVAILABLE',
      'stock': 0,
      'price': 100,
    });

    expect(product.available, isFalse);
  });

  test('Cart item total uses selling price and quantity', () {
    final item = CartItem(
      product: Product.fromJson({
        'id': 1,
        'name': 'Rice',
        'sellingPrice': 250,
        'mrp': 300,
        'unit': '5 kg',
      }),
      quantity: 2,
    );

    expect(item.total, 500);
  });

  test('Order response maps backend totals, status, payment and items', () {
    final order = CustomerOrder.fromJson({
      'id': 10,
      'orderNumber': 'ZP-123',
      'createdAt': '2026-08-26T18:40:00',
      'status': 'PAYMENT_PENDING',
      'paymentMethod': 'ONLINE',
      'paymentStatus': 'CREATED',
      'subtotal': 499,
      'deliveryFee': 0,
      'discountAmount': 20,
      'totalAmount': 479,
      'addressSnapshot': 'Madhapur, Hyderabad',
      'items': [
        {
          'productId': 1,
          'productName': 'Rice',
          'productBrand': 'India Gate',
          'productImageUrl': 'assets/images/products/rice.png',
          'productUnit': '5 kg',
          'quantity': 1,
          'unitPrice': 499,
          'lineTotal': 499,
        },
      ],
    });

    expect(order.orderNumber, 'ZP-123');
    expect(order.status, OrderStatus.paymentPending);
    expect(order.paymentMethod, 'ONLINE');
    expect(order.total, 479);
    expect(order.items.single.product.name, 'Rice');
  });

  test('Order response maps the active customer API contract', () {
    final order = CustomerOrder.fromJson({
      'orderId': '25',
      'createdAt': '2026-09-17T07:30:00Z',
      'status': 'PENDING',
      'address': '12 Main Road, Hyderabad, Telangana, 500001',
      'payment': {'method': 'COD', 'status': 'PENDING'},
      'totals': {
        'subtotal': '120.00',
        'deliveryFee': '30.00',
        'discount': '0.00',
        'grandTotal': '150.00',
      },
      'items': [
        {
          'productId': 8,
          'productName': 'Backend Product',
          'quantity': 2,
          'unitPrice': '60.00',
          'lineTotal': '120.00',
        },
      ],
    });

    expect(order.id, '25');
    expect(order.orderNumber, '25');
    expect(order.paymentMethod, 'COD');
    expect(order.total, 150);
    expect(order.items.single.product.id, 8);
  });

  test('Payment order maps Razorpay create-order response', () {
    final payment = PaymentOrder.fromJson({
      'paymentId': 7,
      'orderId': 10,
      'keyId': 'rzp_test_key',
      'gatewayOrderId': 'order_abc',
      'amount': 479,
      'currency': 'INR',
    });

    expect(payment.gatewayOrderId, 'order_abc');
    expect(payment.orderId, 10);
    expect(payment.currency, 'INR');
  });
}
