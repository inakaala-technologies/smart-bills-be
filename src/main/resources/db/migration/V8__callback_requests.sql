CREATE TABLE callback_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    customer_profile_id BIGINT NULL,
    requester_user_id BIGINT NOT NULL,
    requester_name VARCHAR(160) NOT NULL,
    requester_email VARCHAR(254) NULL,
    requester_phone VARCHAR(30) NULL,
    reason VARCHAR(300) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    INDEX idx_callback_requests_business (tenant_id, business_id, created_at),
    INDEX idx_callback_requests_requester (business_id, requester_user_id, status)
);
