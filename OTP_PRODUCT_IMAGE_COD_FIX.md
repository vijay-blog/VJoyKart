# VJoyKart – OTP + Product Photo + COD Admin Notification Fix

## 1. Customer OTP

The previous OTP failure was caused by `OtpService` reading `nexamart.otp-api-key` while the deployment configuration only supplied `TWOFACTOR_API_KEY` under an unused `twofactor` block.

The fixed backend maps:

- `TWOFACTOR_API_KEY` -> `nexamart.otp-api-key`
- `TWOFACTOR_OTP_TEMPLATE` -> `nexamart.otp-template-name`
- `OTP_TTL_SECONDS` -> OTP lifetime (default 300)
- `OTP_DEV_MODE=false` -> production behavior

### Railway variables

Set:

```text
TWOFACTOR_API_KEY=<your real 2Factor API key>
TWOFACTOR_OTP_TEMPLATE=<your approved OTP template name, if your account requires one>
OTP_TTL_SECONDS=300
OTP_DEV_MODE=false
```

Do not put the 2Factor API key in the Android app.

The backend first uses the current 2Factor OTP REST endpoint and falls back to the legacy manual OTP endpoint for older 2Factor accounts.

## 2. Product photo

Admin product creation/edit now has a photo picker.

Flow:

1. Admin chooses JPG/PNG/WebP photo.
2. Android sends the photo as multipart to the backend.
3. Backend stores the binary image in MySQL in `product_images`.
4. Product `imageUrl` is set to:
   `/api/v1/catalog/products/{id}/image`
5. Customer catalog receives this URL.
6. Customer app downloads the photo from the backend.

Maximum image size is 5 MB.

Flyway migration:

```text
V13__product_images.sql
```

## 3. COD order admin notification

When a customer successfully places a COD order:

- An admin notification record is created for every active ADMIN account.
- The notification contains the order number and total amount.
- The admin dashboard now loads the latest notifications.
- The dashboard notification icon is enabled and displays the notifications.

Example:

```text
New COD order
New COD order #123 placed for ₹750.00.
```

## Deployment order

1. Deploy the backend first.
2. Confirm Flyway applies V13 successfully.
3. Set `TWOFACTOR_API_KEY` in Railway.
4. Deploy/restart backend.
5. Build and install the updated Partner/Admin Android app.
6. Test:
   - Checkout -> Send OTP -> SMS arrives -> Verify
   - Admin -> Add Product -> Choose Photo -> Save
   - Customer -> Product -> uploaded photo appears
   - Customer -> COD -> Place Order -> Admin Dashboard -> Notifications
