package com.bhive.bills.config;

import com.bhive.bills.service.ApiSetuConsentArtifactValidator;
import com.bhive.bills.service.UnavailableApiSetuConsentArtifactValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiSetuConsentArtifactConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiSetuConsentArtifactValidator.class)
    public ApiSetuConsentArtifactValidator apiSetuConsentArtifactValidator() {
        return new UnavailableApiSetuConsentArtifactValidator();
    }
}
