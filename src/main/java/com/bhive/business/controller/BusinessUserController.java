package com.bhive.business.controller;

import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.common.util.TenantContext;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/business-users")
public class BusinessUserController {

    private final BusinessUserRepository businessUserRepository;

    public BusinessUserController(BusinessUserRepository businessUserRepository) {
        this.businessUserRepository = businessUserRepository;
    }

    @GetMapping
    public List<BusinessUser> getAllBusinessUsers() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return businessUserRepository.findAll();
        }
        return businessUserRepository.findAll().stream()
            .filter(member -> tenantId.equals(member.getTenantId()))
            .toList();
    }

    @GetMapping("/business/{businessId}")
    public List<BusinessUser> getByBusinessId(@PathVariable Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return businessUserRepository.findByBusinessId(businessId);
        }
        return businessUserRepository.findByBusinessId(businessId).stream()
            .filter(member -> tenantId.equals(member.getTenantId()) && "ACTIVE".equalsIgnoreCase(member.getStatus()))
            .toList();
    }

    @PostMapping
    public BusinessUser createBusinessUser(@RequestBody BusinessUser businessUser) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && businessUser.getTenantId() == null) {
            businessUser.setTenantId(tenantId);
        }
        return businessUserRepository.save(businessUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessUser> getById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        return businessUserRepository.findById(id)
            .filter(member -> tenantId == null || Objects.equals(tenantId, member.getTenantId()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
