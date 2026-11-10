package com.bhive.receipt.repository;

import com.bhive.receipt.entity.ReceiptPartnerNonce;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptPartnerNonceRepository extends JpaRepository<ReceiptPartnerNonce, Long> {
    @Modifying
    @Query("DELETE FROM ReceiptPartnerNonce nonce WHERE nonce.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
