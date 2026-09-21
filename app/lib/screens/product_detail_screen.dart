import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/product.dart';
import '../providers/cart_provider.dart';
import 'cart_screen.dart';
import 'checkout_screen.dart';
import '../widgets/catalog_image.dart';

class ProductDetailScreen extends StatefulWidget {
  final Product product;

  const ProductDetailScreen({super.key, required this.product});

  @override
  State<ProductDetailScreen> createState() => _ProductDetailScreenState();
}

class _ProductDetailScreenState extends State<ProductDetailScreen> {
  bool _buyNowLoading = false;

  Future<void> _buyNow() async {
    if (_buyNowLoading || !widget.product.available) return;

    setState(() => _buyNowLoading = true);
    try {
      final cart = context.read<CartProvider>();
      final existing = cart.items
          .where((item) => item.product.id == widget.product.id)
          .firstOrNull;
      if (existing == null) {
        cart.add(widget.product);
      }

      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const CheckoutScreen()),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Unable to start checkout right now. Please try again.',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _buyNowLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cart = context.watch<CartProvider>();
    final product = widget.product;
    final item =
        cart.items.where((x) => x.product.id == product.id).firstOrNull;

    return Scaffold(
      appBar: AppBar(
        actions: [
          IconButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const CartScreen()),
            ),
            icon: Badge(
              isLabelVisible: cart.count > 0,
              label: Text('${cart.count}'),
              child: const Icon(Icons.shopping_bag_outlined),
            ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 14),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: product.available ? () => cart.add(product) : null,
                  child: const Text('ADD TO CART'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: FilledButton(
                  onPressed: _buyNowLoading ? null : _buyNow,
                  child: _buyNowLoading
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('BUY NOW'),
                ),
              ),
            ],
          ),
        ),
      ),
      body: ListView(
        children: [
          Container(
            height: 330,
            color: const Color(0xfff2f5ff),
            child:
                CatalogImage(source: product.imageAsset, fit: BoxFit.contain),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (product.discount >= 1)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xffe9edff),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      '${product.discount.round()}% OFF',
                      style: const TextStyle(
                        color: Color(0xff3454d1),
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                Text(
                  product.name,
                  style: const TextStyle(
                      fontSize: 25, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 5),
                Text(
                  product.brand,
                  style: TextStyle(color: Colors.grey.shade600),
                ),
                const SizedBox(height: 6),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Text(
                      '₹${product.sellingPrice.round()}',
                      style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      '₹${product.mrp.round()}',
                      style: TextStyle(
                        color: Colors.grey.shade500,
                        decoration: TextDecoration.lineThrough,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Text(
                  product.unit,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 10),
                Text(
                  'Availability: ${product.available ? 'In Stock' : 'Out of Stock'}',
                  style: TextStyle(
                    color: product.available
                        ? const Color(0xff3454d1)
                        : Colors.red,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                if (product.stock > 0) ...[
                  const SizedBox(height: 10),
                  Text(
                    'Stock: ${product.stock}',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ],
                const SizedBox(height: 18),
                const Text(
                  'About this product',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 8),
                Text(
                  product.description,
                  style: const TextStyle(height: 1.5, color: Colors.black54),
                ),
                const SizedBox(height: 20),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.category_outlined),
                  title: const Text('Category'),
                  subtitle: Text(product.categoryName),
                ),
                if (item != null)
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: const Color(0xffe9edff),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${item.quantity} in your cart',
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        color: Color(0xff3454d1),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
