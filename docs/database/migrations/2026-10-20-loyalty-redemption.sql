CREATE TABLE loyalty_program_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    points_per_currency_unit INT NOT NULL,
    max_redemption_percent DECIMAL(5, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_loyalty_settings_tenant_business UNIQUE (tenant_id, business_id),
    CONSTRAINT chk_loyalty_settings_points_rate CHECK (points_per_currency_unit > 0),
    CONSTRAINT chk_loyalty_settings_redemption_cap CHECK (max_redemption_percent > 0 AND max_redemption_percent <= 100)
);

ALTER TABLE invoices
    ADD COLUMN loyalty_points_redeemed INT NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE appointments
    ADD COLUMN loyalty_points_redeemed INT NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE memberships
    ADD COLUMN loyalty_points_redeemed INT NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;
