CREATE TABLE business_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    discount_label VARCHAR(80) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_business_offers_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    INDEX idx_business_offers_business (tenant_id, business_id, active),
    INDEX idx_business_offers_dates (active, starts_at, ends_at)
);