ALTER TABLE saved_bills
    MODIFY COLUMN response_format VARCHAR(20) NULL,
    MODIFY COLUMN fetched_at DATETIME(6) NULL,
    ADD COLUMN fetch_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN last_fetch_attempt_at DATETIME(6) NULL,
    ADD COLUMN last_fetch_error VARCHAR(100) NULL;

UPDATE saved_bills
SET last_fetch_attempt_at = fetched_at
WHERE last_fetch_attempt_at IS NULL;
