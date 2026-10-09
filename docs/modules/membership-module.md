# Membership Module

## Purpose
Manages customer subscriptions, plan validity windows, renewals, and expiry states for businesses such as gyms, salons, clubs, and education providers.

## Main entity
- membership_plans
- memberships
- membership_notifications

## Key fields
- tenant_id: tenant isolation
- business_id: owning business
- customer_id: linked customer or account holder
- plan_name: membership plan label
- price: plan value in currency
- start_date: begin date
- end_date: expiry date
- status: lifecycle state
- plan_id: source plan; plan name and price are snapshotted onto each subscription
- payment_status: manual PAID, PENDING, or FREE record; BHive does not process membership payments

## Typical lifecycle states
- ACTIVE
- PENDING
- EXPIRED
- CANCELLED
- RENEWED

## Business workflows
- Business owners create and maintain plans, enroll an existing BHive user or a local member, and review subscriptions and renewal dates.
- Local member profiles can be claimed when the customer later registers with the same email or phone number.
- Renewal reminders are explicitly sent by the business owner and appear in the customer's in-app membership view. SMS, email, push delivery, automatic schedules, and configurable reminder rules are not connected.
- Run `database/migrations/2026-09-29-membership-module.sql` before deploying this module because production Hibernate validates rather than creates the schema.

## API
- GET/POST `/api/v1/businesses/{businessId}/membership-plans`
- PUT `/api/v1/businesses/{businessId}/membership-plans/{planId}`
- GET `/api/v1/businesses/{businessId}/member-search?q=...`
- POST `/api/v1/businesses/{businessId}/memberships`
- POST `/api/v1/memberships/{membershipId}/reminders`
- GET `/api/v1/memberships/my`
- GET `/api/v1/membership-notifications/my`

## Rules
- end_date must be equal to or after start_date
- tenant and business membership access must be validated before lifecycle changes
- active memberships are considered valid only when tenant and business access are still intact
- keep membership-specific business logic here instead of in the generic document layer
