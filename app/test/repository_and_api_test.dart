import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:nexamart_customer/core/api_client.dart';
import 'package:nexamart_customer/services/rest_repositories.dart';

void main() {
  group('ApiClient', () {
    test('returns decoded body for success', () async {
      final mockClient = MockClient((_) async {
        return http.Response(jsonEncode({'ok': true}), 200);
      });
      final api = ApiClient(client: mockClient);
      final result = await api.request(HttpMethod.get, '/products');
      expect(result['ok'], true);
    });

    test('throws ApiException for non-2xx', () async {
      final mockClient = MockClient((_) async {
        return http.Response(jsonEncode({'message': 'Invalid'}), 400);
      });
      final api = ApiClient(client: mockClient);
      expect(
        () => api.request(HttpMethod.get, '/products'),
        throwsA(isA<ApiException>()),
      );
    });
  });

  group('Repositories', () {
    test('loads categories from REST response', () async {
      final mockClient = MockClient((request) async {
        if (request.url.path.endsWith('/catalog/categories')) {
          return http.Response(
              jsonEncode({
                'content': [
                  {
                    'categoryId': '1',
                    'name': 'Grocery',
                    'imageUrl': 'https://cdn.example.com/grocery.png'
                  }
                ]
              }),
              200);
        }
        return http.Response('[]', 200);
      });
      final repo =
          RestProductRepository(apiClient: ApiClient(client: mockClient));
      final categories = await repo.getCategories();
      expect(categories.first.name, 'Grocery');
      expect(categories.first.id, '1');
    });
  });
}
