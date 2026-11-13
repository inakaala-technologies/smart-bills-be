package com.bhive.business.controller;

import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.dto.BusinessInsightsResponse;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.business.service.BusinessInsightsService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessInsightsController {

	private final BusinessRepository businessRepository;
	private final BusinessUserRepository businessUserRepository;
	private final InvoiceRepository invoiceRepository;
	private final PaymentRepository paymentRepository;
	private final MembershipRepository membershipRepository;
	private final BusinessCustomerRepository businessCustomerRepository;
	private final BusinessInsightsService businessInsightsService;

	public BusinessInsightsController(BusinessRepository businessRepository,
			BusinessUserRepository businessUserRepository, InvoiceRepository invoiceRepository,
			PaymentRepository paymentRepository, MembershipRepository membershipRepository,
			BusinessCustomerRepository businessCustomerRepository, BusinessInsightsService businessInsightsService) {
		this.businessRepository = businessRepository;
		this.businessUserRepository = businessUserRepository;
		this.invoiceRepository = invoiceRepository;
		this.paymentRepository = paymentRepository;
		this.membershipRepository = membershipRepository;
		this.businessCustomerRepository = businessCustomerRepository;
		this.businessInsightsService = businessInsightsService;
	}

	@GetMapping("/{businessId}/insights")
	public ResponseEntity<BusinessInsightsResponse> getInsights(@PathVariable Long businessId,
			@RequestParam(defaultValue = "6") int months) {
		Long tenantId = TenantContext.getTenantId();
		Long userId = TenantContext.getUserId();
		if (tenantId == null || userId == null || months < 3 || months > 24) {
			return ResponseEntity.badRequest().build();
		}
		boolean authorized = businessRepository.findById(businessId)
				.filter(business -> tenantId.equals(business.getTenantId())).isPresent()
				&& businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, businessId, "ACTIVE")
						.stream().anyMatch(member -> userId.equals(member.getUserId()));
		if (!authorized)
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

		BusinessInsightsResponse insights = businessInsightsService.getInsights(tenantId, userId, businessId, months);

		return ResponseEntity.ok(insights);
	}
}