package com.bhive.bills.repository;

import com.bhive.bills.entity.SavedBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SavedBillRepository extends JpaRepository<SavedBill, Long> {

    Optional<SavedBill> findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
            Long customerId,
            String providerCode,
            String consumerIdentityHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SavedBill> findForUpdateByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
            Long customerId,
            String providerCode,
            String consumerIdentityHash
    );

    boolean existsByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
            Long customerId,
            String providerCode,
            String consumerIdentityHash
    );

    Page<SavedBill> findAllByCustomer_Id(Long customerId, Pageable pageable);

    Page<SavedBill> findAllByCustomer_IdAndCategory_Code(
            Long customerId,
            String categoryCode,
            Pageable pageable
    );

    Optional<SavedBill> findByIdAndCustomer_Id(Long id, Long customerId);

    @Query("""
            select bill.id from SavedBill bill
            where bill.category.code in ('ELECTRICITY', 'WATER', 'ECHALLAN')
              and (
                (bill.fetchStatus = 'SUCCESS'
                    and (bill.nextFetchAt is null or bill.nextFetchAt <= :now))
                or (bill.fetchStatus = 'FAILED'
                    and (bill.nextFetchAt <= :now
                        or (bill.nextFetchAt is null and bill.fetchRetryCount = 0)))
                or (bill.fetchStatus = 'IN_PROGRESS'
                    and bill.fetchClaimToken is not null
                    and bill.lastFetchAttemptAt <= :staleBefore)
              )
            order by coalesce(bill.nextFetchAt, bill.fetchedAt), bill.id
            """)
    List<Long> findEligibleFetchIds(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query(value = """
            update saved_bills
            set fetch_status = 'IN_PROGRESS',
                last_fetch_attempt_at = :claimedAt,
                last_fetch_error = null,
                fetch_claim_token = :claimToken,
                updated_at = :claimedAt
            where id = :id
              and category_id in (
                select id from bill_categories
                where code in ('ELECTRICITY', 'WATER', 'ECHALLAN')
              )
              and (
                (fetch_status = 'SUCCESS'
                    and (next_fetch_at is null or next_fetch_at <= :claimedAt))
                or (fetch_status = 'FAILED'
                    and (next_fetch_at <= :claimedAt
                        or (next_fetch_at is null and fetch_retry_count = 0)))
                or (fetch_status = 'IN_PROGRESS'
                    and fetch_claim_token is not null
                    and last_fetch_attempt_at <= :staleBefore)
              )
            """, nativeQuery = true)
    int claimEligibleFetch(
            @Param("id") Long id,
            @Param("claimToken") String claimToken,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @EntityGraph(attributePaths = {"customer", "provider", "category"})
    Optional<SavedBill> findByIdAndFetchClaimToken(Long id, String fetchClaimToken);
}
