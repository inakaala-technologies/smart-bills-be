package com.bhive.billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "appointment_id", unique = true)
    private Long appointmentId;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "gst_amount", precision = 10, scale = 2)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "invoice_discount_amount", nullable = false, precision = 12, scale = 2, columnDefinition = "DECIMAL(12,2) NOT NULL DEFAULT 0.00")
    private BigDecimal invoiceDiscountAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_discount_type", length = 16)
    private InvoiceDiscountType invoiceDiscountType;

    @Column(name = "invoice_discount_value", nullable = false, precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) NOT NULL DEFAULT 0.00")
    private BigDecimal invoiceDiscountValue = BigDecimal.ZERO;

    @Column(name = "loyalty_points_redeemed", nullable = false)
    private Integer loyaltyPointsRedeemed = 0;

    @Column(name = "loyalty_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal loyaltyDiscountAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

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

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getGstAmount() {
        return gstAmount;
    }

    public void setGstAmount(BigDecimal gstAmount) {
        this.gstAmount = gstAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getInvoiceDiscountAmount() {
        return invoiceDiscountAmount;
    }

    public void setInvoiceDiscountAmount(BigDecimal invoiceDiscountAmount) {
        this.invoiceDiscountAmount = invoiceDiscountAmount;
    }

    public InvoiceDiscountType getInvoiceDiscountType() {
        return invoiceDiscountType;
    }

    public void setInvoiceDiscountType(InvoiceDiscountType invoiceDiscountType) {
        this.invoiceDiscountType = invoiceDiscountType;
    }

    public BigDecimal getInvoiceDiscountValue() {
        return invoiceDiscountValue;
    }

    public void setInvoiceDiscountValue(BigDecimal invoiceDiscountValue) {
        this.invoiceDiscountValue = invoiceDiscountValue;
    }

    public Integer getLoyaltyPointsRedeemed() { return loyaltyPointsRedeemed; }
    public void setLoyaltyPointsRedeemed(Integer loyaltyPointsRedeemed) { this.loyaltyPointsRedeemed = loyaltyPointsRedeemed; }
    public BigDecimal getLoyaltyDiscountAmount() { return loyaltyDiscountAmount; }
    public void setLoyaltyDiscountAmount(BigDecimal loyaltyDiscountAmount) { this.loyaltyDiscountAmount = loyaltyDiscountAmount; }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
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
