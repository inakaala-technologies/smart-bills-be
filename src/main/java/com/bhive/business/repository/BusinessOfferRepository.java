package com.bhive.business.repository;

import com.bhive.business.entity.BusinessOffer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessOfferRepository extends JpaRepository<BusinessOffer, Long> {
    List<BusinessOffer> findByTenantIdAndBusinessIdOrderByCreatedAtDesc(Long tenantId, Long businessId);
    Optional<BusinessOffer> findByIdAndTenantIdAndBusinessId(Long id, Long tenantId, Long businessId);
    List<BusinessOffer> findByBusinessIdInAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
        Collection<Long> businessIds,
        LocalDateTime startsAt,
        LocalDateTime endsAt
    );
}