# Customer OTP verification fix

Customer OTP verification creates/loads a CUSTOMER account independently from partner/admin accounts.

Important Railway variable:
- `NEXAMART_JWT_SECRET` is the primary JWT secret.
- `JWT_SECRET` is accepted as a fallback.
- Use a random secret of at least 32 UTF-8 bytes.

If `NEXAMART_JWT_SECRET` is missing/too short, the backend now fails at startup with a clear configuration error instead of allowing OTP SMS to work and then returning a generic 500 during verification.

Customer OTP accounts use generated unique username/email values so an existing partner/admin account with the same phone cannot cause a unique-key collision.
