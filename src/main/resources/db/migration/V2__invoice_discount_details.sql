ALTER TABLE invoices
    ADD COLUMN invoice_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN invoice_discount_type VARCHAR(16) NULL,
    ADD COLUMN invoice_discount_value DECIMAL(10,2) NOT NULL DEFAULT 0.00;
