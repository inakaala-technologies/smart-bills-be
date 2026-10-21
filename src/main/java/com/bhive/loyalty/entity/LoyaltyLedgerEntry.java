package com.bhive.loyalty.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_ledger", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"business_id", "customer_id", "idempotency_key"})
})
public class LoyaltyLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LoyaltyEntryType entryType;
    @Column(nullable = false)
    private Integer points;
    @Column(name = "reference_type", length = 30)
    private String referenceType;
    @Column(name = "reference_id")
    private Long referenceId;
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
    @Column(length = 500)
    private String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LoyaltyEntryType getEntryType() { return entryType; }
    public void setEntryType(LoyaltyEntryType entryType) { this.entryType = entryType; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
