import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/product.dart';
import '../providers/cart_provider.dart';
import '../providers/catalog_provider.dart';
import '../widgets/catalog_image.dart';
import '../widgets/product_card.dart';
import 'cart_screen.dart';
import 'checkout_screen.dart';
import 'search_screen.dart';

class ProductDetailScreen extends StatefulWidget {
  final Product product;

  const ProductDetailScreen({super.key, required this.product});

  @override
  State<ProductDetailScreen> createState() => _ProductDetailScreenState();
}

class _ProductDetailScreenState extends State<ProductDetailScreen> {
  final PageController _imageController = PageController();
  Timer? _imageTimer;
  int _currentImage = 0;
  bool _buyNowLoading = false;
  bool _detailsExpanded = true;

  List<String> get _images {
    final values = widget.product.images
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    if (values.isEmpty) return const [''];
    return values.take(3).toList();
  }

  @override
  void initState() {
    super.initState();
    _startImageAutoSlide();
  }

  void _startImageAutoSlide() {
    _imageTimer?.cancel();
    if (_images.length < 2) return;
    _imageTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (!mounted || !_imageController.hasClients) return;
      final next = (_currentImage + 1) % _images.length;
      _imageController.animateToPage(
        next,
        duration: const Duration(milliseconds: 450),
        curve: Curves.easeOutCubic,
      );
    });
  }

  void _showNextImage() {
    if (_images.length < 2) return;
    final next = (_currentImage + 1) % _images.length;
    _imageController.animateToPage(
      next,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOut,
    );
    _restartImageTimer();
  }

  void _showPreviousImage() {
    if (_images.length < 2) return;
    final previous = (_currentImage - 1 + _images.length) % _images.length;
    _imageController.animateToPage(
      previous,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOut,
    );
    _restartImageTimer();
  }

  void _restartImageTimer() {
    _imageTimer?.cancel();
    _startImageAutoSlide();
  }

  Future<void> _buyNow() async {
    if (_buyNowLoading || !widget.product.available) return;

    setState(() => _buyNowLoading = true);
    try {
      final cart = context.read<CartProvider>();
      final existing = cart.items
          .where((item) => item.product.id == widget.product.id)
          .firstOrNull;
      if (existing == null) cart.add(widget.product);

      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const CheckoutScreen()),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unable to start checkout right now. Please try again.')),
      );
    } finally {
      if (mounted) setState(() => _buyNowLoading = false);
    }
  }

  @override
  void dispose() {
    _imageTimer?.cancel();
    _imageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cart = context.watch<CartProvider>();
    final product = widget.product;
    final item = cart.items.where((x) => x.product.id == product.id).firstOrNull;
    final similar = context
        .watch<CatalogProvider>()
        .products
        .where((p) => p.id != product.id && p.categoryId == product.categoryId)
        .take(8)
        .toList();

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          IconButton(
            tooltip: 'Search',
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SearchScreen()),
            ),
            icon: const Icon(Icons.search_rounded),
          ),
          IconButton(
            tooltip: 'Share',
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Product sharing will be available soon.')),
              );
            },
            icon: const Icon(Icons.share_outlined),
          ),
          IconButton(
            tooltip: 'Cart',
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
      bottomNavigationBar: _BottomActions(
        available: product.available,
        loading: _buyNowLoading,
        onAdd: () => cart.add(product),
        onBuy: _buyNow,
      ),
      body: ListView(
        padding: EdgeInsets.zero,
        children: [
          _ProductImageGallery(
            images: _images,
            controller: _imageController,
            currentIndex: _currentImage,
            onPageChanged: (index) {
              setState(() => _currentImage = index);
              _restartImageTimer();
            },
            onNext: _showNextImage,
            onPrevious: _showPreviousImage,
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (product.discount >= 1)
                  _DiscountBadge(discount: product.discount),
                const SizedBox(height: 9),
                Text(
                  product.name,
                  style: const TextStyle(fontSize: 25, height: 1.2, fontWeight: FontWeight.w800),
                ),
                if (product.brand.trim().isNotEmpty) ...[
                  const SizedBox(height: 5),
                  Text(
                    product.brand,
                    style: TextStyle(fontSize: 14, color: Colors.grey.shade600),
                  ),
                ],
                const SizedBox(height: 12),
                if (product.categoryName.trim().isNotEmpty) ...[
                  Row(
                    children: [
                      const Icon(Icons.category_outlined, size: 18),
                      const SizedBox(width: 7),
                      Text(
                        product.categoryName,
                        style: TextStyle(color: Colors.grey.shade700, fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                ],
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      '₹${product.sellingPrice.round()}',
                      style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w900),
                    ),
                    const SizedBox(width: 10),
                    if (product.mrp > product.sellingPrice)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 3),
                        child: Text(
                          '₹${product.mrp.round()}',
                          style: TextStyle(
                            color: Colors.grey.shade500,
                            decoration: TextDecoration.lineThrough,
                            fontSize: 16,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 6),
                if (product.unit.trim().isNotEmpty)
                  Text(
                    'Inclusive of all applicable taxes • ${product.unit}',
                    style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
                  ),
                const SizedBox(height: 20),
                _InfoCard(
                  title: 'Delivery & availability',
                  icon: Icons.local_shipping_outlined,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            product.available ? Icons.check_circle : Icons.cancel,
                            size: 19,
                            color: product.available ? Colors.green : Colors.red,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            product.available ? 'In Stock' : 'Currently unavailable',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                        ],
                      ),
                      if (product.stock > 0) ...[
                        const SizedBox(height: 7),
                        Text('Only ${product.stock} left in stock', style: TextStyle(color: Colors.grey.shade700)),
                      ],
                      const SizedBox(height: 10),
                      const Text('Delivery availability will be confirmed at checkout.', style: TextStyle(height: 1.35)),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _SectionTitle(title: 'Product highlights'),
                const SizedBox(height: 12),
                _Highlights(attributes: product.attributes, product: product),
                const SizedBox(height: 22),
                _SectionTitle(
                  title: 'About this product',
                  trailing: IconButton(
                    visualDensity: VisualDensity.compact,
                    onPressed: () => setState(() => _detailsExpanded = !_detailsExpanded),
                    icon: Icon(_detailsExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down),
                  ),
                ),
                if (_detailsExpanded) ...[
                  const SizedBox(height: 8),
                  Text(
                    product.description.trim().isEmpty
                        ? 'Product information will be updated by the seller.'
                        : product.description,
                    style: TextStyle(color: Colors.grey.shade700, height: 1.55, fontSize: 15),
                  ),
                ],
                const SizedBox(height: 22),
                _DetailsTable(product: product),
                const SizedBox(height: 22),
                _PromisesRow(),
                if (item != null) ...[
                  const SizedBox(height: 20),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: const Color(0xfffff7d6),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.shopping_bag_outlined),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            '${item.quantity} ${item.quantity == 1 ? 'item' : 'items'} in your cart',
                            style: const TextStyle(fontWeight: FontWeight.w800),
                          ),
                        ),
                        TextButton(
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const CartScreen()),
                          ),
                          child: const Text('VIEW CART'),
                        ),
                      ],
                    ),
                  ),
                ],
                if (similar.isNotEmpty) ...[
                  const SizedBox(height: 28),
                  const Text('Similar products', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 275,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: similar.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 12),
                      itemBuilder: (_, index) => SizedBox(
                        width: 175,
                        child: ProductCard(product: similar[index]),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 20),
                Center(
                  child: Text(
                    'Product information is supplied by the seller. Packaging and availability may vary.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey.shade500, fontSize: 12, height: 1.4),
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

class _ProductImageGallery extends StatelessWidget {
  final List<String> images;
  final PageController controller;
  final int currentIndex;
  final ValueChanged<int> onPageChanged;
  final VoidCallback onNext;
  final VoidCallback onPrevious;

  const _ProductImageGallery({
    required this.images,
    required this.controller,
    required this.currentIndex,
    required this.onPageChanged,
    required this.onNext,
    required this.onPrevious,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          height: 410,
          margin: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            color: const Color(0xfff6f7f9),
            borderRadius: BorderRadius.circular(18),
          ),
          clipBehavior: Clip.antiAlias,
          child: Stack(
            children: [
              PageView.builder(
                controller: controller,
                itemCount: images.length,
                onPageChanged: onPageChanged,
                itemBuilder: (_, index) => GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: onNext,
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: CatalogImage(source: images[index], fit: BoxFit.contain, iconSize: 60),
                  ),
                ),
              ),
              if (images.length > 1) ...[
                Positioned(
                  left: 10,
                  top: 0,
                  bottom: 0,
                  child: Center(
                    child: _GalleryButton(icon: Icons.chevron_left_rounded, onPressed: onPrevious),
                  ),
                ),
                Positioned(
                  right: 10,
                  top: 0,
                  bottom: 0,
                  child: Center(
                    child: _GalleryButton(icon: Icons.chevron_right_rounded, onPressed: onNext),
                  ),
                ),
                Positioned(
                  right: 18,
                  top: 18,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(.62),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      '${currentIndex + 1}/${images.length}',
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
        if (images.length > 1)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                images.length,
                (index) => AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  margin: const EdgeInsets.symmetric(horizontal: 3),
                  width: currentIndex == index ? 22 : 7,
                  height: 7,
                  decoration: BoxDecoration(
                    color: currentIndex == index ? const Color(0xff2874f0) : Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _GalleryButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onPressed;
  const _GalleryButton({required this.icon, required this.onPressed});

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.white.withOpacity(.92),
        shape: const CircleBorder(),
        elevation: 2,
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onPressed,
          child: Padding(padding: const EdgeInsets.all(7), child: Icon(icon, size: 25)),
        ),
      );
}

class _DiscountBadge extends StatelessWidget {
  final double discount;
  const _DiscountBadge({required this.discount});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
        decoration: BoxDecoration(
          color: const Color(0xffe8f1ff),
          borderRadius: BorderRadius.circular(6),
        ),
        child: Text(
          '${discount.round()}% OFF',
          style: const TextStyle(color: Color(0xff2874f0), fontWeight: FontWeight.w900),
        ),
      );
}

class _InfoCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final Widget child;
  const _InfoCard({required this.title, required this.icon, required this.child});

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: const Color(0xfff8f9fb),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xffeeeeee)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 21),
                const SizedBox(width: 9),
                Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16)),
              ],
            ),
            const SizedBox(height: 12),
            child,
          ],
        ),
      );
}

