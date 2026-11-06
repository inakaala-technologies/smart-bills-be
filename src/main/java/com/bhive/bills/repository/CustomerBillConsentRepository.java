package com.bhive.bills.repository;

import com.bhive.bills.entity.CustomerBillConsent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CustomerBillConsentRepository
        extends JpaRepository<CustomerBillConsent, Long> {

    @EntityGraph(attributePaths = "provider")
    Page<CustomerBillConsent> findAllByCustomer_Id(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = "provider")
    Page<CustomerBillConsent> findAllByCustomer_IdAndProvider_Code(
            Long customerId,
            String providerCode,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "provider")
    Page<CustomerBillConsent> findAllByCustomer_IdAndStatus(
            Long customerId,
            String status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "provider")
    Page<CustomerBillConsent> findAllByCustomer_IdAndProvider_CodeAndStatus(
            Long customerId,
            String providerCode,
            String status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerBillConsent> findByConsentRecordIdAndCustomer_Id(
            String consentRecordId,
            Long customerId
    );
}
