import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/app_theme.dart';
import '../providers/catalog_provider.dart';
import '../providers/cart_provider.dart';
import '../widgets/home_category_slider.dart';
import '../widgets/product_card.dart';
import '../widgets/catalog_image.dart';
import 'all_categories_screen.dart';
import 'category_screen.dart';
import 'search_screen.dart';
import 'cart_screen.dart';
import 'orders_screen.dart';
import 'profile_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int index = 0;
  @override
  Widget build(BuildContext c) {
    final cart = c.watch<CartProvider>();
    final pages = [
      const _HomeTab(),
      const AllCategoriesScreen(),
      const OrdersScreen(),
      const CartScreen(),
      const ProfileScreen()
    ];
    return Scaffold(
        body: pages[index],
        bottomNavigationBar: NavigationBar(
            selectedIndex: index,
            onDestinationSelected: (i) => setState(() => index = i),
            destinations: [
              const NavigationDestination(
                  icon: Icon(Icons.home_outlined),
                  selectedIcon: Icon(Icons.home),
                  label: 'Home'),
              const NavigationDestination(
                  icon: Icon(Icons.grid_view_outlined),
                  selectedIcon: Icon(Icons.grid_view),
                  label: 'Categories'),
              const NavigationDestination(
                  icon: Icon(Icons.receipt_long_outlined),
                  selectedIcon: Icon(Icons.receipt_long),
                  label: 'Orders'),
              NavigationDestination(
                  icon: Badge(
                      isLabelVisible: cart.count > 0,
                      label: Text('${cart.count}'),
                      child: const Icon(Icons.shopping_bag_outlined)),
                  selectedIcon: const Icon(Icons.shopping_bag),
                  label: 'Cart'),
              const NavigationDestination(
                  icon: Icon(Icons.person_outline),
                  selectedIcon: Icon(Icons.person),
                  label: 'Profile')
            ]));
  }
}

class _HomeTab extends StatelessWidget {
  const _HomeTab();

