CREATE TABLE app_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    target_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    read_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_app_notifications_recipient_created (tenant_id, user_id, created_at),
    INDEX idx_app_notifications_recipient_read (tenant_id, user_id, read_at)
);