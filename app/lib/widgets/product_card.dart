import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/product.dart';
import '../providers/cart_provider.dart';
import '../screens/product_detail_screen.dart';
import 'catalog_image.dart';

class ProductCard extends StatelessWidget {
  final Product product;
  const ProductCard({super.key, required this.product});
  @override
  Widget build(BuildContext c) {
    final cart = c.watch<CartProvider>();
    final item =
        cart.items.where((x) => x.product.id == product.id).firstOrNull;
    return InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => Navigator.push(
            c,
            MaterialPageRoute(
                builder: (_) => ProductDetailScreen(product: product))),
        child: Card(
            child: Padding(
                padding: const EdgeInsets.all(10),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                          child: Container(
                              width: double.infinity,
                              decoration: BoxDecoration(
                                  color: const Color(0xfff3f5ff),
                                  borderRadius: BorderRadius.circular(14)),
                              clipBehavior: Clip.antiAlias,
                              child: CatalogImage(
                                  source: product.imageAsset,
                                  fit: BoxFit.contain,
                                  iconSize: 48))),
                      const SizedBox(height: 8),
                      if (product.discount >= 1)
                        Align(
                            alignment: Alignment.centerLeft,
                            child: Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 7, vertical: 3),
                                decoration: BoxDecoration(
                                    color: const Color(0xffe9edff),
                                    borderRadius: BorderRadius.circular(6)),
                                child: Text('${product.discount.round()}% OFF',
                                    style: const TextStyle(
                                        fontSize: 10,
                                        fontWeight: FontWeight.w800,
                                        color: Color(0xff3454d1))))),
                      const SizedBox(height: 4),
                      Text(product.name,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                      Text(product.brand,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              fontSize: 11, color: Colors.grey.shade700)),
                      Text(product.unit,
                          style: TextStyle(
                              fontSize: 11, color: Colors.grey.shade600)),
                      if (!product.available)
                        const Text('Out of stock',
                            style: TextStyle(color: Colors.red, fontSize: 11)),
                      const SizedBox(height: 5),
                      Row(children: [
                        Text('₹${product.sellingPrice.round()}',
                            style: const TextStyle(
                                fontWeight: FontWeight.w900, fontSize: 16)),
                        const SizedBox(width: 6),
                        Text('₹${product.mrp.round()}',
                            style: TextStyle(
                                fontSize: 11,
                                color: Colors.grey.shade500,
                                decoration: TextDecoration.lineThrough)),
                        const Spacer(),
                        item == null
                            ? SizedBox(
                                height: 34,
                                child: OutlinedButton(
                                    onPressed: product.available
                                        ? () => cart.add(product)
                                        : null,
                                    child: const Text('ADD')))
                            : Container(
                                height: 34,
                                decoration: BoxDecoration(
                                    border: Border.all(
                                        color: Theme.of(c).colorScheme.primary),
                                    borderRadius: BorderRadius.circular(10)),
                                child: Row(children: [
                                  IconButton(
                                      padding: EdgeInsets.zero,
                                      onPressed: () => cart.remove(product),
                                      icon: const Icon(Icons.remove, size: 16)),
                                  Text('${item.quantity}',
                                      style: const TextStyle(
                                          fontWeight: FontWeight.bold)),
                                  IconButton(
                                      padding: EdgeInsets.zero,
                                      onPressed: () => cart.add(product),
                                      icon: const Icon(Icons.add, size: 16))
                                ]))
                      ])
                    ]))));
  }
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
