package com.bhive.business.service;

import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessUserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BusinessMembershipService {

    private final BusinessUserRepository businessUserRepository;

    public BusinessMembershipService(BusinessUserRepository businessUserRepository) {
        this.businessUserRepository = businessUserRepository;
    }

    public boolean userHasAccessToBusiness(Long userId, Long businessId, Long tenantId) {
        if (userId == null || businessId == null || tenantId == null) {
            return false;
        }

        Optional<BusinessUser> membership = businessUserRepository
            .findByTenantIdAndBusinessIdAndUserId(tenantId, businessId, userId);

        return membership.isPresent() && "ACTIVE".equalsIgnoreCase(membership.get().getStatus());
    }
}
