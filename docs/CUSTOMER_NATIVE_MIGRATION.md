# VJoyKart Customer native Android migration

## Status

The customer application is now the `:customer` native Android module. It preserves
the published application id, `com.nexamart.customer`, and produces Android App Bundles
without a cross-platform runtime or SDK dependency.

## Audit checklist

| Area | Result |
|---|---|
| Native toolchain | Kotlin, Android SDK, Java 17, Gradle 9.5, AGP 9.3.2 |
| SDK levels | minSdk 24, targetSdk 36, compileSdk 37 |
| Application identity | `com.nexamart.customer`; version 2.2.0 (versionCode 6) |
| API base URL | `https://zeptopluse-production.up.railway.app/api/v1` |
| Catalog image service | `https://nexamartpartner-production.up.railway.app` |
| Networking | Retrofit, OkHttp, Moshi; 15-second customer timeout and 8-second catalog timeout |
| Session storage | Native SharedPreferences, including a one-time import from the previous app's preference store |
| Cart and addresses | Native SharedPreferences, using the original data keys and JSON structures |
| Image loading | Coil; absolute URLs, backend-relative paths, asset paths, missing-image fallback, and image-object variants supported |
| Payments | Razorpay Checkout; server creates and verifies payment orders |
| Maps/location | Android location APIs, Geocoder, runtime location permission, external Google Maps intent |
| Firebase/FCM | No customer Firebase configuration, Google services file, FCM implementation, or notification endpoint existed in the audited application; none was introduced |
| Deep links/camera | No existing customer implementation was found; none was introduced |
| Network security | Existing HTTPS-only native network security configuration retained |
| Release hardening | R8 and resource shrinking enabled, Razorpay and Retrofit keep rules included |

## Architecture

```
customer/
  src/main/java/com/nexamart/customer/
    core/          configuration and error mapping
    data/local/    SharedPreferences and legacy-data importer
    data/network/  Retrofit interfaces and OkHttp client
    model/         API/cache models
    repository/    catalog, session, cart, address, and order state
    presentation/  Material 3 fragments, view models, navigation, and UI utilities
```

`VJoyKartApp` owns app-scoped repositories. Fragment-scoped view models expose state via
`StateFlow`; UI collection is lifecycle-aware. The customer session is intentionally
separate from the Partner application.

## Backend contract mapping

| Native API | Method | Native owner | UI |
|---|---|---|---|
| `auth/customer/send-otp` | POST | `SessionRepository.sendOtp` | checkout OTP gate |
| `auth/customer/verify-otp` | POST | `SessionRepository.verifyOtp` | checkout OTP gate |
| `catalog/products` | GET | `CatalogRepository.load/search` | home, category, search, product detail |
| Partner `api/v1/catalog/products` | GET | `CatalogRepository` | catalog image enrichment |
| `customer/orders` | GET | `OrderRepository.refresh` | orders and order detail |
| `customer/orders` | POST | `OrderRepository.create` | checkout |
| `customer/orders/{id}/tracking` | GET | `OrderRepository.refreshTracking` | order detail polling |
| `payments/create-order` | POST | `OrderRepository.createPaymentOrder` | online checkout |
| `payments/verify` | POST | `OrderRepository.verifyPayment` | Razorpay success result |

Authentication sends/validates a real customer OTP, persists access and refresh tokens,
restores valid sessions on launch, clears expired tokens, and uses the customer endpoints
only. The HTTP client attaches the existing access or guest token and maps API/network
errors to customer-visible messages.

For online payments, checkout creates the backend order, requests a Razorpay order, opens
Checkout with the public key/order/amount/currency, then calls backend verification using
the Razorpay result. The cart is cleared only after backend verification returns success.
No Razorpay secret is packaged in the client.

## Permission mapping

| Permission | Feature | Runtime behavior |
|---|---|---|
| `INTERNET` | backend, image loading, Razorpay | manifest-only |
| `ACCESS_COARSE_LOCATION` | address location helper | requested only after **Use Current Location** |
| `ACCESS_FINE_LOCATION` | precise address location helper | requested only after **Use Current Location** |

There are no Contacts, Phone, Camera, Storage, notification, or background-location
permissions in the customer manifest.

## Native features implemented

- Splash/session restoration; premium fashion-first home with banners, category navigation,
  catalog sections, product images, search, cart badge, profile, and orders.
- Product grids/details, three-image carousel/autoplay, availability, discount, highlights,
  similar products, cart add, and buy now.
- Persistent cart with quantity stock enforcement, exact discount/totals, free-delivery
  threshold, remove/empty states, and checkout navigation.
- Customer OTP gate; address add/edit/select/default/delete; location, reverse geocoding,
  Google Maps handoff, checkout payment selection, COD, Razorpay, and backend verification.
- Order success navigation that returns to the existing Home tab without duplicate screens;
  orders, order detail, delivery progress, delivery partner contact, and maps.
- Profile, Wallet information, help/contact actions, version, and customer logout.

## Validation performed

- `:customer:compileDebugKotlin` completed successfully.
- `:customer:assembleDebug` completed successfully.
- `:customer:testDebugUnitTest` completed successfully: 17 tests covering catalog parsing,
  image ordering/URL handling, stock, cart totals, active/legacy order contracts, payment
  response parsing, delivery tracking, cache persistence, and preference-list migration.
- `:customer:bundleRelease` completed successfully with R8/resource shrinking enabled.
- A debug APK was installed on an API 36 emulator. The production catalog and Partner image
  endpoint loaded successfully. Home, auto-sliding gallery, product detail, add/increase cart,
  cart badge/totals, checkout OTP gate, OTP validation, profile, address validation,
  address persistence/default selection, and location permission/map affordance were exercised.

## Manual release testing still required

- Real customer OTP send/verify and session restoration using a production test account.
- COD order placement against a non-production-safe order/account, then order success,
  order history/detail, and back navigation.
- Razorpay success, cancellation, and failure with a configured test merchant/account; confirm
  server verification and no cart clearing before verification.
- Delivery tracking transitions and delivery-partner phone/maps with a live assigned order.
- Release signing with the registered Play upload key and upload to an internal test track.

## Build and install commands

```powershell
cd C:\app\application\VJoyKart
.\gradlew.bat :customer:assembleDebug
.\gradlew.bat :customer:testDebugUnitTest
.\gradlew.bat :customer:bundleRelease

# Install the debug build on a connected device/emulator
adb install -r customer\build\outputs\apk\debug\customer-debug.apk
```

The release bundle is:

```
customer\build\outputs\bundle\release\customer-release.aab
```

To sign the release bundle, create a non-versioned `customer\keystore.properties`:

```properties
storeFile=C:\\secure\\vjoykart-upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

The same values can instead be supplied through `VJOYKART_KEYSTORE_FILE`,
`VJOYKART_KEYSTORE_PASSWORD`, `VJOYKART_KEY_ALIAS`, and `VJOYKART_KEY_PASSWORD`.
Without these values, the release task intentionally produces an unsigned AAB rather than
falling back to the debug key.

The currently available upload key does not match the key registered in Google Play
(expected SHA-1 `EB:15:05:B7:2C:48:CE:07:1E:91:C3:5B:E4:AE:12:02:66:C4:DE:89`).
Complete a Play Console upload-key reset, then configure the replacement upload key before
submitting this bundle.
