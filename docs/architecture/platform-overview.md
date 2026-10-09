# BHive Platform Overview

## Product vision
BHive is a multi-tenant digital business and customer platform for Indian business workflows. The platform allows one customer identity to interact with many businesses while keeping each business isolated.

## Core architecture
- Frontend: React + TypeScript
- Mobile: Capacitor initially
- Backend: Spring Boot + Java
- Database: MySQL hosted on EC2
- Cache: Redis
- Storage: S3 for documents and generated files
- Deployment model: modular monolith

## Multi-tenancy model
- Shared database
- Shared schema
- tenant_id on tenant-owned tables

## Security model
- Authentication answers: Who are you?
- Authorization answers: What are you allowed to do?
- tenant_id must come from authenticated context
- never trust client-supplied tenant values

## Initial modules
- identity
- business
- customer
- billing
- payment
- document
- membership
- appointment
- notification
- reporting
- audit
- subscription
