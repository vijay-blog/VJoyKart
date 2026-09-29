# NexaMart

NexaMart is a Hyderabad-first hyperlocal, multi-category marketplace. The
modular monolith supports a Flutter customer experience now and leaves clear
boundaries for future partner, delivery, and operations applications.

## Modules

| Module | Technology | Responsibility |
| --- | --- | --- |
| `app/` | Flutter, Dart, Material 3 | Customer catalog, cart, addresses, checkout, COD, Razorpay online payment, orders, and tracking flow |
| `backend/` | Spring Boot, JPA, MySQL, Flyway | Authoritative catalog, inventory, pricing, addresses, orders, payment creation and Razorpay signature verification |
| `frontend/` | React, Vite, TypeScript | Operations dashboard foundation for catalog and orders |

## Start MySQL

Create a local `.env` containing non-production credentials, then run:

```powershell
$env:MYSQL_PASSWORD = "local-password"
$env:MYSQL_ROOT_PASSWORD = "local-root-password"
docker compose up -d mysql
```

Backend migration and seed data run at startup.

## Start the backend

```powershell
cd backend
$env:DB_USERNAME = "nexamart"
$env:DB_PASSWORD = "local-password"
$env:RAZORPAY_KEY_ID = "rzp_test_xxxxx"
$env:RAZORPAY_KEY_SECRET = "your-test-secret"
mvn spring-boot:run
```

Razorpay credentials are only required for online payment. COD order creation works without them. Never place Razorpay secret keys in Flutter or commit them to source control.

## Start the customer app

The repository also contains a separate native partner/admin Gradle module under
`app/src`. Running the root `gradlew` builds that operations APK, whose expected
startup is email/password login. It is not the customer application.

Always build the customer APK with Flutter from the `app` directory:

```powershell
cd app
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
```

Production customer artifacts:

```powershell
cd app
flutter build apk --release
flutter build appbundle --release
```

The customer APK uses application ID `com.vjoykart.customer` and is written to
`app/build/app/outputs/flutter-apk/app-release.apk`. It opens splash then Home;
mobile OTP is requested only after the customer continues from Cart to Checkout.

`10.0.2.2` reaches the host from the Android emulator. For a physical Android
device, supply your development computer's LAN address instead:

```powershell
flutter run --dart-define=API_BASE_URL=http://192.168.1.25:8080/api/v1
```

### Flutter API URL targets

- Local emulator development: `http://10.0.2.2:8080/api/v1`
- Railway private network: `http://nexamart.railway.internal/api/v1`
- Public production (real user devices): `https://<your-public-domain>/api/v1`

`nexamart.railway.internal` is an internal hostname and is only valid from
runtime environments that can resolve Railway private networking.

## Start the operations frontend

```powershell
cd frontend
npm install
npm run dev
```

Set `VITE_API_BASE_URL=http://localhost:8080/api/v1` when needed.

## API endpoints

- `GET /api/v1/categories`
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `GET /api/v1/products/category/{categoryId}`
- `GET /api/v1/products/search?query=rice`
- `POST /api/v1/customers`
- `GET /api/v1/customers/{id}/addresses`
- `POST /api/v1/customers/{id}/addresses`
- `PUT /api/v1/addresses/{id}`
- `DELETE /api/v1/addresses/{id}`
- `POST /api/v1/orders`
- `GET /api/v1/orders?customerId={id}`
- `GET /api/v1/orders/{id}`
- `POST /api/v1/orders/{id}/cancel`
- `POST /api/v1/payments/create-order`
- `POST /api/v1/payments/verify`
- `GET /api/v1/payments/{id}`

The backend calculates subtotal, discount, delivery fee, and total from current product records. Flutter sends product IDs and quantities only. Online orders remain `PAYMENT_PENDING` until `/payments/verify` confirms the Razorpay signature server-side.

## Testing

```powershell
cd app
flutter analyze
flutter test
flutter build apk --debug

cd ../backend
mvn clean test

cd ../frontend
npm run build
```

## Production checklist

- Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RAZORPAY_KEY_ID`, and `RAZORPAY_KEY_SECRET` from environment variables.
- Use HTTPS for production API URLs and Razorpay callbacks.
- Keep customer authentication guest-first and enforce mobile OTP only at checkout.
- Restrict CORS origins in production via deployment configuration.
- Keep Flyway migrations as the only schema-change mechanism; Hibernate remains `ddl-auto=validate`.

## Future architecture

The backend has extension points and schema for partners, partner inventory,
delivery partners, and order assignments. Future multi-partner fulfillment can
split a customer order into partner-specific sub-orders without changing the
customer product model. Authentication boundaries are prepared through a guest
customer session abstraction that can be replaced by mobile OTP/Firebase/backend
identity.
