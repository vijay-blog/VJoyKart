# VJoyKart Customer App — Updated Build

## 1. Guest shopping / checkout OTP
- Removed automatic guest-account creation at app startup.
- No customer login/register UI is required while browsing.
- Customers can browse categories, products and cart without signing in.
- Checkout now asks for mobile OTP only when the customer continues to place an order.
- OTP verification creates/uses the customer account and returns a customer JWT.
- Orders/customer APIs are called only after the customer is authenticated.

## 2. Admin-driven catalog
- Customer catalog remains backend-only.
- Products and categories are loaded from `/api/v1/catalog/**`.
- No production hardcoded products/categories are used.
- Active admin products and active categories are shown.
- Relative backend image paths are resolved correctly.

## 3. Screenshot / dark-screen fix
- Cleared Android `FLAG_SECURE`.
- Forced the native Android window to a light theme.
- Disabled Android force-dark for the activity themes.
- Forced Flutter to use the light theme.
- Set light status/navigation bar icon configuration.
- Native launch background is white/light.

## 4. VJoyKart icon
The supplied VJoyKart image is now used as the Android launcher icon at all standard densities and in the Flutter splash screen.

## 5. Backend OTP configuration
Production SMS requires:

- `TWO_FACTOR_API_KEY`
- `TWO_FACTOR_TEMPLATE_NAME` (if required by the SMS template)
- `OTP_TTL_SECONDS` (optional, default 300)
- `OTP_DEV_MODE=false`

For local testing only, `OTP_DEV_MODE=true` can return a development OTP. Do not enable it in production.

## 6. Version
Flutter customer app version: `2.1.0+3`.
If Google Play has already consumed version code 3, increase the build number before uploading.

## 7. Validation limitation
This environment does not have Flutter SDK or Maven installed, so a local `flutter analyze/test/build` and Maven package run could not be executed here. The source changes were made against the supplied project structure; run the normal project build commands on a Flutter/Java development machine before release.
