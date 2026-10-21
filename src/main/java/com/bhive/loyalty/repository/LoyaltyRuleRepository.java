package com.bhive.loyalty.repository;

import com.bhive.loyalty.entity.LoyaltyRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, Long> {
    List<LoyaltyRule> findByTenantIdAndBusinessIdOrderByTargetTypeAscTargetIdAsc(Long tenantId, Long businessId);
    Optional<LoyaltyRule> findByIdAndTenantIdAndBusinessId(Long id, Long tenantId, Long businessId);
    Optional<LoyaltyRule> findByTenantIdAndBusinessIdAndTargetTypeAndTargetId(Long tenantId, Long businessId, com.bhive.loyalty.entity.LoyaltyTargetType targetType, Long targetId);
}
