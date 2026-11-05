CREATE TABLE invoice_line_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity DECIMAL(11,3) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_invoice_line_items_invoice_id (invoice_id),
    CONSTRAINT fk_invoice_line_items_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id)
        ON DELETE CASCADE
);
