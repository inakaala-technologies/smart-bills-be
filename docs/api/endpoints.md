# API Endpoint Overview

## Auth
- POST /api/v1/auth/login
- POST /api/v1/auth/logout
- POST /api/v1/auth/refresh

## Users
- GET /api/v1/users/me
- GET /api/v1/users/{id}

## Businesses
- POST /api/v1/businesses
- GET /api/v1/businesses/{id}
- PUT /api/v1/businesses/{id}

## Customers
- POST /api/v1/customers/profiles
- GET /api/v1/customers/{businessId}
- POST /api/v1/customers/{businessId}/link

## Billing
- POST /api/v1/billing/products
- GET /api/v1/billing/products
- POST /api/v1/billing/invoices
- GET /api/v1/billing/invoices/{id}

## Payments
- POST /api/v1/payments
- GET /api/v1/payments/{id}
- POST /api/v1/payments/verify

## Documents
- POST /api/v1/documents
- GET /api/v1/documents/{id}

## Memberships
- GET/POST /api/v1/businesses/{businessId}/membership-plans
- PUT /api/v1/businesses/{businessId}/membership-plans/{planId}
- GET /api/v1/businesses/{businessId}/member-search?q=...
- POST /api/v1/businesses/{businessId}/memberships
- POST /api/v1/memberships/{membershipId}/reminders
- GET /api/v1/memberships/my
- GET /api/v1/membership-notifications/my

## In-app Notifications
- GET /api/v1/notifications/my returns the latest 50 notifications and the current user's unread count.
- PATCH /api/v1/notifications/{notificationId}/read marks one notification read.
- PATCH /api/v1/notifications/my/read marks all notifications read.
- Dashboard clients poll the shared API while visible; membership renewal reminders are the first producer. Push delivery can be added behind the notification service later.

## Location Discovery
- GET /api/v1/businesses/nearby?latitude={lat}&longitude={lon}&radiusKm={radius} returns active, mapped businesses ordered by distance (maximum radius 500 km).
- GET /api/v1/businesses/offers/nearby?latitude={lat}&longitude={lon}&radiusKm={radius} returns active, in-date offers from nearby businesses ordered by distance.
- GET/POST /api/v1/businesses/{businessId}/offers lists or publishes offers for a business the current user manages.
- DELETE /api/v1/businesses/{businessId}/offers/{offerId} deactivates an offer.
- PUT /api/v1/customers/my/location updates the signed-in customer's location address and optional coordinate pair.
- POST /api/v1/auth/register accepts `locationAddress` and optional `latitude`/`longitude` for business and customer profiles.
