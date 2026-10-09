package com.bhive.business.controller;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessStatus;
import com.bhive.business.dto.NearbyBusinessSummary;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.common.util.GeoUtils;
import com.bhive.common.util.ProfileIdGenerator;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.repository.PaymentRepository;
import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.repository.AppointmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;

    public BusinessController(BusinessRepository businessRepository,
                             BusinessUserRepository businessUserRepository,
                             CustomerProfileRepository customerProfileRepository,
                             BusinessCustomerRepository businessCustomerRepository,
                             InvoiceRepository invoiceRepository,
                             PaymentRepository paymentRepository,
                             AppointmentRepository appointmentRepository) {
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessCustomerRepository = businessCustomerRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping
    public List<Business> getAllBusinesses() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return businessRepository.findAll();
        }
        return businessRepository.findAll().stream()
            .filter(business -> tenantId.equals(business.getTenantId()))
            .toList();
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyBusinessSummary>> getNearbyBusinesses(
        @RequestParam Double latitude,
        @RequestParam Double longitude,
        @RequestParam(defaultValue = "25") double radiusKm
    ) {
        if (!GeoUtils.isValidCoordinates(latitude, longitude) || radiusKm <= 0 || radiusKm > 500) {
            return ResponseEntity.badRequest().build();
        }
        List<NearbyBusinessSummary> nearby = businessRepository
            .findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(BusinessStatus.ACTIVE).stream()
            .map(business -> new NearbyBusinessSummary(
                business.getId(),
                business.getName(),
                business.getBusinessType(),
                business.getAddress(),
                business.getLatitude(),
                business.getLongitude(),
                GeoUtils.distanceKm(latitude, longitude, business.getLatitude(), business.getLongitude())
            ))
            .filter(business -> business.distanceKm() <= radiusKm)
            .sorted(java.util.Comparator.comparingDouble(NearbyBusinessSummary::distanceKm))
            .limit(100)
            .toList();
        return ResponseEntity.ok(nearby);
    }

    @GetMapping("/my")
    public List<Business> getMyBusinesses() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (tenantId == null || userId == null) {
            return businessRepository.findAll();
        }

        List<Long> businessIds = businessUserRepository.findByUserIdAndTenantId(userId, tenantId).stream()
            .map(BusinessUser::getBusinessId)
            .toList();

        if (businessIds.isEmpty()) {
            return List.of();
        }

        return businessRepository.findAll().stream()
            .filter(business -> businessIds.contains(business.getId()) && tenantId.equals(business.getTenantId()))
            .toList();
    }

    @GetMapping("/customer/invoices")
    public List<Business> getCustomerInvoiceBusinesses() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (tenantId == null || userId == null) {
            return List.of();
        }

        List<Long> customerIds = customerProfileRepository.findAll().stream()
            .filter(customer -> tenantId.equals(customer.getTenantId()))
            .filter(customer -> userId.equals(customer.getUserId()))
            .map(CustomerProfile::getId)
            .toList();

        if (customerIds.isEmpty()) {
            return List.of();
        }

        Set<Long> businessIds = invoiceRepository.findAll().stream()
            .filter(invoice -> invoice.getCustomerId() != null)
            .filter(invoice -> customerIds.contains(invoice.getCustomerId()))
            .map(Invoice::getBusinessId)
            .collect(Collectors.toSet());

        businessCustomerRepository.findByTenantId(tenantId).stream()
            .filter(link -> customerIds.contains(link.getCustomerProfileId()))
            .filter(link -> "ACTIVE".equalsIgnoreCase(link.getStatus()))
            .map(BusinessCustomer::getBusinessId)
            .forEach(businessIds::add);

        if (businessIds.isEmpty()) {
            return List.of();
        }

        return businessRepository.findAll().stream()
            .filter(business -> tenantId.equals(business.getTenantId()))
            .filter(business -> businessIds.contains(business.getId()))
            .toList();
    }

    @GetMapping("/dashboard-summary")
    public Map<String, Object> getBusinessDashboardSummary(@RequestParam(required = false) Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (tenantId == null || userId == null) {
            return emptySummary();
        }

        List<Long> linkedBusinessIds = businessUserRepository.findByUserIdAndTenantId(userId, tenantId).stream()
            .map(BusinessUser::getBusinessId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (linkedBusinessIds.isEmpty()) {
            return emptySummary();
        }

        List<Long> targetBusinessIds = businessId != null
            ? linkedBusinessIds.stream().filter(id -> id.equals(businessId)).toList()
            : linkedBusinessIds;

        if (targetBusinessIds.isEmpty()) {
            return emptySummary();
        }

        List<Invoice> invoices = invoiceRepository.findAll().stream()
            .filter(invoice -> targetBusinessIds.contains(invoice.getBusinessId()))
            .toList();

        List<Payment> payments = paymentRepository.findAll().stream()
            .filter(payment -> targetBusinessIds.contains(payment.getBusinessId()))
            .toList();

        List<Appointment> appointments = appointmentRepository.findAll().stream()
            .filter(appointment -> targetBusinessIds.contains(appointment.getBusinessId()))
            .toList();

        List<Long> customerIds = businessCustomerRepository.findAll().stream()
            .filter(link -> targetBusinessIds.contains(link.getBusinessId()))
            .filter(link -> tenantId.equals(link.getTenantId()))
            .map(BusinessCustomer::getCustomerProfileId)
            .distinct()
            .toList();

        List<CustomerProfile> activeCustomers = customerProfileRepository.findAll().stream()
            .filter(customer -> tenantId.equals(customer.getTenantId()))
            .filter(customer -> customerIds.contains(customer.getId()))
            .toList();

        BigDecimal totalRevenue = invoices.stream()
            .map(Invoice::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingPayments = payments.stream()
            .filter(payment -> payment.getStatus() != null && payment.getStatus().name().equalsIgnoreCase("PENDING"))
            .map(Payment::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long newCustomers = activeCustomers.stream()
            .filter(customer -> customer.getCreatedAt() != null)
            .filter(customer -> customer.getCreatedAt().isAfter(LocalDate.now().minusDays(30).atStartOfDay()))
            .count();

        List<Map<String, Object>> recentTransactions = invoices.stream()
            .sorted((first, second) -> second.getCreatedAt().compareTo(first.getCreatedAt()))
            .limit(3)
            .map(invoice -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", invoice.getId());
                item.put("name", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "Invoice");
                item.put("amount", invoice.getTotalAmount() != null ? invoice.getTotalAmount().doubleValue() : 0d);
                item.put("meta", invoice.getStatus() != null ? invoice.getStatus().name() : "SENT");
                item.put("date", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : "Today");
                return item;
            })
            .toList();

        List<Map<String, Object>> upcomingRenewals = appointments.stream()
            .filter(appointment -> appointment.getScheduledAt() != null)
            .sorted((first, second) -> first.getScheduledAt().compareTo(second.getScheduledAt()))
            .limit(3)
            .map(appointment -> {
                Map<String, Object> item = new LinkedHashMap<>();
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), appointment.getScheduledAt().toLocalDate());
                item.put("id", appointment.getId());
                item.put("name", appointment.getServiceName() != null ? appointment.getServiceName() : "Appointment");
                item.put("daysLeft", Math.max(daysLeft, 0L));
                item.put("note", appointment.getStatus() != null ? appointment.getStatus().name() : "BOOKED");
                return item;
            })
            .toList();

        List<Map<String, Object>> revenueTrend = new ArrayList<>();
        for (int index = 6; index >= 0; index--) {
            LocalDate date = LocalDate.now().minusDays(index);
            BigDecimal amount = invoices.stream()
                .filter(invoice -> invoice.getIssueDate() != null && invoice.getIssueDate().equals(date))
                .map(Invoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", date.getDayOfMonth() + "" );
            point.put("value", amount.doubleValue());
            revenueTrend.add(point);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRevenue.doubleValue());
        summary.put("activeMembers", (long) activeCustomers.size());
        summary.put("pendingPayments", pendingPayments.doubleValue());
        summary.put("newCustomers", newCustomers);
        summary.put("invoiceCount", invoices.size());
        summary.put("appointmentCount", appointments.size());
        summary.put("recentTransactions", recentTransactions);
        summary.put("upcomingRenewals", upcomingRenewals);
        summary.put("revenueTrend", revenueTrend);
        return summary;
    }

    private Map<String, Object> emptySummary() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("totalRevenue", 0d);
        empty.put("activeMembers", 0L);
        empty.put("pendingPayments", 0d);
        empty.put("newCustomers", 0L);
        empty.put("invoiceCount", 0);
        empty.put("appointmentCount", 0);
        empty.put("recentTransactions", List.of());
        empty.put("upcomingRenewals", List.of());
        empty.put("revenueTrend", List.of());
        return empty;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Business> getBusinessById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        return businessRepository.findById(id)
            .filter(business -> tenantId == null || tenantId.equals(business.getTenantId()))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Business createBusiness(@RequestBody Business business) {
        if (business.getCountry() == null || business.getCountry().isBlank() || business.getCountry().length() > 80
            || business.getRegion() == null || business.getRegion().isBlank() || business.getRegion().length() > 80
            || business.getCity() == null || business.getCity().isBlank() || business.getCity().length() > 80
            || !GeoUtils.isValidOptionalCoordinates(business.getLatitude(), business.getLongitude())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Country, state or region, city or town, and a valid coordinate pair are required.");
        }
        business.setAddress(String.join(", ", business.getCity().trim(), business.getRegion().trim(), business.getCountry().trim()));
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (tenantId == null) {
            tenantId = business.getTenantId() != null ? business.getTenantId() : 1L;
        }

        final Long resolvedTenantId = tenantId;
        final Long currentUserId = userId;
        business.setTenantId(resolvedTenantId);

        Business existingBusiness = null;
        if (business.getId() != null) {
            existingBusiness = businessRepository.findById(business.getId()).orElse(null);
        } else if (currentUserId != null) {
            existingBusiness = businessUserRepository.findByUserIdAndTenantId(currentUserId, resolvedTenantId).stream()
                .map(BusinessUser::getBusinessId)
                .map(businessRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(existing -> Objects.equals(existing.getTenantId(), resolvedTenantId))
                .findFirst()
                .orElse(null);
        }

        if (existingBusiness != null) {
            if (business.getName() != null && !business.getName().isBlank()) {
                existingBusiness.setName(business.getName());
            }
            if (business.getLegalName() != null) {
                existingBusiness.setLegalName(business.getLegalName());
            }
            if (business.getBusinessType() != null) {
                existingBusiness.setBusinessType(business.getBusinessType());
            }
            if (business.getGstin() != null) {
                existingBusiness.setGstin(business.getGstin());
            }
            if (business.getPan() != null) {
                existingBusiness.setPan(business.getPan());
            }
            if (business.getEmail() != null) {
                existingBusiness.setEmail(business.getEmail());
            }
            if (business.getPhone() != null) {
                existingBusiness.setPhone(business.getPhone());
            }
            if (business.getAddress() != null) {
                existingBusiness.setAddress(business.getAddress());
            }
            if (business.getLatitude() != null && business.getLongitude() != null) {
                existingBusiness.setLatitude(business.getLatitude());
                existingBusiness.setLongitude(business.getLongitude());
            }
            if (business.getStatus() != null) {
                existingBusiness.setStatus(business.getStatus());
            }
            if (existingBusiness.getTenantId() == null) {
                existingBusiness.setTenantId(resolvedTenantId);
            }
            if (existingBusiness.getProfileId() == null || existingBusiness.getProfileId().isBlank()) {
                existingBusiness.setProfileId(ProfileIdGenerator.nextBusinessId(businessRepository::existsByProfileId));
            }

            Business savedBusiness = businessRepository.save(existingBusiness);
            ensureBusinessMembership(currentUserId, resolvedTenantId, savedBusiness.getId());
            return savedBusiness;
        }

        business.setProfileId(ProfileIdGenerator.nextBusinessId(businessRepository::existsByProfileId));
        Business savedBusiness = businessRepository.save(business);
        ensureBusinessMembership(currentUserId, resolvedTenantId, savedBusiness.getId());
        return savedBusiness;
    }

    private void ensureBusinessMembership(Long userId, Long tenantId, Long businessId) {
        if (userId == null || tenantId == null || businessId == null) {
            return;
        }

        boolean alreadyLinked = businessUserRepository.findByTenantIdAndBusinessIdAndUserId(
            tenantId,
            businessId,
            userId
        ).isPresent();

        if (alreadyLinked) {
            return;
        }

        BusinessUser membership = new BusinessUser();
        membership.setBusinessId(businessId);
        membership.setUserId(userId);
        membership.setTenantId(tenantId);
        membership.setRole(com.bhive.business.entity.BusinessUserRole.BUSINESS_ADMIN);
        membership.setStatus("ACTIVE");
        businessUserRepository.save(membership);
    }
}
