package com.bhive.business.repository;

import com.bhive.business.entity.CallbackRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackRequestRepository extends JpaRepository<CallbackRequest, Long> {

    List<CallbackRequest> findTop200ByTenantIdAndBusinessIdOrderByCreatedAtDesc(Long tenantId, Long businessId);

    Optional<CallbackRequest> findByIdAndTenantIdAndBusinessId(Long id, Long tenantId, Long businessId);

    boolean existsByTenantIdAndBusinessIdAndRequesterUserIdAndStatus(
        Long tenantId, Long businessId, Long requesterUserId, String status);
}
