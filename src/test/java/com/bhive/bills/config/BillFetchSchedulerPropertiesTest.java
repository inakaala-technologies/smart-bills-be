package com.bhive.bills.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BillFetchSchedulerPropertiesTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void accepts_default_scheduler_configuration() {
        assertTrue(validator.validate(new BillFetchSchedulerProperties()).isEmpty());
    }

    @Test
    void rejects_non_positive_scheduler_values_and_invalid_retry_bounds() {
        BillFetchSchedulerProperties properties = new BillFetchSchedulerProperties();
        properties.setIntervalHours(0);
        properties.setSchedulerDelayMs(0);
        properties.setBatchSize(0);
        properties.setStaleClaimMinutes(0);
        properties.setRetryBaseDelaySeconds(500);
        properties.setRetryMaxDelaySeconds(100);
        properties.setMaxRetries(-1);

        Set<String> violations = validator.validate(properties).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());

        assertTrue(violations.contains(
                "app.bills.fetch.interval-hours must be greater than zero"
        ));
        assertTrue(violations.contains(
                "app.bills.fetch.scheduler-delay-ms must be greater than zero"
        ));
        assertTrue(violations.contains(
                "app.bills.fetch.batch-size must be greater than zero"
        ));
        assertTrue(violations.contains(
                "app.bills.fetch.stale-claim-minutes must be greater than zero"
        ));
        assertTrue(violations.contains(
                "app.bills.fetch.retry-max-delay-seconds must be at least retry-base-delay-seconds"
        ));
        assertTrue(violations.contains(
                "app.bills.fetch.max-retries must not be negative"
        ));
    }

    @Test
    void rejects_non_positive_retry_delay() {
        BillFetchSchedulerProperties properties = new BillFetchSchedulerProperties();
        properties.setRetryBaseDelaySeconds(0);

        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getMessage().equals(
                        "app.bills.fetch.retry-base-delay-seconds must be greater than zero"
                )));
    }

    @Test
    void rejects_unbounded_batch_and_retry_counts() {
        BillFetchSchedulerProperties properties = new BillFetchSchedulerProperties();
        properties.setBatchSize(101);
        properties.setMaxRetries(101);

        Set<String> violations = validator.validate(properties).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());

        assertTrue(violations.contains("app.bills.fetch.batch-size must not exceed 100"));
        assertTrue(violations.contains("app.bills.fetch.max-retries must not exceed 100"));
    }
}
