# Database Schema Overview

## Core tables
- users
- roles
- businesses
- business_users
- customer_profiles
- business_customers
- products
- invoices
- invoice_items
- payments
- documents
- membership_plans
- memberships
- membership_notifications
- business location coordinates (latitude, longitude)
- customer location (location_address, latitude, longitude)

## Key constraints
- unique(business_id, invoice_number)
- unique(business_id, customer_profile_id)
- unique(business_id, user_id)
- unique(businesses.profile_id)
- unique(customer_profiles.profile_id)

## Design rule
All business-scoped tables should include tenant_id or business_id and must be filtered by tenant during backend access.

Apply `migrations/2026-09-29-membership-module.sql` before deploying membership management. Existing memberships are preserved; the new `payment_status` column defaults to `PENDING` and `plan_id` remains nullable for legacy rows.

Apply `migrations/2026-09-29-location-discovery.sql` before deploying GPS registration and nearby-business discovery. Existing profile locations remain nullable until users provide an address or coordinates.
