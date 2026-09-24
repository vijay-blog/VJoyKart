import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../models/product.dart';
import '../core/app_config.dart';
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

      // The customer API and Partner catalog can be deployed independently.
      // When the customer API returns only the legacy primary image, enrich
      // the same products from the public Partner catalog in one paged call.
      // This is intentionally best-effort: the customer catalog still works
      // if the image service is temporarily unavailable.
      if (products.any((product) => product.images.length < 2)) {
        products = await _hydratePartnerImages(products);
      }

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


  Future<List<Product>> _hydratePartnerImages(List<Product> current) async {
    try {
      final byId = <int, List<String>>{};
      var page = 0;
      var hasNext = true;
      while (hasNext) {
        final uri = Uri.parse(
          '${AppConfig.catalogImageBaseUrl}/api/v1/catalog/products',
        ).replace(queryParameters: {
          'page': '$page',
          'pageSize': '100',
        });
        final response = await http.get(uri).timeout(const Duration(seconds: 8));
        if (response.statusCode < 200 || response.statusCode >= 300) break;
        final decoded = jsonDecode(response.body);
        if (decoded is! Map || decoded['content'] is! List) break;

        for (final raw in decoded['content'] as List) {
          if (raw is! Map) continue;
          final id = int.tryParse(
            '${raw['productId'] ?? raw['id'] ?? ''}',
          );
          if (id == null || id <= 0) continue;
          final images = <String>[];
          final rawImages = raw['images'];
          if (rawImages is List) {
            for (final image in rawImages) {
              final value = image is Map
                  ? (image['url'] ?? image['imageUrl'] ?? image['path'])
                  : image;
              if (value != null && value.toString().trim().isNotEmpty) {
                final url = value.toString().trim();
                if (!images.contains(url)) images.add(url);
              }
            }
          }
          final primary = raw['imageUrl']?.toString().trim() ?? '';
          if (images.isEmpty && primary.isNotEmpty) images.add(primary);
          if (images.isNotEmpty) byId[id] = images.take(3).toList();
        }

        hasNext = decoded['hasNextPage'] == true;
        page++;
      }

      if (byId.isEmpty) return current;
      return current.map((product) {
        final partnerImages = byId[product.id];
        if (partnerImages == null || partnerImages.isEmpty) return product;
        final merged = <String>[...partnerImages, ...product.images];
        final unique = <String>[];
        for (final image in merged) {
          if (image.trim().isNotEmpty && !unique.contains(image.trim())) {
            unique.add(image.trim());
          }
          if (unique.length == 3) break;
        }
        return product.copyWith(images: unique);
      }).toList();
    } catch (_) {
      return current;
    }
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
