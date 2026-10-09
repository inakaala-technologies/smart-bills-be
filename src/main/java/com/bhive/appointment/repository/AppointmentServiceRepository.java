package com.bhive.appointment.repository;

import com.bhive.appointment.entity.AppointmentService;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentServiceRepository extends JpaRepository<AppointmentService, Long> {
    List<AppointmentService> findByTenantIdAndBusinessIdOrderByNameAsc(Long tenantId, Long businessId);
    List<AppointmentService> findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(Long tenantId, Long businessId);
}