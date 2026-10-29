package com.bhive.loyalty.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_program_settings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "business_id"})
})
public class LoyaltyProgramSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Column(name = "points_per_currency_unit", nullable = false)
    private Integer pointsPerCurrencyUnit;
    @Column(name = "max_redemption_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxRedemptionPercent;
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_earning_mode", length = 20)
    private InvoiceEarningMode invoiceEarningMode;
    @Column(name = "invoice_earning_value", precision = 10, scale = 2)
    private BigDecimal invoiceEarningValue;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Integer getPointsPerCurrencyUnit() { return pointsPerCurrencyUnit; }
    public void setPointsPerCurrencyUnit(Integer pointsPerCurrencyUnit) { this.pointsPerCurrencyUnit = pointsPerCurrencyUnit; }
    public BigDecimal getMaxRedemptionPercent() { return maxRedemptionPercent; }
    public void setMaxRedemptionPercent(BigDecimal maxRedemptionPercent) { this.maxRedemptionPercent = maxRedemptionPercent; }
    public InvoiceEarningMode getInvoiceEarningMode() { return invoiceEarningMode; }
    public void setInvoiceEarningMode(InvoiceEarningMode invoiceEarningMode) { this.invoiceEarningMode = invoiceEarningMode; }
    public BigDecimal getInvoiceEarningValue() { return invoiceEarningValue; }
    public void setInvoiceEarningValue(BigDecimal invoiceEarningValue) { this.invoiceEarningValue = invoiceEarningValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
