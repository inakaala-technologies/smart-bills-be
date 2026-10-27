package com.bhive.business.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessStatus;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.appointment.repository.AppointmentRepository;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentStatus;
import com.bhive.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessControllerTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessUserRepository businessUserRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private BusinessCustomerRepository businessCustomerRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private BusinessController businessController;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateBusinessMembershipForCurrentUserWhenSavingProfile() {
        TenantContext.setTenantId(7L);
        TenantContext.setUserId(15L);

        Business business = new Business();
        business.setName("Green Leaf Studio");
        business.setLegalName("Green Leaf Studio Pvt Ltd");
        business.setEmail("hello@greenleaf.io");
        business.setBusinessType("SERVICE");
        business.setCountry("United States");
        business.setRegion("California");
        business.setCity("San Francisco");

        Business savedBusiness = new Business();
        savedBusiness.setId(101L);
        savedBusiness.setName("Green Leaf Studio");
        savedBusiness.setLegalName("Green Leaf Studio Pvt Ltd");
        savedBusiness.setEmail("hello@greenleaf.io");
        savedBusiness.setBusinessType("SERVICE");
        savedBusiness.setTenantId(7L);

        when(businessRepository.save(any(Business.class))).thenReturn(savedBusiness);

        Business result = businessController.createBusiness(business);

        assertNotNull(result);
        assertEquals(101L, result.getId());
        verify(businessUserRepository, times(1)).save(any(BusinessUser.class));
    }

    @Test
    void shouldReturnBusinessesLinkedToCustomerInvoices() {
        TenantContext.setTenantId(7L);
        TenantContext.setUserId(15L);

        CustomerProfile customerProfile = new CustomerProfile();
        customerProfile.setId(21L);
        customerProfile.setTenantId(7L);
        customerProfile.setUserId(15L);

        Business business = new Business();
        business.setId(101L);
        business.setName("Green Leaf Studio");
        business.setTenantId(7L);

        Invoice invoice = new Invoice();
        invoice.setId(5001L);
        invoice.setBusinessId(101L);
        invoice.setCustomerId(21L);
        invoice.setInvoiceNumber("INV-5001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setTotalAmount(new BigDecimal("1200.00"));

        when(customerProfileRepository.findAll()).thenReturn(List.of(customerProfile));
        when(invoiceRepository.findAll()).thenReturn(List.of(invoice));
        when(businessRepository.findAll()).thenReturn(List.of(business));

        List<Business> result = businessController.getCustomerInvoiceBusinesses();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getId());
    }

    @Test
    void shouldReturnActiveBusinessesWithinRadiusOrderedByDistance() {
        Business nearest = new Business();
        nearest.setId(301L);
        nearest.setName("Near Studio");
        nearest.setBusinessType("FITNESS");
        nearest.setAddress("Main Street");
        nearest.setStatus(BusinessStatus.ACTIVE);
        nearest.setLatitude(37.775);
        nearest.setLongitude(-122.4194);

        Business outsideRadius = new Business();
        outsideRadius.setId(302L);
        outsideRadius.setName("Far Studio");
        outsideRadius.setStatus(BusinessStatus.ACTIVE);
        outsideRadius.setLatitude(37.8);
        outsideRadius.setLongitude(-122.4194);

        when(businessRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(BusinessStatus.ACTIVE))
            .thenReturn(List.of(outsideRadius, nearest));

        var response = businessController.getNearbyBusinesses(37.7749, -122.4194, 2);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals(301L, response.getBody().get(0).id());
        assertTrue(response.getBody().get(0).distanceKm() < 1);
    }

    @Test
    void returnsInvoiceRevenueTrendForDailyWeeklyAndMonthlyIntervals() {
        TenantContext.setTenantId(7L);
        TenantContext.setUserId(15L);
        BusinessUser user = new BusinessUser();
        user.setBusinessId(101L);
        Invoice invoice = new Invoice();
        invoice.setBusinessId(101L);
        invoice.setId(101L);
        invoice.setIssueDate(LocalDate.now());
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setTotalAmount(new BigDecimal("125.00"));
        invoice.setStatus(InvoiceStatus.SENT);
        Invoice paidInvoice = new Invoice();
        paidInvoice.setBusinessId(101L);
        paidInvoice.setId(102L);
        paidInvoice.setIssueDate(LocalDate.now());
        paidInvoice.setCreatedAt(LocalDateTime.now().minusDays(1));
        paidInvoice.setTotalAmount(new BigDecimal("75.00"));
        paidInvoice.setStatus(InvoiceStatus.PAID);
        Payment verifiedPayment = new Payment();
        verifiedPayment.setBusinessId(101L);
        verifiedPayment.setInvoiceId(101L);
        verifiedPayment.setStatus(PaymentStatus.VERIFIED);
        verifiedPayment.setAmount(new BigDecimal("50.00"));
        when(businessUserRepository.findByUserIdAndTenantId(15L, 7L)).thenReturn(List.of(user));
        when(invoiceRepository.findAll()).thenReturn(List.of(invoice, paidInvoice));
        when(paymentRepository.findAll()).thenReturn(List.of(verifiedPayment));
        when(appointmentRepository.findAll()).thenReturn(List.of());
        when(businessCustomerRepository.findAll()).thenReturn(List.of());
        when(customerProfileRepository.findAll()).thenReturn(List.of());

        var daily = businessController.getBusinessDashboardSummary(101L, "DAILY");
        var weekly = businessController.getBusinessDashboardSummary(101L, "WEEKLY");
        var monthly = businessController.getBusinessDashboardSummary(101L, "MONTHLY");

        assertEquals(7, ((List<?>) daily.get("revenueTrend")).size());
        assertEquals(6, ((List<?>) weekly.get("revenueTrend")).size());
        assertEquals(6, ((List<?>) monthly.get("revenueTrend")).size());
        assertEquals(200.0, trendValueAt(daily, 6));
        assertEquals(200.0, trendValueAt(weekly, 5));
        assertEquals(200.0, trendValueAt(monthly, 5));
        assertEquals(200.0, ((Number) monthly.get("billableRevenue")).doubleValue());
        assertEquals(125.0, ((Number) monthly.get("completedRevenue")).doubleValue());
    }

    private double trendValueAt(java.util.Map<String, Object> summary, int index) {
        List<?> trend = (List<?>) summary.get("revenueTrend");
        return ((Number) ((java.util.Map<?, ?>) trend.get(index)).get("value")).doubleValue();
    }

    @Test
    void shouldUpdateExistingBusinessForCurrentUserInsteadOfCreatingDuplicate() {
        TenantContext.setTenantId(7L);
        TenantContext.setUserId(15L);

        Business existingBusiness = new Business();
        existingBusiness.setId(101L);
        existingBusiness.setName("Old Name");
        existingBusiness.setLegalName("Old Legal Name");
        existingBusiness.setEmail("old@greenleaf.io");
        existingBusiness.setTenantId(7L);

        BusinessUser membership = new BusinessUser();
        membership.setBusinessId(101L);
        membership.setUserId(15L);
        membership.setTenantId(7L);

        when(businessUserRepository.findByUserIdAndTenantId(15L, 7L)).thenReturn(List.of(membership));
        when(businessRepository.findById(101L)).thenReturn(Optional.of(existingBusiness));
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessUserRepository.findByTenantIdAndBusinessIdAndUserId(7L, 101L, 15L)).thenReturn(Optional.of(membership));

        Business updatedRequest = new Business();
        updatedRequest.setName("Green Leaf Studio");
        updatedRequest.setLegalName("Green Leaf Studio Pvt Ltd");
        updatedRequest.setEmail("hello@greenleaf.io");
        updatedRequest.setBusinessType("SERVICE");
        updatedRequest.setAddress("Market Street");
        updatedRequest.setCountry("United States");
        updatedRequest.setRegion("California");
        updatedRequest.setCity("San Francisco");
        updatedRequest.setLatitude(37.7749);
        updatedRequest.setLongitude(-122.4194);

        Business result = businessController.createBusiness(updatedRequest);

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("Green Leaf Studio", result.getName());
        assertEquals("hello@greenleaf.io", result.getEmail());
        assertEquals("San Francisco, California, United States", result.getAddress());
        assertEquals(37.7749, result.getLatitude());
        assertEquals(-122.4194, result.getLongitude());
        verify(businessUserRepository, never()).save(any(BusinessUser.class));
    }
}
