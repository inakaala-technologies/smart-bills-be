CREATE TABLE external_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    partner_id VARCHAR(36) NOT NULL,
    external_receipt_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    receipt_number VARCHAR(100) NOT NULL,
    retailer_name VARCHAR(160) NOT NULL,
    outlet_name VARCHAR(160) NOT NULL,
    retailer_reference VARCHAR(100) NOT NULL,
    outlet_reference VARCHAR(100) NOT NULL,
    transaction_at DATETIME(6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount DECIMAL(14, 2) NOT NULL,
    items_json LONGTEXT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_external_receipts_partner_external_id (partner_id, external_receipt_id),
    UNIQUE KEY uq_external_receipts_partner_idempotency (partner_id, idempotency_key),
    KEY idx_external_receipts_user_transaction (user_id, transaction_at),
    KEY idx_external_receipts_partner (partner_id),
    CONSTRAINT fk_external_receipts_user
        FOREIGN KEY (user_id) REFERENCES bhive_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_external_receipts_partner
        FOREIGN KEY (partner_id) REFERENCES pos_integrations (partner_id)
);

CREATE TABLE receipt_partner_nonces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id VARCHAR(36) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_receipt_partner_nonce (partner_id, nonce),
    KEY idx_receipt_partner_nonce_created (created_at),
    CONSTRAINT fk_receipt_partner_nonce_integration
        FOREIGN KEY (partner_id) REFERENCES pos_integrations (partner_id) ON DELETE CASCADE
);
