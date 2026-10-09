package com.bhive.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration(proxyBeanMethods = false)
public class AwsSecretsManagerConfiguration {

    @Bean(destroyMethod = "close")
    public SecretsManagerClient secretsManagerClient(@Value("${aws.region:}") String configuredRegion) {
        var builder = SecretsManagerClient.builder();
        if (StringUtils.hasText(configuredRegion)) {
            builder.region(Region.of(configuredRegion.trim()));
        }
        return builder.build();
    }
}
