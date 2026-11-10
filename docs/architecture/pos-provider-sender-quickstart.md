# POS Provider Sender Quickstart

This guide describes the implemented partner application, review, and signed receipt submission flow. The POS provider is the sender/integrator; the retailer remains the merchant and owner of each sale.

## Flow at a glance

1. The partner applies at `/partner/onboarding`, generates an Ed25519 keypair, and submits its public key with a proof-of-possession signature.
2. An active BHive admin reviews the application in **Admin dashboard → POS partner applications**.
3. Approval creates an active partner ID bound to the submitted public key. The partner keeps the private key and never sends it to BHive.
4. A BHive customer either generates a five-minute, one-use receipt QR at checkout or uses the email/mobile saved in their customer profile; contact matching is on by default and can be turned off by channel.
5. After a successful sale, the provider signs the exact JSON request bytes and sends them from its server to BHive.
6. BHive verifies the approved key, signature timestamp and nonce, outlet scope, and recipient; receipt persistence and QR-token consumption occur in one transaction.
7. The authenticated customer sees the receipt and items in **Receipts → Your receipt inbox**.

## Apply and get approved

Open the public onboarding page and submit partner name, contact details, retailer/outlet references, and a public key. The browser downloads the PKCS#8 private key. Store it in the provider's server-side secret manager; never commit, email, or log it.

The application proof signature covers the following newline-delimited UTF-8 fields:

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

After review, BHive displays the generated partner ID in the admin panel and partner status page. The partner ID and private key are used for receipt requests. Approval activates the same public key submitted with the application.

## Create the customer link

The customer signs in, opens **Receipts**, and selects **Generate receipt QR**. QR tokens expire after five minutes and may be consumed only once. The POS/provider must pass the opaque token to BHive only after the sale succeeds. It must not be logged, reused, or used to search for a customer.

Customers can receive receipts by email or mobile by default using the details in their BHive customer profile. They can turn either channel off under **Receipts → Receipts by email or mobile**. The POS/provider submits the contact the customer gives at checkout, and BHive matches it to a linked profile only when that channel is enabled. BHive stores only the receipt, not the submitted contact as part of the receipt record.

## Submit a receipt

Endpoint:

```http
POST /api/v1/partner/receipts
Content-Type: application/json
X-Partner-Id: <approved partner ID>
X-Partner-Timestamp: <ISO-8601 UTC instant>
X-Partner-Nonce: <unique base64url random value, at least 16 random bytes>
X-Partner-Signature: <Base64 Ed25519 signature>
Idempotency-Key: <stable key for this sale>
```

The signature covers this UTF-8 header followed by the **exact raw JSON request bytes** (no extra newline is inserted after the final header newline):

```text
BHIVE_POS_RECEIPT_V1
POST
/api/v1/partner/receipts
<partnerId>
<timestamp>
<nonce>
<idempotencyKey>
```

The timestamp must be within five minutes of BHive's clock. A nonce can be used only once per partner. Generate a fresh timestamp, nonce, and signature for each retry; keep the exact JSON body and idempotency key unchanged.

Example request:

```json
{
  "externalReceiptId": "sale-2026-001",
  "receiptNumber": "R-1001",
  "customerLinkToken": "opaque-one-use-token",
  "retailerReference": "retailer-123",
  "outletReference": "outlet-456",
  "transactionAt": "2026-11-09T05:30:00Z",
  "currency": "INR",
  "totalAmount": "1121.00",
  "items": [
    {
      "productName": "Tea",
      "quantity": "2",
      "unitPrice": "500.00",
      "lineTotal": "1000.00"
    },
    {
      "productName": "Tax",
      "quantity": "1",
      "unitPrice": "121.00",
      "lineTotal": "121.00"
    }
  ]
}
```

For contact-based matching, send `customerEmail` or `customerPhone` instead of `customerLinkToken`. Exactly one recipient field is required:

```json
{
  "customerEmail": "customer@example.com"
}
```

