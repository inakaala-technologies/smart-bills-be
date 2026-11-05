# Billing Module

## Purpose
Handles products, invoices, and invoice items for a tenant.

## Main entities
- products
- invoices
- invoice_line_items

## Rules
- invoice numbers are unique per business
- submitted line-item subtotal and GST are validated against the invoice totals
- invoice access is tenant scoped
- line items preserve the product name, quantity, unit price, and GST rate charged when the invoice was issued
- production deployments must apply `docs/database/migrations/2026-11-05-invoice-line-items.sql` before starting the service
- invoices created before line-item capture was added have no recoverable product details
