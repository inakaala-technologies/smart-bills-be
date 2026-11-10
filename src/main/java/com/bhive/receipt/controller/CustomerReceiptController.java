package com.bhive.receipt.controller;

import com.bhive.common.util.TenantContext;
import com.bhive.receipt.dto.CustomerReceiptResponse;
import com.bhive.receipt.dto.ReceiptDeliveryPreferenceRequest;
import com.bhive.receipt.dto.ReceiptDeliveryPreferenceResponse;
import com.bhive.receipt.service.PartnerReceiptService;
import com.bhive.receipt.service.ReceiptDeliveryPreferenceService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/customer/receipts")
public class CustomerReceiptController {

    private final PartnerReceiptService service;
    private final ReceiptDeliveryPreferenceService preferenceService;

    public CustomerReceiptController(
        PartnerReceiptService service,
        ReceiptDeliveryPreferenceService preferenceService
    ) {
        this.service = service;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerReceiptResponse>> list() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.getCustomerReceipts(TenantContext.getUserId()));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ReceiptDeliveryPreferenceResponse> getPreference() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(preferenceService.getPreference(TenantContext.getUserId(), TenantContext.getTenantId()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ReceiptDeliveryPreferenceResponse> updatePreference(
        @RequestBody ReceiptDeliveryPreferenceRequest request
    ) {
        if (request == null || request.emailEnabled() == null || request.phoneEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and phone receipt preferences are required.");
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(preferenceService.updatePreference(
                TenantContext.getUserId(),
                TenantContext.getTenantId(),
                request.emailEnabled(),
                request.phoneEnabled()
            ));
    }
}
