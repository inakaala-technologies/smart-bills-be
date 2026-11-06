CREATE TABLE IF NOT EXISTS customer_bill_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consent_record_id VARCHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    requested_data_fields LONGTEXT NOT NULL,
    purpose_code VARCHAR(60) NOT NULL,
    statement_version VARCHAR(60) NOT NULL,
    statement_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    accepted_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    saved_bill_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_bill_consent_record_id
        UNIQUE (consent_record_id),
    CONSTRAINT fk_bill_consents_customer
        FOREIGN KEY (customer_id) REFERENCES customer_profiles (id),
    CONSTRAINT fk_bill_consents_provider
        FOREIGN KEY (provider_id) REFERENCES bill_providers (id),
    CONSTRAINT fk_bill_consents_saved_bill
        FOREIGN KEY (saved_bill_id) REFERENCES saved_bills (id)
        ON DELETE SET NULL,
    INDEX idx_bill_consents_customer_accepted (customer_id, accepted_at),
    INDEX idx_bill_consents_customer_provider_status
        (customer_id, provider_id, status)
) ENGINE=InnoDB;

ALTER TABLE saved_bills
    ADD COLUMN application_consent_id BIGINT NULL,
    ADD CONSTRAINT uk_saved_bill_application_consent
        UNIQUE (application_consent_id),
    ADD CONSTRAINT fk_saved_bills_application_consent
        FOREIGN KEY (application_consent_id)
        REFERENCES customer_bill_consents (id)
        ON DELETE SET NULL;
