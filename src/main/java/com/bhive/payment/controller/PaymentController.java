package com.bhive.payment.controller;

import com.bhive.billing.entity.Invoice;
import com.bhive.billing.entity.InvoiceStatus;
import com.bhive.billing.repository.InvoiceRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import com.bhive.payment.dto.PaymentCreateRequest;
import com.bhive.payment.dto.PaymentResponse;
import com.bhive.payment.entity.Payment;
import com.bhive.payment.repository.PaymentRepository;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BusinessMembershipService businessMembershipService;
    private final CustomerProfileRepository customerProfileRepository;
    private final NotificationService notificationService;

    public PaymentController(PaymentRepository paymentRepository,
                             InvoiceRepository invoiceRepository,
                             BusinessMembershipService businessMembershipService,
                             CustomerProfileRepository customerProfileRepository,
                             NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.businessMembershipService = businessMembershipService;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/payments")
    public List<PaymentResponse> getAllPayments() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
        }
        return paymentRepository.findAll().stream()
            .filter(payment -> businessMembershipService.userHasAccessToBusiness(userId, payment.getBusinessId(), tenantId))
            .map(PaymentResponse::from)
            .toList();
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        return paymentRepository.findById(id)
            .filter(payment -> tenantId == null || userId == null || businessMembershipService.userHasAccessToBusiness(userId, payment.getBusinessId(), tenantId))
            .map(PaymentResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || !businessMembershipService.userHasAccessToBusiness(userId, request.businessId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Invoice invoice = invoiceRepository.findById(request.invoiceId()).orElse(null);
        if (invoice == null || !request.businessId().equals(invoice.getBusinessId())
            || invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return ResponseEntity.badRequest().build();
        }

        Payment payment = new Payment();
        payment.setInvoiceId(invoice.getId());
        payment.setBusinessId(invoice.getBusinessId());
        payment.setCustomerId(invoice.getCustomerId());
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setStatus(com.bhive.payment.entity.PaymentStatus.PENDING);
        payment.setReferenceNumber("BH-" + UUID.randomUUID());
        payment.setPaymentDate(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        customerProfileRepository.findById(saved.getCustomerId())
            .filter(customer -> tenantId.equals(customer.getTenantId()))
            .map(CustomerProfile::getUserId)
            .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(userId))
            .ifPresent(recipientUserId -> notificationService.create(
                tenantId,
                recipientUserId,
                "PAYMENT_RECORDED",
                "Payment recorded",
                "A payment of " + saved.getAmount().stripTrailingZeros().toPlainString()
                    + " was recorded for invoice #" + saved.getInvoiceId() + ".",
                "/dashboard"
            ));
        return ResponseEntity.ok(PaymentResponse.from(saved));
    }
}
