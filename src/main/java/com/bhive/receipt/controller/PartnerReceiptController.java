package com.bhive.receipt.controller;

import com.bhive.receipt.dto.PartnerReceiptSubmissionResponse;
import com.bhive.receipt.service.PartnerReceiptService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/partner/receipts")
public class PartnerReceiptController {

    private final PartnerReceiptService service;

    public PartnerReceiptController(PartnerReceiptService service) {
        this.service = service;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<PartnerReceiptSubmissionResponse> submit(
        @RequestHeader("X-Partner-Id") String partnerId,
        @RequestHeader("X-Partner-Timestamp") String timestamp,
        @RequestHeader("X-Partner-Nonce") String nonce,
        @RequestHeader("X-Partner-Signature") String signature,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody byte[] rawBody
    ) {
        if (rawBody.length == 0 || rawBody.length > 1_048_576) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Receipt request body must be between 1 byte and 1 MiB.");
        }
        if (partnerId.length() > 36 || timestamp.length() > 64 || nonce.length() > 128
            || signature.length() > 256 || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Partner authentication headers exceed their allowed length.");
        }
        var result = service.submit(partnerId, timestamp, nonce, signature, idempotencyKey, rawBody);
        HttpStatus status = result.genericAcknowledgement()
            ? HttpStatus.ACCEPTED
            : result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(result.response());
    }
}
