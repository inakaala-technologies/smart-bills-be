package com.bhive.loyalty.repository;

import com.bhive.loyalty.entity.LoyaltyProgramSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyProgramSettingsRepository extends JpaRepository<LoyaltyProgramSettings, Long> {
    Optional<LoyaltyProgramSettings> findByTenantIdAndBusinessId(Long tenantId, Long businessId);
}
