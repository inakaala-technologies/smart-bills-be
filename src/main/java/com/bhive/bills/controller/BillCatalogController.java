
package com.bhive.bills.controller;

import com.bhive.bills.dto.BillCategoryResponse;
import com.bhive.bills.dto.BillProviderResponse;
import com.bhive.bills.dto.ProviderInputFieldResponse;
import com.bhive.bills.service.BillCatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bills")
public class BillCatalogController {

    private final BillCatalogService catalogService;

    public BillCatalogController(BillCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<BillCategoryResponse> getCategories() {
        return catalogService.getCategories();
    }

    @GetMapping("/providers")
    public List<BillProviderResponse> getProviders(
            @RequestParam(required = false) String category) {
        return catalogService.getProviders(category);
    }

    @GetMapping("/providers/{providerCode}/fields")
    public List<ProviderInputFieldResponse> getProviderFields(
            @PathVariable String providerCode) {
        return catalogService.getProviderFields(providerCode);
    }
}
