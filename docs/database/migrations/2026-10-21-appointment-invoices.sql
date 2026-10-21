ALTER TABLE invoices
    ADD COLUMN appointment_id BIGINT;

ALTER TABLE invoices
    ADD CONSTRAINT uk_invoices_appointment_id UNIQUE (appointment_id);
