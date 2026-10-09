CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_tokens_hash (token_hash),
    KEY idx_auth_refresh_tokens_family (family_id),
    KEY idx_auth_refresh_tokens_user (user_id),
    CONSTRAINT fk_auth_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES bhive_users (id)
) ENGINE=InnoDB;