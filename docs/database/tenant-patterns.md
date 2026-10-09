# Tenant Access Patterns

## Recommended pattern
- Use shared database and shared schema
- Add tenant_id or business_id to each tenant-owned table
- Always include business filter in repository queries

## Good example
invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)

## Bad example
invoiceRepository.findById(invoiceId)

## Rules
- never trust tenantId from the frontend
- resolve tenant from authenticated user context
- enforce tenant-aware access at repository/service boundaries
