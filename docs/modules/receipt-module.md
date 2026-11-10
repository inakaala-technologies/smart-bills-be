# Digital Receipt Module

## Purpose

The receipt module will let participating retailers deliver purchase receipts to BHive customers through their POS/billing-software provider. Imported receipts are separate from invoices issued by BHive businesses.

See [POS Provider Digital Receipt Integration](../architecture/pos-provider-digital-receipts.md) for the full architecture and [POS Provider Sender Quickstart](../architecture/pos-provider-sender-quickstart.md) for the signed sender contract and sample.

## Implemented first step: customer receipt-link QR

- `POST /api/v1/customer/receipt-link-tokens` creates a receipt-link token for the authenticated BHive user.
- The customer app renders the token as a QR code in the customer dashboard's **Receipts** tab.
- The customer must explicitly request the code. It expires after five minutes and is intended for one receipt.
- The API response is marked `Cache-Control: no-store`.
- Tokens contain 256 bits of randomness. Only a domain-separated HMAC-SHA-256 hash is stored, and expired token rows are cleaned up after a retention period.
- Token consumption uses a database write lock so a later POS ingestion endpoint can enforce one-time use under concurrent requests.
- The generic idempotency response cache intentionally excludes this endpoint so it cannot retain or replay the raw single-use token.
- POS partners apply through the public onboarding page. BHive validates proof of possession of the submitted Ed25519 key, stores the public key, and provides a random status-access token whose hash is stored.
- Active admins can review, approve, or reject applications in the admin dashboard. Approval creates an active POS integration using the submitted public key; the partner retains and uses the matching private key.

Apply [2026-11-09-receipt-link-tokens.sql](../database/migrations/2026-11-09-receipt-link-tokens.sql), [2026-11-09-pos-integrations.sql](../database/migrations/2026-11-09-pos-integrations.sql), and [2026-11-09-pos-partner-applications.sql](../database/migrations/2026-11-09-pos-partner-applications.sql) before deploying the feature.

## Not implemented yet

Approved integrations can submit signed receipts using Ed25519, a five-minute timestamp window, one-use partner nonces, and durable partner-scoped idempotency. Receipt persistence and QR-token consumption share one database transaction. Customers can list only receipts linked to their authenticated user; the inbox shows receipt and line-item details.

Apply [2026-11-09-external-receipts.sql](../database/migrations/2026-11-09-external-receipts.sql) after the integration and receipt-link migrations. See [POS Provider Sender Quickstart](../architecture/pos-provider-sender-quickstart.md) for the exact signing bytes and API contract.

PDF/image upload, asynchronous delivery/notifications, refunds/voids, and shared distributed rate limiting remain future work.

## Security rules

- Derive the recipient user ID from BHive's authenticated request context; never accept it from the browser or POS provider.
- Never log, persist, or cache the raw QR token beyond returning it to the authenticated customer once.
- The partner API authenticates approved integrations, restricts each integration to its approved retailer/outlet, uses timestamped Ed25519 signatures and persistent nonce/idempotency checks, and does not expose customer account lookup.
- Customer receipt reads must verify ownership for every request.
- Store timestamps in UTC and preserve the original source offset when receipt ingestion is added.
