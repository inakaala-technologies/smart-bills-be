package com.bhive.customer.repository;

import com.bhive.customer.entity.BusinessCustomer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessCustomerRepository extends JpaRepository<BusinessCustomer, Long> {
    List<BusinessCustomer> findByBusinessId(Long businessId);
    List<BusinessCustomer> findByTenantId(Long tenantId);
    List<BusinessCustomer> findByTenantIdAndBusinessId(Long tenantId, Long businessId);
    Optional<BusinessCustomer> findByBusinessIdAndCustomerProfileId(Long businessId, Long customerProfileId);
}
