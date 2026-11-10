package com.bhive.receipt.controller;

import com.bhive.common.util.TenantContext;
import com.bhive.receipt.dto.ReceiptLinkTokenResponse;
import com.bhive.receipt.service.ReceiptLinkTokenService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/receipt-link-tokens")
public class ReceiptLinkTokenController {

    private final ReceiptLinkTokenService receiptLinkTokenService;

    public ReceiptLinkTokenController(ReceiptLinkTokenService receiptLinkTokenService) {
        this.receiptLinkTokenService = receiptLinkTokenService;
    }

    @PostMapping
    public ResponseEntity<ReceiptLinkTokenResponse> createToken() {
        Long userId = TenantContext.getUserId();
        Long tenantId = TenantContext.getTenantId();
        if (userId == null || tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(receiptLinkTokenService.createToken(userId, tenantId));
    }
}
