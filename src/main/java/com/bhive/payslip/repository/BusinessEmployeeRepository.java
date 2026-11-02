package com.bhive.payslip.repository;

import com.bhive.payslip.entity.BusinessEmployee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessEmployeeRepository extends JpaRepository<BusinessEmployee, Long> {

    List<BusinessEmployee> findByTenantIdAndBusinessIdAndStatusOrderByCreatedAtDesc(Long tenantId, Long businessId, String status);

    Optional<BusinessEmployee> findByIdAndTenantIdAndBusinessId(Long id, Long tenantId, Long businessId);

    Optional<BusinessEmployee> findByTenantIdAndBusinessIdAndCustomerProfileId(Long tenantId, Long businessId, Long customerProfileId);
}
