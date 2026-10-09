package com.bhive.business.repository;

import com.bhive.business.entity.BusinessUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessUserRepository extends JpaRepository<BusinessUser, Long> {
    List<BusinessUser> findByBusinessId(Long businessId);
    List<BusinessUser> findByTenantIdAndBusinessIdAndStatusIgnoreCase(Long tenantId, Long businessId, String status);
    List<BusinessUser> findByUserId(Long userId);
    List<BusinessUser> findByUserIdAndTenantId(Long userId, Long tenantId);
    Optional<BusinessUser> findByBusinessIdAndUserId(Long businessId, Long userId);
    List<BusinessUser> findByTenantId(Long tenantId);
    Optional<BusinessUser> findByTenantIdAndBusinessIdAndUserId(Long tenantId, Long businessId, Long userId);
}
