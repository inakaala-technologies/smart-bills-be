package com.bhive.receipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "pos_partner_applications",
    indexes = {
        @Index(name = "idx_pos_partner_app_status", columnList = "status"),
        @Index(name = "idx_pos_partner_app_submitted", columnList = "submitted_at")
    }
)
public class PosPartnerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true, length = 36)
    private String applicationId;

    @Column(name = "status_token_hash", nullable = false, length = 64)
    private String statusTokenHash;

    @Column(name = "partner_name", nullable = false, length = 160)
    private String partnerName;

    @Column(name = "contact_name", nullable = false, length = 160)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "retailer_reference", nullable = false, length = 100)
    private String retailerReference;

    @Column(name = "retailer_name", nullable = false, length = 160)
    private String retailerName;

    @Column(name = "outlet_reference", nullable = false, length = 100)
    private String outletReference;

    @Column(name = "outlet_name", nullable = false, length = 160)
    private String outletName;

    @Column(name = "public_key", nullable = false, length = 1024)
    private String publicKey;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "partner_id", unique = true, length = 36)
    private String partnerId;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected PosPartnerApplication() {
    }

    public PosPartnerApplication(
        String applicationId,
        String statusTokenHash,
        String partnerName,
        String contactName,
        String contactEmail,
        String contactPhone,
        String retailerReference,
        String retailerName,
        String outletReference,
        String outletName,
        String publicKey
    ) {
        this.applicationId = applicationId;
        this.statusTokenHash = statusTokenHash;
        this.partnerName = partnerName;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.retailerReference = retailerReference;
        this.retailerName = retailerName;
        this.outletReference = outletReference;
        this.outletName = outletName;
        this.publicKey = publicKey;
    }

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void approve(String partnerId, Long adminUserId, String reason) {
        this.status = "APPROVED";
        this.partnerId = partnerId;
        this.reviewedBy = adminUserId;
        this.reviewedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.decisionReason = reason;
    }

    public void reject(Long adminUserId, String reason) {
        this.status = "REJECTED";
        this.reviewedBy = adminUserId;
        this.reviewedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.decisionReason = reason;
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getStatusTokenHash() { return statusTokenHash; }
    public String getPartnerName() { return partnerName; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getRetailerReference() { return retailerReference; }
    public String getRetailerName() { return retailerName; }
    public String getOutletReference() { return outletReference; }
    public String getOutletName() { return outletName; }
    public String getPublicKey() { return publicKey; }
    public String getStatus() { return status; }
    public String getPartnerId() { return partnerId; }
    public String getDecisionReason() { return decisionReason; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public Long getReviewedBy() { return reviewedBy; }
}
