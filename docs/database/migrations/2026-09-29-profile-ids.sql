ALTER TABLE businesses
    ADD COLUMN profile_id VARCHAR(8) NULL;

UPDATE businesses
SET profile_id = CONCAT(
    'BIZ',
    CHAR(65 + MOD(FLOOR(id / 456976), 26)),
    CHAR(65 + MOD(FLOOR(id / 17576), 26)),
    CHAR(65 + MOD(FLOOR(id / 676), 26)),
    CHAR(65 + MOD(FLOOR(id / 26), 26)),
    CHAR(65 + MOD(id, 26))
)
WHERE profile_id IS NULL;

ALTER TABLE businesses
    MODIFY COLUMN profile_id VARCHAR(8) NOT NULL,
    ADD CONSTRAINT uq_businesses_profile_id UNIQUE (profile_id);

ALTER TABLE customer_profiles
    ADD COLUMN profile_id VARCHAR(8) NULL;

UPDATE customer_profiles
SET profile_id = CONCAT(
    'CUS',
    CHAR(65 + MOD(FLOOR(id / 456976), 26)),
    CHAR(65 + MOD(FLOOR(id / 17576), 26)),
    CHAR(65 + MOD(FLOOR(id / 676), 26)),
    CHAR(65 + MOD(FLOOR(id / 26), 26)),
    CHAR(65 + MOD(id, 26))
)
WHERE profile_id IS NULL;

ALTER TABLE customer_profiles
    MODIFY COLUMN profile_id VARCHAR(8) NOT NULL,
    ADD CONSTRAINT uq_customer_profiles_profile_id UNIQUE (profile_id);