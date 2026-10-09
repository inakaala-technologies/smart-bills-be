package com.bhive.payment.controller;

import com.bhive.payment.entity.Payment;
import com.bhive.payment.entity.PaymentMethod;
import com.bhive.payment.entity.PaymentStatus;
import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.payment.repository.PaymentRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private com.bhive.business.service.BusinessMembershipService businessMembershipService;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private NotificationService notificationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getAllPaymentsShouldReturnList() throws Exception {
        Payment payment = new Payment();
        payment.setInvoiceId(10L);
        payment.setBusinessId(1L);
        payment.setCustomerId(2L);
        payment.setAmount(new BigDecimal("2499.00"));
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setReferenceNumber("REF-001");

        Mockito.when(paymentRepository.findAll()).thenReturn(List.of(payment));

        mockMvc.perform(get("/api/v1/payments")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].referenceNumber").value("REF-001"));
    }

    @Test
    void createPaymentNotifiesLinkedCustomerUser() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(9L);
        Mockito.when(businessMembershipService.userHasAccessToBusiness(9L, 1L, 5L)).thenReturn(true);
        Mockito.when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Invoice invoice = new Invoice();
        invoice.setId(10L);
        invoice.setBusinessId(1L);
        invoice.setCustomerId(2L);
        invoice.setStatus(InvoiceStatus.SENT);
        Mockito.when(invoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        CustomerProfile customer = new CustomerProfile();
        customer.setId(2L);
        customer.setTenantId(5L);
        customer.setUserId(44L);
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customer));

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"invoiceId":10,"businessId":1,"customerId":999,"amount":2499.00,
                     "paymentMethod":"UPI","status":"VERIFIED","referenceNumber":"REF-FORGED",
                     "paymentDate":"2026-09-30T10:00:00"}
                    """))
            .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Payment> paymentCaptor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        Mockito.verify(paymentRepository).save(paymentCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2L, paymentCaptor.getValue().getCustomerId());
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.PENDING, paymentCaptor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertNotEquals("REF-FORGED", paymentCaptor.getValue().getReferenceNumber());
        Mockito.verify(notificationService).create(
            5L, 44L, "PAYMENT_RECORDED", "Payment recorded",
            "A payment of 2499 was recorded for invoice #10.", "/dashboard"
        );
    }
}
