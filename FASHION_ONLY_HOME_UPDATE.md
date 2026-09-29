# VJoyKart Customer — Fashion-Only Launch Update

## Scope
The customer app is now positioned as a clothing/fashion-only launch. Grocery, food, electronics and other non-clothing catalog products are not displayed by the customer app.

## Home page
- New 3-slide fashion launch banner carousel.
- Auto-advances every 3 seconds.
- Launch message: 10% to 70% OFF on clothing.
- Fashion-only search placeholder.
- Local fashion category strip with Men, Women, Boys, Girls, Kids Wear, T-Shirts, Shirts, Jeans, Dresses, Ethnic, Western, Innerwear, Nightwear, Sportswear, Winterwear, Trousers, Shorts, Sarees, Kurtis, Lehengas, Leggings, Palazzo, Party Wear, Casual Wear, Formal Wear and Baby Wear.
- Trending Fashion, Men's Wear, Women's Wear and Kids/Girls sections.
- Upcoming cards for Slippers, Electronics and Mobiles.
- Bottom Categories tab removed from customer navigation.
- Customer catalog no longer depends on the legacy `/catalog/categories` endpoint.

## Assets
Added:
- `app/assets/images/fashion_banners/launch_10_70.png`
- `app/assets/images/fashion_banners/fashion_family.png`
- `app/assets/images/fashion_banners/new_styles.png`

## Product filtering
`CatalogProvider` filters both initial catalog loading and remote search to fashion/clothing products only.
