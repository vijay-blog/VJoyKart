class Product {
  final int id;
  final String name;
  final String description;
  final String brand;
  final String categoryId;
  final String categoryName;
  final List<String> images;
  final double mrp;
  final double sellingPrice;
  final double discountPercentage;
  final String unit;
  final String weight;
  final String size;
  final Map<String, String> attributes;
  final bool available;
  final int stockQuantity;
  final String deliveryType;

  const Product({
    required this.id,
    required this.name,
    required this.description,
    required this.brand,
    required this.categoryId,
    required this.categoryName,
    required this.images,
    required this.mrp,
    required this.sellingPrice,
    required this.discountPercentage,
    required this.unit,
    required this.weight,
    required this.size,
    required this.attributes,
    required this.available,
    required this.stockQuantity,
    required this.deliveryType,
  });

  String get category => categoryName;
  String get imageAsset => images.isEmpty ? '' : images.first;
  int get stock => stockQuantity;
  double get discount => discountPercentage > 0
      ? discountPercentage
      : (mrp <= 0 ? 0 : ((mrp - sellingPrice) / mrp) * 100);

  factory Product.fromJson(Map<String, dynamic> j) {
    final imageFromServer = (j['imageAsset'] ?? j['imageUrl'] ?? '').toString();
    final imgs = (j['images'] is List)
        ? (j['images'] as List).map((e) => e.toString()).toList()
        : <String>[];
    if (imgs.isEmpty && imageFromServer.isNotEmpty) {
      imgs.add(imageFromServer);
    }

    // Product images are uploaded by the VJoyKart Partner/Admin backend.
    // Older product records may have an empty imageUrl in the customer API,
    // while the partner backend already has the uploaded BLOB. Use its public
    // primary-image endpoint as a fallback so existing products do not need
    // to be re-created or re-uploaded.
    final productId = _integer(j['productId'] ?? j['id']);
    if (imgs.isEmpty && productId > 0) {
      imgs.add(
        'https://nexamartpartner-production.up.railway.app/api/v1/catalog/products/$productId/image',
      );
    }
    final price = _number(j['price'] ?? j['mrp']);
    final discountedPrice = _number(
      j['discountedPrice'] ?? j['sellingPrice'] ?? price,
    );
    final availability = j['availability']?.toString().toUpperCase();
    final status = j['status']?.toString().toUpperCase();
    final hasStock = j.containsKey('stock') || j.containsKey('stockQuantity');
    final stock = _integer(j['stock'] ?? j['stockQuantity']);
    return Product(
      id: _integer(j['productId'] ?? j['id']),
      name: (j['name'] ?? '').toString(),
      description: (j['description'] ?? '').toString(),
      brand: (j['brand'] ?? '').toString(),
      categoryId: (j['categoryId'] ?? '').toString(),
      categoryName: (j['categoryName'] ?? j['category'] ?? '').toString(),
      images: imgs,
      mrp: price,
      sellingPrice: discountedPrice,
      discountPercentage: _number(
        j['discountPercent'] ?? j['discountPercentage'],
      ),
      unit: (j['unit'] ?? '1 unit').toString(),
      weight: (j['weight'] ?? '').toString(),
      size: (j['size'] ?? '').toString(),
      attributes: (j['attributes'] is Map)
          ? (j['attributes'] as Map).map(
              (k, v) => MapEntry(k.toString(), v.toString()),
            )
          : const <String, String>{},
      available:
          status != 'INACTIVE' &&
          availability != 'UNAVAILABLE' &&
          availability != 'OUT_OF_STOCK' &&
          (j['available'] ?? true) != false &&
          (!hasStock || stock > 0),
      stockQuantity: stock,
      deliveryType: (j['deliveryType'] ?? 'SMALL').toString(),
    );
  }

  static double _number(dynamic value) =>
      value is num ? value.toDouble() : double.tryParse('$value') ?? 0;

  static int _integer(dynamic value) =>
      value is num ? value.toInt() : int.tryParse('$value') ?? 0;

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'description': description,
    'brand': brand,
    'categoryId': categoryId,
    'categoryName': categoryName,
    'images': images,
    'mrp': mrp,
    'sellingPrice': sellingPrice,
    'discountPercentage': discountPercentage,
    'unit': unit,
    'weight': weight,
    'size': size,
    'attributes': attributes,
    'available': available,
    'stockQuantity': stockQuantity,
    'deliveryType': deliveryType,
  };
}

class CategoryItem {
  final String id;
  final String name;
  final String imageUrl;

  const CategoryItem({
    required this.id,
    required this.name,
    required this.imageUrl,
  });

  factory CategoryItem.fromJson(Map<String, dynamic> json) => CategoryItem(
    id: (json['categoryId'] ?? json['id'] ?? '').toString(),
    name: (json['name'] ?? '').toString(),
    imageUrl: (json['imageUrl'] ?? '').toString(),
  );
}
