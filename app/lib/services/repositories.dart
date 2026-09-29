import '../models/address.dart';
import '../models/cart_item.dart';
import '../models/order.dart';
import '../models/product.dart';

abstract class ProductRepository {
  Future<List<CategoryItem>> getCategories();
  Future<List<Product>> getProducts({
    int page = 1,
    int pageSize = 20,
    String? search,
    String? categoryId,
    String? sort,
  });
  Future<Product?> getProductById(String id);
  Future<List<Product>> getProductsByCategory(String categoryId);
  Future<List<Product>> searchProducts(String query);
}

abstract class OrderRepository {
  Future<CustomerOrder> createOrder({
    required List<CartItem> items,
    required Address address,
    double? subtotal,
    double? deliveryFee,
    double? discount,
    required double total,
  });
  Future<List<CustomerOrder>> getOrders();
  Future<CustomerOrder?> getOrderById(String orderId);
}

abstract class AddressRepository {
  Future<List<Address>> getAddresses();
  Future<Address> saveAddress(Address address);
  Future<void> deleteAddress(String id);
}
