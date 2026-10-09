# Payment Module

## Purpose
Records customer payments and verifies payment success server-side before finalizing financial records.

## Main entity
- payments

## Key rules
- never trust the frontend payment status
- always verify payment success via backend provider confirmation
- payment must belong to a valid invoice and customer relationship
- partial payments must be tracked against invoice totals

## Supported methods
- UPI
- CARD
- CASH
- BANK_TRANSFER
- NET_BANKING
- OTHER
