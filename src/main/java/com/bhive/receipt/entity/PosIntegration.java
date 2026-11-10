package com.bhive.receipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "pos_integrations",
    indexes = @Index(name = "idx_pos_integrations_status", columnList = "status")
)
public class PosIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false, unique = true, length = 36)
    private String partnerId;

    @Column(name = "partner_name", nullable = false, length = 160)
    private String partnerName;

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
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PosIntegration() {
    }

    public PosIntegration(
        String partnerId,
        String partnerName,
        String retailerReference,
        String retailerName,
        String outletReference,
        String outletName,
        String publicKey
    ) {
        this.partnerId = partnerId;
        this.partnerName = partnerName;
        this.retailerReference = retailerReference;
        this.retailerName = retailerName;
        this.outletReference = outletReference;
        this.outletName = outletName;
        this.publicKey = publicKey;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getPartnerId() { return partnerId; }
    public String getPartnerName() { return partnerName; }
    public String getRetailerReference() { return retailerReference; }
    public String getRetailerName() { return retailerName; }
    public String getOutletReference() { return outletReference; }
    public String getOutletName() { return outletName; }
    public String getPublicKey() { return publicKey; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
