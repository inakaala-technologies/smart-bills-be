package com.bhive.appointment.repository;

import com.bhive.appointment.entity.AppointmentStaff;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentStaffRepository extends JpaRepository<AppointmentStaff, Long> {
    List<AppointmentStaff> findByTenantIdAndBusinessIdAndStatusIgnoreCaseOrderByNameAsc(
        Long tenantId, Long businessId, String status);

    List<AppointmentStaff> findByTenantIdAndBusinessId(Long tenantId, Long businessId);

    Optional<AppointmentStaff> findByTenantIdAndBusinessIdAndLinkedUserId(
        Long tenantId, Long businessId, Long linkedUserId);
}
