package com.bhive.business.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhive.business.dto.BusinessOfferRequest;
import com.bhive.business.dto.NearbyOfferSummary;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessOffer;
import com.bhive.business.entity.BusinessStatus;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.entity.CustomerSegment;
import com.bhive.business.repository.BusinessOfferRepository;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.common.service.CacheInvalidationService;
import com.bhive.common.service.S3ImageUploadService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.membership.repository.MembershipRepository;
import com.bhive.notification.service.NotificationService;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentStatus;
import com.bhive.payment.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class BusinessOfferControllerTest {

	@Mock
	private BusinessOfferRepository offerRepository;

	@Mock
	private BusinessRepository businessRepository;

	@Mock
	private BusinessUserRepository businessUserRepository;

	@Mock
	private BusinessCustomerRepository businessCustomerRepository;

	@Mock
	private CustomerProfileRepository customerProfileRepository;

	@Mock
	private InvoiceRepository invoiceRepository;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private MembershipRepository membershipRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private NotificationService notificationService;

	@Mock
	private S3ImageUploadService s3ImageUploadService;

	@Mock
	private CacheInvalidationService cacheInvalidationService;

	@InjectMocks
	private BusinessOfferController controller;

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void businessOwnerCanPublishOffer() {

		Business business = business(10L, 3L, 12.0, 77.0);

		BusinessUser owner = new BusinessUser();
		owner.setBusinessId(10L);
		owner.setTenantId(3L);
		owner.setUserId(7L);
		owner.setStatus("ACTIVE");

		when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

		when(businessUserRepository.findByUserIdAndTenantId(7L, 3L)).thenReturn(List.of(owner));

		when(offerRepository.save(any(BusinessOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TenantContext.setTenantId(3L);
		TenantContext.setUserId(7L);

		BusinessOfferRequest request = new BusinessOfferRequest("Lunch special", "Weekday lunch deal", "20% off",
				LocalDateTime.of(2026, 9, 29, 0, 0), LocalDateTime.of(2026, 10, 6, 23, 59), CustomerSegment.ALL);

		ResponseEntity<?> response = controller.createOffer(10L, request, null);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());

		BusinessOffer created = (BusinessOffer) response.getBody();

		assertNotNull(created);
		assertEquals(10L, created.getBusinessId());
		assertEquals("20% off", created.getDiscountLabel());

		verify(cacheInvalidationService).evictOfferCaches();
	}

	@Test
	void nearbyOffersIncludeBusinessDistance() {

		Business business = business(10L, 3L, 12.0, 77.0);
		business.setBusinessType("Restaurant");
		business.setAddress("Local business address");

		BusinessOffer offer = new BusinessOffer();
		offer.setId(22L);
		offer.setTenantId(3L);
		offer.setBusinessId(10L);
		offer.setTitle("Lunch special");
		offer.setDiscountLabel("20% off");
		offer.setStartsAt(LocalDateTime.now().minusDays(1));
		offer.setEndsAt(LocalDateTime.now().plusDays(1));

		when(businessRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(BusinessStatus.ACTIVE))
				.thenReturn(List.of(business));

		when(offerRepository.findByBusinessIdInAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
				anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(offer));

		ResponseEntity<List<NearbyOfferSummary>> response = controller.getNearbyOffers(12.0, 77.0, 25);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());
		assertEquals("Lunch special", response.getBody().get(0).title());
		assertEquals("Local business", response.getBody().get(0).businessName());
		assertEquals(0.0, response.getBody().get(0).distanceKm());

		verify(cacheInvalidationService, never()).evictOfferCaches();
	}

	@Test
	void loyalSegmentOfferNotifiesOnlyEligibleCustomer() {

		Business business = business(10L, 3L, 12.0, 77.0);

		BusinessUser owner = new BusinessUser();
		owner.setBusinessId(10L);
		owner.setTenantId(3L);
		owner.setUserId(7L);
		owner.setStatus("ACTIVE");

		BusinessCustomer link = new BusinessCustomer();
		link.setBusinessId(10L);
		link.setTenantId(3L);
		link.setCustomerProfileId(23L);

		CustomerProfile customer = new CustomerProfile();
		customer.setId(23L);
		customer.setTenantId(3L);
		customer.setUserId(91L);

		Payment payment = new Payment();
		payment.setCustomerId(23L);
		payment.setStatus(PaymentStatus.VERIFIED);
		payment.setAmount(new java.math.BigDecimal("10000.00"));

		when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

		when(businessUserRepository.findByUserIdAndTenantId(7L, 3L)).thenReturn(List.of(owner));

		when(businessCustomerRepository.findByTenantIdAndBusinessId(3L, 10L)).thenReturn(List.of(link));

		when(customerProfileRepository.findById(23L)).thenReturn(Optional.of(customer));

		when(paymentRepository.findByBusinessId(10L)).thenReturn(List.of(payment));

		when(offerRepository.save(any(BusinessOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TenantContext.setTenantId(3L);
		TenantContext.setUserId(7L);

		BusinessOfferRequest request = new BusinessOfferRequest("Loyal customer special",
				"Thank you for staying with us", "₹500 reward", LocalDateTime.now().minusMinutes(1),
				LocalDateTime.now().plusDays(7), CustomerSegment.LOYAL);

		ResponseEntity<?> response = controller.createOffer(10L, request, null);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());

		BusinessOffer created = (BusinessOffer) response.getBody();

		assertNotNull(created);
		assertEquals(CustomerSegment.LOYAL, created.getTargetSegment());

		verify(notificationService).create(3L, 91L, "TARGETED_OFFER", "Loyal customer special",
				"₹500 reward: Thank you for staying with us", "/dashboard");

		verify(cacheInvalidationService).evictOfferCaches();
	}

	@Test
	void deactivatingOfferEvictsOfferCaches() {

		Business business = business(10L, 3L, 12.0, 77.0);

		BusinessUser owner = new BusinessUser();
		owner.setBusinessId(10L);
		owner.setTenantId(3L);
		owner.setUserId(7L);
		owner.setStatus("ACTIVE");

		BusinessOffer offer = new BusinessOffer();
		offer.setId(22L);
		offer.setTenantId(3L);
		offer.setBusinessId(10L);
		offer.setActive(true);

		when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

		when(businessUserRepository.findByUserIdAndTenantId(7L, 3L)).thenReturn(List.of(owner));

		when(offerRepository.findByIdAndTenantIdAndBusinessId(22L, 3L, 10L)).thenReturn(Optional.of(offer));

		when(offerRepository.save(any(BusinessOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TenantContext.setTenantId(3L);
		TenantContext.setUserId(7L);

		ResponseEntity<Void> response = controller.deactivateOffer(10L, 22L);

		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
		assertEquals(false, offer.isActive());

		verify(offerRepository).save(offer);
		verify(cacheInvalidationService).evictOfferCaches();
	}

	@Test
	void deactivatingNonExistingOfferDoesNotEvictCaches() {

		Business business = business(10L, 3L, 12.0, 77.0);

		BusinessUser owner = new BusinessUser();
		owner.setBusinessId(10L);
		owner.setTenantId(3L);
		owner.setUserId(7L);
		owner.setStatus("ACTIVE");

		when(businessRepository.findById(10L)).thenReturn(Optional.of(business));

		when(businessUserRepository.findByUserIdAndTenantId(7L, 3L)).thenReturn(List.of(owner));

		when(offerRepository.findByIdAndTenantIdAndBusinessId(22L, 3L, 10L)).thenReturn(Optional.empty());

		TenantContext.setTenantId(3L);
		TenantContext.setUserId(7L);

		ResponseEntity<Void> response = controller.deactivateOffer(10L, 22L);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

		verify(cacheInvalidationService, never()).evictOfferCaches();
	}

	private Business business(Long id, Long tenantId, double latitude, double longitude) {

		Business business = new Business();
		business.setId(id);
		business.setTenantId(tenantId);
		business.setName("Local business");
		business.setLatitude(latitude);
		business.setLongitude(longitude);
		business.setStatus(BusinessStatus.ACTIVE);

		return business;
	}
}
