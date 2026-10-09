# Identity Module

## Purpose
Manages authentication, user identity, roles, and access to multiple businesses.

## Main entities
- users
- roles
- business_users

## Features
- login and logout
- JWT or session-based auth
- user profile management
- business membership mapping
- role-based access control

## Key rules
- one user can belong to multiple businesses
- role is evaluated within the active tenant context
- no trust in client-supplied tenantId
- Business profile IDs use `BIZ` plus five uppercase letters; customer profile IDs use `CUS` plus five uppercase letters. IDs are generated during profile creation and unique within their profile tables.
- Existing deployments must apply `docs/database/migrations/2026-09-29-profile-ids.sql` before starting the application with the updated entity mappings.
