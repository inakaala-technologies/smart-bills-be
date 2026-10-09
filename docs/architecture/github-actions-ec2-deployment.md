# GitHub Actions Deployment to EC2

The `dev` and `feature/**` branches deploy the Spring Boot backend to EC2 using GitHub Actions OIDC, an S3 deployment archive, and AWS Systems Manager Run Command. No AWS access keys are stored in GitHub or passed into the application container.

## GitHub configuration

Create a GitHub environment named `dev`. The workflow currently sets the AWS role ARN, deployment bucket, and EC2 instance ID directly in `.github/workflows/deploy-dev-java.yml`.

The role trust policy should trust GitHub's OIDC provider with audience `sts.amazonaws.com` and restrict the subject to this repository's `dev` environment:
`repo:inakaala-technologies@185234221/smart-bills-be@1369726264:environment:dev`.
The GitHub role needs permission to upload objects under `bhive-backend/dev/*`, run `AWS-RunShellScript` on the selected instance, and read the resulting SSM command invocation. Configure the GitHub `dev` environment's deployment branch rules to permit `dev` and `feature/**`.

## EC2 setup

Before the first deployment, the EC2 instance must:

- Be registered as an online Systems Manager managed node, with the `AmazonSSMManagedInstanceCore` instance profile policy.
- Have Docker Engine and the Docker Compose plugin installed.
- Be able to download the archive from the deployment bucket (`s3:GetObject` scoped to `bhive-backend/dev/*`) and reach AWS Systems Manager. Use suitable VPC endpoints or outbound network access.
- Have its own runtime IAM role granting `secretsmanager:GetSecretValue` for `dev/bhive/db`. Grant `kms:Decrypt` on the customer-managed key if the secret uses one. This EC2 role is distinct from the GitHub OIDC role.
- Allow the application container to access EC2 instance metadata credentials. With IMDSv2, configure the metadata hop limit for container networking as needed; do not pass AWS access keys into Docker Compose.

The workflow builds and tests the JAR, uploads an archive to `bhive-backend/dev/bhive-deployment.tar.gz`, then uses SSM to replace and start the Docker Compose service and verify that its container is running. The application container listens on port 8080 internally and is published as **EC2 port 8084**. The deployment does not install or reconfigure host Nginx. With no DNS or domain, access the application directly at:

```text
http://<EC2-public-ip>:8084
```

Allow inbound TCP 8084 only from the networks that need access; avoid opening it broadly to the internet. Existing host Nginx sites and listeners are left unchanged.
