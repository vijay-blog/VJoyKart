import 'dart:async';

import 'package:flutter/material.dart';

class FashionBannerSlider extends StatefulWidget {
  const FashionBannerSlider({super.key, this.onShopNow});

  final VoidCallback? onShopNow;

  @override
  State<FashionBannerSlider> createState() => _FashionBannerSliderState();
}

class _FashionBannerSliderState extends State<FashionBannerSlider> {
  final PageController _controller = PageController();
  Timer? _timer;
  int _page = 0;

  static const _banners = [
    (
      asset: 'assets/images/fashion_banners/launch_10_70.png',
      title: 'Grand Launch Offer',
    ),
    (
      asset: 'assets/images/fashion_banners/fashion_family.png',
      title: 'Fashion For Everyone',
    ),
    (
      asset: 'assets/images/fashion_banners/new_styles.png',
      title: 'New Season Styles',
    ),
  ];

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (!_controller.hasClients) return;
      final next = (_page + 1) % _banners.length;
      _controller.animateToPage(
        next,
        duration: const Duration(milliseconds: 450),
        curve: Curves.easeOutCubic,
      );
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SizedBox(
          height: 188,
          child: PageView.builder(
            controller: _controller,
            itemCount: _banners.length,
            onPageChanged: (value) => setState(() => _page = value),
            itemBuilder: (_, index) => GestureDetector(
              onTap: widget.onShopNow,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(22),
                child: Image.asset(
                  _banners[index].asset,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => Container(
                    color: Theme.of(context).colorScheme.surfaceContainerHighest,
                    alignment: Alignment.center,
                    child: Text(_banners[index].title),
                  ),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: List.generate(
            _banners.length,
            (index) => AnimatedContainer(
              duration: const Duration(milliseconds: 250),
              margin: const EdgeInsets.symmetric(horizontal: 3),
              width: index == _page ? 22 : 7,
              height: 7,
              decoration: BoxDecoration(
                color: index == _page
                    ? Theme.of(context).colorScheme.primary
                    : Colors.black26,
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
