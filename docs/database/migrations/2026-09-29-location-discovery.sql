ALTER TABLE businesses
    ADD COLUMN latitude DOUBLE NULL,
    ADD COLUMN longitude DOUBLE NULL,
    ADD INDEX idx_business_status_coordinates (status, latitude, longitude);

ALTER TABLE customer_profiles
    ADD COLUMN location_address VARCHAR(500) NULL,
    ADD COLUMN latitude DOUBLE NULL,
    ADD COLUMN longitude DOUBLE NULL;