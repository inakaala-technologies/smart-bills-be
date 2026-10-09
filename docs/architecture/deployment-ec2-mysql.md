# EC2 + MySQL Deployment Plan

## Deployment assumptions
- Spring Boot backend runs on EC2
- MySQL database is hosted on EC2
- the backend is stateless and can scale via load balancer later
- Redis is used for cache and transient state
- S3 is used for file storage and generated PDFs

## Initial setup
- 1 application instance for backend
- 1 MySQL instance
- Redis instance
- S3 bucket for documents and invoice files

## Production readiness later
- load balancer for multiple Spring Boot instances
- DB backup and restore strategy
- monitoring and alerting
- environment-based configuration
- secure secret management

## Dev deployment
The backend's GitHub Actions workflow deploys the dev container to EC2 through OIDC and Systems Manager. It publishes port `8084` directly and leaves the host's Nginx configuration untouched. See [GitHub Actions Deployment to EC2](github-actions-ec2-deployment.md) for setup details.
