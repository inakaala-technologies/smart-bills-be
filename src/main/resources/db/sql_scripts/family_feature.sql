-- used this query to add the phone field in users table

ALTER TABLE bhive_db.bhive_users
ADD COLUMN phone VARCHAR(30) NULL;

--used this query to create the family id
CREATE TABLE IF NOT EXISTS bhive_db.families (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id)
);

--used this query to store the family members details
CREATE TABLE IF NOT EXISTS bhive_db.family_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    invited_by BIGINT NULL,
    joined_at TIMESTAMP(6) NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_family_members_user (user_id),

    KEY idx_family_members_family_status (family_id, status),

    CONSTRAINT fk_family_members_family
        FOREIGN KEY (family_id)
        REFERENCES families(id)
        ON DELETE CASCADE
);