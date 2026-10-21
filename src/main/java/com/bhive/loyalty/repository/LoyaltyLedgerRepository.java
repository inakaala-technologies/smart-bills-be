package com.bhive.loyalty.repository;

import com.bhive.loyalty.entity.LoyaltyLedgerEntry;
import com.bhive.loyalty.dto.LoyaltyCustomerBalanceResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyLedgerRepository extends JpaRepository<LoyaltyLedgerEntry, Long> {
    Optional<LoyaltyLedgerEntry> findByBusinessIdAndCustomerIdAndIdempotencyKey(Long businessId, Long customerId, String idempotencyKey);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN entry.entryType = com.bhive.loyalty.entity.LoyaltyEntryType.REDEEM
            THEN -entry.points ELSE entry.points END), 0)
        FROM LoyaltyLedgerEntry entry
        WHERE entry.tenantId = :tenantId AND entry.businessId = :businessId AND entry.customerId = :customerId
        """)
    Long getBalance(@Param("tenantId") Long tenantId, @Param("businessId") Long businessId, @Param("customerId") Long customerId);

    @Query("""
        SELECT new com.bhive.loyalty.dto.LoyaltyCustomerBalanceResponse(entry.customerId,
            COALESCE(SUM(CASE WHEN entry.entryType = com.bhive.loyalty.entity.LoyaltyEntryType.REDEEM
                THEN -entry.points ELSE entry.points END), 0))
        FROM LoyaltyLedgerEntry entry
        WHERE entry.tenantId = :tenantId AND entry.businessId = :businessId
        GROUP BY entry.customerId
        """)
    List<LoyaltyCustomerBalanceResponse> getBalances(@Param("tenantId") Long tenantId,
                                                     @Param("businessId") Long businessId);
}
