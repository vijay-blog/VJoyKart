import 'package:flutter/material.dart';
import 'catalog_image.dart';

class HomeCategorySlider extends StatelessWidget {
  final List<Map<String, String>> categories;
  final ValueChanged<String> onTapCategory;

  const HomeCategorySlider({
    super.key,
    required this.categories,
    required this.onTapCategory,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 104,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: categories.length,
        separatorBuilder: (_, __) => const SizedBox(width: 10),
        itemBuilder: (_, index) {
          final category = categories[index];
          final name = category['name'] ?? 'Category';
          final image = category['image'] ?? '';
          return InkWell(
            onTap: () => onTapCategory(name),
            borderRadius: BorderRadius.circular(14),
            child: Container(
              width: 86,
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(
                children: [
                  Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: CatalogImage(
                        source: image,
                        fit: BoxFit.contain,
                        iconSize: 26,
                      ),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
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
    );
  }
}
