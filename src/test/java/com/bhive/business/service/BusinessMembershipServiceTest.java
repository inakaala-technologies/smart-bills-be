package com.bhive.business.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.bhive.business.entity.BusinessUser;
import com.bhive.business.entity.BusinessUserRole;
import com.bhive.business.repository.BusinessUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessMembershipServiceTest {

    @Mock
    private BusinessUserRepository businessUserRepository;

    @InjectMocks
    private BusinessMembershipService businessMembershipService;

    @Test
    void shouldAllowAccessWhenUserIsMemberOfBusinessInSameTenant() {
        BusinessUser membership = new BusinessUser();
        membership.setBusinessId(10L);
        membership.setUserId(5L);
        membership.setTenantId(20L);
        membership.setRole(BusinessUserRole.BUSINESS_ADMIN);
        membership.setStatus("ACTIVE");

        when(businessUserRepository.findByTenantIdAndBusinessIdAndUserId(20L, 10L, 5L))
            .thenReturn(Optional.of(membership));

        assertTrue(businessMembershipService.userHasAccessToBusiness(5L, 10L, 20L));
    }

    @Test
    void shouldRejectAccessWhenTenantDoesNotMatch() {
        when(businessUserRepository.findByTenantIdAndBusinessIdAndUserId(20L, 10L, 5L))
            .thenReturn(Optional.empty());

        assertFalse(businessMembershipService.userHasAccessToBusiness(5L, 10L, 20L));
    }
}