  @override
  Widget build(BuildContext context) {
    final catalog = context.watch<CatalogProvider>();
    final cart = context.watch<CartProvider>();
    final sections = catalog.categories
        .map((category) => (
              category: category,
              products:
                  catalog.search('', categoryId: category.id).take(10).toList(),
            ))
        .where((section) => section.products.isNotEmpty)
        .toList();

    return SafeArea(
      child: RefreshIndicator(
        onRefresh: catalog.load,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            if (catalog.error != null && catalog.products.isEmpty)
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
                sliver: SliverToBoxAdapter(
                  child: Card(
                    child: ListTile(
                      leading: const Icon(Icons.cloud_off),
                      title: const Text('Unable to load products'),
                      subtitle: const Text('Please try again.'),
                      trailing: IconButton(
                        onPressed: catalog.load,
                        icon: const Icon(Icons.refresh),
                      ),
                    ),
                  ),
                ),
              ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: const Color(0xffe9edff),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.shopping_bag_rounded,
                        color: AppTheme.green,
                      ),
                    ),
                    const SizedBox(width: 10),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'VJoyKart',
                            style: TextStyle(
                              color: AppTheme.green,
                              fontWeight: FontWeight.w900,
                              fontSize: 16,
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Deliver to',
                            style: TextStyle(color: Colors.grey, fontSize: 12),
                          ),
                          SizedBox(height: 2),
                          Row(
                            children: [
                              Icon(
                                Icons.location_on,
                                color: AppTheme.green,
                                size: 18,
                              ),
                              SizedBox(width: 4),
                              Text(
                                'Hyderabad',
                                style: TextStyle(
                                  fontWeight: FontWeight.w900,
                                  fontSize: 18,
                                ),
                              ),
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
                          MaterialPageRoute(
                            builder: (_) => const CartScreen(),
                          ),
                        ),
                        icon: const Icon(
                          Icons.shopping_bag_outlined,
                          size: 27,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
              sliver: SliverToBoxAdapter(
                child: InkWell(
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const SearchScreen()),
                  ),
                  borderRadius: BorderRadius.circular(15),
                  child: Container(
                    height: 52,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(15),
                    ),
                    child: const Row(
                      children: [
                        Icon(Icons.search, color: Colors.grey),
                        SizedBox(width: 10),
                        Text(
                          'Search products...',
                          style: TextStyle(color: Colors.grey),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            if (catalog.categories.isNotEmpty)
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(18, 2, 18, 0),
                sliver: SliverToBoxAdapter(
                  child: HomeCategorySlider(
                    categories: catalog.categories
                        .take(24)
                        .map((item) => {
                              'name': item.name,
                              'image': item.imageUrl,
                            })
                        .toList(),
                    onTapCategory: (name) {
                      final category =
                          catalog.categories.firstWhere((x) => x.name == name);
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => CategoryScreen(
                            categoryId: category.id,
                            categoryName: category.name,
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(18, 12, 18, 0),
              sliver: SliverToBoxAdapter(
                child: Container(
                  height: 154,
                  padding: const EdgeInsets.all(22),
                  decoration: BoxDecoration(
                    color: const Color(0xffe7ebff),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              'Everything You Need, Near You.',
                              style: TextStyle(
                                fontSize: 21,
                                fontWeight: FontWeight.w900,
                                color: AppTheme.green,
                              ),
                            ),
                            SizedBox(height: 8),
                            Text(
                              'Products selected by your local store',
                              style: TextStyle(color: Colors.black54),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        width: 104,
                        height: 104,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(22),
                        ),
                        child: const Icon(
                          Icons.local_mall_rounded,
                          size: 58,
                          color: AppTheme.green,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (catalog.categories.isNotEmpty) ...[
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(18, 24, 18, 10),
                sliver: SliverToBoxAdapter(
                  child: Row(
                    children: [
                      const Text(
                        'Shop by Category',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const Spacer(),
                      TextButton(
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const AllCategoriesScreen(),
                          ),
                        ),
                        child: const Text('View all'),
                      ),
                    ],
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                sliver: SliverToBoxAdapter(
                  child: SizedBox(
                    height: 112,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: catalog.categories.take(8).length,
                      separatorBuilder: (_, __) => const SizedBox(width: 10),
                      itemBuilder: (_, index) {
                        final category = catalog.categories[index];
                        return InkWell(
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => CategoryScreen(
                                categoryId: category.id,
                                categoryName: category.name,
                              ),
                            ),
                          ),
                          child: Container(
                            width: 82,
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Column(
                              children: [
                                Expanded(
                                  child: CatalogImage(
                                    source: category.imageUrl,
                                  ),
                                ),
                                const SizedBox(height: 5),
                                Text(
                                  category.name,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ),
              ),
            ],
            if (catalog.loading)
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.all(30),
                  child: Center(child: CircularProgressIndicator()),
                ),
              )
            else if (catalog.products.isEmpty && catalog.error == null)
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 24, vertical: 42),
                  child: Column(
                    children: [
                      Icon(
                        Icons.inventory_2_outlined,
                        size: 58,
                        color: Colors.grey,
                      ),
                      SizedBox(height: 12),
                      Text(
                        'No products available',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      SizedBox(height: 6),
                      Text(
                        'Products added by the administrator will appear here.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.black54),
                      ),
                    ],
                  ),
                ),
              )
            else
              ...sections.map(
                (section) => SliverPadding(
                  padding: const EdgeInsets.fromLTRB(18, 22, 18, 0),
                  sliver: SliverToBoxAdapter(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          section.category.name,
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 10),
                        SizedBox(
                          height: 286,
                          child: ListView.separated(
                            scrollDirection: Axis.horizontal,
                            itemCount: section.products.length,
                            itemBuilder: (_, index) => SizedBox(
                              width: 176,
                              child: ProductCard(
                                product: section.products[index],
                              ),
                            ),
                            separatorBuilder: (_, __) =>
                                const SizedBox(width: 12),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            const SliverToBoxAdapter(child: SizedBox(height: 30)),
          ],
        ),
      ),
    );
  }
}
