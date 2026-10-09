package com.bhive.common.config;

import com.bhive.common.service.AwsSecretsManagerService;
import com.bhive.common.service.AwsSecretsManagerService.SecretRetrievalException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseConfigTest {

    private static final String SECRET_ID = "dev/bhive/db";

    @Test
    void buildsHikariDatasourceFromAwsSecret() {
        AwsSecretsManagerService secretsManagerService = mock(AwsSecretsManagerService.class);
        when(secretsManagerService.getDatabaseCredentials(SECRET_ID))
            .thenReturn(new DatabaseCredentials(
                "jdbc:mysql://db.example:3306/bhive_db",
                "bhive_app",
                "secret-password"
            ));
        DatabaseConfig config = new DatabaseConfig(
            secretsManagerService,
            new MockEnvironment().withProperty("spring.profiles.active", "dev"),
            SECRET_ID,
            false,
            "",
            "",
            ""
        );

        try (HikariDataSource dataSource = config.dataSource()) {
            assertEquals("jdbc:mysql://db.example:3306/bhive_db", dataSource.getJdbcUrl());
            assertEquals("bhive_app", dataSource.getUsername());
            assertEquals("secret-password", dataSource.getPassword());
            assertEquals("com.mysql.cj.jdbc.Driver", dataSource.getDriverClassName());
        }
    }

    @Test
    void allowsFallbackOnlyInExplicitLocalProfile() {
        AwsSecretsManagerService secretsManagerService = mock(AwsSecretsManagerService.class);
        when(secretsManagerService.getDatabaseCredentials(SECRET_ID))
            .thenThrow(new SecretRetrievalException("Unavailable", new RuntimeException("offline")));
        DatabaseConfig config = new DatabaseConfig(
            secretsManagerService,
            new MockEnvironment().withProperty("spring.profiles.active", "local"),
            SECRET_ID,
            true,
            "jdbc:mysql://localhost:3306/bhive_db",
            "local_user",
            "local_password"
        );

        try (HikariDataSource dataSource = config.dataSource()) {
            assertEquals("jdbc:mysql://localhost:3306/bhive_db", dataSource.getJdbcUrl());
            assertEquals("local_user", dataSource.getUsername());
        }
    }

    @Test
    void neverAllowsLocalFallbackWhenProductionProfileIsActive() {
        AwsSecretsManagerService secretsManagerService = mock(AwsSecretsManagerService.class);
        when(secretsManagerService.getDatabaseCredentials(SECRET_ID))
            .thenThrow(new SecretRetrievalException("Unavailable", new RuntimeException("offline")));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");
        DatabaseConfig config = new DatabaseConfig(
            secretsManagerService,
            environment,
            SECRET_ID,
            true,
            "jdbc:mysql://localhost:3306/bhive_db",
            "local_user",
            "local_password"
        );

        assertThrows(IllegalStateException.class, config::dataSource);
    }
}
