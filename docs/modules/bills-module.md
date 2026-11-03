# Bills Module

## Saved bill persistence

`saved_bills` stores provider inputs before each supported provider request, then stores the response after a successful fetch. It references the authenticated user's `CustomerProfile`, the existing `BillProvider`, and the provider's `BillCategory`. Provider inputs are saved as JSON text; JSON responses are stored without removing provider fields, XML is stored as text, and PDF data is stored as raw bytes in a `LONGBLOB`. `fetch_status`, `last_fetch_attempt_at`, and `last_fetch_error` record fetch state. `next_fetch_at`, `fetch_retry_count`, and `fetch_claim_token` support scheduled polling, bounded retries, and atomic cross-instance claims. A failed retry preserves any previous successful response and `fetched_at`. Normal JSON responses expose only whether a PDF is available, not its bytes.

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

The database unique constraint is the concurrency backstop. Persistence is transactional; if simultaneous first fetches race on the unique key, the losing request reloads the row and rejects the fetch if another attempt is already in progress.

The transport and insurance catalog is documented in
[`api-setu-transport-insurance.md`](./api-setu-transport-insurance.md). It
registers transport challan, four manual document services, transport vehicle
insurance, and the 60 distinct insurer/policy services from the supplied API
Setu specifications. The existing generic table and response columns are used;
no separate registration table or migration is needed.

## Authentication and ownership

Saved bill operations use the existing `TenantContext`, populated from the verified access-token principal and validated tenant header. The service resolves `CustomerProfile` by `userId + tenantId`. No customer ID is accepted from request data. Reads, PDF downloads, and updates are scoped to that profile; a record owned by another customer is returned as not found.

The existing `POST /api/v1/bills/fetch` endpoint retains its provider response shape. After provider/input validation and customer authentication, it saves the complete inputs before making the provider call. Success or failure updates that same row. Manual fetches use a short row lock for preparation and completion, and return a conflict rather than overlap a scheduled or other active fetch. Provider HTTP requests execute outside database transactions. Fetches remain scoped to the authenticated customer profile and tenant.

The scheduled fetch task polls saved ELECTRICITY, WATER, and ECHALLAN rows in bounded batches. It claims each due row in a short database transaction using a claim token, then performs the external request outside that transaction. Stale scheduled claims can be reclaimed after the configured threshold; manual `IN_PROGRESS` rows have no claim token and are never stale-claimed by the scheduler. A later manual request can recover an abandoned manual attempt after that same threshold. Temporary transport/provider failures use capped exponential retry; permanent failures receive no `next_fetch_at`. Missing consent is deferred until the next regular fetch interval without consuming retry attempts. INSURANCE and DOCUMENTS are excluded from scheduler queries.

Scheduled API Setu fetches require a valid consent artifact. The current default `ScheduledBillConsentProvider` intentionally returns no artifact, so scheduled rows are marked `CONSENT_UNAVAILABLE` and deferred without making an upstream request until a genuine consent source is integrated. Scheduled ECHALLAN additionally requires the trusted consent provider to affirmatively validate recurring access; that method defaults to false. API Setu sample consent placeholders do not authorize recurring access. Scheduler properties use the `BILL_FETCH_*` environment variables; interval, polling delay, batch size, stale-claim threshold, and retry delays must be positive, the batch and retry-count limits are capped at 100, and the retry maximum must be at least the retry base.

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

Optional category filter: `?category=ELECTRICITY`, `WATER`, `ECHALLAN`, `INSURANCE`, or `DOCUMENTS`. Returns a paged `SavedBillResponse`; PDF bytes are never embedded in the list response.

### Get one saved bill

`GET /api/v1/bills/saved/{id}`

Returns the same DTO for the authenticated customer only.

### Download or preview PDF

`GET /api/v1/bills/saved/{id}/pdf`

Returns raw bytes with the stored upstream Content-Type (or `application/pdf` when none was supplied) and an inline filename. Missing/non-PDF records return 404.

## Database rollout

Apply [`../database/migrations/2026-10-30-saved-bills.sql`](../database/migrations/2026-10-30-saved-bills.sql), then [`../database/migrations/2026-11-02-saved-bill-fetch-state.sql`](../database/migrations/2026-11-02-saved-bill-fetch-state.sql), then [`../database/migrations/2026-11-02-saved-bill-scheduling.sql`](../database/migrations/2026-11-02-saved-bill-scheduling.sql) before deploying to production. Development/local profiles use Hibernate schema update; production uses `ddl-auto=validate` and therefore requires the table/indexes/columns to be provisioned first.

## Verification

Automated tests cover identity mapping/normalization, composite account identities, optional GOAONLINE fields, inserts, updates, distinct consumer identities, invalid provider responses, category filtering, and customer-scoped record lookup. External API Setu calls are mocked; no sandbox credential or consent is used.
