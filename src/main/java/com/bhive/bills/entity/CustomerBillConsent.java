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
        name = "customer_bill_consents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_bill_consent_record_id",
                columnNames = "consent_record_id"
        ),
        indexes = {
                @Index(name = "idx_bill_consents_customer_accepted",
                        columnList = "customer_id, accepted_at"),
                @Index(name = "idx_bill_consents_customer_provider_status",
                        columnList = "customer_id, provider_id, status")
        }
)
public class CustomerBillConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consent_record_id", nullable = false, length = 36)
    private String consentRecordId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private BillProvider provider;

    @Lob
    @Column(name = "requested_data_fields", columnDefinition = "LONGTEXT", nullable = false)
    private String requestedDataFields;

    @Column(name = "purpose_code", nullable = false, length = 60)
    private String purposeCode;

    @Column(name = "statement_version", nullable = false, length = 60)
    private String statementVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "statement_hash",
            nullable = false,
            length = 64,
            columnDefinition = "CHAR(64)"
    )
    private String statementHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_bill_id")
    private SavedBill savedBill;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CustomerBillConsent() {
    }

    public CustomerBillConsent(
            CustomerProfile customer,
            BillProvider provider,
            String requestedDataFields,
            String purposeCode,
            String statementVersion,
            String statementHash,
            LocalDateTime acceptedAt,
            SavedBill savedBill
    ) {
        this.consentRecordId = UUID.randomUUID().toString();
        this.customer = customer;
        this.provider = provider;
        this.requestedDataFields = requestedDataFields;
        this.purposeCode = purposeCode;
        this.statementVersion = statementVersion;
        this.statementHash = statementHash;
        this.status = "ACCEPTED";
        this.acceptedAt = acceptedAt;
        this.savedBill = savedBill;
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

    public void revoke(LocalDateTime revokedAt) {
        if (!"REVOKED".equals(status)) {
            status = "REVOKED";
            this.revokedAt = revokedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getConsentRecordId() {
        return consentRecordId;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public BillProvider getProvider() {
        return provider;
    }

    public String getRequestedDataFields() {
        return requestedDataFields;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public String getStatementVersion() {
        return statementVersion;
    }

    public String getStatementHash() {
        return statementHash;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public SavedBill getSavedBill() {
        return savedBill;
    }

    public void linkToSavedBill(SavedBill savedBill) {
        if (!"ACCEPTED".equals(status) || this.savedBill != null) {
            throw new IllegalStateException("Consent is not available for a new fetch.");
        }
        this.savedBill = savedBill;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
