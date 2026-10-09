# Security and Authorization Model

## Authentication
Authentication answers the question: Who are you?

- Login and registration return a signed, 15-minute HS256 bearer token and set a separate 30-day refresh cookie.
- The refresh cookie is `HttpOnly`, `SameSite=Lax`, scoped to `/api/v1/auth`, and `Secure` in production. Refresh tokens are random opaque values; only SHA-256 hashes are persisted.
- `POST /api/v1/auth/refresh` rotates the refresh token. Replaying a revoked token revokes its active token family. `POST /api/v1/auth/logout` revokes the current refresh family and expires the cookie.
- `BHIVE_REFRESH_COOKIE_SAME_SITE` may be set for the deployed topology; cross-site cookie modes require additional CSRF review.
- Protected API requests must send `Authorization: Bearer <accessToken>`.
- Set `BHIVE_JWT_SECRET` to a unique secret of at least 32 bytes in every environment. The development fallback must never be used in production.
- Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; database credentials are not stored in application configuration.
- Apply `src/main/resources/db/migration/V1__create_auth_refresh_tokens.sql` before starting production, where Hibernate validates rather than creates the schema.
- Public registration supports business and customer accounts only. Administrator accounts must be provisioned out of band.
- Legacy plaintext passwords are upgraded to BCrypt after a successful login. New passwords are stored with BCrypt.
- Existing browser sessions without an access token are discarded and must sign in again. The frontend keeps the token in tab-scoped `sessionStorage`, not persistent local storage, and never stores the password.

## Authorization
Authorization answers the question: What are you allowed to do?

## Core principles
- user identity is global
- business membership is tenant-specific
- business A cannot see business B data
- tenant context must be derived from authenticated session or token
- do not trust frontend tenantId values
- role checks must happen in backend business services
- `X-User-Id` is not an identity source; tenant selection is limited to tenant IDs in the verified bearer token

## Request protections
- API requests are rate limited by remote IP; login and registration use a stricter bucket. The in-memory limiter is per application instance and must be replaced with a shared gateway/store for multi-instance deployments.
- Mutating API requests can send an `Idempotency-Key` header. Identical requests replay the stored response for 24 hours; reusing a key with a different request returns `409 Conflict`.
- The frontend creates an idempotency key for each mutation and shares each identical in-flight mutation, preventing rapid duplicate submissions. A same-button click within 600 ms is ignored.
- Idempotency storage is in memory and is not shared across instances or restarts. Use a durable shared store before relying on it for payment-grade exactly-once behavior.
- CORS currently permits the configured local frontend origins. Add the deployed frontend origin before production deployment.
- Session storage is still accessible to scripts running on the origin; XSS prevention remains necessary, and an HttpOnly secure-cookie session would provide stronger token isolation.

## Access pattern
1. User authenticates.
2. Tenant is selected or resolved from context.
3. Role and permission are validated.
4. Business logic runs with tenant-scoped repository filters.
