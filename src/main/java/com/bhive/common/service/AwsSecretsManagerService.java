package com.bhive.common.service;

import com.bhive.common.config.DatabaseCredentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

@Service
public class AwsSecretsManagerService {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    public AwsSecretsManagerService(
        SecretsManagerClient secretsManagerClient,
        ObjectMapper objectMapper
    ) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
    }

    public DatabaseCredentials getDatabaseCredentials(String secretId) {
        if (!StringUtils.hasText(secretId)) {
            throw new IllegalArgumentException("The database secret ID must be configured.");
        }

        GetSecretValueResponse response;
        try {
            response = secretsManagerClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretId).build()
            );
        } catch (SdkException exception) {
            throw new SecretRetrievalException(
                "Unable to retrieve database secret '" + secretId + "' from AWS Secrets Manager.",
                exception
            );
        }

        if (response == null || !StringUtils.hasText(response.secretString())) {
            throw new InvalidSecretException(
                "Database secret '" + secretId + "' must contain a JSON secret string."
            );
        }

        JsonNode secretJson;
        try {
            secretJson = objectMapper.readTree(response.secretString());
        } catch (JsonProcessingException exception) {
            throw new InvalidSecretException(
                "Database secret '" + secretId + "' must contain valid JSON.",
                exception
            );
        }

        if (secretJson == null || !secretJson.isObject()) {
            throw new InvalidSecretException(
                "Database secret '" + secretId + "' must be a JSON object."
            );
        }

        String url = requiredText(secretJson, "url", secretId);
        if (!url.startsWith("jdbc:mysql:")) {
            throw new InvalidSecretException(
                "Database secret '" + secretId + "' must contain a MySQL JDBC URL."
            );
        }

        return new DatabaseCredentials(
            url,
            requiredText(secretJson, "username", secretId),
            requiredText(secretJson, "password", secretId)
        );
    }

    private String requiredText(JsonNode secretJson, String field, String secretId) {
        JsonNode value = secretJson.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new InvalidSecretException(
                "Database secret '" + secretId + "' is missing a non-empty '" + field + "' field."
            );
        }
        return value.asText().trim();
    }

    public static final class SecretRetrievalException extends IllegalStateException {

        public SecretRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class InvalidSecretException extends IllegalStateException {

        public InvalidSecretException(String message) {
            super(message);
        }

        public InvalidSecretException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
