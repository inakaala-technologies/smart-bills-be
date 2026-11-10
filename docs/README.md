# BHive Documentation

This folder contains the architecture, module design, API contracts, and database guidance for the BHive platform.

## Project goals
- Multi-tenant platform
- Global customer identity
- Business-specific customer relationship
- Strict tenant isolation in backend logic
- Modular monolith first
- Spring Boot backend with React frontend

## Core structure
- architecture/
- api/
- modules/
- database/

See [Location and Business Discovery](modules/location-discovery.md) for GPS registration, customer location updates, nearby business and offer discovery, and required migrations.
See [Loyalty Points](modules/loyalty-module.md) for business-configured point rules, ledger workflows, APIs, and the required schema migration.
See [POS Provider Digital Receipt Integration](architecture/pos-provider-digital-receipts.md) for the architecture, security controls, API plan, delivery phases, and launch gates for receiving retailer receipts through a POS provider.
See [POS Provider Sender Quickstart](architecture/pos-provider-sender-quickstart.md) for a simple end-to-end explanation and sender API integration flow.
See [Digital Receipt Module](modules/receipt-module.md) for partner onboarding, signed receipt submission, customer inbox behavior, and remaining integration milestones.
See [POS Provider Partner Onboarding and Sandbox Testing](architecture/pos-provider-sandbox-testing.md) for partner applications, admin approval, safe key handling, and local integration testing.
