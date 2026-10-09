# Database Credentials with AWS Secrets Manager

The application retrieves its MySQL connection details from AWS Secrets Manager while creating the Spring-managed HikariCP datasource. It does not log secret values or use datasource credentials embedded in the `dev` or `prod` profiles.

## Secret format

Store the secret value as a JSON string with these required, non-empty fields:

```json
{
  "url": "jdbc:mysql://<host>:3306/<database>?serverTimezone=UTC",
  "username": "<database-user>",
  "password": "<database-password>"
}
```

The URL must use the MySQL JDBC scheme (`jdbc:mysql:`). Create separate secrets, for example:

- `dev/bhive/db`
- `prod/bhive/db`
- `local/bhive/db`

The active profile selects its secret ID directly from its properties file: `dev/bhive/db`, `prod/bhive/db`, or `local/bhive/db`. Change `app.db.secret-id` in that profile's properties file to use a different ID. A missing secret, invalid JSON, or missing/blank field fails startup; only AWS retrieval failures can activate the explicitly configured local fallback.

## AWS region and credentials

The `dev` profile defaults `aws.region` to `ap-south-1`, matching the documented development setup. If the secret is in a different region, set `AWS_REGION` or override the Spring property `aws.region` in that profile. Production must configure the region containing its secret using `AWS_REGION` or `aws.region`. If neither is set, the AWS SDK region provider chain uses the configured AWS profile/environment.

The AWS SDK default credential provider chain is used; do not configure access keys in application properties:

- In deployments, attach an IAM role to the application workload (EC2 instance profile, ECS task role, EKS workload identity, or Lambda execution role).
- For local development, authenticate using an AWS CLI profile. With IAM Identity Center/SSO, configure the profile with `aws configure sso`, run `aws sso login --profile <profile>`, and set `AWS_PROFILE=<profile>`. Environment credentials are also supported.

Grant the runtime identity `secretsmanager:GetSecretValue` on the specific database secret ARN. If the secret uses a customer-managed KMS key, also grant `kms:Decrypt` for that key. Do not grant broad access to all secrets when a resource-scoped policy is possible.

## Profile behavior

`dev` and `prod` require Secrets Manager retrieval; they do not fall back to plaintext datasource configuration. Set `SPRING_PROFILES_ACTIVE=prod` for production, and ensure the corresponding secret ID and region are configured.

The `local` profile explicitly enables a development-only fallback for an unavailable AWS secret. It uses `DB_URL` (defaults to local MySQL), `DB_USERNAME`, and `DB_PASSWORD`. All three values must be present, and the code refuses the fallback if `prod` is also active. A malformed or incomplete secret never falls back.

Example local setup:

```powershell
$env:AWS_PROFILE = "bhive-dev"
$env:AWS_REGION = "ap-south-1"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_SECRET_ID = "dev/bhive/db"
aws sso login --profile $env:AWS_PROFILE
.\mvnw.cmd spring-boot:run
```

To use the local MySQL fallback instead, activate `local` and set `DB_USERNAME` and `DB_PASSWORD`; AWS secret retrieval is still attempted first.

## Connection pool

HikariCP remains the application datasource. Existing `spring.datasource.hikari.*` settings continue to apply after the secret-provided JDBC URL and credentials are loaded.
