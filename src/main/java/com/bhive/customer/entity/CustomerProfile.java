package com.bhive.customer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "customer_profiles",
    uniqueConstraints = { @UniqueConstraint(columnNames = {"tenant_id", "user_id"}) },
    indexes = {
        @Index(name = "idx_customer_profiles_receipt_email", columnList = "email_normalized, receipt_email_enabled, user_id"),
        @Index(name = "idx_customer_profiles_receipt_phone", columnList = "phone_normalized, receipt_phone_enabled, user_id")
    }
)
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "profile_id", unique = true, length = 8)
    private String profileId;

    private String phone;

    @Column(name = "phone_normalized", length = 30)
    private String phoneNormalized;

    private String email;

    @Column(name = "email_normalized", length = 255)
    private String emailNormalized;

    @Column(name = "receipt_email_enabled", nullable = false)
    private boolean receiptEmailEnabled = true;

    @Column(name = "receipt_phone_enabled", nullable = false)
    private boolean receiptPhoneEnabled = true;

    private String pan;
    private String gstin;

    @Column(name = "location_address", length = 500)
    private String locationAddress;

    private Double latitude;

    private Double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        String normalized = phone == null || phone.isBlank() ? "" : phone.replaceAll("[^0-9]", "");
        this.phoneNormalized = normalized.isEmpty() || normalized.length() > 30 ? null : normalized;
    }

    public String getPhoneNormalized() {
        return phoneNormalized;
    }

    public boolean isReceiptEmailEnabled() {
        return receiptEmailEnabled;
    }

    public void setReceiptEmailEnabled(boolean receiptEmailEnabled) {
        this.receiptEmailEnabled = receiptEmailEnabled;
    }

    public boolean isReceiptPhoneEnabled() {
        return receiptPhoneEnabled;
    }

    public void setReceiptPhoneEnabled(boolean receiptPhoneEnabled) {
        this.receiptPhoneEnabled = receiptPhoneEnabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
        String normalized = email == null || email.isBlank() ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        this.emailNormalized = normalized.isEmpty() || normalized.length() > 255 ? null : normalized;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getLocationAddress() { return locationAddress; }
    public void setLocationAddress(String locationAddress) { this.locationAddress = locationAddress; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
