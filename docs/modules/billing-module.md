# Billing Module

## Purpose
Handles products, invoices, and invoice items for a tenant.

## Main entities
- products
- invoices
- invoice_items

## Rules
- invoice numbers are unique per business
- taxes and totals are calculated server-side
- invoice access is tenant scoped
