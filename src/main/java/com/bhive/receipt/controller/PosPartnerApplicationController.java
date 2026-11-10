package com.bhive.receipt.controller;

import com.bhive.receipt.dto.PosPartnerApplicationRequest;
import com.bhive.receipt.dto.PosPartnerApplicationStatusResponse;
import com.bhive.receipt.dto.PosPartnerApplicationSubmissionResponse;
import com.bhive.receipt.service.PosPartnerApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos-partner-applications")
public class PosPartnerApplicationController {

    private final PosPartnerApplicationService service;

    public PosPartnerApplicationController(PosPartnerApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PosPartnerApplicationSubmissionResponse> submit(
        @Valid @RequestBody PosPartnerApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .cacheControl(CacheControl.noStore())
            .body(service.submit(request));
    }

    @GetMapping("/{applicationId}/status")
    public ResponseEntity<PosPartnerApplicationStatusResponse> status(
        @PathVariable String applicationId,
        @RequestHeader(value = "X-Application-Access-Token", required = false) String statusToken
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.getStatus(applicationId, statusToken));
    }
}
