package com.bhive.customer.repository;

import com.bhive.customer.entity.BusinessCustomer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessCustomerRepository extends JpaRepository<BusinessCustomer, Long> {
    List<BusinessCustomer> findByBusinessId(Long businessId);
    List<BusinessCustomer> findByTenantId(Long tenantId);
    List<BusinessCustomer> findByTenantIdAndBusinessId(Long tenantId, Long businessId);
    Optional<BusinessCustomer> findByBusinessIdAndCustomerProfileId(Long businessId, Long customerProfileId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT customer FROM BusinessCustomer customer WHERE customer.tenantId = :tenantId AND customer.businessId = :businessId AND customer.customerProfileId = :customerProfileId")
    Optional<BusinessCustomer> findForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("businessId") Long businessId,
                                             @Param("customerProfileId") Long customerProfileId);
}
