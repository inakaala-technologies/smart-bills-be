package com.bhive.appointment.service;

import com.bhive.appointment.entity.Appointment;
import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;

class AppointmentBillingServiceTest {

    private final InvoiceRepository invoiceRepository = Mockito.mock(InvoiceRepository.class);
    private final CustomerProfileRepository customerProfileRepository = Mockito.mock(CustomerProfileRepository.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final AppointmentBillingService service = new AppointmentBillingService(
        invoiceRepository,
        customerProfileRepository,
        notificationService
    );

    @Test
    void createsAndNotifiesCustomerForAnAppointmentInvoice() {
        Appointment appointment = new Appointment();
        appointment.setId(14L);
        appointment.setBusinessId(1L);
        appointment.setCustomerId(2L);
        appointment.setServiceName("Consultation");
        appointment.setServicePrice(new BigDecimal("450.00"));
        appointment.setLoyaltyPointsRedeemed(5);
        appointment.setLoyaltyDiscountAmount(new BigDecimal("5.00"));
        CustomerProfile customer = new CustomerProfile();
        customer.setTenantId(5L);
        customer.setUserId(44L);

        Mockito.when(invoiceRepository.findByAppointmentId(14L)).thenReturn(Optional.empty());
        Mockito.when(invoiceRepository.save(any(Invoice.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(customerProfileRepository.findById(2L)).thenReturn(Optional.of(customer));

        Invoice invoice = service.createInvoiceForCompletedAppointment(appointment, 5L, 9L);

        assertEquals(14L, invoice.getAppointmentId());
        assertEquals(1L, invoice.getBusinessId());
        assertEquals(2L, invoice.getCustomerId());
        assertEquals(new BigDecimal("450.00"), invoice.getSubtotal());
        assertEquals(BigDecimal.ZERO, invoice.getGstAmount());
        assertEquals(new BigDecimal("445.00"), invoice.getTotalAmount());
        assertEquals(5, invoice.getLoyaltyPointsRedeemed());
        assertEquals(new BigDecimal("5.00"), invoice.getLoyaltyDiscountAmount());
        assertEquals(InvoiceStatus.SENT, invoice.getStatus());

        Mockito.verify(invoiceRepository).save(Mockito.any(Invoice.class));
        Mockito.verify(notificationService).create(
            5L,
            44L,
            "INVOICE_CREATED",
            "Appointment invoice",
            "Invoice " + invoice.getInvoiceNumber() + " for your completed Consultation appointment is ready to review.",
            "/dashboard/invoices"
        );
    }

    @Test
    void reusesExistingInvoiceInsteadOfCreatingAnother() {
        Appointment appointment = new Appointment();
        appointment.setId(14L);
        Invoice existingInvoice = new Invoice();
        Mockito.when(invoiceRepository.findByAppointmentId(14L)).thenReturn(Optional.of(existingInvoice));

        assertSame(existingInvoice, service.createInvoiceForCompletedAppointment(appointment, 5L, 9L));
        Mockito.verify(invoiceRepository, Mockito.never()).save(any(Invoice.class));
        Mockito.verifyNoInteractions(customerProfileRepository, notificationService);
    }
}
