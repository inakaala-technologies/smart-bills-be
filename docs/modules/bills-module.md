# Bills Module

## Saved bill persistence

`saved_bills` stores successfully fetched electricity and water bills. It references the authenticated user's `CustomerProfile`, the existing `BillProvider`, and the provider's `BillCategory`. Provider inputs are saved as JSON text; JSON responses are stored without removing provider fields, XML is stored as text, and PDF data is stored as raw bytes in a `LONGBLOB`. Normal JSON responses expose only whether a PDF is available, not its bytes.

The uniqueness rule is customer profile + provider + a SHA-256 hash of the mapped consumer identity. Provider codes are uppercased; identity values are trimmed and retain their case. The mappings are:

| Provider | Identity field(s) |
|---|---|
| KSEB | `ConsumerNo` |
| GUVNL | `consno` |
| PSPCL | `PSPCLACCOUNT` |
| JKPDD | `consumerid` |
| NBPDCL | `ConsumerId` |
| BSESDELHI | `ca_number` |
| MPWZ | `consumer_no` |
| UPPCL | `AccountNo`, then `Discom` |
| GOAONLINE | `UDF1`, then `UID` (`UDF2` excluded) |
| KIADB | `RegNum` |
| ULB_UP | `CONNECTIONNO` |

The database unique constraint is the concurrency backstop. Persistence is transactional; if simultaneous first fetches race on the unique key, the losing request reloads and updates the saved row.

## Authentication and ownership

Saved bill operations use the existing `TenantContext`, populated from the verified access-token principal and validated tenant header. The service resolves `CustomerProfile` by `userId + tenantId`. No customer ID is accepted from request data. Reads, PDF downloads, and updates are scoped to that profile; a record owned by another customer is returned as not found.

The existing `POST /api/v1/bills/fetch` endpoint retains its provider response shape. It saves the response after a successful provider call and only for an authenticated customer with a profile.

## APIs

All endpoints require the existing bearer-token authentication.

### Fetch and save a bill

`POST /api/v1/bills/fetch`

```json
{
  "providerCode": "KSEB",
  "providerInputs": {"ConsumerNo": "1111111111111"},
  "consentArtifact": {"consent": {}, "signature": {}}
}
```

The consent artifact above illustrates the JSON shape only; a real request must use a genuine signed artifact. The response retains the existing JSON/XML/PDF provider response format. A subsequent fetch with the same mapped identity updates that saved bill.

### List saved bills

`GET /api/v1/bills/saved?page=0&size=20`

Optional category filter: `?category=ELECTRICITY` or `?category=WATER`. Returns a paged `SavedBillResponse`; PDF bytes are never embedded in the list response.

### Get one saved bill

`GET /api/v1/bills/saved/{id}`

Returns the same DTO for the authenticated customer only.

### Download or preview PDF

`GET /api/v1/bills/saved/{id}/pdf`

Returns raw bytes with the stored upstream Content-Type (or `application/pdf` when none was supplied) and an inline filename. Missing/non-PDF records return 404.

## Database rollout

Apply [`../database/migrations/2026-10-30-saved-bills.sql`](../database/migrations/2026-10-30-saved-bills.sql) before deploying to production. Development/local profiles use Hibernate schema update; production uses `ddl-auto=validate` and therefore requires the table/indexes to be provisioned first.

## Verification

Automated tests cover identity mapping/normalization, composite account identities, optional GOAONLINE fields, inserts, updates, distinct consumer identities, invalid provider responses, category filtering, and customer-scoped record lookup. External API Setu calls are mocked; no sandbox credential or consent is used.