Use `"customerPhone": "+919876543210"` when the customer provides a mobile number. BHive matches against the linked customer profile, not the login credentials. A contact submission receives `202 Accepted` only when it resolves to an eligible linked profile with that channel enabled. A well-formed contact with no eligible match (including an opted-out or ambiguous match) receives a generic `422 Unprocessable Entity`; malformed email or phone values receive `400 Bad Request`. Contact matching disabled by server configuration receives `409 Conflict`.

The `202`/`422` distinction reveals whether a submitted contact is eligible for receipt delivery to an authenticated partner. Production contact matching remains disabled until contact verification and a privacy review are complete.

The `retailerReference` and `outletReference` must exactly match the approved integration. The API accepts up to 100 line items and a request body up to 1 MiB. Amounts use at most two decimal places; quantities support up to three.

Minimal Node.js signing example (with a saved PKCS#8 PEM key):

```js
import { readFileSync } from 'node:fs'
import { createPrivateKey, randomBytes, sign } from 'node:crypto'

const partnerId = process.env.BHIVE_PARTNER_ID
const privateKey = createPrivateKey(readFileSync(process.env.BHIVE_PRIVATE_KEY_FILE))
const timestamp = new Date().toISOString()
const nonce = randomBytes(24).toString('base64url')
const idempotencyKey = 'sale-2026-001'
const body = JSON.stringify(receipt)
const signedHeader = [
  'BHIVE_POS_RECEIPT_V1',
  'POST',
  '/api/v1/partner/receipts',
  partnerId,
  timestamp,
  nonce,
  idempotencyKey,
  '',
].join('\n')
const signature = sign(
  null,
  Buffer.concat([Buffer.from(signedHeader, 'utf8'), Buffer.from(body, 'utf8')]),
  privateKey,
).toString('base64')

const response = await fetch(`${process.env.BHIVE_API_URL}/api/v1/partner/receipts`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Partner-Id': partnerId,
    'X-Partner-Timestamp': timestamp,
    'X-Partner-Nonce': nonce,
    'X-Partner-Signature': signature,
    'Idempotency-Key': idempotencyKey,
  },
  body,
})
```

The trailing empty string in the header array ensures a newline immediately before the raw body.

## Responses and retries

- `201 Created`: receipt was stored and linked to the customer.
- `202 Accepted`: receipt was stored for a matched email/mobile recipient; the response does not disclose the customer's identity.
- `400 Bad Request`: invalid headers, JSON, or receipt fields.
- `401 Unauthorized`: partner is not active, signature is invalid, or timestamp is stale.
- `409 Conflict`: nonce replay, contact matching disabled, or any previously used idempotency key/external receipt ID, including an exact retry.
- `413 Payload Too Large`: body exceeds 1 MiB.
- `422 Unprocessable Entity`: receipt-link token is invalid, expired, or already used, or no eligible customer matches the supplied contact.
- `429 Too Many Requests`: respect `Retry-After`.

After a timeout, retry with the same body, external receipt ID, and idempotency key, but a new nonce, timestamp, and signature. A `409` means an earlier request may already have been accepted; reconcile the sale before treating it as a failure. BHive stores a request fingerprint, not the raw QR token or submitted contact. Receipt, token, and nonce updates roll back together on failure.

## Customer inbox and current limits

Customers can list only their own receipts at `GET /api/v1/customer/receipts`; the API derives ownership from the authenticated BHive user and returns up to 100 newest receipts. The UI refreshes the inbox on entry and on demand.
The authenticated customer can read or update contact-based receipt settings at `GET` or `PUT /api/v1/customer/receipts/preferences`. Both channels default to enabled. Send both channel choices to update them, for example `{"emailEnabled":true,"phoneEnabled":false}`.

The development and local profiles allow contact matching against saved customer-profile contacts before OTP verification is implemented. Production explicitly disables unverified contact matching; keep it disabled until the email/SMS verification flow is added.

This first ingestion slice stores receipt item data in the database. PDF/image upload, asynchronous notifications, refunds/voids, and a distributed WAF/shared rate limiter are not included. Before production, deploy behind HTTPS/WAF, configure production database migrations and backups, and complete partner/reconciliation testing.
