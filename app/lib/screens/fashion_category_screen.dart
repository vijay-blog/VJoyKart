import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/product.dart';
import '../providers/catalog_provider.dart';
import '../widgets/product_card.dart';

class FashionCategoryScreen extends StatelessWidget {
  final String title;
  final List<String> keywords;

  const FashionCategoryScreen({
    super.key,
    required this.title,
    required this.keywords,
  });

  bool _matches(Product product) {
    final raw = [
      product.name,
      product.categoryName,
      product.description,
      product.brand,
      product.attributes.values.join(' '),
    ].join(' ');
    final normalized = raw.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), ' ').trim();
    final padded = ' $normalized ';
    return keywords.any((keyword) {
      final value = keyword.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), ' ').trim();
      return value.isNotEmpty && padded.contains(' $value ');
    });
  }

  @override
  Widget build(BuildContext context) {
    final products = context
        .watch<CatalogProvider>()
        .products
        .where(_matches)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
      ),
      body: products.isEmpty
          ? const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.checkroom_outlined, size: 58, color: Colors.grey),
                  SizedBox(height: 10),
                  Text('More styles coming soon'),
                ],
              ),
            )
          : GridView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: products.length,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: .63,
              ),
              itemBuilder: (_, i) => ProductCard(product: products[i]),
            ),
    );
  }
}
