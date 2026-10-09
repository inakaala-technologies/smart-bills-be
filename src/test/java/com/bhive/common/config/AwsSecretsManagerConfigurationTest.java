package com.bhive.common.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AwsSecretsManagerConfigurationTest {

    @Test
    void buildsSecretsManagerClientWithConfiguredRegion() {
        try (SecretsManagerClient client =
                 new AwsSecretsManagerConfiguration().secretsManagerClient("ap-south-1")) {
            assertEquals("ap-south-1", client.serviceClientConfiguration().region().id());
        }
    }
}