class _SectionTitle extends StatelessWidget {
  final String title;
  final Widget? trailing;
  const _SectionTitle({required this.title, this.trailing});

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Expanded(child: Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900))),
          if (trailing != null) trailing!,
        ],
      );
}

class _Highlights extends StatelessWidget {
  final Map<String, String> attributes;
  final Product product;
  const _Highlights({required this.attributes, required this.product});

  @override
  Widget build(BuildContext context) {
    final entries = <MapEntry<String, String>>[];
    void add(String label, String value) {
      if (value.trim().isNotEmpty && entries.length < 10) {
        entries.add(MapEntry(label, value.trim()));
      }
    }

    add('Brand', product.brand);
    add('Category', product.categoryName);
    add('Quantity', product.unit);
    add('Weight', product.weight);
    add('Size', product.size);
    for (final entry in attributes.entries) {
      if (entry.key.trim().isNotEmpty && !entries.any((e) => e.key.toLowerCase() == entry.key.toLowerCase())) {
        add(entry.key, entry.value);
      }
    }

    if (entries.isEmpty) {
      return Text('No additional product highlights have been provided by the seller.', style: TextStyle(color: Colors.grey.shade600));
    }

    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xffe8e8e8)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        children: List.generate(entries.length, (index) {
          final entry = entries[index];
          return Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              border: index == entries.length - 1 ? null : const Border(bottom: BorderSide(color: Color(0xffeeeeee))),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: Text(entry.key, style: TextStyle(color: Colors.grey.shade600, fontSize: 13))),
                const SizedBox(width: 14),
                Expanded(flex: 2, child: Text(entry.value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14))),
              ],
            ),
          );
        }),
      ),
    );
  }
}

