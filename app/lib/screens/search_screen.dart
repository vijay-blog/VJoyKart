import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/product.dart';
import '../providers/catalog_provider.dart';
import '../widgets/product_card.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});
  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final controller = TextEditingController();
  Timer? debounce;
  bool searching = false;
  List<Product>? remoteProducts;
  String? searchError;
  int searchGeneration = 0;

  @override
  void dispose() {
    debounce?.cancel();
    controller.dispose();
    super.dispose();
  }

  void onSearchChanged() {
    debounce?.cancel();
    final query = controller.text.trim();
    final generation = ++searchGeneration;
    if (query.isEmpty) {
      setState(() {
        searching = false;
        remoteProducts = null;
        searchError = null;
      });
      return;
    }
    setState(() {
      searching = true;
      searchError = null;
    });
    debounce = Timer(const Duration(milliseconds: 300), () async {
      try {
        final result = await context.read<CatalogProvider>().searchRemote(
          query,
        );
        if (!mounted || generation != searchGeneration) return;
        setState(() {
          remoteProducts = result;
          searching = false;
        });
      } catch (_) {
        if (!mounted || generation != searchGeneration) return;
        setState(() {
          remoteProducts = const [];
          searchError = 'Unable to search products';
          searching = false;
        });
      }
    });
  }

  @override
  Widget build(BuildContext c) {
    final catalog = c.watch<CatalogProvider>();
    final products = controller.text.trim().isEmpty
        ? catalog.products
        : (remoteProducts ?? const <Product>[]);
    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: controller,
          autofocus: true,
          onChanged: (_) => onSearchChanged(),
          decoration: InputDecoration(
            hintText: 'Search products...',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: controller.text.isEmpty
                ? null
                : IconButton(
                    onPressed: () {
                      controller.clear();
                      setState(() {});
                    },
                    icon: const Icon(Icons.close),
                  ),
            border: InputBorder.none,
          ),
        ),
      ),
      body: searchError != null
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(searchError!),
                  const SizedBox(height: 8),
                  FilledButton(
                    onPressed: onSearchChanged,
                    child: const Text('Retry'),
                  ),
                ],
              ),
            )
          : catalog.error != null && controller.text.trim().isEmpty
          ? const Center(child: Text('Unable to load products'))
          : searching
          ? const Center(child: CircularProgressIndicator())
          : products.isEmpty
          ? const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.search_off, size: 56, color: Colors.grey),
                  SizedBox(height: 10),
                  Text('No products found'),
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
