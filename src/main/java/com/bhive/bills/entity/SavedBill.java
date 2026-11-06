package com.bhive.bills.entity;

import com.bhive.customer.entity.CustomerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "saved_bills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saved_bill_business_identity",
                columnNames = {"customer_id", "provider_id", "consumer_identity_hash"}
        ),
        indexes = {
                @Index(name = "uk_saved_bill_application_consent",
                        columnList = "application_consent_id", unique = true),
                @Index(name = "idx_saved_bills_customer_category", columnList = "customer_id,category_id"),
                @Index(name = "idx_saved_bills_consumer_number", columnList = "consumer_number"),
                @Index(name = "idx_saved_bills_provider", columnList = "provider_id")
        }
)
public class SavedBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private BillProvider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private BillCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_consent_id")
    private CustomerBillConsent applicationConsent;

    @Column(name = "consumer_number", nullable = false, length = 255)
    private String consumerNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "consumer_identity_hash",
            nullable = false,
            length = 64,
            columnDefinition = "CHAR(64)"
    )
    private String consumerIdentityHash;

    @Lob
    @Column(name = "provider_input_data", columnDefinition = "LONGTEXT", nullable = false)
    private String providerInputData;

    @Column(name = "response_format", length = 20)
    private String responseFormat;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Lob
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    @Lob
    @Column(name = "response_xml", columnDefinition = "LONGTEXT")
    private String responseXml;

    @Lob
    @Column(name = "pdf_data", columnDefinition = "LONGBLOB")
    private byte[] pdfData;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "fetch_status", nullable = false, length = 20)
    private String fetchStatus = "PENDING";

    @Column(name = "last_fetch_attempt_at")
    private LocalDateTime lastFetchAttemptAt;

    @Column(name = "last_fetch_error", length = 100)
    private String lastFetchError;

    @Column(name = "next_fetch_at")
    private LocalDateTime nextFetchAt;

    @Column(name = "fetch_retry_count", nullable = false)
    private int fetchRetryCount;

    @Column(name = "fetch_claim_token", length = 36)
    private String fetchClaimToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SavedBill() {
    }

    public SavedBill(
            CustomerProfile customer,
            BillProvider provider,
            BillCategory category,
            String consumerNumber,
            String consumerIdentityHash
    ) {
        this.customer = customer;
        this.provider = provider;
        this.category = category;
        this.consumerNumber = consumerNumber;
        this.consumerIdentityHash = consumerIdentityHash;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public BillProvider getProvider() {
        return provider;
    }

    public BillCategory getCategory() {
        return category;
    }

    public CustomerBillConsent getApplicationConsent() {
        return applicationConsent;
    }

    public void setApplicationConsent(CustomerBillConsent applicationConsent) {
        this.applicationConsent = applicationConsent;
    }

    public String getConsumerNumber() {
        return consumerNumber;
    }

    public String getConsumerIdentityHash() {
        return consumerIdentityHash;
    }

    public String getProviderInputData() {
        return providerInputData;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public String getContentType() {
        return contentType;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public String getResponseXml() {
        return responseXml;
    }

    public byte[] getPdfData() {
        return pdfData;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public String getFetchStatus() {
        return fetchStatus;
    }

    public LocalDateTime getLastFetchAttemptAt() {
        return lastFetchAttemptAt;
    }

    public String getLastFetchError() {
        return lastFetchError;
    }

    public LocalDateTime getNextFetchAt() {
        return nextFetchAt;
    }

    public int getFetchRetryCount() {
        return fetchRetryCount;
    }

    public String getFetchClaimToken() {
        return fetchClaimToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateResponse(
            String providerInputData,
            String responseFormat,
            String contentType,
            String responseJson,
            String responseXml,
            byte[] pdfData
    ) {
        this.providerInputData = providerInputData;
        this.responseFormat = responseFormat;
        this.contentType = contentType;
        this.responseJson = responseJson;
        this.responseXml = responseXml;
        this.pdfData = pdfData;
        LocalDateTime now = LocalDateTime.now();
        this.fetchedAt = now;
        this.lastFetchAttemptAt = now;
        this.fetchStatus = "SUCCESS";
        this.lastFetchError = null;
        this.fetchRetryCount = 0;
        this.fetchClaimToken = null;
    }

    public void prepareFetch(String providerInputData) {
        prepareFetch(providerInputData, LocalDateTime.now());
    }

    public void prepareFetch(String providerInputData, LocalDateTime attemptedAt) {
        this.providerInputData = providerInputData;
        this.lastFetchAttemptAt = attemptedAt;
        this.fetchStatus = "IN_PROGRESS";
        this.lastFetchError = null;
        this.fetchClaimToken = null;
    }

    public void markFetchFailed(String errorCode) {
        this.fetchStatus = "FAILED";
        this.lastFetchError = errorCode;
        this.fetchClaimToken = null;
    }

    public void deferForConsent(LocalDateTime retryAt) {
        this.fetchStatus = "FAILED";
        this.lastFetchError = "CONSENT_UNAVAILABLE";
        this.nextFetchAt = retryAt;
        this.fetchClaimToken = null;
    }

    public void scheduleNextFetch(LocalDateTime nextFetchAt) {
        this.nextFetchAt = nextFetchAt;
    }

    public void claimFetch(UUID claimToken, LocalDateTime attemptedAt) {
        this.fetchStatus = "IN_PROGRESS";
        this.lastFetchAttemptAt = attemptedAt;
        this.lastFetchError = null;
        this.fetchClaimToken = claimToken.toString();
    }

    public boolean ownsFetchClaim(UUID claimToken) {
        return claimToken != null && claimToken.toString().equals(fetchClaimToken);
    }

    public int incrementFetchRetryCount() {
        return ++fetchRetryCount;
    }

    public void scheduleRetry(LocalDateTime nextFetchAt, String errorCode) {
        this.nextFetchAt = nextFetchAt;
        this.fetchStatus = "FAILED";
        this.lastFetchError = errorCode;
        this.fetchClaimToken = null;
    }

    public void stopRetrying(String errorCode) {
        this.nextFetchAt = null;
        this.fetchStatus = "FAILED";
        this.lastFetchError = errorCode;
        this.fetchClaimToken = null;
    }
}
