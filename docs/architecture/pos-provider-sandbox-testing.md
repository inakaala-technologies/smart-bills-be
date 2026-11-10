# POS Provider Partner Onboarding and Sandbox Testing

This flow lets a POS provider or retailer apply to BHive, receive an admin decision, and use the Ed25519 keypair submitted with the application for later signed API requests. The private key never leaves the partner's control.

## What is available

- Public partner application form at `/partner/onboarding`.
- Browser-generated Ed25519 keys; the partner downloads and keeps the PKCS#8 private key.
- Proof-of-possession signature verification before BHive accepts an application.
- Application status lookup protected by a random access token; only its SHA-256 hash is stored.
- Admin review in **Admin dashboard → POS partner applications**.
- Approval creates an active POS integration with the same public key submitted by the partner. Rejection records a required reason.
- Public application and status responses use `Cache-Control: no-store`; applications allow five immediate submissions per client IP and then one every twelve seconds.

Approved dummy partners can submit signed receipts to the sandbox API. The customer receipt inbox displays accepted receipts.

## 1. Apply the database migrations

Apply the existing [POS integrations migration](../database/migrations/2026-11-09-pos-integrations.sql), [partner applications migration](../database/migrations/2026-11-09-pos-partner-applications.sql), [receipt-link token migration](../database/migrations/2026-11-09-receipt-link-tokens.sql), [external receipt migration](../database/migrations/2026-11-09-external-receipts.sql), [receipt contact preferences migration](../database/migrations/2026-11-09-receipt-contact-preferences.sql), and [default-on upgrade migration](../database/migrations/2026-11-09-receipt-contact-default-on.sql).

## 2. Submit a test partner application

1. Open `http://localhost:5173/partner/onboarding` (or use **Apply to integrate with BHive** on the sign-in page).
2. Enter partner contact and retailer/outlet information.
3. Generate a keypair. Save the downloaded `bhive-pos-ed25519-private-key.pem` outside the repository in a secure location. Do not send it to BHive, commit it, or put it in logs.
4. Submit the application. The browser signs the application fields with the private key; BHive verifies this proof against the submitted public key and creates a **Pending** application.
5. Keep the application status token available in the same browser session. It authorizes status lookup for this application; do not share it.

The signed application uses UTF-8 fields separated by newline characters in this exact order:

```text
BHIVE_POS_ONBOARDING_V1
partnerName
contactName
contactEmail (lowercase)
contactPhone
retailerReference
retailerName
outletReference
outletName
publicKey (Base64 DER/SPKI)
```

## 3. Review as a BHive admin

1. Sign in with an active BHive admin account.
2. Open **Admin dashboard → POS partner applications**.
3. Verify the partner, contact, retailer, outlet, and public-key information using the organization's onboarding process.
4. Approve to create the active integration, or enter a reason and reject.
5. On approval, give the partner its generated `partnerId` and API integration instructions. Never request or distribute its private key.

Decisions are recorded with the reviewer and UTC decision time. Applications cannot be approved or rejected more than once.

## 4. Check application status

The application page polls the status endpoint with the access token returned at submission. The token is stored only in the current browser session. If the browser session is lost, the partner must contact BHive support; the status token is not recoverable from the database.

## 5. Send a test receipt

After admin approval, follow the exact signed request and Node.js example in the [POS Provider Sender Quickstart](pos-provider-sender-quickstart.md). Use a customer-generated QR token or the email/mobile saved in the customer's profile, together with the approved partner ID and the private key downloaded during application. Contact matching is enabled by default; customers can turn off either channel on the Receipts page. The same keypair is used: BHive stores/verifies the public key, and the partner signs with its private key.

For QR submissions, confirm the API returns `201 Created`; for email/mobile submissions, expect `202 Accepted` only when BHive finds an eligible profile with that channel enabled. An unknown, opted-out, or ambiguous contact returns a generic `422 Unprocessable Entity`; malformed contact values return `400 Bad Request`. Sign in as the customer and refresh **Receipts → Your receipt inbox** to confirm delivery. Email/mobile matching is enabled by default for customer profiles; turn off a channel in **Receipts → Receipts by email or mobile** to opt out. Submit the exact contact saved on the profile. Any duplicate idempotency key or external receipt ID returns `409 Conflict`, including an exact retry with a fresh timestamp/nonce/signature. After a timeout, reconcile before treating that conflict as proof the original submission failed.

For local testing, an active admin can also use **Admin menu → Receipt sender test (dev)** at `/dev/receipt-sender`. This page exists only in Vite development builds; it signs the exact JSON text in the browser and sends the request to the configured API URL. Use only a test key. The private key remains in page memory, is not sent to BHive, and is cleared by reloading the page. Enter the raw value decoded from the customer QR in `customerLinkToken`, or use the profile's enabled `customerEmail` or `customerPhone`; provide only one recipient field.

## API endpoints

| Purpose | Endpoint | Access |
|---|---|---|
| Submit signed application | `POST /api/v1/pos-partner-applications` | Public, five immediate requests per IP, then one per twelve seconds |
| Read application status | `GET /api/v1/pos-partner-applications/{applicationId}/status` | Public with `X-Application-Access-Token` |
| List applications | `GET /api/v1/admin/pos-partner-applications` | Authenticated active admin |
| Approve application | `POST /api/v1/admin/pos-partner-applications/{applicationId}/approve` | Authenticated active admin |
| Reject application | `POST /api/v1/admin/pos-partner-applications/{applicationId}/reject` | Authenticated active admin, required reason |
| Submit signed receipt | `POST /api/v1/partner/receipts` | Approved partner key, signature, nonce, timestamp, idempotency key |
| Read customer inbox | `GET /api/v1/customer/receipts` | Authenticated BHive user; ownership derived server-side |
| Read/update email/phone receipt settings | `GET`/`PUT /api/v1/customer/receipts/preferences` | Authenticated BHive user |
