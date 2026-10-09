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

## Dev deployment
The `dev` and `feature/**` branches use GitHub Actions OIDC and AWS Systems Manager to deploy the backend container to EC2. Direct access uses port `8084` (`http://<EC2-public-ip>:8084`); the deployment does not modify the host's Nginx configuration. See [GitHub Actions Deployment to EC2](docs/architecture/github-actions-ec2-deployment.md) for the required GitHub secrets, AWS roles, EC2 setup, and network access.
