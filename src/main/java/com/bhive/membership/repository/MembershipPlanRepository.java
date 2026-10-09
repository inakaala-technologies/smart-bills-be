package com.bhive.membership.repository;

import com.bhive.membership.entity.MembershipPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    List<MembershipPlan> findByBusinessIdAndTenantIdOrderByNameAsc(Long businessId, Long tenantId);
    Optional<MembershipPlan> findByIdAndBusinessIdAndTenantId(Long id, Long businessId, Long tenantId);
}