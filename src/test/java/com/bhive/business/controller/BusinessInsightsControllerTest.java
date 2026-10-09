package com.bhive.business.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.dto.BusinessInsightsResponse;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.membership.entity.Membership;
import com.bhive.membership.entity.MembershipStatus;
import com.bhive.membership.repository.MembershipRepository;
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
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class BusinessInsightsControllerTest {

    private final BusinessRepository businessRepository = Mockito.mock(BusinessRepository.class);
    private final BusinessUserRepository businessUserRepository = Mockito.mock(BusinessUserRepository.class);
    private final InvoiceRepository invoiceRepository = Mockito.mock(InvoiceRepository.class);
    private final PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
    private final MembershipRepository membershipRepository = Mockito.mock(MembershipRepository.class);
    private final BusinessCustomerRepository businessCustomerRepository = Mockito.mock(BusinessCustomerRepository.class);
    private final BusinessInsightsController controller = new BusinessInsightsController(
        businessRepository, businessUserRepository, invoiceRepository, paymentRepository,
        membershipRepository, businessCustomerRepository
    );

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void aggregatesAuthorizedBusinessTrends() {
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

        Invoice overdueInvoice = new Invoice();
        overdueInvoice.setCustomerId(12L);
        overdueInvoice.setStatus(InvoiceStatus.SENT);
        overdueInvoice.setDueDate(LocalDate.now().minusDays(40));
        overdueInvoice.setTotalAmount(new BigDecimal("350.00"));
        Invoice paidInvoice = new Invoice();
        paidInvoice.setCustomerId(12L);
        paidInvoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByBusinessId(3L)).thenReturn(List.of(overdueInvoice, paidInvoice));

        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setAmount(new BigDecimal("500.00"));
        when(paymentRepository.findByBusinessId(3L)).thenReturn(List.of(payment));

        Membership membership = new Membership();
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setCustomerId(12L);
        membership.setStartDate(LocalDate.now().minusDays(10));
        membership.setEndDate(LocalDate.now().plusDays(20));
        Membership previousMembership = new Membership();
        previousMembership.setStatus(MembershipStatus.EXPIRED);
        previousMembership.setCustomerId(12L);
        previousMembership.setStartDate(LocalDate.now().minusDays(50));
        previousMembership.setEndDate(LocalDate.now().minusDays(11));
        when(membershipRepository.findByBusinessIdAndTenantId(3L, 5L))
            .thenReturn(List.of(membership, previousMembership));

        BusinessCustomer customer = new BusinessCustomer();
        customer.setCustomerProfileId(12L);
        customer.setCreatedAt(LocalDateTime.now());
        when(businessCustomerRepository.findByTenantIdAndBusinessId(5L, 3L)).thenReturn(List.of(customer));

        var response = controller.getInsights(3L, 6);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BusinessInsightsResponse insights = response.getBody();
        assertEquals(new BigDecimal("500.00"), insights.revenueTrend().get(5).amount());
        assertEquals(new BigDecimal("350.00"), insights.paymentAging().get(2).amount());
        assertEquals(1, insights.memberships().active());
        assertEquals(1, insights.memberships().renewingSoon());
        assertEquals(1.0, insights.memberships().renewalRate());
        assertEquals(0, insights.memberships().churned());
        assertEquals(1, insights.customers().acquiredLast30Days());
        assertEquals(1, insights.customers().repeatCustomers());
    }

    @Test
    void deniesInsightsForBusinessOutsideTenant() {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(7L);
        when(businessRepository.findById(3L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.FORBIDDEN, controller.getInsights(3L, 6).getStatusCode());
    }
}