ALTER TABLE receipt_link_tokens
    ADD COLUMN tenant_id BIGINT NULL AFTER user_id;
