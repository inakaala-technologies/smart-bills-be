package com.bhive.billing.controller;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.dto.InvoiceCreateRequest;
import com.bhive.billing.dto.InvoiceResponse;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.family.entity.FamilyMember;
import com.bhive.family.entity.FamilyMemberStatus;
import com.bhive.family.repository.FamilyMemberRepository;
import com.bhive.notification.service.NotificationService;
import com.bhive.loyalty.service.LoyaltyService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/billing")
public class InvoiceController {

	private final InvoiceRepository invoiceRepository;
	private final BusinessMembershipService businessMembershipService;
	private final BusinessCustomerRepository businessCustomerRepository;
	private final CustomerProfileRepository customerProfileRepository;
	private final NotificationService notificationService;
    private final LoyaltyService loyaltyService;
	private final FamilyMemberRepository familyMemberRepository;

	public InvoiceController(InvoiceRepository invoiceRepository, BusinessMembershipService businessMembershipService,
			BusinessCustomerRepository businessCustomerRepository, CustomerProfileRepository customerProfileRepository,
			NotificationService notificationService, FamilyMemberRepository familyMemberRepository,
                            LoyaltyService loyaltyService) {
		this.invoiceRepository = invoiceRepository;
		this.businessMembershipService = businessMembershipService;
		this.businessCustomerRepository = businessCustomerRepository;
		this.customerProfileRepository = customerProfileRepository;
		this.notificationService = notificationService;
		this.familyMemberRepository = familyMemberRepository;
        this.loyaltyService = loyaltyService;
	}

	private boolean canAccessInvoice(Long userId, Long tenantId, Invoice invoice) {
		if (invoice == null || tenantId == null || userId == null) {
			return false;
		}

		if (businessMembershipService.userHasAccessToBusiness(userId, invoice.getBusinessId(), tenantId)) {
			return true;
		}

		return customerProfileRepository.findAll().stream().filter(customer -> tenantId.equals(customer.getTenantId()))
				.filter(customer -> userId.equals(customer.getUserId()))
				.anyMatch(customer -> invoice.getCustomerId() != null
						&& invoice.getCustomerId().equals(customer.getId()));
	}

	@GetMapping("/invoices")
	public List<InvoiceResponse> getAllInvoices() {
		Long tenantId = TenantContext.getTenantId();
		Long userId = TenantContext.getUserId();
		if (tenantId == null || userId == null) {
			return List.of();
		}
		return invoiceRepository.findAll().stream().filter(invoice -> canAccessInvoice(userId, tenantId, invoice))
				.map(InvoiceResponse::from).toList();
	}

	@GetMapping("/invoices/business")
	public List<InvoiceResponse> getBusinessInvoices() {
		Long tenantId = TenantContext.getTenantId();
		Long userId = TenantContext.getUserId();
		if (tenantId == null || userId == null) {
			return List.of();
		}

		return invoiceRepository.findAll().stream().filter(
				invoice -> businessMembershipService.userHasAccessToBusiness(userId, invoice.getBusinessId(), tenantId))
				.map(InvoiceResponse::from).toList();
	}

	@GetMapping("/invoices/customer")
	public List<InvoiceResponse> getCustomerInvoices() {
		Long tenantId = TenantContext.getTenantId();
		Long userId = TenantContext.getUserId();
		if (tenantId == null || userId == null) {
			return List.of();
		}

		return invoiceRepository.findAll().stream().filter(invoice -> invoice.getCustomerId() != null)
				.filter(invoice -> customerProfileRepository.findAll().stream()
						.filter(customer -> tenantId.equals(customer.getTenantId()))
						.filter(customer -> userId.equals(customer.getUserId()))
						.anyMatch(customer -> invoice.getCustomerId().equals(customer.getId())))
				.map(InvoiceResponse::from).toList();
	}

