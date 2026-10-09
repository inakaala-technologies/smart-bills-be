package com.bhive.membership.repository;

import com.bhive.membership.entity.Membership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByBusinessId(Long businessId);
    List<Membership> findByTenantId(Long tenantId);
    List<Membership> findByCustomerId(Long customerId);
    List<Membership> findByBusinessIdAndCustomerId(Long businessId, Long customerId);
    List<Membership> findByBusinessIdAndTenantId(Long businessId, Long tenantId);
    Optional<Membership> findByIdAndBusinessIdAndTenantId(Long id, Long businessId, Long tenantId);
}
