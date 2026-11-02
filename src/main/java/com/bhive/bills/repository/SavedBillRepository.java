package com.bhive.bills.repository;

import com.bhive.bills.entity.SavedBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedBillRepository extends JpaRepository<SavedBill, Long> {

    Optional<SavedBill> findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
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
}