	@GetMapping("/invoices/{id}")
	public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable Long id) {
		Long tenantId = TenantContext.getTenantId();
		Long userId = TenantContext.getUserId();
		if (tenantId == null || userId == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return invoiceRepository.findById(id).filter(invoice -> canAccessInvoice(userId, tenantId, invoice))
				.map(InvoiceResponse::from).map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
	}

    @PostMapping("/invoices")
    @Transactional
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceCreateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || !businessMembershipService.userHasAccessToBusiness(userId, request.businessId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.totalAmount().compareTo(request.subtotal().add(request.gstAmount())) != 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.status() != InvoiceStatus.DRAFT && request.status() != InvoiceStatus.SENT) {
            return ResponseEntity.badRequest().build();
        }
        Optional<CustomerProfile> customer = customerProfileRepository.findById(request.customerId())
            .filter(profile -> tenantId.equals(profile.getTenantId()));
        if (customer.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

		businessCustomerRepository.findByBusinessIdAndCustomerProfileId(request.businessId(), request.customerId())
				.or(() -> {
					BusinessCustomer businessCustomer = new BusinessCustomer();
					businessCustomer.setBusinessId(request.businessId());
					businessCustomer.setCustomerProfileId(request.customerId());
					businessCustomer.setTenantId(tenantId);
					businessCustomer.setStatus("ACTIVE");
					return Optional.of(businessCustomerRepository.save(businessCustomer));
				});

        Invoice invoice = new Invoice();
        invoice.setBusinessId(request.businessId());
        invoice.setCustomerId(request.customerId());
        invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(request.dueDate());
        invoice.setSubtotal(request.subtotal());
        invoice.setGstAmount(request.gstAmount());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setStatus(request.status());
        Invoice saved = invoiceRepository.save(invoice);
        int pointsToRedeem = request.loyaltyPointsToRedeem() == null ? 0 : request.loyaltyPointsToRedeem();
        if (pointsToRedeem > 0) {
            var redemption = loyaltyService.redeemForPurchase(tenantId, saved.getBusinessId(), saved.getCustomerId(),
                pointsToRedeem, saved.getTotalAmount(), "INVOICE:" + saved.getId(),
                "INVOICE", saved.getId(), "Points applied to invoice " + saved.getInvoiceNumber());
            saved.setLoyaltyPointsRedeemed(redemption.pointsRedeemed());
            saved.setLoyaltyDiscountAmount(redemption.discountAmount());
            saved.setTotalAmount(saved.getTotalAmount().subtract(redemption.discountAmount()));
            saved = invoiceRepository.save(saved);
        }
        Invoice responseInvoice = saved;
        if (responseInvoice.getStatus() == InvoiceStatus.SENT) {
            customer.map(CustomerProfile::getUserId)
                .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(userId))
                .ifPresent(recipientUserId -> notificationService.create(
                    tenantId,
                    recipientUserId,
                    "INVOICE_CREATED",
                    "New invoice",
                    "Invoice " + responseInvoice.getInvoiceNumber() + " is ready to review.",
                    "/dashboard"
                ));
        }
        return ResponseEntity.ok(InvoiceResponse.from(responseInvoice));
    }

	@PatchMapping("/invoices/{id}/status")
	public ResponseEntity<InvoiceResponse> updateInvoiceStatus(@PathVariable Long id,
			@RequestBody java.util.Map<String, String> request) {
		Long tenantId = TenantContext.getTenantId();
		Long userId = TenantContext.getUserId();
		if (tenantId == null || userId == null) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		Optional<Invoice> invoiceResult = invoiceRepository.findById(id);
		if (invoiceResult.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Invoice invoice = invoiceResult.get();
		if (!businessMembershipService.userHasAccessToBusiness(userId, invoice.getBusinessId(), tenantId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		String requestedStatusValue = request.get("status");
		if (requestedStatusValue == null || requestedStatusValue.isBlank()) {
			return ResponseEntity.badRequest().build();
		}

		InvoiceStatus requestedStatus;
		try {
			requestedStatus = InvoiceStatus.valueOf(requestedStatusValue.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().build();
		}

		InvoiceStatus currentStatus = invoice.getStatus();
		boolean sendingDraft = currentStatus == InvoiceStatus.DRAFT && requestedStatus == InvoiceStatus.SENT;
		boolean recordingPayment = (currentStatus == InvoiceStatus.SENT || currentStatus == InvoiceStatus.PARTIAL
				|| currentStatus == InvoiceStatus.OVERDUE) && requestedStatus == InvoiceStatus.PAID;
		if (!sendingDraft && !recordingPayment) {
			return ResponseEntity.badRequest().build();
		}

		invoice.setStatus(requestedStatus);
		Invoice saved = invoiceRepository.save(invoice);
		if (sendingDraft) {
			customerProfileRepository.findById(saved.getCustomerId())
					.filter(customer -> tenantId.equals(customer.getTenantId())).map(CustomerProfile::getUserId)
					.filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(userId))
					.ifPresent(recipientUserId -> notificationService.create(tenantId, recipientUserId,
							"INVOICE_CREATED", "New invoice",
							"Invoice " + saved.getInvoiceNumber() + " is ready to review.", "/dashboard"));
		}

		return ResponseEntity.ok(InvoiceResponse.from(saved));
	}

	@GetMapping("/invoices/family")
	@Transactional
	public ResponseEntity<List<InvoiceResponse>> getFamilyInvoices() {

		Long userId = TenantContext.getUserId();

		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		// The logged-in user must have an accepted family membership.
		Optional<FamilyMember> currentMembership = familyMemberRepository.findByUserIdAndStatus(userId,
				FamilyMemberStatus.ACCEPTED);

		if (currentMembership.isEmpty()) {
			return ResponseEntity.ok(List.of());
		}

		Long familyId = currentMembership.get().getFamilyId();

		// Only accepted family members can access each other's invoices.
		List<FamilyMember> acceptedMembers = familyMemberRepository.findByFamilyIdAndStatusOrderByIdAsc(familyId,
				FamilyMemberStatus.ACCEPTED);

		Set<Long> familyUserIds = acceptedMembers.stream().map(FamilyMember::getUserId)
				.filter(java.util.Objects::nonNull).collect(Collectors.toSet());

		if (familyUserIds.isEmpty()) {
			return ResponseEntity.ok(List.of());
		}

		// Resolve users to customer profiles across all tenants.
		List<CustomerProfile> familyProfiles = customerProfileRepository.findByUserIdIn(familyUserIds);

		Set<Long> customerProfileIds = familyProfiles.stream().map(CustomerProfile::getId)
				.filter(java.util.Objects::nonNull).collect(Collectors.toSet());

		if (customerProfileIds.isEmpty()) {
			return ResponseEntity.ok(List.of());
		}

		List<InvoiceResponse> familyInvoices = invoiceRepository.findByCustomerIdIn(customerProfileIds).stream()
				.map(InvoiceResponse::from).toList();

		return ResponseEntity.ok(familyInvoices);
	}

}
