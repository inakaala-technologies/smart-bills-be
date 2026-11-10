CREATE TABLE pos_integrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id VARCHAR(36) NOT NULL,
    partner_name VARCHAR(160) NOT NULL,
    retailer_reference VARCHAR(100) NOT NULL,
    retailer_name VARCHAR(160) NOT NULL,
    outlet_reference VARCHAR(100) NOT NULL,
    outlet_name VARCHAR(160) NOT NULL,
    public_key VARCHAR(1024) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pos_integrations_partner_id (partner_id),
    INDEX idx_pos_integrations_status (status)
);
