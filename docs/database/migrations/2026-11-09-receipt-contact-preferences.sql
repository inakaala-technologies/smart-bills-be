ALTER TABLE customer_profiles
    ADD COLUMN phone_normalized VARCHAR(30) NULL,
    ADD COLUMN email_normalized VARCHAR(255) NULL,
    ADD COLUMN receipt_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN receipt_phone_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD KEY idx_customer_profiles_receipt_email (email_normalized, receipt_email_enabled, user_id),
    ADD KEY idx_customer_profiles_receipt_phone (phone_normalized, receipt_phone_enabled, user_id);

UPDATE customer_profiles
SET email_normalized = LOWER(TRIM(email))
WHERE email IS NOT NULL AND TRIM(email) <> '' AND CHAR_LENGTH(TRIM(email)) <= 255;

UPDATE customer_profiles
SET phone_normalized = REGEXP_REPLACE(phone, '[^0-9]', '')
WHERE phone IS NOT NULL AND TRIM(phone) <> ''
  AND CHAR_LENGTH(REGEXP_REPLACE(phone, '[^0-9]', '')) BETWEEN 1 AND 30;
