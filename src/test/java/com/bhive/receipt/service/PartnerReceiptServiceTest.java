package com.bhive.receipt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bhive.notification.service.NotificationService;
import com.bhive.receipt.dto.PartnerReceiptSubmissionRequest;
import com.bhive.receipt.dto.ReceiptLineItem;
import com.bhive.receipt.entity.PosIntegration;
import com.bhive.receipt.entity.ReceiptRecord;
import com.bhive.receipt.repository.PosIntegrationRepository;
import com.bhive.receipt.repository.ReceiptPartnerNonceRepository;
import com.bhive.receipt.repository.ReceiptRecordRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bhive.receipt.service.ReceiptLinkTokenService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PartnerReceiptServiceTest {

    private static final String PARTNER_ID = "c79947ce-854b-4ce8-92c7-ad6fbc67953c";
    private static final String IDEMPOTENCY_KEY = "sale-2026-001";

    @Mock
    private PosIntegrationRepository integrationRepository;

    @Mock
    private ReceiptPartnerNonceRepository nonceRepository;

    @Mock
    private ReceiptRecordRepository receiptRepository;

    @Mock
    private ReceiptLinkTokenService receiptLinkTokenService;

    @Mock
    private ReceiptDeliveryPreferenceService receiptDeliveryPreferenceService;

    @Mock
    private NotificationService notificationService;

    private PartnerReceiptService service;
    private KeyPair keyPair;
    private PosIntegration integration;
    private String timestamp;
    private String nonce;
    private byte[] body;
    private String signature;

    @BeforeEach
    void setUp() throws Exception {
        service = new PartnerReceiptService(
            integrationRepository,
            nonceRepository,
            receiptRepository,
            receiptLinkTokenService,
            receiptDeliveryPreferenceService,
            notificationService,
            new ObjectMapper()
        );
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        integration = new PosIntegration(
            PARTNER_ID, "Demo POS", "retailer-1", "Demo Market", "outlet-1", "Demo Outlet", encodedPublicKey
        );
        PartnerReceiptSubmissionRequest request = new PartnerReceiptSubmissionRequest(
            "sale-2026-001", "R-1001", "customer-link-token", null, null, "retailer-1", "outlet-1",
            "2026-11-09T05:30:00Z", "INR", new BigDecimal("125.00"),
            List.of(new ReceiptLineItem("Tea", new BigDecimal("1"), new BigDecimal("125.00"), new BigDecimal("125.00")))
        );
        body = new ObjectMapper().writeValueAsBytes(request);
        timestamp = Instant.now().toString();
        nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[24]);
        signature = sign(timestamp, nonce, IDEMPOTENCY_KEY, body);
    }

    @Test
    void savesReceiptAndConsumesCustomerTokenAtomicallyForApprovedIntegration() throws Exception {
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.empty());
        when(receiptLinkTokenService.consumeToken("customer-link-token"))
            .thenReturn(Optional.of(new ReceiptRecipient(88L, 7L)));
        when(receiptRepository.save(any(ReceiptRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submit(PARTNER_ID, timestamp, nonce, signature, IDEMPOTENCY_KEY, body);

        assertTrue(result.created());
        assertEquals("R-1001", result.response().receiptNumber());
        ArgumentCaptor<ReceiptRecord> saved = ArgumentCaptor.forClass(ReceiptRecord.class);
        verify(receiptRepository).save(saved.capture());
        assertEquals(88L, saved.getValue().getUserId());
        assertTrue(saved.getValue().getItemsJson().contains("Tea"));
        assertFalse(saved.getValue().getItemsJson().contains("customer-link-token"));
        verify(notificationService).create(
            7L,
            88L,
            "RECEIPT_RECEIVED",
            "New receipt received",
            "A receipt from Demo Market is now available in your BHive receipt inbox.",
            "/customer/dashboard/receipts"
        );
        verify(nonceRepository).saveAndFlush(any());
    }

    @Test
    void findsTenantForReceiptLinkTokensIssuedBeforeTenantWasStored() throws Exception {
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.empty());
        when(receiptLinkTokenService.consumeToken("customer-link-token"))
            .thenReturn(Optional.of(new ReceiptRecipient(88L, null)));
        when(receiptDeliveryPreferenceService.findRecipientsByUserId(88L))
            .thenReturn(List.of(new ReceiptRecipient(88L, 7L)));
        when(receiptRepository.save(any(ReceiptRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(PARTNER_ID, timestamp, nonce, signature, IDEMPOTENCY_KEY, body);

        verify(notificationService).create(
            7L,
            88L,
            "RECEIPT_RECEIVED",
            "New receipt received",
            "A receipt from Demo Market is now available in your BHive receipt inbox.",
            "/customer/dashboard/receipts"
        );
    }

    @Test
    void acceptsOptedInEmailWithoutRevealingWhetherItMatched() throws Exception {
        String customerEmail = "customer@example.com";
        PartnerReceiptSubmissionRequest request = requestWithEmail(customerEmail);
        byte[] receiptBody = new ObjectMapper().writeValueAsBytes(request);
        String receiptNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[26]);
        String receiptSignature = sign(timestamp, receiptNonce, IDEMPOTENCY_KEY, receiptBody);
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.empty());
        when(receiptDeliveryPreferenceService.findOptedInRecipientsByEmail(customerEmail))
            .thenReturn(List.of(new ReceiptRecipient(88L, 7L)));
        when(receiptRepository.save(any(ReceiptRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submit(PARTNER_ID, timestamp, receiptNonce, receiptSignature, IDEMPOTENCY_KEY, receiptBody);

        assertTrue(result.genericAcknowledgement());
        assertEquals("ACCEPTED", result.response().status());
        assertEquals(null, result.response().receiptId());
        ArgumentCaptor<ReceiptRecord> saved = ArgumentCaptor.forClass(ReceiptRecord.class);
        verify(receiptRepository).save(saved.capture());
        assertEquals(88L, saved.getValue().getUserId());
        verify(notificationService).create(
            7L,
            88L,
            "RECEIPT_RECEIVED",
            "New receipt received",
            "A receipt from Demo Market is now available in your BHive receipt inbox.",
            "/customer/dashboard/receipts"
        );
    }

    @Test
    void notifiesOptedInPhoneRecipientWhenReceiptIsCreated() throws Exception {
        String customerPhone = "+91 98765-43210";
        PartnerReceiptSubmissionRequest request = requestWithPhone(customerPhone);
        byte[] receiptBody = new ObjectMapper().writeValueAsBytes(request);
        String receiptNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[28]);
        String receiptSignature = sign(timestamp, receiptNonce, IDEMPOTENCY_KEY, receiptBody);
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.empty());
        when(receiptDeliveryPreferenceService.findOptedInRecipientsByPhone(customerPhone))
            .thenReturn(List.of(new ReceiptRecipient(88L, 7L)));
        when(receiptRepository.save(any(ReceiptRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submit(PARTNER_ID, timestamp, receiptNonce, receiptSignature, IDEMPOTENCY_KEY, receiptBody);

        assertTrue(result.created());
        assertTrue(result.genericAcknowledgement());
        verify(notificationService).create(
            7L,
            88L,
            "RECEIPT_RECEIVED",
            "New receipt received",
            "A receipt from Demo Market is now available in your BHive receipt inbox.",
            "/customer/dashboard/receipts"
        );
    }

    @Test
    void rejectsUnknownOrOptedOutPhoneWithoutSavingReceipt() throws Exception {
        String customerPhone = "+91 98765-43210";
        PartnerReceiptSubmissionRequest request = requestWithPhone(customerPhone);
        byte[] receiptBody = new ObjectMapper().writeValueAsBytes(request);
        String receiptNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[27]);
        String receiptSignature = sign(timestamp, receiptNonce, IDEMPOTENCY_KEY, receiptBody);
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.empty());
        when(receiptDeliveryPreferenceService.findOptedInRecipientsByPhone(customerPhone))
            .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.submit(PARTNER_ID, timestamp, receiptNonce, receiptSignature, IDEMPOTENCY_KEY, receiptBody)
        );

        assertEquals(422, exception.getStatusCode().value());
        verify(receiptRepository, never()).save(any(ReceiptRecord.class));
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
        verify(nonceRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownOrOptedOutEmailWithoutSavingReceipt() throws Exception {
        String customerEmail = "unknown@example.com";
        PartnerReceiptSubmissionRequest request = requestWithEmail(customerEmail);
        byte[] receiptBody = new ObjectMapper().writeValueAsBytes(request);
        String receiptNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[24]);
        String receiptSignature = sign(timestamp, receiptNonce, IDEMPOTENCY_KEY, receiptBody);
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.empty());
        when(receiptDeliveryPreferenceService.findOptedInRecipientsByEmail(customerEmail))
            .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.submit(PARTNER_ID, timestamp, receiptNonce, receiptSignature, IDEMPOTENCY_KEY, receiptBody)
        );

        assertEquals(422, exception.getStatusCode().value());
        verify(receiptRepository, never()).save(any(ReceiptRecord.class));
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
        verify(nonceRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsTamperedRequestBeforeConsumingCustomerToken() {
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));

        assertThrows(ResponseStatusException.class,
            () -> service.submit(PARTNER_ID, timestamp, nonce, "invalid", IDEMPOTENCY_KEY, body));

        verify(receiptLinkTokenService, never()).consumeToken(any());
        verify(receiptRepository, never()).save(any(ReceiptRecord.class));
    }

    @Test
    void rejectsFreshSignedRetryWithMatchingIdempotencyKey() throws Exception {
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        ReceiptRecord existing = new ReceiptRecord(
            88L, PARTNER_ID, "sale-2026-001", IDEMPOTENCY_KEY,
            java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body)),
            "R-1001", "Demo Market", "Demo Outlet", "retailer-1", "outlet-1",
            java.time.LocalDateTime.of(2026, 11, 9, 5, 30), "INR", new BigDecimal("125.00"), "[]"
        );
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existing));
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.of(existing));
        String retryNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[25]);
        String retrySignature = sign(timestamp, retryNonce, IDEMPOTENCY_KEY, body);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.submit(PARTNER_ID, timestamp, retryNonce, retrySignature, IDEMPOTENCY_KEY, body)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(receiptLinkTokenService, never()).consumeToken(any());
        verify(nonceRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateExternalReceiptIdWithDifferentIdempotencyKey() throws Exception {
        when(integrationRepository.findByPartnerIdAndStatusForUpdate(PARTNER_ID, "ACTIVE"))
            .thenReturn(Optional.of(integration));
        ReceiptRecord existing = new ReceiptRecord(
            88L, PARTNER_ID, "sale-2026-001", IDEMPOTENCY_KEY,
            "existing-fingerprint", "R-1001", "Demo Market", "Demo Outlet", "retailer-1", "outlet-1",
            java.time.LocalDateTime.of(2026, 11, 9, 5, 30), "INR", new BigDecimal("125.00"), "[]"
        );
        when(receiptRepository.findByPartnerIdAndIdempotencyKey(PARTNER_ID, "sale-2026-002"))
            .thenReturn(Optional.empty());
        when(receiptRepository.findByPartnerIdAndExternalReceiptId(PARTNER_ID, "sale-2026-001"))
            .thenReturn(Optional.of(existing));
        String retryNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[23]);
        String retrySignature = sign(timestamp, retryNonce, "sale-2026-002", body);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.submit(PARTNER_ID, timestamp, retryNonce, retrySignature, "sale-2026-002", body)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(nonceRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any());
    }

    private String sign(String signedAt, String requestNonce, String key, byte[] rawBody) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        String header = "BHIVE_POS_RECEIPT_V1\nPOST\n" + PartnerReceiptService.RECEIPT_PATH + "\n"
            + PARTNER_ID + "\n" + signedAt + "\n" + requestNonce + "\n" + key + "\n";
        signer.update(header.getBytes(StandardCharsets.UTF_8));
        signer.update(rawBody);
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private PartnerReceiptSubmissionRequest requestWithEmail(String customerEmail) {
        return new PartnerReceiptSubmissionRequest(
            "sale-2026-001", "R-1001", null, customerEmail, null, "retailer-1", "outlet-1",
            "2026-11-09T05:30:00Z", "INR", new BigDecimal("125.00"),
            List.of(new ReceiptLineItem("Tea", new BigDecimal("1"), new BigDecimal("125.00"), new BigDecimal("125.00")))
        );
    }

    private PartnerReceiptSubmissionRequest requestWithPhone(String customerPhone) {
        return new PartnerReceiptSubmissionRequest(
            "sale-2026-001", "R-1001", null, null, customerPhone, "retailer-1", "outlet-1",
            "2026-11-09T05:30:00Z", "INR", new BigDecimal("125.00"),
            List.of(new ReceiptLineItem("Tea", new BigDecimal("1"), new BigDecimal("125.00"), new BigDecimal("125.00")))
        );
    }
}
