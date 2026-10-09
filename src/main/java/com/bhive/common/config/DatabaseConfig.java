package com.bhive.common.config;

import com.bhive.common.service.AwsSecretsManagerService;
import com.bhive.common.service.AwsSecretsManagerService.SecretRetrievalException;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private final AwsSecretsManagerService secretsManagerService;
    private final Environment environment;
    private final String secretId;
    private final boolean localFallbackEnabled;
    private final String fallbackUrl;
    private final String fallbackUsername;
    private final String fallbackPassword;

    public DatabaseConfig(
        AwsSecretsManagerService secretsManagerService,
        Environment environment,
        @Value("${app.db.secret-id:}") String secretId,
        @Value("${app.db.local-fallback-enabled:false}") boolean localFallbackEnabled,
        @Value("${spring.datasource.url:}") String fallbackUrl,
        @Value("${spring.datasource.username:}") String fallbackUsername,
        @Value("${spring.datasource.password:}") String fallbackPassword
    ) {
        this.secretsManagerService = secretsManagerService;
        this.environment = environment;
        this.secretId = secretId;
        this.localFallbackEnabled = localFallbackEnabled;
        this.fallbackUrl = fallbackUrl;
        this.fallbackUsername = fallbackUsername;
        this.fallbackPassword = fallbackPassword;
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource() {
        if (!StringUtils.hasText(secretId)) {
            throw new IllegalStateException(
                "Property 'app.db.secret-id' must identify the AWS Secrets Manager database secret."
            );
        }

        try {
            return createDataSource(secretsManagerService.getDatabaseCredentials(secretId));
        } catch (SecretRetrievalException exception) {
            if (canUseLocalFallback()) {
                LOGGER.warn(
                    "Unable to retrieve database secret '{}'; using the explicitly enabled local-only datasource fallback.",
                    secretId
                );
                return createDataSource(
                    new DatabaseCredentials(fallbackUrl, fallbackUsername, fallbackPassword)
                );
            }

            throw new IllegalStateException(
                "Unable to load database credentials from AWS Secrets Manager secret '"
                    + secretId
                    + "'. Verify the secret, AWS region, and runtime IAM permissions.",
                exception
            );
        }
    }

    private boolean canUseLocalFallback() {
        boolean localProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("local");
        boolean productionProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        return localFallbackEnabled
            && localProfileActive
            && !productionProfileActive
            && StringUtils.hasText(fallbackUrl)
            && StringUtils.hasText(fallbackUsername)
            && StringUtils.hasText(fallbackPassword);
    }

    private HikariDataSource createDataSource(DatabaseCredentials credentials) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(credentials.url());
        dataSource.setUsername(credentials.username());
        dataSource.setPassword(credentials.password());
        dataSource.setDriverClassName(MYSQL_DRIVER);
        return dataSource;
    }
}
