package com.bhive.receipt.repository;

import com.bhive.receipt.entity.PosIntegration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PosIntegrationRepository extends JpaRepository<PosIntegration, Long> {
    Optional<PosIntegration> findByPartnerIdAndStatus(String partnerId, String status);
    boolean existsByPublicKey(String publicKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT integration FROM PosIntegration integration WHERE integration.partnerId = :partnerId AND integration.status = :status")
    Optional<PosIntegration> findByPartnerIdAndStatusForUpdate(
        @Param("partnerId") String partnerId,
        @Param("status") String status
    );
}
