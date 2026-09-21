import 'package:flutter/foundation.dart';

import '../models/product.dart';
import '../services/api_service.dart';

class CatalogProvider extends ChangeNotifier {
  final ApiService api = ApiService();
  List<Product> products = [];
  List<CategoryItem> categories = [];
  bool loading = false;
  String? error;
  Future<void> load() async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      final results = await Future.wait([
        _fetchAll('/catalog/products'),
        _fetchAll('/catalog/categories'),
      ]);
      products = results[0]
          .map(Product.fromJson)
          .where(
            (product) =>
                product.id > 0 && product.name.isNotEmpty && product.available,
          )
          .toList();
      categories = results[1]
          .map(CategoryItem.fromJson)
          .where(
            (category) => category.id.isNotEmpty && category.name.isNotEmpty,
          )
          .toList();
    } catch (e) {
      products = [];
      categories = [];
      error = e.toString();
    }
    loading = false;
    notifyListeners();
  }

  Future<List<Map<String, dynamic>>> _fetchAll(
    String path, {
    Map<String, String> query = const {},
  }) async {
    final items = <Map<String, dynamic>>[];
    var page = 0;
    var hasNext = true;
    while (hasNext) {
      final data = await api.get(path, {
        ...query,
        'page': '$page',
        'pageSize': '100',
      });
      if (data is! Map) {
        throw const FormatException('Invalid catalog response');
      }
      final content = data['content'];
      if (content is! List) {
        throw const FormatException('Invalid catalog content');
      }
      items.addAll(content.whereType<Map<String, dynamic>>());
      hasNext = data['hasNextPage'] == true;
      page++;
    }
    return items;
  }

  Future<List<Product>> searchRemote(String query) async {
    final normalized = query.trim();
    if (normalized.isEmpty) return products;
    final data = await _fetchAll(
      '/catalog/products',
      query: {'search': normalized},
    );
    return data
        .map(Product.fromJson)
        .where(
          (product) =>
              product.id > 0 && product.name.isNotEmpty && product.available,
        )
        .toList();
  }

  List<Product> search(String query, {String? categoryId}) {
    var result = products;
    if (categoryId != null && categoryId.isNotEmpty) {
      result = result
          .where((product) => product.categoryId == categoryId)
          .toList();
    }
    if (query.trim().isNotEmpty) {
      final normalized = query.trim().toLowerCase();
      result = result
          .where(
            (product) =>
                product.name.toLowerCase().contains(normalized) ||
                product.description.toLowerCase().contains(normalized) ||
                product.category.toLowerCase().contains(normalized),
          )
          .toList();
    }
    return result;
  }
}
