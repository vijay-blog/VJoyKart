import 'package:shared_preferences/shared_preferences.dart';
import '../core/api_client.dart';
import '../models/address.dart';
import '../models/cart_item.dart';
import '../models/order.dart';
import '../models/product.dart';
import 'repositories.dart';

class RestProductRepository implements ProductRepository {
  final ApiClient apiClient;

  RestProductRepository({required this.apiClient});

  @override
  Future<List<CategoryItem>> getCategories() async {
    final data = await apiClient.request(HttpMethod.get, '/catalog/categories');
    final content = data is Map ? data['content'] : data;
    if (content is List) {
      return content
          .map((e) => CategoryItem(
                id: (e as Map<String, dynamic>)['categoryId']?.toString() ?? '',
                name: e['name']?.toString() ?? '',
                imageUrl: e['imageUrl']?.toString() ?? '',
              ))
          .toList();
    }
    return const [];
  }

  @override
  Future<List<Product>> getProducts({
    int page = 1,
    int pageSize = 20,
    String? search,
    String? categoryId,
    String? sort,
  }) async {
    final params = <String, dynamic>{
      'page': page,
      'pageSize': pageSize,
      if (search != null && search.isNotEmpty) 'search': search,
      if (categoryId != null && categoryId.isNotEmpty) 'categoryId': categoryId,
      if (sort != null && sort.isNotEmpty) 'sort': sort,
    };

    final data = await apiClient.request(
      HttpMethod.get,
      '/catalog/products',
      queryParameters: params,
    );

    List<dynamic> list = [];
    if (data is List) {
      list = data;
    } else if (data is Map && data.containsKey('content')) {
      list = data['content'] as List<dynamic>;
    }

    return list
        .map((e) => Product.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<Product?> getProductById(String id) async {
    final data =
        await apiClient.request(HttpMethod.get, '/catalog/products/$id');
    if (data is Map<String, dynamic>) {
      return Product.fromJson(data);
    }
    return null;
  }

  @override
  Future<List<Product>> getProductsByCategory(String categoryId) async {
    return getProducts(categoryId: categoryId);
  }

  @override
  Future<List<Product>> searchProducts(String query) async {
    final data = await apiClient.request(
      HttpMethod.get,
      '/catalog/products',
      queryParameters: {'query': query},
    );
    if (data is List) {
      return data
          .map((e) => Product.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return const [];
  }
}

class RestOrderRepository implements OrderRepository {
  final ApiClient apiClient;
  Future<int> _customerId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt('nm.customerId') ?? 0;
  }

  RestOrderRepository({required this.apiClient});

  @override
  Future<CustomerOrder> createOrder({
    required List<CartItem> items,
    required Address address,
    double? subtotal,
    double? deliveryFee,
    double? discount,
    required double total,
  }) async {
    final body = {
      if (address.id != null && int.tryParse(address.id!) != null)
        'addressId': int.parse(address.id!),
      'address': address.toJson(),
      'paymentMethod': 'COD',
      'items': items
          .map((i) => {
                'productId': i.product.id,
                'quantity': i.quantity,
              })
          .toList(),
      'idempotencyKey': 'idemp_${DateTime.now().millisecondsSinceEpoch}',
    };

    final data =
        await apiClient.request(HttpMethod.post, '/orders', body: body);
    if (data is Map<String, dynamic>) {
      return _parseOrder(data, items, address.fullAddress);
    }
    throw ApiException('Failed to create order from server response.', 500);
  }

  @override
  Future<List<CustomerOrder>> getOrders() async {
    final customerId = await _customerId();
    final data = await apiClient.request(HttpMethod.get, '/orders',
        queryParameters: {'customerId': customerId});
    if (data is List) {
      return data
          .map((e) => _parseOrder(e as Map<String, dynamic>, const [], ''))
          .toList();
    }
    return const [];
  }

  @override
  Future<CustomerOrder?> getOrderById(String orderId) async {
    final data = await apiClient.request(HttpMethod.get, '/orders/$orderId');
    if (data is Map<String, dynamic>) {
      return _parseOrder(data, const [], '');
    }
    return null;
  }

  CustomerOrder _parseOrder(
    Map<String, dynamic> json,
    List<CartItem> fallbackItems,
    String fallbackAddress,
  ) {
    if ((json['items'] as List? ?? const []).isEmpty &&
        fallbackItems.isNotEmpty) {
      return CustomerOrder.fromJson({
        ...json,
        'items': fallbackItems.map((i) => i.toJson()).toList(),
        'address': fallbackAddress,
      });
    }
    return CustomerOrder.fromJson(json);
  }
}

class RestAddressRepository implements AddressRepository {
  final ApiClient apiClient;

  RestAddressRepository({required this.apiClient});

  @override
  Future<List<Address>> getAddresses() async {
    final prefs = await SharedPreferences.getInstance();
    final customerId = prefs.getInt('nm.customerId') ?? 0;
    final data = await apiClient.request(
        HttpMethod.get, '/customers/$customerId/addresses');
    if (data is List) {
      return data
          .map((e) => Address.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    return const [];
  }

  @override
  Future<Address> saveAddress(Address address) async {
    late dynamic data;
    if (address.id == null ||
        address.id!.isEmpty ||
        address.id!.startsWith('addr_new')) {
      final prefs = await SharedPreferences.getInstance();
      final customerId = prefs.getInt('nm.customerId') ?? 0;
      data = await apiClient.request(
        HttpMethod.post,
        '/customers/$customerId/addresses',
        body: address.toJson(),
      );
    } else {
      data = await apiClient.request(
        HttpMethod.put,
        '/addresses/${address.id}',
        body: address.toJson(),
      );
    }
    if (data is Map<String, dynamic>) {
      return Address.fromJson(data);
    }
    return address;
  }

  @override
  Future<void> deleteAddress(String id) async {
    await apiClient.request(HttpMethod.delete, '/addresses/$id');
  }
}
