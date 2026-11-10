package com.bhive.receipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "receipt_partner_nonces",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_receipt_partner_nonce",
        columnNames = {"partner_id", "nonce"}
    ),
    indexes = @Index(name = "idx_receipt_partner_nonce_created", columnList = "created_at")
)
public class ReceiptPartnerNonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false, length = 36)
    private String partnerId;

    @Column(nullable = false, length = 128)
    private String nonce;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReceiptPartnerNonce() {
    }

    public ReceiptPartnerNonce(String partnerId, String nonce) {
        this.partnerId = partnerId;
        this.nonce = nonce;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
