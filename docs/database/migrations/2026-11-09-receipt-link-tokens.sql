CREATE TABLE receipt_link_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_receipt_link_tokens_token_hash (token_hash),
    INDEX idx_receipt_link_tokens_user_expiry (user_id, expires_at),
    INDEX idx_receipt_link_tokens_expiry (expires_at),
    CONSTRAINT fk_receipt_link_tokens_user
        FOREIGN KEY (user_id) REFERENCES bhive_users (id)
        ON DELETE CASCADE
);
