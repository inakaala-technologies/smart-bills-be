package com.bhive.business.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.dto.BusinessInsightsResponse;
import com.bhive.business.dto.BusinessInsightsResponse.CustomerMetrics;
import com.bhive.business.dto.BusinessInsightsResponse.MembershipMetrics;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.business.service.BusinessInsightsService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.payment.repository.PaymentRepository;

class BusinessInsightsControllerTest {

	private final BusinessRepository businessRepository = Mockito.mock(BusinessRepository.class);

	private final BusinessUserRepository businessUserRepository = Mockito.mock(BusinessUserRepository.class);

	private final InvoiceRepository invoiceRepository = Mockito.mock(InvoiceRepository.class);

	private final PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);

	private final MembershipRepository membershipRepository = Mockito.mock(MembershipRepository.class);

	private final BusinessCustomerRepository businessCustomerRepository = Mockito
			.mock(BusinessCustomerRepository.class);

	private final BusinessInsightsService businessInsightsService = Mockito.mock(BusinessInsightsService.class);

	private final BusinessInsightsController controller = new BusinessInsightsController(businessRepository,
			businessUserRepository, invoiceRepository, paymentRepository, membershipRepository,
			businessCustomerRepository, businessInsightsService);

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void returnsInsightsForAuthorizedBusiness() {

		TenantContext.setTenantId(5L);
		TenantContext.setUserId(7L);

		Business business = new Business();
		business.setTenantId(5L);

		BusinessUser user = new BusinessUser();
		user.setUserId(7L);
		user.setStatus("ACTIVE");

		when(businessRepository.findById(3L)).thenReturn(Optional.of(business));

		when(businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(5L, 3L, "ACTIVE"))
				.thenReturn(List.of(user));

		BusinessInsightsResponse expectedResponse = new BusinessInsightsResponse(List.of(), List.of(),
				new MembershipMetrics(1, 1, 0, 1.0), new CustomerMetrics(1, 1, 1.0, List.of()));

		when(businessInsightsService.getInsights(5L, 7L, 3L, 6)).thenReturn(expectedResponse);

		var response = controller.getInsights(3L, 6);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(expectedResponse, response.getBody());

		verify(businessInsightsService).getInsights(5L, 7L, 3L, 6);
	}

	@Test
	void deniesInsightsForBusinessOutsideTenant() {

		TenantContext.setTenantId(5L);
		TenantContext.setUserId(7L);

		when(businessRepository.findById(3L)).thenReturn(Optional.empty());

		var response = controller.getInsights(3L, 6);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

		verify(businessInsightsService, never()).getInsights(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.anyInt());
	}

	@Test
	void deniesInsightsForInactiveOrUnauthorizedUser() {

		TenantContext.setTenantId(5L);
		TenantContext.setUserId(7L);

		Business business = new Business();
		business.setTenantId(5L);

		when(businessRepository.findById(3L)).thenReturn(Optional.of(business));

		when(businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(5L, 3L, "ACTIVE"))
				.thenReturn(List.of());

		var response = controller.getInsights(3L, 6);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

		verify(businessInsightsService, never()).getInsights(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.anyInt());
	}

	@Test
	void returnsBadRequestWhenTenantIdIsMissing() {

		TenantContext.setUserId(7L);

		var response = controller.getInsights(3L, 6);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

		verify(businessInsightsService, never()).getInsights(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.anyInt());
	}

	@Test
	void returnsBadRequestWhenUserIdIsMissing() {

		TenantContext.setTenantId(5L);

		var response = controller.getInsights(3L, 6);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

		verify(businessInsightsService, never()).getInsights(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.anyInt());
	}

	@Test
	void returnsBadRequestWhenMonthsAreLessThanThree() {

		TenantContext.setTenantId(5L);
		TenantContext.setUserId(7L);

		var response = controller.getInsights(3L, 2);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

		verify(businessInsightsService, never()).getInsights(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.anyInt());
	}

	@Test
	void returnsBadRequestWhenMonthsAreGreaterThanTwentyFour() {

		TenantContext.setTenantId(5L);
		TenantContext.setUserId(7L);

		var response = controller.getInsights(3L, 25);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

		verify(businessInsightsService, never()).getInsights(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.anyInt());
	}
}
