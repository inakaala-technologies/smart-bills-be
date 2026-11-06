package com.bhive.bills.config;

import com.bhive.bills.service.ApiSetuConsentArtifactValidator;
import com.bhive.bills.service.UnavailableApiSetuConsentArtifactValidator;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiSetuConsentArtifactConfigurationTest {

    @Test
    void registers_a_single_fail_closed_validator_when_no_trusted_validator_is_configured() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ApiSetuConsentArtifactConfiguration.class
                     )) {
            assertEquals(1, context.getBeansOfType(ApiSetuConsentArtifactValidator.class).size());
            ApiSetuConsentArtifactValidator validator =
                    context.getBean(ApiSetuConsentArtifactValidator.class);
            assertInstanceOf(UnavailableApiSetuConsentArtifactValidator.class, validator);
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> validator.requireVerifiedArtifact(
                            12L,
                            "KSEB",
                            JsonNodeFactory.instance.objectNode().put("unverified", true)
                    )
            );
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        }
    }
}
