package com.bhive.receipt.repository;

import com.bhive.receipt.entity.PosPartnerApplication;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PosPartnerApplicationRepository extends JpaRepository<PosPartnerApplication, Long> {
    List<PosPartnerApplication> findAllByOrderBySubmittedAtDesc();
    Optional<PosPartnerApplication> findByApplicationId(String applicationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT application FROM PosPartnerApplication application WHERE application.applicationId = :applicationId")
    Optional<PosPartnerApplication> findByApplicationIdForUpdate(@Param("applicationId") String applicationId);
    Optional<PosPartnerApplication> findByApplicationIdAndStatusTokenHash(String applicationId, String statusTokenHash);
    boolean existsByPublicKeyAndStatusIn(String publicKey, List<String> statuses);
}
