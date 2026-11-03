ALTER TABLE saved_bills
    ADD COLUMN next_fetch_at DATETIME(6) NULL,
    ADD COLUMN fetch_retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN fetch_claim_token VARCHAR(36) NULL,
    ADD INDEX idx_saved_bills_fetch_schedule (
        fetch_status,
        next_fetch_at,
        last_fetch_attempt_at,
        id
    );
