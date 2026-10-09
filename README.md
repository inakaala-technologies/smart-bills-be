# BHive Platform Backend

This is the backend starter for the BHive modular monolith.

## Modules
- auth
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
- common

## Run locally
```bash
mvn spring-boot:run
```

## Database
Database credentials are loaded from AWS Secrets Manager. Configure the secret ID, AWS region, and runtime credentials for your active profile. See [Database Credentials with AWS Secrets Manager](docs/architecture/aws-secrets-manager-database.md) for secret JSON format, IAM permissions, local AWS SSO setup, and the development-only local fallback.
