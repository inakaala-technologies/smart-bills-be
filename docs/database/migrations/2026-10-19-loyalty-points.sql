CREATE TABLE loyalty_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    points INT NOT NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_loyalty_rule_target UNIQUE (business_id, target_type, target_id),
    CONSTRAINT chk_loyalty_rule_target_type CHECK (target_type IN ('PRODUCT', 'SERVICE', 'MEMBERSHIP')),
    CONSTRAINT chk_loyalty_rule_points CHECK (points > 0),
    INDEX idx_loyalty_rule_business (tenant_id, business_id, active)
);

CREATE TABLE loyalty_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    points INT NOT NULL,
    reference_type VARCHAR(30) NULL,
    reference_id BIGINT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_loyalty_ledger_idempotency UNIQUE (business_id, customer_id, idempotency_key),
    CONSTRAINT chk_loyalty_ledger_entry_type CHECK (entry_type IN ('EARN', 'REDEEM', 'ADJUST')),
    CONSTRAINT chk_loyalty_ledger_points CHECK (points > 0),
    INDEX idx_loyalty_ledger_balance (tenant_id, business_id, customer_id, created_at),
    INDEX idx_loyalty_ledger_reference (business_id, reference_type, reference_id)
);
