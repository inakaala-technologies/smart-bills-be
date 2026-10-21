package com.bhive.appointment.service;

import com.bhive.appointment.entity.Appointment;
import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentBillingService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final NotificationService notificationService;

    public AppointmentBillingService(InvoiceRepository invoiceRepository,
                                     CustomerProfileRepository customerProfileRepository,
                                     NotificationService notificationService) {
        this.invoiceRepository = invoiceRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Invoice createInvoiceForCompletedAppointment(Appointment appointment, Long tenantId, Long actorUserId) {
        if (appointment.getId() == null) {
            throw new IllegalArgumentException("A saved appointment is required to create its invoice.");
        }

        Optional<Invoice> existingInvoice = invoiceRepository.findByAppointmentId(appointment.getId());
        if (existingInvoice.isPresent()) {
            return existingInvoice.get();
        }

        BigDecimal subtotal = appointment.getServicePrice() == null ? BigDecimal.ZERO : appointment.getServicePrice();
        BigDecimal loyaltyDiscount = appointment.getLoyaltyDiscountAmount() == null
            ? BigDecimal.ZERO
            : appointment.getLoyaltyDiscountAmount();
        if (subtotal.signum() < 0 || loyaltyDiscount.signum() < 0 || loyaltyDiscount.compareTo(subtotal) > 0) {
            throw new IllegalStateException("Appointment pricing is invalid; its invoice was not created.");
        }

        Invoice invoice = new Invoice();
        invoice.setAppointmentId(appointment.getId());
        invoice.setBusinessId(appointment.getBusinessId());
        invoice.setCustomerId(appointment.getCustomerId());
        invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setSubtotal(subtotal);
        invoice.setGstAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(subtotal.subtract(loyaltyDiscount));
        invoice.setLoyaltyPointsRedeemed(appointment.getLoyaltyPointsRedeemed() == null
            ? 0
            : appointment.getLoyaltyPointsRedeemed());
        invoice.setLoyaltyDiscountAmount(loyaltyDiscount);
        invoice.setStatus(InvoiceStatus.SENT);
        Invoice savedInvoice = invoiceRepository.save(invoice);

        customerProfileRepository.findById(appointment.getCustomerId())
            .filter(customer -> tenantId.equals(customer.getTenantId()))
            .map(CustomerProfile::getUserId)
            .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(actorUserId))
            .ifPresent(recipientUserId -> notificationService.create(
                tenantId,
                recipientUserId,
                "INVOICE_CREATED",
                "Appointment invoice",
                "Invoice " + savedInvoice.getInvoiceNumber() + " for your completed "
                    + appointment.getServiceName() + " appointment is ready to review.",
                "/dashboard/invoices"
            ));

        return savedInvoice;
    }
}
