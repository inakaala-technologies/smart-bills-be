package com.bhive.business.controller;

import com.bhive.business.dto.BusinessOfferRequest;
import com.bhive.business.dto.NearbyOfferSummary;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessOffer;
import com.bhive.business.entity.BusinessStatus;
import com.bhive.business.entity.CustomerSegment;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessOfferRepository;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.billing.entity.Invoice;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.common.util.GeoUtils;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.notification.service.NotificationService;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentStatus;
import com.bhive.payment.repository.PaymentRepository;
import com.bhive.appointment.entity.Appointment;
import com.bhive.appointment.entity.AppointmentStatus;
import com.bhive.appointment.repository.AppointmentRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessOfferController {

    private final BusinessOfferRepository offerRepository;
    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    public BusinessOfferController(BusinessOfferRepository offerRepository,
                                   BusinessRepository businessRepository,
                                   BusinessUserRepository businessUserRepository,
                                   BusinessCustomerRepository businessCustomerRepository,
                                   CustomerProfileRepository customerProfileRepository,
                                   InvoiceRepository invoiceRepository,
                                   PaymentRepository paymentRepository,
                                   MembershipRepository membershipRepository,
                                   AppointmentRepository appointmentRepository,
                                   NotificationService notificationService) {
        this.offerRepository = offerRepository;
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.businessCustomerRepository = businessCustomerRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.membershipRepository = membershipRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/offers/nearby")
    public ResponseEntity<List<NearbyOfferSummary>> getNearbyOffers(
        @RequestParam Double latitude,
        @RequestParam Double longitude,
        @RequestParam(defaultValue = "25") double radiusKm
    ) {
        if (!GeoUtils.isValidCoordinates(latitude, longitude) || radiusKm <= 0 || radiusKm > 500) {
            return ResponseEntity.badRequest().build();
        }

        List<Business> nearbyBusinesses = businessRepository
            .findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(BusinessStatus.ACTIVE).stream()
            .filter(business -> GeoUtils.distanceKm(latitude, longitude, business.getLatitude(), business.getLongitude()) <= radiusKm)
            .toList();
        if (nearbyBusinesses.isEmpty()) return ResponseEntity.ok(List.of());

        Map<Long, Business> businessesById = nearbyBusinesses.stream()
            .collect(Collectors.toMap(Business::getId, Function.identity()));
        List<Long> businessIds = nearbyBusinesses.stream().map(Business::getId).toList();
        LocalDateTime now = LocalDateTime.now();
        List<NearbyOfferSummary> offers = offerRepository
            .findByBusinessIdInAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
                businessIds,
                now,
                now
            ).stream()
            .filter(offer -> offer.getTargetSegment() == CustomerSegment.ALL)
            .map(offer -> {
                Business business = businessesById.get(offer.getBusinessId());
                return new NearbyOfferSummary(
                    offer.getId(),
                    business.getId(),
                    business.getName(),
                    business.getAddress(),
                    offer.getTitle(),
                    offer.getDescription(),
                    offer.getDiscountLabel(),
                    offer.getEndsAt(),
                    GeoUtils.distanceKm(latitude, longitude, business.getLatitude(), business.getLongitude())
                );
            })
            .filter(offer -> offer.distanceKm() <= radiusKm)
            .sorted(java.util.Comparator.comparingDouble(NearbyOfferSummary::distanceKm))
            .limit(100)
            .toList();
        return ResponseEntity.ok(offers);
    }

    @GetMapping("/{businessId}/offers")
    public ResponseEntity<List<BusinessOffer>> getBusinessOffers(@PathVariable Long businessId) {
        Long tenantId = TenantContext.getTenantId();
        if (!canManageBusiness(businessId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(offerRepository.findByTenantIdAndBusinessIdOrderByCreatedAtDesc(tenantId, businessId));
    }

    @PostMapping("/{businessId}/offers")
    public ResponseEntity<?> createOffer(@PathVariable Long businessId, @RequestBody BusinessOfferRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (!canManageBusiness(businessId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (request == null || request.title() == null || request.title().isBlank() || request.title().length() > 120
            || request.discountLabel() == null || request.discountLabel().isBlank() || request.discountLabel().length() > 80
            || request.description() != null && request.description().length() > 1000
            || request.startsAt() == null || request.endsAt() == null || !request.endsAt().isAfter(request.startsAt())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a title, offer, and valid start and end dates."));
        }

        BusinessOffer offer = new BusinessOffer();
        offer.setTenantId(tenantId);
        offer.setBusinessId(businessId);
        offer.setTitle(request.title().trim());
        offer.setDescription(request.description() == null ? null : request.description().trim());
        offer.setDiscountLabel(request.discountLabel().trim());
        offer.setStartsAt(request.startsAt());
        offer.setEndsAt(request.endsAt());
        offer.setTargetSegment(request.targetSegment() == null ? CustomerSegment.ALL : request.targetSegment());
        BusinessOffer saved = offerRepository.save(offer);
        notifyTargetedCustomers(saved, TenantContext.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{businessId}/offers/{offerId}")
    public ResponseEntity<Void> deactivateOffer(@PathVariable Long businessId, @PathVariable Long offerId) {
        Long tenantId = TenantContext.getTenantId();
        if (!canManageBusiness(businessId, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return offerRepository.findByIdAndTenantIdAndBusinessId(offerId, tenantId, businessId)
            .map(offer -> {
                offer.setActive(false);
                offerRepository.save(offer);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean canManageBusiness(Long businessId, Long tenantId) {
        Long userId = TenantContext.getUserId();
        if (businessId == null || tenantId == null || userId == null) return false;
        return businessRepository.findById(businessId)
            .filter(business -> tenantId.equals(business.getTenantId()))
            .isPresent()
            && businessUserRepository.findByUserIdAndTenantId(userId, tenantId).stream()
                .anyMatch(user -> businessId.equals(user.getBusinessId()) && "ACTIVE".equalsIgnoreCase(user.getStatus()));
    }

    private void notifyTargetedCustomers(BusinessOffer offer, Long actorUserId) {
        CustomerSegment segment = offer.getTargetSegment();
        if (segment == CustomerSegment.ALL) return;

        List<BusinessCustomer> links = businessCustomerRepository.findByTenantIdAndBusinessId(offer.getTenantId(), offer.getBusinessId());
        List<Invoice> invoices = invoiceRepository.findByBusinessId(offer.getBusinessId());
        List<Payment> payments = paymentRepository.findByBusinessId(offer.getBusinessId());
        List<Appointment> appointments = appointmentRepository.findByBusinessId(offer.getBusinessId());
        Set<Long> notifiedUsers = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (BusinessCustomer link : links) {
            Optional<CustomerProfile> profileResult = customerProfileRepository.findById(link.getCustomerProfileId())
                .filter(profile -> offer.getTenantId().equals(profile.getTenantId()))
                .filter(profile -> profile.getUserId() != null && !profile.getUserId().equals(actorUserId));
            if (profileResult.isEmpty() || !matchesSegment(segment, link, profileResult.get(), invoices, payments, appointments, now)) continue;

            Long recipientUserId = profileResult.get().getUserId();
            if (notifiedUsers.add(recipientUserId)) {
                notificationService.create(
                    offer.getTenantId(), recipientUserId, "TARGETED_OFFER", offer.getTitle(),
                    offer.getDiscountLabel() + (offer.getDescription() == null ? "" : ": " + offer.getDescription()),
                    "/dashboard"
                );
            }
        }
    }

    private boolean matchesSegment(CustomerSegment segment, BusinessCustomer link, CustomerProfile profile,
                                   List<Invoice> invoices, List<Payment> payments, List<Appointment> appointments,
                                   LocalDateTime now) {
        Long customerId = profile.getId();
        return switch (segment) {
            case ALL -> true;
            case NEW -> link.getCreatedAt() != null && link.getCreatedAt().isAfter(now.minusDays(30));
            case LOYAL -> payments.stream()
                .filter(payment -> customerId.equals(payment.getCustomerId()) && payment.getStatus() == PaymentStatus.VERIFIED)
                .map(Payment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("10000")) >= 0;
            case MEMBERS -> membershipRepository.findByBusinessIdAndCustomerId(link.getBusinessId(), customerId).stream()
                .anyMatch(membership -> membership.getStatus() == MembershipStatus.ACTIVE
                    && (membership.getEndDate() == null || !membership.getEndDate().isBefore(LocalDate.now())));
            case AT_RISK -> {
                LocalDateTime cutoff = now.minusDays(90);
                if (link.getCreatedAt() != null && link.getCreatedAt().isAfter(cutoff)) yield false;
                boolean recentInvoice = invoices.stream().anyMatch(invoice -> customerId.equals(invoice.getCustomerId())
                    && invoice.getIssueDate() != null && !invoice.getIssueDate().isBefore(cutoff.toLocalDate()));
                boolean recentPayment = payments.stream().anyMatch(payment -> customerId.equals(payment.getCustomerId())
                    && payment.getPaymentDate() != null && payment.getPaymentDate().isAfter(cutoff));
                boolean recentAppointment = appointments.stream().anyMatch(appointment -> customerId.equals(appointment.getCustomerId())
                    && appointment.getScheduledAt() != null && appointment.getScheduledAt().isAfter(cutoff)
                    && appointment.getStatus() != AppointmentStatus.CANCELLED);
                yield !recentInvoice && !recentPayment && !recentAppointment;
            }
        };
    }

}