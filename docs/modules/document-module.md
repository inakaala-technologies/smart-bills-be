# Document Module

## Purpose
Handles generic document metadata and storage references for invoices, receipts, certificates, memberships, and other business files while keeping domain-specific lifecycle logic in their owning modules.

## Main entity
- documents

## Key fields
- tenant_id: tenant isolation
- business_id: optional business ownership
- customer_id: optional customer linkage
- document_type: enum-based classification
- file_name: original document name
- file_url: persisted storage reference
- status: enum-backed lifecycle state

## Lifecycle states
- ACTIVE
- PENDING_UPLOAD
- ARCHIVED
- DELETED

## Design rules
- Keep domain-specific business data in specialized modules
- Validate tenant and access before save or retrieval
- Store only metadata and file references in the database
- Prefer enum values over free-form strings for status/type tracking

## Storage model
- MySQL stores metadata and references
- Object storage (for example, S3/Blob storage) stores the actual file content
- The app should treat file_url as the authoritative storage pointer
