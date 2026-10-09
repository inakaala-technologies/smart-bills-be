package com.bhive.billing.controller;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private com.bhive.business.service.BusinessMembershipService businessMembershipService;

    @MockBean
    private BusinessCustomerRepository businessCustomerRepository;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAllInvoicesShouldReturnEmptyListWithoutTenantContext() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setBusinessId(1L);
        invoice.setCustomerId(2L);
        invoice.setInvoiceNumber("INV-1001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setTotalAmount(new BigDecimal("2499.00"));
        invoice.setStatus(InvoiceStatus.SENT);

        Mockito.when(invoiceRepository.findAll()).thenReturn(List.of(invoice));

        mockMvc.perform(get("/api/v1/billing/invoices")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAllInvoicesShouldReturnCustomerInvoicesForCustomerUser() throws Exception {
        TenantContext.setTenantId(11L);
        TenantContext.setUserId(99L);

        CustomerProfile customerProfile = new CustomerProfile();
        customerProfile.setId(42L);
        customerProfile.setTenantId(11L);
        customerProfile.setUserId(99L);

        Invoice invoice = new Invoice();
        invoice.setBusinessId(5L);
        invoice.setCustomerId(42L);
        invoice.setInvoiceNumber("INV-3001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setTotalAmount(new BigDecimal("4500.00"));
        invoice.setStatus(InvoiceStatus.SENT);

        Mockito.when(invoiceRepository.findAll()).thenReturn(List.of(invoice));
        Mockito.when(customerProfileRepository.findAll()).thenReturn(List.of(customerProfile));

        mockMvc.perform(get("/api/v1/billing/invoices")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].invoiceNumber").value("INV-3001"));
    }

    @Test
    void createInvoiceShouldLinkCustomerAndGenerateInvoiceMetadata() throws Exception {
        TenantContext.setTenantId(10L);
        TenantContext.setUserId(20L);

        Invoice invoice = new Invoice();
        invoice.setBusinessId(1L);
        invoice.setCustomerId(2L);
        invoice.setInvoiceNumber("INV-2001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setSubtotal(new BigDecimal("1000.00"));
        invoice.setGstAmount(new BigDecimal("180.00"));
        invoice.setTotalAmount(new BigDecimal("1180.00"));
        invoice.setStatus(InvoiceStatus.SENT);

        Mockito.when(businessMembershipService.userHasAccessToBusiness(20L, 1L, 10L)).thenReturn(true);
        Mockito.when(businessCustomerRepository.findByBusinessIdAndCustomerProfileId(1L, 2L)).thenReturn(Optional.empty());
        Mockito.when(invoiceRepository.save(Mockito.any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(businessCustomerRepository.save(Mockito.any(BusinessCustomer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerProfile customerProfile = new CustomerProfile();
        customerProfile.setId(2L);
        customerProfile.setTenantId(10L);
        customerProfile.setUserId(30L);
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customerProfile));

        mockMvc.perform(post("/api/v1/billing/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessId\":1,\"customerId\":2,\"invoiceNumber\":\"INV-2001\",\"issueDate\":\"" + LocalDate.now() + "\",\"dueDate\":\"" + LocalDate.now().plusDays(7) + "\",\"subtotal\":1000.00,\"gstAmount\":180.00,\"totalAmount\":1180.00,\"status\":\"SENT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.invoiceNumber", org.hamcrest.Matchers.startsWith("INV-")))
            .andExpect(jsonPath("$.issueDate").value(LocalDate.now().toString()));

        ArgumentCaptor<BusinessCustomer> captor = ArgumentCaptor.forClass(BusinessCustomer.class);
        verify(businessCustomerRepository, times(1)).save(captor.capture());
        BusinessCustomer savedLink = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(1L, savedLink.getBusinessId());
        org.junit.jupiter.api.Assertions.assertEquals(2L, savedLink.getCustomerProfileId());
        org.junit.jupiter.api.Assertions.assertEquals(10L, savedLink.getTenantId());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", savedLink.getStatus());
        verify(notificationService).create(
            Mockito.eq(10L), Mockito.eq(30L), Mockito.eq("INVOICE_CREATED"), Mockito.eq("New invoice"),
            Mockito.contains("is ready to review."), Mockito.eq("/dashboard")
        );
    }

    @Test
    void createDraftInvoiceShouldNotNotifyCustomer() throws Exception {
        TenantContext.setTenantId(10L);
        TenantContext.setUserId(20L);

        Invoice invoice = new Invoice();
        invoice.setBusinessId(1L);
        invoice.setCustomerId(2L);
        invoice.setInvoiceNumber("INV-DRAFT-1");
        invoice.setIssueDate(LocalDate.now());
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("18.00"));
        invoice.setTotalAmount(new BigDecimal("118.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);

        Mockito.when(businessMembershipService.userHasAccessToBusiness(20L, 1L, 10L)).thenReturn(true);
        Mockito.when(businessCustomerRepository.findByBusinessIdAndCustomerProfileId(1L, 2L)).thenReturn(Optional.of(new BusinessCustomer()));
        Mockito.when(invoiceRepository.save(Mockito.any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerProfile customerProfile = new CustomerProfile();
        customerProfile.setId(2L);
        customerProfile.setTenantId(10L);
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customerProfile));

        mockMvc.perform(post("/api/v1/billing/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessId\":1,\"customerId\":2,\"invoiceNumber\":\"INV-DRAFT-1\",\"issueDate\":\"" + LocalDate.now() + "\",\"subtotal\":100.00,\"gstAmount\":18.00,\"totalAmount\":118.00,\"status\":\"DRAFT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(notificationService, Mockito.never()).create(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void createInvoiceShouldRejectCustomerFromAnotherTenant() throws Exception {
        TenantContext.setTenantId(10L);
        TenantContext.setUserId(20L);
        Mockito.when(businessMembershipService.userHasAccessToBusiness(20L, 1L, 10L)).thenReturn(true);
        CustomerProfile customerProfile = new CustomerProfile();
        customerProfile.setId(2L);
        customerProfile.setTenantId(11L);
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customerProfile));

        mockMvc.perform(post("/api/v1/billing/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessId\":1,\"customerId\":2,\"status\":\"DRAFT\",\"subtotal\":100.00,\"gstAmount\":18.00,\"totalAmount\":118.00}"))
            .andExpect(status().isForbidden());

        Mockito.verify(invoiceRepository, Mockito.never()).save(Mockito.any(Invoice.class));
    }

    @Test
    void createInvoiceShouldRejectPaidInitialStatus() throws Exception {
        TenantContext.setTenantId(10L);
        TenantContext.setUserId(20L);
        Mockito.when(businessMembershipService.userHasAccessToBusiness(20L, 1L, 10L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/billing/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessId\":1,\"customerId\":2,\"status\":\"PAID\",\"subtotal\":100.00,\"gstAmount\":18.00,\"totalAmount\":118.00}"))
            .andExpect(status().isBadRequest());

        Mockito.verify(invoiceRepository, Mockito.never()).save(Mockito.any(Invoice.class));
    }

    @Test
    void updateDraftToSentShouldPersistAndNotifyCustomer() throws Exception {
        TenantContext.setTenantId(10L);
        TenantContext.setUserId(20L);

        Invoice invoice = new Invoice();
        invoice.setId(7L);
        invoice.setBusinessId(1L);
        invoice.setCustomerId(2L);
        invoice.setInvoiceNumber("INV-DRAFT-2");
        invoice.setStatus(InvoiceStatus.DRAFT);
        CustomerProfile customerProfile = new CustomerProfile();
        customerProfile.setId(2L);
        customerProfile.setTenantId(10L);
        customerProfile.setUserId(30L);

        Mockito.when(invoiceRepository.findById(7L)).thenReturn(Optional.of(invoice));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(20L, 1L, 10L)).thenReturn(true);
        Mockito.when(invoiceRepository.save(invoice)).thenReturn(invoice);
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customerProfile));

        mockMvc.perform(patch("/api/v1/billing/invoices/7/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SENT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"));

        verify(notificationService).create(10L, 30L, "INVOICE_CREATED", "New invoice", "Invoice INV-DRAFT-2 is ready to review.", "/dashboard");
    }

    @Test
    void updateOutstandingInvoiceToPaidShouldPersistWithoutSendingNewInvoiceNotification() throws Exception {
        TenantContext.setTenantId(10L);
        TenantContext.setUserId(20L);

        Invoice invoice = new Invoice();
        invoice.setId(8L);
        invoice.setBusinessId(1L);
        invoice.setCustomerId(2L);
        invoice.setInvoiceNumber("INV-SENT-1");
        invoice.setStatus(InvoiceStatus.SENT);

        Mockito.when(invoiceRepository.findById(8L)).thenReturn(Optional.of(invoice));
        Mockito.when(businessMembershipService.userHasAccessToBusiness(20L, 1L, 10L)).thenReturn(true);
        Mockito.when(invoiceRepository.save(invoice)).thenReturn(invoice);

        mockMvc.perform(patch("/api/v1/billing/invoices/8/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"));

        verify(notificationService, Mockito.never()).create(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }
}
