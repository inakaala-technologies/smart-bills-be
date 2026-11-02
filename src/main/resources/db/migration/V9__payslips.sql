ALTER TABLE businesses ADD COLUMN payslips_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE business_employees (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    customer_profile_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    employee_code VARCHAR(40) NULL,
    designation VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    UNIQUE KEY uk_business_employee_profile (business_id, customer_profile_id),
    INDEX idx_business_employees_business (tenant_id, business_id, status)
);

CREATE TABLE payslips (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    pay_month INT NOT NULL,
    pay_year INT NOT NULL,
    note VARCHAR(300) NULL,
    file_url VARCHAR(2048) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    sent_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_payslips_business (tenant_id, business_id, created_at),
    INDEX idx_payslips_recipient (tenant_id, recipient_user_id, created_at)
);
