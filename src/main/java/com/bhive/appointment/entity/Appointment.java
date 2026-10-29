package com.bhive.appointment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "staff_user_id")
    private Long staffUserId;

    @Column(name = "appointment_service_id")
    private Long appointmentServiceId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_price", precision = 12, scale = 2)
    private BigDecimal servicePrice;

    @Column(name = "home_visit", nullable = false)
    private boolean homeVisit;

    @Column(name = "visiting_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal visitingCharge = BigDecimal.ZERO;

    @Column(name = "visit_address", length = 500)
    private String visitAddress;

    @Column(name = "loyalty_points_redeemed", nullable = false)
    private Integer loyaltyPointsRedeemed = 0;

    @Column(name = "loyalty_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal loyaltyDiscountAmount = BigDecimal.ZERO;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer loyaltyPointsToRedeem;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    private String notes;

    @Column(name = "business_note", length = 1000)
    private String businessNote;

    @Column(name = "reminder_24h_sent_at")
    private LocalDateTime reminder24hSentAt;

    @Column(name = "reminder_1h_sent_at")
    private LocalDateTime reminder1hSentAt;

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

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(Long staffUserId) {
        this.staffUserId = staffUserId;
    }

    public Long getAppointmentServiceId() {
        return appointmentServiceId;
    }

    public void setAppointmentServiceId(Long appointmentServiceId) {
        this.appointmentServiceId = appointmentServiceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public BigDecimal getServicePrice() {
        return servicePrice;
    }

    public void setServicePrice(BigDecimal servicePrice) {
        this.servicePrice = servicePrice;
    }

    public boolean isHomeVisit() { return homeVisit; }
    public void setHomeVisit(boolean homeVisit) { this.homeVisit = homeVisit; }
    public BigDecimal getVisitingCharge() { return visitingCharge; }
    public void setVisitingCharge(BigDecimal visitingCharge) { this.visitingCharge = visitingCharge == null ? BigDecimal.ZERO : visitingCharge; }
    public String getVisitAddress() { return visitAddress; }
    public void setVisitAddress(String visitAddress) { this.visitAddress = visitAddress; }

    public Integer getLoyaltyPointsRedeemed() { return loyaltyPointsRedeemed; }
    public void setLoyaltyPointsRedeemed(Integer loyaltyPointsRedeemed) { this.loyaltyPointsRedeemed = loyaltyPointsRedeemed; }
    public BigDecimal getLoyaltyDiscountAmount() { return loyaltyDiscountAmount; }
    public void setLoyaltyDiscountAmount(BigDecimal loyaltyDiscountAmount) { this.loyaltyDiscountAmount = loyaltyDiscountAmount; }
    public Integer getLoyaltyPointsToRedeem() { return loyaltyPointsToRedeem; }
    public void setLoyaltyPointsToRedeem(Integer loyaltyPointsToRedeem) { this.loyaltyPointsToRedeem = loyaltyPointsToRedeem; }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getBusinessNote() {
        return businessNote;
    }

    public void setBusinessNote(String businessNote) {
        this.businessNote = businessNote;
    }

    public LocalDateTime getReminder24hSentAt() {
        return reminder24hSentAt;
    }

    public void setReminder24hSentAt(LocalDateTime reminder24hSentAt) {
        this.reminder24hSentAt = reminder24hSentAt;
    }

    public LocalDateTime getReminder1hSentAt() {
        return reminder1hSentAt;
    }

    public void setReminder1hSentAt(LocalDateTime reminder1hSentAt) {
        this.reminder1hSentAt = reminder1hSentAt;
    }

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
