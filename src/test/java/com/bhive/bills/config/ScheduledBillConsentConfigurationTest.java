package com.bhive.bills.config;

import com.bhive.bills.service.ScheduledBillConsentProvider;
import com.bhive.bills.service.ScheduledBillFetch;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledBillConsentConfigurationTest {

    @Test
    void registers_one_fail_closed_default_provider_when_no_real_provider_exists() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ScheduledBillConsentConfiguration.class
                     )) {
            assertEquals(1, context.getBeansOfType(ScheduledBillConsentProvider.class).size());
            ScheduledBillConsentProvider provider =
                    context.getBean(ScheduledBillConsentProvider.class);
            assertInstanceOf(
                    com.bhive.bills.service.UnavailableScheduledBillConsentProvider.class,
                    provider
            );
            assertTrue(provider.findValidConsent(null).isEmpty());
        }
    }

    @Test
    void uses_a_configured_genuine_provider_instead_of_registering_the_default() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ScheduledBillConsentConfiguration.class,
                             GenuineConsentConfiguration.class
                     )) {
            assertEquals(1, context.getBeansOfType(ScheduledBillConsentProvider.class).size());
            assertInstanceOf(
                    GenuineTestConsentProvider.class,
                    context.getBean(ScheduledBillConsentProvider.class)
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GenuineConsentConfiguration {

        @Bean
        ScheduledBillConsentProvider genuineConsentProvider() {
            return new GenuineTestConsentProvider();
        }
    }

    static class GenuineTestConsentProvider implements ScheduledBillConsentProvider {

        @Override
        public Optional<JsonNode> findValidConsent(ScheduledBillFetch scheduledBill) {
            return Optional.empty();
        }
    }
}
