# Loyalty Points Module

## Purpose
Manages business-specific points rules and an auditable customer points ledger. Points earned at one business are only available for redemption at that same business.

## Database
Apply `database/migrations/2026-10-19-loyalty-points.sql` and `database/migrations/2026-10-20-loyalty-redemption.sql` to the configured database before deploying the corresponding loyalty features to production. Production Hibernate validates the schema and will not create the loyalty tables or redemption columns. Development environments using Hibernate `ddl-auto=update` create/update them at application startup when the database account has DDL privileges.

## Business workflows
- A business configures points for products, appointment services, or membership plans.
- The business must configure both the points-to-currency rate and the maximum percentage redeemable before checkout redemptions are enabled.
- Points are recorded as immutable earn or redemption ledger entries.
- Every transaction requires an idempotency key to prevent duplicate ledger entries.
- Redemption is rejected when the customer's available balance is insufficient.
- Completed appointment services and memberships recorded as paid award points when the corresponding active rule exists.

## API
- GET/POST `/api/v1/businesses/{businessId}/loyalty/rules`
- PUT `/api/v1/businesses/{businessId}/loyalty/rules/{ruleId}`
- GET/PUT `/api/v1/businesses/{businessId}/loyalty/settings`
- GET `/api/v1/businesses/{businessId}/loyalty/balances`
- GET `/api/v1/businesses/{businessId}/loyalty/customers/{customerId}/balance`
- POST `/api/v1/businesses/{businessId}/loyalty/customers/{customerId}/earn`
- POST `/api/v1/businesses/{businessId}/loyalty/customers/{customerId}/earn-for-target`
