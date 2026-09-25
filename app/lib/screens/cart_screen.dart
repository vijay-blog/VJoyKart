import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../providers/cart_provider.dart';
import 'checkout_screen.dart';
import '../widgets/catalog_image.dart';

class CartScreen extends StatelessWidget {
  const CartScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final cart = context.watch<CartProvider>();

    if (cart.items.isEmpty) {
      return Scaffold(
        appBar: AppBar(
          title: const Text(
            'Cart',
            style: TextStyle(fontWeight: FontWeight.w900),
          ),
        ),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 90,
                height: 90,
                decoration: BoxDecoration(
                  color: const Color(0xffe9edff),
                  borderRadius: BorderRadius.circular(30),
                ),
                child: const Icon(
                  Icons.shopping_bag_outlined,
                  size: 44,
                  color: Color(0xff3454d1),
                ),
              ),
              const SizedBox(height: 15),
              const Text(
                'Your cart is empty',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 6),
              const Text('Add products you love to continue.'),
              const SizedBox(height: 14),
              FilledButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Continue Shopping'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Cart',
          style: TextStyle(fontWeight: FontWeight.w900),
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: cart.items.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) {
                final item = cart.items[i];
                return Card(
                  child: Padding(
                    padding: const EdgeInsets.all(10),
                    child: Row(
                      children: [
                        Container(
                          width: 76,
                          height: 76,
                          decoration: BoxDecoration(
                            color: const Color(0xfff3f5ff),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: CatalogImage(
                            source: item.product.imageAsset,
                            fit: BoxFit.contain,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                item.product.name,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w800),
                              ),
                              Text(
                                item.product.unit,
                                style: TextStyle(
                                  color: Colors.grey.shade600,
                                  fontSize: 12,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                '₹${item.product.sellingPrice.round()}',
                                style: const TextStyle(
                                    fontWeight: FontWeight.w900),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          onPressed: () => cart.remove(item.product),
                          icon: const Icon(Icons.remove_circle_outline),
                        ),
                        Text(
                          '${item.quantity}',
                          style: const TextStyle(fontWeight: FontWeight.w900),
                        ),
                        IconButton(
                          onPressed: () => cart.add(item.product),
                          icon: const Icon(Icons.add_circle_outline),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(18, 15, 18, 18),
            decoration: const BoxDecoration(color: Colors.white),
            child: Column(
              children: [
                const _SummaryRow('Subtotal', true),
                const _SummaryRow('Delivery', false),
                const _SummaryRow('Discount', null),
                const Divider(),
                const _TotalRow(),
                if (cart.subtotal < 499)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      'Add ₹${(499 - cart.subtotal).round()} more for free delivery',
                      style:
                          TextStyle(color: Colors.grey.shade700, fontSize: 12),
                    ),
                  ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  height: 52,
                  child: Builder(
                    builder: (builderContext) => FilledButton(
                      onPressed: () => Navigator.push(
                        builderContext,
                        MaterialPageRoute(
                          builder: (_) => const CheckoutScreen(),
                        ),
                      ),
                      child: Text('Continue • ₹${cart.total.round()}'),
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

class _SummaryRow extends StatelessWidget {
  final String name;
  final bool? isSubtotal;

  const _SummaryRow(this.name, this.isSubtotal);

  @override
  Widget build(BuildContext context) {
    final cart = context.read<CartProvider>();
    final value = isSubtotal == null
        ? cart.items.fold<double>(
            0,
            (sum, item) =>
                sum +
                ((item.product.mrp - item.product.sellingPrice) *
                    item.quantity),
          )
        : (isSubtotal! ? cart.subtotal : cart.delivery);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Text(name),
          const Spacer(),
          Text(isSubtotal == null ? '-₹${value.round()}' : '₹${value.round()}'),
        ],
      ),
    );
  }
}

class _TotalRow extends StatelessWidget {
  const _TotalRow();

  @override
  Widget build(BuildContext context) {
    final cart = context.watch<CartProvider>();

    return Row(
      children: [
        const Text(
          'Total',
          style: TextStyle(fontWeight: FontWeight.w900, fontSize: 17),
        ),
        const Spacer(),
        Text(
          '₹${cart.total.round()}',
          style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 18),
        ),
      ],
    );
  }
}
