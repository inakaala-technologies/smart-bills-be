package com.bhive.receipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "external_receipts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_external_receipts_partner_external_id", columnNames = {"partner_id", "external_receipt_id"}),
        @UniqueConstraint(name = "uq_external_receipts_partner_idempotency", columnNames = {"partner_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "idx_external_receipts_user_transaction", columnList = "user_id, transaction_at"),
        @Index(name = "idx_external_receipts_partner", columnList = "partner_id")
    }
)
public class ReceiptRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "partner_id", nullable = false, length = 36)
    private String partnerId;

    @Column(name = "external_receipt_id", nullable = false, length = 100)
    private String externalReceiptId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "receipt_number", nullable = false, length = 100)
    private String receiptNumber;

    @Column(name = "retailer_name", nullable = false, length = 160)
    private String retailerName;

    @Column(name = "outlet_name", nullable = false, length = 160)
    private String outletName;

    @Column(name = "retailer_reference", nullable = false, length = 100)
    private String retailerReference;

    @Column(name = "outlet_reference", nullable = false, length = 100)
    private String outletReference;

    @Column(name = "transaction_at", nullable = false)
    private LocalDateTime transactionAt;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Lob
    @Column(name = "items_json", nullable = false, columnDefinition = "LONGTEXT")
    private String itemsJson;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected ReceiptRecord() {
    }

    public ReceiptRecord(
        Long userId,
        String partnerId,
        String externalReceiptId,
        String idempotencyKey,
        String requestFingerprint,
        String receiptNumber,
        String retailerName,
        String outletName,
        String retailerReference,
        String outletReference,
        LocalDateTime transactionAt,
        String currency,
        BigDecimal totalAmount,
        String itemsJson
    ) {
        this.userId = userId;
        this.partnerId = partnerId;
        this.externalReceiptId = externalReceiptId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.receiptNumber = receiptNumber;
        this.retailerName = retailerName;
        this.outletName = outletName;
        this.retailerReference = retailerReference;
        this.outletReference = outletReference;
        this.transactionAt = transactionAt;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.itemsJson = itemsJson;
    }

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPartnerId() { return partnerId; }
    public String getExternalReceiptId() { return externalReceiptId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getRetailerName() { return retailerName; }
    public String getOutletName() { return outletName; }
    public String getRetailerReference() { return retailerReference; }
    public String getOutletReference() { return outletReference; }
    public LocalDateTime getTransactionAt() { return transactionAt; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getItemsJson() { return itemsJson; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
