
package com.bhive.bills.controller;

import com.bhive.bills.provider.BillProviderRequest;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.bhive.bills.dto.SavedBillResponse;
import com.bhive.bills.dto.SavedBillPdfResponse;
import com.bhive.bills.service.BillProviderIntegrationService;
import com.bhive.bills.service.SavedBillService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bills")
public class BillFetchController {

    private final BillProviderIntegrationService integrationService;
    private final SavedBillService savedBillService;

    public BillFetchController(
            BillProviderIntegrationService integrationService,
            SavedBillService savedBillService
    ) {
        this.integrationService = integrationService;
        this.savedBillService = savedBillService;
    }

    @PostMapping("/fetch")
    public ProviderFetchResponse fetchBill(
            @Valid @RequestBody BillProviderRequest request
    ) {
        return integrationService.fetchBill(request);
    }

    @GetMapping("/saved")
    public Page<SavedBillResponse> getSavedBills(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "fetchedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return savedBillService.getSavedBills(category, pageable);
    }

    @GetMapping("/saved/{id}")
    public SavedBillResponse getSavedBill(@PathVariable Long id) {
        return savedBillService.getSavedBill(id);
    }

    @GetMapping("/saved/{id}/pdf")
    public ResponseEntity<byte[]> getSavedBillPdf(@PathVariable Long id) {
        SavedBillPdfResponse pdf = savedBillService.getSavedBillForPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.data().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("saved-bill-" + id + ".pdf")
                                .build()
                                .toString()
                )
                .body(pdf.data());
    }
}
