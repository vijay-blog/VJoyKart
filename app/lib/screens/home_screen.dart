import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../models/product.dart';
import '../providers/catalog_provider.dart';
import '../providers/cart_provider.dart';
import '../widgets/fashion_banner_slider.dart';
import '../widgets/product_card.dart';
import 'cart_screen.dart';
import 'fashion_category_screen.dart';
import 'orders_screen.dart';
import 'profile_screen.dart';
import 'search_screen.dart';

class HomeScreen extends StatefulWidget {
  final int initialIndex;

  const HomeScreen({super.key, this.initialIndex = 0});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late int index;

  @override
  void initState() {
    super.initState();
    index = widget.initialIndex.clamp(0, 3).toInt();
  }

  @override
  Widget build(BuildContext context) {
    final cart = context.watch<CartProvider>();
    final pages = [
      const _HomeTab(),
      const OrdersScreen(),
      const CartScreen(),
      const ProfileScreen(),
    ];

    return Scaffold(
      body: pages[index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Home',
          ),
          const NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Orders',
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: cart.count > 0,
              label: Text('${cart.count}'),
              child: const Icon(Icons.shopping_bag_outlined),
            ),
            selectedIcon: const Icon(Icons.shopping_bag),
            label: 'Cart',
          ),
          const NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}

class _FashionCategory {
  final String name;
  final String asset;
  final List<String> keywords;

  const _FashionCategory(this.name, this.asset, this.keywords);
}

const _fashionCategories = [
  _FashionCategory('Men', 'assets/images/products/tshirt.png', ['men', 'mens', 'man']),
  _FashionCategory('Women', 'assets/images/products/women_dress.png', ['women', 'womens', 'woman']),
  _FashionCategory('Boys', 'assets/images/products/boys_clothing.png', ['boys', 'boy']),
  _FashionCategory('Girls', 'assets/images/products/girls_clothing.png', ['girls', 'girl']),
  _FashionCategory('Kids Wear', 'assets/images/products/boys_clothing.png', ['kids', 'kid', 'children', 'boys', 'girls']),
  _FashionCategory('T-Shirts', 'assets/images/products/tshirt.png', ['t-shirt', 'tshirt', 'tee']),
  _FashionCategory('Shirts', 'assets/images/products/tshirt.png', ['shirt']),
  _FashionCategory('Jeans', 'assets/images/products/tshirt.png', ['jeans', 'denim']),
  _FashionCategory('Dresses', 'assets/images/products/dress.png', ['dress']),
  _FashionCategory('Ethnic Wear', 'assets/images/products/women_dress.png', ['ethnic', 'saree', 'sari', 'kurti', 'kurta', 'lehenga', 'salwar']),
  _FashionCategory('Western Wear', 'assets/images/products/dress.png', ['western', 'top', 'skirt', 'jacket']),
  _FashionCategory('Innerwear', 'assets/images/products/tshirt.png', ['innerwear', 'underwear']),
  _FashionCategory('Nightwear', 'assets/images/products/tshirt.png', ['nightwear', 'sleepwear']),
  _FashionCategory('Sportswear', 'assets/images/products/tshirt.png', ['sportswear', 'track pant', 'sports']),
  _FashionCategory('Winterwear', 'assets/images/products/tshirt.png', ['winterwear', 'hoodie', 'sweater', 'jacket', 'coat']),
  _FashionCategory('Trousers', 'assets/images/products/tshirt.png', ['trouser', 'pants']),
  _FashionCategory('Shorts', 'assets/images/products/tshirt.png', ['shorts']),
  _FashionCategory('Sarees', 'assets/images/products/women_dress.png', ['saree', 'sari']),
  _FashionCategory('Kurtis', 'assets/images/products/women_dress.png', ['kurti', 'kurta']),
  _FashionCategory('Lehengas', 'assets/images/products/women_dress.png', ['lehenga']),
  _FashionCategory('Leggings', 'assets/images/products/tshirt.png', ['leggings']),
  _FashionCategory('Palazzo', 'assets/images/products/women_dress.png', ['palazzo']),
  _FashionCategory('Party Wear', 'assets/images/products/dress.png', ['party wear', 'party']),
  _FashionCategory('Casual Wear', 'assets/images/products/tshirt.png', ['casual wear', 'casual']),
  _FashionCategory('Formal Wear', 'assets/images/products/tshirt.png', ['formal wear', 'formal', 'blazer']),
  _FashionCategory('Baby Wear', 'assets/images/products/girls_clothing.png', ['baby', 'newborn']),
];

