
package com.bhive.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.dto.BusinessInsightsResponse;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentStatus;
import com.bhive.payment.repository.PaymentRepository;

class BusinessInsightsServiceTest {

	private final InvoiceRepository invoiceRepository = Mockito.mock(InvoiceRepository.class);

	private final PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);

	private final MembershipRepository membershipRepository = Mockito.mock(MembershipRepository.class);

	private final BusinessCustomerRepository businessCustomerRepository = Mockito
			.mock(BusinessCustomerRepository.class);

	private final BusinessInsightsService service = new BusinessInsightsService(invoiceRepository, paymentRepository,
			membershipRepository, businessCustomerRepository);

	@Test
	void aggregatesAuthorizedBusinessTrends() {

		LocalDate today = LocalDate.now();

		// Invoice data
		Invoice overdueInvoice = new Invoice();
		overdueInvoice.setCustomerId(12L);
		overdueInvoice.setStatus(InvoiceStatus.SENT);
		overdueInvoice.setDueDate(today.minusDays(40));
		overdueInvoice.setTotalAmount(new BigDecimal("350.00"));

		Invoice paidInvoice = new Invoice();
		paidInvoice.setCustomerId(12L);
		paidInvoice.setStatus(InvoiceStatus.PAID);

		when(invoiceRepository.findByBusinessId(3L)).thenReturn(List.of(overdueInvoice, paidInvoice));

		// Payment data
		Payment payment = new Payment();
		payment.setStatus(PaymentStatus.VERIFIED);
		payment.setPaymentDate(LocalDateTime.now());
		payment.setAmount(new BigDecimal("500.00"));

		when(paymentRepository.findByBusinessId(3L)).thenReturn(List.of(payment));

		// Membership data
		Membership membership = new Membership();
		membership.setStatus(MembershipStatus.ACTIVE);
		membership.setCustomerId(12L);
		membership.setStartDate(today.minusDays(10));
		membership.setEndDate(today.plusDays(20));

		Membership previousMembership = new Membership();
		previousMembership.setStatus(MembershipStatus.EXPIRED);
		previousMembership.setCustomerId(12L);
		previousMembership.setStartDate(today.minusDays(50));
		previousMembership.setEndDate(today.minusDays(11));

		when(membershipRepository.findByBusinessIdAndTenantId(3L, 5L))
				.thenReturn(List.of(membership, previousMembership));

		// Customer data
		BusinessCustomer customer = new BusinessCustomer();
		customer.setCustomerProfileId(12L);
		customer.setCreatedAt(LocalDateTime.now());

		when(businessCustomerRepository.findByTenantIdAndBusinessId(5L, 3L)).thenReturn(List.of(customer));

		// Execute service
		BusinessInsightsResponse insights = service.getInsights(5L, 7L, 3L, 6);

		// Revenue trend
		assertEquals(new BigDecimal("500.00"), insights.revenueTrend().get(5).amount());

		// Payment aging
		assertEquals(new BigDecimal("350.00"), insights.paymentAging().get(2).amount());

		// Membership metrics
		assertEquals(1, insights.memberships().active());
		assertEquals(1, insights.memberships().renewingSoon());
		assertEquals(1.0, insights.memberships().renewalRate());
		assertEquals(0, insights.memberships().churned());

		// Customer metrics
		assertEquals(1, insights.customers().acquiredLast30Days());
		assertEquals(1, insights.customers().repeatCustomers());
		assertEquals(1.0, insights.customers().retentionRate());

		// Acquisition trend
		assertEquals(BigDecimal.ONE, insights.customers().acquisitionTrend().get(5).amount());

		// Verify repository calls
		verify(invoiceRepository).findByBusinessId(3L);
		verify(paymentRepository).findByBusinessId(3L);
		verify(membershipRepository).findByBusinessIdAndTenantId(3L, 5L);
		verify(businessCustomerRepository).findByTenantIdAndBusinessId(5L, 3L);
	}
}
