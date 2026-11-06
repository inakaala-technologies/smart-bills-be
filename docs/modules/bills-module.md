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

## Application-level customer consent

`customer_bill_consents` stores an audit record that an authenticated customer accepted the server-published InGyro statement for a manual bill/document request. It stores requested provider field names only, never provider input values or an API Setu artifact. Records are retained when revoked. The current statement is version `1`; its SHA-256 hash is computed server-side from `ApplicationConsentStatement.TEXT`.

The consent endpoints are `POST /api/v1/bills/consents`, `GET /api/v1/bills/consents`, and `POST /api/v1/bills/consents/{consentRecordId}/revoke`. They use the authenticated user and tenant context; no customer ID, record UUID, final status, or timestamp is accepted from the client. Application-level acceptance is not API Setu authorization and does not permit automatic retrieval.

Manual fetches require an `applicationConsentRecordId` in addition to the API Setu `consentArtifact`. The application-consent record must be accepted, unused, owned by the authenticated customer, and match the provider, purpose, current statement version, and exact set of submitted provider field names. In one short database transaction the consent is locked, the saved-bill attempt is prepared, and both records are linked. The consent is single-use; a later manual fetch requires a new acceptance. Revoked, mismatched, cross-customer, or previously linked records are rejected before the adapter is invoked. A provider failure does not erase the consent or its saved-bill linkage.

Application acceptance is not API Setu authorization. Both manual and scheduled fetches pass through `ApiSetuConsentArtifactValidator` before adapter selection. The trusted validator must verify authenticity, scope, validity and expiry; recurring-access permission is checked separately by the scheduled consent provider. The configured default rejects all supplied artifacts with 503 because no trusted acquisition/verification mechanism has been confirmed. Scheduled records are deferred, without consuming retries, when artifact validation is unavailable. No artifact reaches a provider adapter until a trusted validator accepts it.

Apply `../database/migrations/2026-11-04-customer-bill-consents.sql` after the existing bill migrations before deploying this application version. It adds `saved_bills.application_consent_id`, a nullable FK to the consent record, so the saved bill identifies the application consent used for its latest manual fetch. Historical consent rows retain their `saved_bill_id` association.

### Consent API examples

Record customer acceptance (`201 Created`):

```http
POST /api/v1/bills/consents
Authorization: Bearer <access-token>
X-Tenant-Id: <authorized-tenant-id>
Content-Type: application/json
```

```json
{
  "providerCode": "KSEB",
  "requestedDataFields": ["ConsumerNo"],
  "purposeCode": "MANUAL_BILL_FETCH",
  "statementVersion": "1",
  "accepted": true
}
```

The server generates `consentRecordId`, `acceptedAt`, status, and statement hash. The response includes provider metadata, field names, purpose/version, status, timestamps, and optional `savedBillId`; it never includes an API Setu artifact or submitted provider input values.

Retrieve history (`GET /api/v1/bills/consents?providerCode=KSEB&status=ACCEPTED&page=0&size=20`): filters are optional; statuses are `ACCEPTED` or `REVOKED`. Results are scoped to the current customer.

Revoke (`POST /api/v1/bills/consents/{consentRecordId}/revoke`): revocation updates the existing record and retains its acceptance history. A record not owned by the current customer returns 404.

Until a trusted API Setu artifact verifier is configured, `POST /api/v1/bills/fetch` returns 503 before creating/updating a saved-bill fetch attempt, even if an application-consent record is accepted. An application-consent acknowledgement is never used as an API Setu artifact.

## APIs

All endpoints require the existing bearer-token authentication.

### Fetch and save a bill

`POST /api/v1/bills/fetch`

```json
{
  "providerCode": "KSEB",
  "providerInputs": {"ConsumerNo": "1111111111111"},
  "applicationConsentRecordId": "<consent-record-uuid>",
  "consentArtifact": {"consent": {}, "signature": {}}
}
```

`applicationConsentRecordId` is the UUID returned by the consent acceptance endpoint and is required for manual fetch. `consentArtifact` must be a genuine artifact accepted by the configured trusted validator; the example illustrates shape only and is not a usable artifact. The response retains the existing JSON/XML/PDF provider response format. A subsequent fetch with the same mapped identity updates that saved bill and requires a new application-consent record.

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
