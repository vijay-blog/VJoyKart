# VJoyKart customer checkout + mobile OTP

## Customer behavior

- Customers can browse the catalog without signing in.
- No customer login/register screen is shown during normal shopping.
- The cart remains available as a guest.
- Opening checkout asks for a 10-digit Indian mobile number only when the customer is ready to place an order.
- The backend sends a 6-digit OTP and creates/uses the customer account after OTP verification.
- The verified customer JWT is then used for address/order APIs.

## OTP provider

The active customer backend uses 2Factor for SMS OTP delivery.

Production Railway variables:

- `TWO_FACTOR_API_KEY` — required for real SMS delivery.
- `TWO_FACTOR_TEMPLATE_NAME` — optional; use the exact approved template name if your 2Factor account requires it.
- `OTP_TTL_SECONDS` — optional, default `300`.
- `OTP_DEV_MODE` — keep `false` in production.

For local testing only, `OTP_DEV_MODE=true` can be used without an SMS provider. The API returns a development OTP and the Flutter UI displays it. Never enable this in production.

## Important

The catalog remains backend-driven. VJoyKart customer products/categories are read from `/api/v1/catalog/**` and no production hardcoded products are used.
