package com.bhive.appointment.controller;

import com.bhive.appointment.entity.AppointmentService;
import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AppointmentConfigurationController {

    private final AppointmentServiceRepository appointmentServiceRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipService businessMembershipService;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public AppointmentConfigurationController(AppointmentServiceRepository appointmentServiceRepository,
                                              BusinessRepository businessRepository,
                                              BusinessMembershipService businessMembershipService,
                                              BusinessCustomerRepository businessCustomerRepository,
                                              CustomerProfileRepository customerProfileRepository) {
        this.appointmentServiceRepository = appointmentServiceRepository;
        this.businessRepository = businessRepository;
        this.businessMembershipService = businessMembershipService;
        this.businessCustomerRepository = businessCustomerRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    @PutMapping("/businesses/{businessId}/appointments-enabled")
    public ResponseEntity<?> setAppointmentsEnabled(@PathVariable Long businessId, @RequestParam boolean enabled) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return businessRepository.findById(businessId)
            .filter(business -> tenantId.equals(business.getTenantId()))
            .filter(business -> businessMembershipService.userHasAccessToBusiness(userId, businessId, tenantId))
            .map(business -> {
                if (enabled && appointmentServiceRepository
                    .findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(tenantId, businessId).isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "message", "Add and save at least one active service before enabling appointments."
                    ));
                }
                business.setAppointmentsEnabled(enabled);
                return ResponseEntity.ok(businessRepository.save(business));
            })
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @GetMapping("/appointment-services")
    public ResponseEntity<List<AppointmentService>> getAppointmentServices(@RequestParam Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return businessRepository.findById(businessId)
            .filter(business -> tenantId.equals(business.getTenantId()))
            .map(business -> {
                boolean businessUser = businessMembershipService.userHasAccessToBusiness(userId, businessId, tenantId);
                if (businessUser) {
                    return ResponseEntity.ok(appointmentServiceRepository.findByTenantIdAndBusinessIdOrderByNameAsc(tenantId, businessId));
                }
                if (!business.isAppointmentsEnabled() || !isLinkedCustomer(tenantId, businessId, userId)) {
                    return ResponseEntity.ok(List.<AppointmentService>of());
                }
                return ResponseEntity.ok(appointmentServiceRepository.findByTenantIdAndBusinessIdAndEnabledTrueOrderByNameAsc(tenantId, businessId));
            })
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PutMapping("/businesses/{businessId}/appointment-services")
    @Transactional
    public ResponseEntity<List<AppointmentService>> saveAppointmentServices(
        @PathVariable Long businessId,
        @RequestBody List<AppointmentServiceRequest> requests
    ) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || requests == null || requests.size() > 50) {
            return ResponseEntity.badRequest().build();
        }

        Business business = businessRepository.findById(businessId)
            .filter(candidate -> tenantId.equals(candidate.getTenantId()))
            .orElse(null);
        if (business == null || !businessMembershipService.userHasAccessToBusiness(userId, businessId, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Set<Long> requestedIds = new HashSet<>();
        for (AppointmentServiceRequest request : requests) {
            if (!isValid(request) || (request.id() != null && !requestedIds.add(request.id()))) {
                return ResponseEntity.badRequest().build();
            }
        }

        List<AppointmentService> current = appointmentServiceRepository
            .findByTenantIdAndBusinessIdOrderByNameAsc(tenantId, businessId);
        Map<Long, AppointmentService> currentById = current.stream()
            .collect(Collectors.toMap(AppointmentService::getId, service -> service));
        if (requestedIds.stream().anyMatch(id -> !currentById.containsKey(id))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        current.stream()
            .filter(service -> !requestedIds.contains(service.getId()))
            .forEach(service -> service.setEnabled(false));

        for (AppointmentServiceRequest request : requests) {
            AppointmentService service = request.id() == null
                ? new AppointmentService()
                : currentById.get(request.id());
            service.setTenantId(tenantId);
            service.setBusinessId(businessId);
            service.setName(request.name().trim());
            service.setDescription(request.description() == null ? null : request.description().trim());
            service.setPrice(request.price());
            service.setDurationMinutes(request.durationMinutes());
            service.setEnabled(request.enabled());
            appointmentServiceRepository.save(service);
        }

        return ResponseEntity.ok(appointmentServiceRepository
            .findByTenantIdAndBusinessIdOrderByNameAsc(tenantId, businessId));
    }

    private boolean isLinkedCustomer(Long tenantId, Long businessId, Long userId) {
        Long customerId = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .map(com.bhive.customer.entity.CustomerProfile::getId)
            .orElse(null);
        return customerId != null && businessCustomerRepository.findByTenantIdAndBusinessId(tenantId, businessId).stream()
            .filter(link -> "ACTIVE".equalsIgnoreCase(link.getStatus()))
            .map(BusinessCustomer::getCustomerProfileId)
            .anyMatch(customerId::equals);
    }

    private boolean isValid(AppointmentServiceRequest request) {
        return request != null
            && request.name() != null
            && !request.name().isBlank()
            && request.name().trim().length() <= 120
            && (request.description() == null || request.description().length() <= 500)
            && request.price() != null
            && request.price().compareTo(BigDecimal.ZERO) >= 0
            && request.price().scale() <= 2
            && request.durationMinutes() != null
            && request.durationMinutes() >= 5
            && request.durationMinutes() <= 480;
    }

    public record AppointmentServiceRequest(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        boolean enabled
    ) { }
}