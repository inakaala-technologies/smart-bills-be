package com.bhive.billing.dto;

import com.bhive.billing.entity.InvoiceLineItem;
import java.math.BigDecimal;

public record InvoiceLineItemView(
    Long id,
    String productName,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal gstRate
) {
    public static InvoiceLineItemView from(InvoiceLineItem item) {
        return new InvoiceLineItemView(
            item.getId(),
            item.getProductName(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getGstRate()
        );
    }
}
