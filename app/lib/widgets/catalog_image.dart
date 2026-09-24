import 'package:flutter/material.dart';

import '../core/app_config.dart';

class CatalogImage extends StatelessWidget {
  final String source;
  final BoxFit fit;
  final double? width;
  final double? height;
  final double iconSize;

  const CatalogImage({
    super.key,
    required this.source,
    this.fit = BoxFit.contain,
    this.width,
    this.height,
    this.iconSize = 42,
  });

  @override
  Widget build(BuildContext context) {
    final placeholder = Icon(
      Icons.image_not_supported_outlined,
      size: iconSize,
      color: Colors.grey,
    );
    final raw = source.trim();
    if (raw.isEmpty) return placeholder;

    if (raw.startsWith('http://') || raw.startsWith('https://')) {
      return Image.network(
        raw,
        width: width,
        height: height,
        fit: fit,
        errorBuilder: (_, __, ___) => placeholder,
      );
    }

    // Admin catalog images may be stored as a relative backend path such as
    // /uploads/products/abc.jpg. Resolve those against the configured API host.
    if (raw.startsWith('/')) {
      final isProductImage = raw.startsWith('/api/v1/catalog/products/');
      final base = isProductImage
          ? AppConfig.catalogImageBaseUrl
          : AppConfig.apiBaseUrl.replaceFirst(RegExp(r'/api/v1$'), '');
      final url = '$base$raw';
      return Image.network(
        url,
        width: width,
        height: height,
        fit: fit,
        errorBuilder: (_, __, ___) => placeholder,
      );
    }

    return Image.asset(
      raw,
      width: width,
      height: height,
      fit: fit,
      errorBuilder: (_, __, ___) => placeholder,
    );
  }
}