class _DetailsTable extends StatelessWidget {
  final Product product;
  const _DetailsTable({required this.product});

  @override
  Widget build(BuildContext context) {
    final details = <MapEntry<String, String>>[
      if (product.categoryName.isNotEmpty) MapEntry('Category', product.categoryName),
      if (product.unit.isNotEmpty) MapEntry('Unit', product.unit),
      if (product.weight.isNotEmpty) MapEntry('Weight', product.weight),
      if (product.size.isNotEmpty) MapEntry('Size', product.size),
      if (product.deliveryType.isNotEmpty) MapEntry('Delivery type', product.deliveryType),
      if (product.stock >= 0) MapEntry('Stock available', '${product.stock}'),
      ...product.attributes.entries,
    ];

    if (details.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('All details', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        const SizedBox(height: 12),
        ...details.map(
          (entry) => Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: Text(entry.key, style: TextStyle(color: Colors.grey.shade600, fontSize: 13))),
                Expanded(flex: 2, child: Text(entry.value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600))),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _PromisesRow extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    const promises = [
      (Icons.shopping_bag_outlined, 'Easy\nordering'),
      (Icons.local_shipping_outlined, 'Delivery\ntracking'),
      (Icons.currency_rupee_rounded, 'Online\npayment'),
      (Icons.support_agent_outlined, 'Order\nsupport'),
    ];
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
      decoration: BoxDecoration(
        color: const Color(0xfffff8fb),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: promises
            .map(
              (item) => Expanded(
                child: Column(
                  children: [
                    Icon(item.$1, color: const Color(0xffe91e63), size: 26),
                    const SizedBox(height: 6),
                    Text(item.$2, textAlign: TextAlign.center, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, height: 1.2)),
                  ],
                ),
              ),
            )
            .toList(),
      ),
    );
  }
}

class _BottomActions extends StatelessWidget {
  final bool available;
  final bool loading;
  final VoidCallback onAdd;
  final VoidCallback onBuy;

  const _BottomActions({required this.available, required this.loading, required this.onAdd, required this.onBuy});

  @override
  Widget build(BuildContext context) => SafeArea(
        top: false,
        child: Container(
          padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
          decoration: BoxDecoration(
            color: Colors.white,
            boxShadow: [BoxShadow(color: Colors.black.withOpacity(.10), blurRadius: 16, offset: const Offset(0, -4))],
          ),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: available ? onAdd : null,
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: const Text('ADD TO CART', style: TextStyle(fontWeight: FontWeight.w900)),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: FilledButton(
                  onPressed: available && !loading ? onBuy : null,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                    backgroundColor: const Color(0xff2874f0),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: loading
                      ? const SizedBox(width: 21, height: 21, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Text('BUY NOW', style: TextStyle(fontWeight: FontWeight.w900)),
                ),
              ),
            ],
          ),
        ),
      );
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
