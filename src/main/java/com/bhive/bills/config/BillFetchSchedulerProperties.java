package com.bhive.bills.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@ConfigurationProperties(prefix = "app.bills.fetch")
@Validated
public class BillFetchSchedulerProperties {

    @Min(value = 1, message = "app.bills.fetch.interval-hours must be greater than zero")
    private long intervalHours = 24;
    @Min(value = 1, message = "app.bills.fetch.scheduler-delay-ms must be greater than zero")
    private long schedulerDelayMs = 60_000;
    @Min(value = 1, message = "app.bills.fetch.batch-size must be greater than zero")
    @Max(value = 100, message = "app.bills.fetch.batch-size must not exceed 100")
    private int batchSize = 10;
    @Min(value = 1, message = "app.bills.fetch.stale-claim-minutes must be greater than zero")
    private long staleClaimMinutes = 15;
    @Min(value = 1, message = "app.bills.fetch.retry-base-delay-seconds must be greater than zero")
    private long retryBaseDelaySeconds = 300;
    @Min(value = 1, message = "app.bills.fetch.retry-max-delay-seconds must be greater than zero")
    private long retryMaxDelaySeconds = 21_600;
    @Min(value = 0, message = "app.bills.fetch.max-retries must not be negative")
    @Max(value = 100, message = "app.bills.fetch.max-retries must not exceed 100")
    private int maxRetries = 5;

    @AssertTrue(message = "app.bills.fetch.retry-max-delay-seconds must be at least retry-base-delay-seconds")
    public boolean isRetryMaxDelayAtLeastBaseDelay() {
        return retryMaxDelaySeconds >= retryBaseDelaySeconds;
    }

    public long getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(long intervalHours) {
        this.intervalHours = intervalHours;
    }

    public long getSchedulerDelayMs() {
        return schedulerDelayMs;
    }

    public void setSchedulerDelayMs(long schedulerDelayMs) {
        this.schedulerDelayMs = schedulerDelayMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getStaleClaimMinutes() {
        return staleClaimMinutes;
    }

    public void setStaleClaimMinutes(long staleClaimMinutes) {
        this.staleClaimMinutes = staleClaimMinutes;
    }

    public long getRetryBaseDelaySeconds() {
        return retryBaseDelaySeconds;
    }

    public void setRetryBaseDelaySeconds(long retryBaseDelaySeconds) {
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    public long getRetryMaxDelaySeconds() {
        return retryMaxDelaySeconds;
    }

    public void setRetryMaxDelaySeconds(long retryMaxDelaySeconds) {
        this.retryMaxDelaySeconds = retryMaxDelaySeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
