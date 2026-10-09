# Customer Module

## Purpose
Manages the global customer identity and the business-specific customer relationship.

## Main entities
- customer_profiles
- business_customers

## Key principle
Customer identity is global, while business relationship is specific to the business.

## Rules
- one customer can be linked to multiple businesses
- each business relationship has its own customer code and business-specific details
- privacy is enforced at business level
