ALTER TABLE customer_profiles
    MODIFY receipt_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    MODIFY receipt_phone_enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE customer_profiles
SET receipt_email_enabled = TRUE,
    receipt_phone_enabled = TRUE;

UPDATE customer_profiles
SET email_normalized = LOWER(TRIM(email))
WHERE email IS NOT NULL AND TRIM(email) <> '' AND CHAR_LENGTH(TRIM(email)) <= 255;

UPDATE customer_profiles
SET phone_normalized = REGEXP_REPLACE(phone, '[^0-9]', '')
WHERE phone IS NOT NULL AND TRIM(phone) <> ''
  AND CHAR_LENGTH(REGEXP_REPLACE(phone, '[^0-9]', '')) BETWEEN 1 AND 30;
