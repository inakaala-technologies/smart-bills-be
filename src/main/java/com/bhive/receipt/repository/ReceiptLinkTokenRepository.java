package com.bhive.receipt.repository;

import com.bhive.receipt.entity.ReceiptLinkToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptLinkTokenRepository extends JpaRepository<ReceiptLinkToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT token FROM ReceiptLinkToken token
        WHERE token.tokenHash = :tokenHash AND token.consumedAt IS NULL
        """)
    Optional<ReceiptLinkToken> findUnconsumedByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("DELETE FROM ReceiptLinkToken token WHERE token.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
