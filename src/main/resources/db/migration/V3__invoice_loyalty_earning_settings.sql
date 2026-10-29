ALTER TABLE loyalty_program_settings
    ADD COLUMN invoice_earning_mode VARCHAR(20) NULL,
    ADD COLUMN invoice_earning_value DECIMAL(10, 2) NULL;
