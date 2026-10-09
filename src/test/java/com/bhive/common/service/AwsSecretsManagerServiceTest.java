package com.bhive.common.service;

import com.bhive.common.config.DatabaseCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsSecretsManagerServiceTest {

    private static final String SECRET_ID = "dev/bhive/db";

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private AwsSecretsManagerService service;

    @BeforeEach
    void setUp() {
        service = new AwsSecretsManagerService(secretsManagerClient, new ObjectMapper());
    }

    @Test
    void loadsMysqlCredentialsFromSecretJson() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(secretResponse("""
                {
                  "url": "jdbc:mysql://db.example:3306/bhive_db",
                  "username": "bhive_app",
                  "password": "test-password"
                }
                """));

        DatabaseCredentials credentials = service.getDatabaseCredentials(SECRET_ID);

        assertEquals("jdbc:mysql://db.example:3306/bhive_db", credentials.url());
        assertEquals("bhive_app", credentials.username());
        assertEquals("test-password", credentials.password());
        ArgumentCaptor<GetSecretValueRequest> requestCaptor =
            ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(secretsManagerClient).getSecretValue(requestCaptor.capture());
        assertEquals(SECRET_ID, requestCaptor.getValue().secretId());
    }

    @Test
    void rejectsMalformedJson() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(secretResponse("{not-json"));

        assertThrows(
            AwsSecretsManagerService.InvalidSecretException.class,
            () -> service.getDatabaseCredentials(SECRET_ID)
        );
    }

    @Test
    void rejectsSecretsMissingRequiredFields() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(secretResponse("""
                {
                  "url": "jdbc:mysql://db.example:3306/bhive_db",
                  "username": "bhive_app"
                }
                """));

        AwsSecretsManagerService.InvalidSecretException exception = assertThrows(
            AwsSecretsManagerService.InvalidSecretException.class,
            () -> service.getDatabaseCredentials(SECRET_ID)
        );

        assertTrue(exception.getMessage().contains("'password'"));
    }

    @Test
    void failsClearlyWhenAwsRetrievalFails() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenThrow(SecretsManagerException.builder().message("Access denied").build());

        AwsSecretsManagerService.SecretRetrievalException exception = assertThrows(
            AwsSecretsManagerService.SecretRetrievalException.class,
            () -> service.getDatabaseCredentials(SECRET_ID)
        );

        assertTrue(exception.getMessage().contains(SECRET_ID));
    }

    @Test
    void rejectsNonMysqlJdbcUrls() {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenReturn(secretResponse("""
                {
                  "url": "jdbc:postgresql://db.example:5432/bhive_db",
                  "username": "bhive_app",
                  "password": "test-password"
                }
                """));

        assertThrows(
            AwsSecretsManagerService.InvalidSecretException.class,
            () -> service.getDatabaseCredentials(SECRET_ID)
        );
    }

    private GetSecretValueResponse secretResponse(String secretJson) {
        return GetSecretValueResponse.builder().secretString(secretJson).build();
    }
}
