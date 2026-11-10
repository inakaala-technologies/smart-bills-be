package com.bhive.receipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "receipt_link_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uq_receipt_link_tokens_token_hash", columnNames = "token_hash"),
    indexes = {
        @Index(name = "idx_receipt_link_tokens_user_expiry", columnList = "user_id, expires_at"),
        @Index(name = "idx_receipt_link_tokens_expiry", columnList = "expires_at")
    }
)
public class ReceiptLinkToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    protected ReceiptLinkToken() {
    }

    public ReceiptLinkToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this(userId, null, tokenHash, expiresAt);
    }

    public ReceiptLinkToken(Long userId, Long tenantId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }
}