bool _matchesAny(Product product, List<String> keywords) {
  final raw = [product.name, product.categoryName, product.description, product.brand].join(' ');
  final normalized = raw.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), ' ').trim();
  final padded = ' $normalized ';
  return keywords.any((keyword) {
    final value = keyword.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), ' ').trim();
    return value.isNotEmpty && padded.contains(' $value ');
  });
}

class _HomeTab extends StatelessWidget {
  const _HomeTab();

  void _openCategory(BuildContext context, _FashionCategory category) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => FashionCategoryScreen(
          title: category.name,
          keywords: category.keywords,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final catalog = context.watch<CatalogProvider>();
    final cart = context.watch<CartProvider>();

    final men = catalog.products.where((p) => _matchesAny(p, const ['men', 'mens', 'man'])).take(10).toList();
    final women = catalog.products.where((p) => _matchesAny(p, const ['women', 'womens', 'woman'])).take(10).toList();
    final kids = catalog.products.where((p) => _matchesAny(p, const ['kids', 'boys', 'girls', 'kid', 'boy', 'girl'])).take(10).toList();

    return SafeArea(
      child: RefreshIndicator(
        onRefresh: catalog.load,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 16, 18, 8),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFFFF2B70), Color(0xFFFF7A45)],
                        ),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: const Icon(Icons.checkroom_rounded, color: Colors.white),
                    ),
                    const SizedBox(width: 11),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'VJoyKart',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                          ),
                          SizedBox(height: 1),
                          Text('Cloths Shopping', style: TextStyle(color: Colors.black54, fontSize: 12)),
                          SizedBox(height: 2),
                          Row(
                            children: [
                              Icon(Icons.location_on, color: AppTheme.green, size: 16),
                              SizedBox(width: 3),
                              Text('Hyderabad', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
                            ],
                          ),
                        ],
                      ),
                    ),
                    Badge(
                      isLabelVisible: cart.count > 0,
                      label: Text('${cart.count}'),
                      child: IconButton(
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const CartScreen()),
                        ),
                        icon: const Icon(Icons.shopping_bag_outlined, size: 27),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
              sliver: SliverToBoxAdapter(
                child: InkWell(
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const SearchScreen()),
                  ),
                  borderRadius: BorderRadius.circular(16),
                  child: Container(
                    height: 52,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.black.withOpacity(.05)),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.search, color: Colors.black54),
                        SizedBox(width: 10),
                        Expanded(child: Text('Search clothes, brands, styles...', style: TextStyle(color: Colors.black54))),
                        Icon(Icons.mic_none_rounded, color: Colors.black45),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 8, 18, 0),
              sliver: SliverToBoxAdapter(
                child: FashionBannerSlider(
                  onShopNow: () => _openCategory(context, _fashionCategories.first),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 20, 18, 8),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    const Text('Shop by Fashion', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                    const Spacer(),
                    Text('All styles', style: TextStyle(color: AppTheme.green, fontWeight: FontWeight.w800)),
                  ],
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: SizedBox(
                height: 116,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 18),
                  scrollDirection: Axis.horizontal,
                  itemCount: _fashionCategories.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 10),
                  itemBuilder: (_, index) {
                    final item = _fashionCategories[index];
                    return InkWell(
                      onTap: () => _openCategory(context, item),
                      borderRadius: BorderRadius.circular(16),
                      child: Container(
                        width: 82,
                        padding: const EdgeInsets.fromLTRB(6, 7, 6, 7),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.black.withOpacity(.05)),
                        ),
                        child: Column(
                          children: [
                            Expanded(
                              child: ClipRRect(
                                borderRadius: BorderRadius.circular(12),
                                child: Image.asset(item.asset, fit: BoxFit.cover),
                              ),
                            ),
                            const SizedBox(height: 5),
                            Text(item.name, maxLines: 1, overflow: TextOverflow.ellipsis, textAlign: TextAlign.center, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800)),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 20, 18, 10),
              sliver: SliverToBoxAdapter(
                child: Container(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(colors: [Color(0xFF1E2140), Color(0xFF3B3E6E)]),
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.local_offer_rounded, color: Color(0xFFFFD166), size: 32),
                      SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('NEW LAUNCH • CLOTHS ONLY', style: TextStyle(color: Color(0xFFFFD166), fontSize: 11, fontWeight: FontWeight.w900, letterSpacing: .8)),
                            SizedBox(height: 5),
                            Text('10% to 70% OFF', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w900)),
                            SizedBox(height: 2),
                            Text('Shop fresh styles for men, women & kids.', style: TextStyle(color: Colors.white70)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (catalog.products.isNotEmpty)
              _productSection('Trending Fashion', catalog.products.take(12).toList()),
            if (men.isNotEmpty) _productSection("Men's Wear", men),
            if (women.isNotEmpty) _productSection("Women's Wear", women),
            if (kids.isNotEmpty) _productSection('Kids & Girls Wear', kids),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 24, 18, 10),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Upcoming', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                          SizedBox(height: 3),
                          Text('More departments are coming soon', style: TextStyle(color: Colors.black54)),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
                      decoration: BoxDecoration(color: const Color(0xFFFFEEF4), borderRadius: BorderRadius.circular(20)),
                      child: const Text('Stay tuned', style: TextStyle(color: Color(0xFFE91E63), fontWeight: FontWeight.w900, fontSize: 12)),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: const [
                    _UpcomingCard(title: 'Slippers', subtitle: 'Footwear', icon: Icons.hiking_rounded),
                    SizedBox(width: 10),
                    _UpcomingCard(title: 'Electronics', subtitle: 'Coming soon', icon: Icons.headphones_rounded),
                    SizedBox(width: 10),
                    _UpcomingCard(title: 'Mobiles', subtitle: 'Coming soon', icon: Icons.phone_iphone_rounded),
                  ],
                ),
              ),
            ),
            if (catalog.loading)
              const SliverToBoxAdapter(child: Padding(padding: EdgeInsets.all(30), child: Center(child: CircularProgressIndicator())))
            else if (catalog.products.isEmpty && catalog.error != null)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(28),
                  child: Card(
                    child: ListTile(
                      leading: const Icon(Icons.cloud_off),
                      title: const Text('Unable to load clothing'),
                      subtitle: const Text('Please try again.'),
                      trailing: IconButton(onPressed: catalog.load, icon: const Icon(Icons.refresh)),
                    ),
                  ),
                ),
              )
            else if (catalog.products.isEmpty)
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 24, vertical: 42),
                  child: Column(
                    children: [
                      Icon(Icons.checkroom_outlined, size: 58, color: Colors.grey),
                      SizedBox(height: 12),
                      Text('Clothing collection is coming soon', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900)),
                      SizedBox(height: 6),
                      Text('New fashion products added by the administrator will appear here.', textAlign: TextAlign.center, style: TextStyle(color: Colors.black54)),
                    ],
                  ),
                ),
              ),
            const SliverToBoxAdapter(child: SizedBox(height: 30)),
          ],
        ),
      ),
    );
  }

  SliverPadding _productSection(String title, List<Product> products) {
    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(18, 22, 18, 0),
      sliver: SliverToBoxAdapter(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                const Spacer(),
                const Text('See all', style: TextStyle(color: AppTheme.green, fontWeight: FontWeight.w800)),
              ],
            ),
            const SizedBox(height: 10),
            SizedBox(
              height: 286,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: products.length,
                itemBuilder: (_, index) => SizedBox(width: 176, child: ProductCard(product: products[index])),
                separatorBuilder: (_, __) => const SizedBox(width: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _UpcomingCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;

  const _UpcomingCard({required this.title, required this.subtitle, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        height: 105,
        padding: const EdgeInsets.all(11),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: Colors.black.withOpacity(.05)),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: const Color(0xFFF0F2FF),
                borderRadius: BorderRadius.circular(15),
              ),
              child: Icon(icon, color: AppTheme.green, size: 27),
            ),
            const SizedBox(height: 6),
            Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 12)),
            Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.black45, fontSize: 9)),
          ],
        ),
      ),
    );
  }
}
