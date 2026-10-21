package com.bhive.loyalty.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_rules", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"business_id", "target_type", "target_id"})
})
public class LoyaltyRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "business_id", nullable = false)
    private Long businessId;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private LoyaltyTargetType targetType;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(nullable = false)
    private Integer points;
    @Column(nullable = false)
    private boolean active = true;
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
    public LoyaltyTargetType getTargetType() { return targetType; }
    public void setTargetType(LoyaltyTargetType targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
