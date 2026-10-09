package com.bhive.customer.controller;

import com.bhive.common.util.TenantContext;
import com.bhive.common.util.GeoUtils;
import com.bhive.common.util.ProfileIdGenerator;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.dto.CustomerCreateRequest;
import com.bhive.customer.dto.CustomerPageResponse;
import com.bhive.customer.dto.CustomerProfileResponse;
import com.bhive.customer.dto.LocationUpdateRequest;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessCustomerRepository businessCustomerRepository;

    public CustomerController(CustomerProfileRepository customerProfileRepository,
                             BusinessCustomerRepository businessCustomerRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.businessCustomerRepository = businessCustomerRepository;
    }

    @GetMapping
    public CustomerPageResponse getAllCustomers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return CustomerPageResponse.from(Page.empty());
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name").ascending());
        return CustomerPageResponse.from(customerProfileRepository.findByTenantId(tenantId, pageable).map(CustomerProfileResponse::from));
    }

    @GetMapping("/my")
    public List<CustomerProfileResponse> getMyCustomers() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return List.of();
        }

        return customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .map(CustomerProfileResponse::from)
            .map(List::of)
            .orElseGet(List::of);
    }

    @PutMapping("/my/location")
    public ResponseEntity<CustomerProfileResponse> updateMyLocation(@RequestBody LocationUpdateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) return ResponseEntity.status(403).build();
        if (request == null || request.country() == null || request.country().isBlank() || request.country().length() > 80
            || request.region() == null || request.region().isBlank() || request.region().length() > 80
            || request.city() == null || request.city().isBlank() || request.city().length() > 80
            || !GeoUtils.isValidOptionalCoordinates(request.latitude(), request.longitude())) {
            return ResponseEntity.badRequest().build();
        }
        return customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .map(profile -> {
                profile.setLocationAddress(String.join(", ", request.city().trim(), request.region().trim(), request.country().trim()));
                profile.setLatitude(request.latitude());
                profile.setLongitude(request.longitude());
                return ResponseEntity.ok(CustomerProfileResponse.from(customerProfileRepository.save(profile)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/business/{businessId}")
    public CustomerPageResponse getCustomersForBusiness(
        @PathVariable Long businessId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return CustomerPageResponse.from(Page.empty());
        }

        Set<Long> customerIds = businessCustomerRepository.findByTenantIdAndBusinessId(tenantId, businessId).stream()
            .map(BusinessCustomer::getCustomerProfileId)
            .collect(java.util.stream.Collectors.toSet());

        if (customerIds.isEmpty()) return CustomerPageResponse.from(Page.empty());
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name").ascending());
        Page<CustomerProfileResponse> results = customerProfileRepository.findByTenantIdAndIdIn(tenantId, customerIds, pageable)
            .map(CustomerProfileResponse::from);
        return CustomerPageResponse.from(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerProfileResponse> getCustomerById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return ResponseEntity.notFound().build();
        return customerProfileRepository.findById(id)
            .filter(customer -> tenantId.equals(customer.getTenantId()))
            .map(CustomerProfileResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public CustomerPageResponse searchCustomers(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return CustomerPageResponse.from(Page.empty());
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by("name").ascending());
        Page<CustomerProfileResponse> results = (q == null || q.isBlank()
            ? customerProfileRepository.findByTenantId(tenantId, pageable)
            : customerProfileRepository.searchByTenantId(tenantId, q.trim(), pageable))
            .map(CustomerProfileResponse::from);
        return CustomerPageResponse.from(results);
    }

    @PostMapping
    public ResponseEntity<CustomerProfileResponse> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return ResponseEntity.status(403).build();

        Optional<CustomerProfile> existingProfile = Optional.empty();
        if (request.email() != null && !request.email().isBlank()) {
            existingProfile = customerProfileRepository.findFirstByEmailIgnoreCaseAndTenantIdAndUserIdIsNull(request.email().trim(), tenantId);
        }
        if (existingProfile.isEmpty()) {
            existingProfile = customerProfileRepository.findFirstByPhoneAndTenantIdAndUserIdIsNull(request.phone().trim(), tenantId);
        }
        if (existingProfile.isPresent()) {
            return ResponseEntity.ok(CustomerProfileResponse.from(existingProfile.get()));
        }

        CustomerProfile profile = new CustomerProfile();
        profile.setTenantId(tenantId);
        profile.setUserId(null);
        profile.setName(request.name().trim());
        profile.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        profile.setPhone(request.phone().trim());
        profile.setProfileId(ProfileIdGenerator.nextCustomerId(customerProfileRepository::existsByProfileId));
        return ResponseEntity.ok(CustomerProfileResponse.from(customerProfileRepository.save(profile)));
    }
}
