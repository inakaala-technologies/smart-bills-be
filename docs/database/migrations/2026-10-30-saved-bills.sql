CREATE TABLE IF NOT EXISTS saved_bills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    consumer_number VARCHAR(255) NOT NULL,
    consumer_identity_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_input_data LONGTEXT NOT NULL,
    response_format VARCHAR(20) NOT NULL,
    content_type VARCHAR(255),
    response_json LONGTEXT,
    response_xml LONGTEXT,
    pdf_data LONGBLOB,
    fetched_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_saved_bill_business_identity
        UNIQUE (customer_id, provider_id, consumer_identity_hash),
    CONSTRAINT fk_saved_bills_customer
        FOREIGN KEY (customer_id) REFERENCES customer_profiles (id),
    CONSTRAINT fk_saved_bills_provider
        FOREIGN KEY (provider_id) REFERENCES bill_providers (id),
    CONSTRAINT fk_saved_bills_category
        FOREIGN KEY (category_id) REFERENCES bill_categories (id),
    INDEX idx_saved_bills_customer_category (customer_id, category_id),
    INDEX idx_saved_bills_consumer_number (consumer_number),
    INDEX idx_saved_bills_provider (provider_id)
) ENGINE=InnoDB;
