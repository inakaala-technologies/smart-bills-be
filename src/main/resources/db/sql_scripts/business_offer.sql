-- Added these columns to business_offer table
ALTER TABLE bhive_db.business_offers
    ADD COLUMN attachment_url VARCHAR(2048) NULL,
    ADD COLUMN attachment_name VARCHAR(255) NULL;