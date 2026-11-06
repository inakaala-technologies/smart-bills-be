package com.bhive.bills.controller;

import com.bhive.bills.dto.CustomerBillConsentResponse;
import com.bhive.bills.dto.RecordCustomerBillConsentRequest;
import com.bhive.bills.service.CustomerBillConsentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bills/consents")
public class CustomerBillConsentController {

    private final CustomerBillConsentService consentService;

    public CustomerBillConsentController(CustomerBillConsentService consentService) {
        this.consentService = consentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerBillConsentResponse recordAcceptance(
            @Valid @RequestBody RecordCustomerBillConsentRequest request
    ) {
        return consentService.recordAcceptance(request);
    }

    @GetMapping
    public Page<CustomerBillConsentResponse> getHistory(
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "acceptedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return consentService.getHistory(providerCode, status, pageable);
    }

    @PostMapping("/{consentRecordId}/revoke")
    public CustomerBillConsentResponse revoke(@PathVariable String consentRecordId) {
        return consentService.revoke(consentRecordId);
    }
}
