package com.bhive.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "callback_requests", indexes = {
    @Index(name = "idx_callback_requests_business", columnList = "tenant_id,business_id,created_at"),
    @Index(name = "idx_callback_requests_requester", columnList = "business_id,requester_user_id,status")
})
public class CallbackRequest {

    public static final String PENDING = "PENDING";
    public static final String CONTACTED = "CONTACTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_profile_id")
    private Long customerProfileId;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Column(name = "requester_name", nullable = false, length = 160)
    private String requesterName;

    @Column(name = "requester_email", length = 254)
    private String requesterEmail;

    @Column(name = "requester_phone", length = 30)
    private String requesterPhone;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Long getCustomerProfileId() { return customerProfileId; }
    public void setCustomerProfileId(Long customerProfileId) { this.customerProfileId = customerProfileId; }
    public Long getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(Long requesterUserId) { this.requesterUserId = requesterUserId; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
    public String getRequesterPhone() { return requesterPhone; }
    public void setRequesterPhone(String requesterPhone) { this.requesterPhone = requesterPhone; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
