package com.bhive.customer.controller;

import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/business-customers")
public class BusinessCustomerController {

    private final BusinessCustomerRepository businessCustomerRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipService businessMembershipService;

    public BusinessCustomerController(BusinessCustomerRepository businessCustomerRepository,
                                      CustomerProfileRepository customerProfileRepository,
                                      BusinessRepository businessRepository,
                                      BusinessMembershipService businessMembershipService) {
        this.businessCustomerRepository = businessCustomerRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessRepository = businessRepository;
        this.businessMembershipService = businessMembershipService;
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
    public ResponseEntity<BusinessCustomer> createBusinessCustomer(@RequestBody BusinessCustomer request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || request == null
            || request.getBusinessId() == null || request.getCustomerProfileId() == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean canManageBusiness = businessRepository.findById(request.getBusinessId())
            .map(Business::getTenantId)
            .filter(tenantId::equals)
            .isPresent()
            && businessMembershipService.userHasAccessToBusiness(userId, request.getBusinessId(), tenantId);
        if (!canManageBusiness) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        CustomerProfile profile = customerProfileRepository.findById(request.getCustomerProfileId())
            .filter(customer -> tenantId.equals(customer.getTenantId()) && customer.getUserId() != null)
            .orElse(null);
        if (profile == null) return ResponseEntity.notFound().build();

        return businessCustomerRepository.findByBusinessIdAndCustomerProfileId(request.getBusinessId(), profile.getId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> {
                BusinessCustomer link = new BusinessCustomer();
                link.setBusinessId(request.getBusinessId());
                link.setCustomerProfileId(profile.getId());
                link.setTenantId(tenantId);
                link.setStatus("ACTIVE");
                return ResponseEntity.status(HttpStatus.CREATED).body(businessCustomerRepository.save(link));
            });
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
