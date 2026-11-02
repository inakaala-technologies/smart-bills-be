package com.bhive.payslip.repository;

import com.bhive.payslip.entity.Payslip;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    List<Payslip> findTop200ByTenantIdAndBusinessIdOrderByCreatedAtDesc(Long tenantId, Long businessId);

    List<Payslip> findTop200ByTenantIdAndBusinessIdAndEmployeeIdOrderByCreatedAtDesc(Long tenantId, Long businessId, Long employeeId);

    List<Payslip> findTop200ByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(Long tenantId, Long recipientUserId);

    Optional<Payslip> findByIdAndTenantId(Long id, Long tenantId);
}
