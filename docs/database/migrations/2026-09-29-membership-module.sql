CREATE TABLE membership_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    price DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    billing_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    duration_months INT NOT NULL DEFAULT 1,
    benefits VARCHAR(2000) NULL,
    max_members INT NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_membership_plan_business_name UNIQUE (business_id, name),
    INDEX idx_membership_plan_tenant_business (tenant_id, business_id)
);

ALTER TABLE memberships
    ADD COLUMN plan_id BIGINT NULL,
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD INDEX idx_membership_business_end_date (business_id, end_date),
    ADD INDEX idx_membership_customer (customer_id);

CREATE TABLE membership_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL DEFAULT 'RENEWAL_REMINDER',
    message VARCHAR(1000) NOT NULL,
    sent_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_membership_notification_customer (tenant_id, customer_id, sent_at),
    INDEX idx_membership_notification_membership (business_id, membership_id, sent_at)
);