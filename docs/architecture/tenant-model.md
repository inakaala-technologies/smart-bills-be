# Tenant and Business Model

## Tenant concept
A tenant is a business entity with its own users, customers, invoices, products, memberships, and documents.

## Recommended model
- Shared database
- Shared schema
- tenant_id on business-owned tables

## Business rules
- every business-owned record must be tenant scoped
- business A must never access business B data
- tenant scoping must be enforced by backend logic

## Example
- Tenant 1001 -> ABC Electronics
- Tenant 1002 -> Pavan Fitness
- Tenant 1003 -> XYZ Salon
