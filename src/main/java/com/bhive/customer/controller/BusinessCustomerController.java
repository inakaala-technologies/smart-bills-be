package com.bhive.customer.controller;

import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.repository.BusinessCustomerRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/business-customers")
public class BusinessCustomerController {

    private final BusinessCustomerRepository businessCustomerRepository;

    public BusinessCustomerController(BusinessCustomerRepository businessCustomerRepository) {
        this.businessCustomerRepository = businessCustomerRepository;
    }

    @GetMapping
    public List<BusinessCustomer> getAllBusinessCustomers() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return businessCustomerRepository.findAll();
        }
        return businessCustomerRepository.findAll().stream()
            .filter(mapping -> tenantId.equals(mapping.getTenantId()))
            .toList();
    }

    @GetMapping("/business/{businessId}")
    public List<BusinessCustomer> getByBusinessId(@PathVariable Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return businessCustomerRepository.findByBusinessId(businessId);
        }
        return businessCustomerRepository.findByBusinessId(businessId).stream()
            .filter(mapping -> tenantId.equals(mapping.getTenantId()) && "ACTIVE".equalsIgnoreCase(mapping.getStatus()))
            .toList();
    }

    @PostMapping
    public BusinessCustomer createBusinessCustomer(@RequestBody BusinessCustomer businessCustomer) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && businessCustomer.getTenantId() == null) {
            businessCustomer.setTenantId(tenantId);
        }
        return businessCustomerRepository.save(businessCustomer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessCustomer> getById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        return businessCustomerRepository.findById(id)
            .filter(mapping -> tenantId == null || Objects.equals(tenantId, mapping.getTenantId()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
