package com.bhive.receipt.service;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bhive.receipt.dto.CustomerReceiptResponse;
import com.bhive.receipt.dto.PartnerReceiptSubmissionRequest;
import com.bhive.receipt.dto.PartnerReceiptSubmissionResponse;
import com.bhive.receipt.dto.ReceiptLineItem;
import com.bhive.receipt.entity.PosIntegration;
import com.bhive.receipt.entity.ReceiptPartnerNonce;
import com.bhive.receipt.entity.ReceiptRecord;
import com.bhive.receipt.repository.PosIntegrationRepository;
import com.bhive.receipt.repository.ReceiptPartnerNonceRepository;
import com.bhive.receipt.repository.ReceiptRecordRepository;
import com.bhive.notification.service.NotificationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PartnerReceiptService {

    public static final String RECEIPT_PATH = "/api/v1/partner/receipts";
    private static final Duration MAX_SIGNATURE_SKEW = Duration.ofMinutes(5);
    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22,128}");
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("[+0-9().\\-\\s]+");

    private final PosIntegrationRepository integrationRepository;
    private final ReceiptPartnerNonceRepository nonceRepository;
    private final ReceiptRecordRepository receiptRepository;
    private final ReceiptLinkTokenService receiptLinkTokenService;
    private final ReceiptDeliveryPreferenceService receiptDeliveryPreferenceService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public PartnerReceiptService(
        PosIntegrationRepository integrationRepository,
        ReceiptPartnerNonceRepository nonceRepository,
        ReceiptRecordRepository receiptRepository,
        ReceiptLinkTokenService receiptLinkTokenService,
        ReceiptDeliveryPreferenceService receiptDeliveryPreferenceService,
        NotificationService notificationService,
        ObjectMapper objectMapper
    ) {
        this.integrationRepository = integrationRepository;
        this.nonceRepository = nonceRepository;
        this.receiptRepository = receiptRepository;
        this.receiptLinkTokenService = receiptLinkTokenService;
        this.receiptDeliveryPreferenceService = receiptDeliveryPreferenceService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubmissionResult submit(
        String partnerId,
        String timestamp,
        String nonce,
        String signature,
        String idempotencyKey,
        byte[] rawBody
    ) {
        PosIntegration integration = integrationRepository.findByPartnerIdAndStatusForUpdate(partnerId, "ACTIVE")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner integration is not active."));
        parseAndValidateTimestamp(timestamp);
        if (nonce == null || !NONCE_PATTERN.matcher(nonce).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid partner nonce.");
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key.");
        }
        verifySignature(integration, partnerId, timestamp, nonce, signature, idempotencyKey, rawBody);

        PartnerReceiptSubmissionRequest request = parseRequest(rawBody);
        Instant transactionAt = validateRequest(request, integration);
        boolean genericAcknowledgement = !isBlank(request.customerEmail()) || !isBlank(request.customerPhone());
        String fingerprint = sha256Hex(rawBody);

        ReceiptRecord byIdempotencyKey = receiptRepository
            .findByPartnerIdAndIdempotencyKey(partnerId, idempotencyKey)
            .orElse(null);
        ReceiptRecord byExternalId = receiptRepository
            .findByPartnerIdAndExternalReceiptId(partnerId, request.externalReceiptId().trim())
            .orElse(null);
        if (byIdempotencyKey != null || byExternalId != null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Duplicate receipt submission; the earlier request may already have been accepted."
            );
        }

        List<ReceiptRecipient> recipients;
        if (genericAcknowledgement) {
            recipients = !isBlank(request.customerEmail())
                ? receiptDeliveryPreferenceService.findOptedInRecipientsByEmail(request.customerEmail())
                : receiptDeliveryPreferenceService.findOptedInRecipientsByPhone(request.customerPhone());
        } else {
            ReceiptRecipient tokenRecipient = receiptLinkTokenService.consumeToken(request.customerLinkToken().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Customer receipt-link token is invalid, expired, or already used."));
            if (tokenRecipient.tenantId() == null) {
                recipients = receiptDeliveryPreferenceService.findRecipientsByUserId(tokenRecipient.userId());
                if (recipients.isEmpty()) {
                    recipients = List.of(tokenRecipient);
                }
            } else {
                recipients = List.of(tokenRecipient);
            }
        }
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "No eligible customer receipt recipient was found for the supplied contact."
            );
        }
        saveNonce(partnerId, nonce);
        String itemsJson = serializeItems(request.items());
        ReceiptRecord receipt = receiptRepository.save(new ReceiptRecord(
            recipients.get(0).userId(),
            partnerId,
            request.externalReceiptId().trim(),
            idempotencyKey,
            fingerprint,
            request.receiptNumber().trim(),
            integration.getRetailerName(),
            integration.getOutletName(),
            integration.getRetailerReference(),
            integration.getOutletReference(),
            LocalDateTime.ofInstant(transactionAt, ZoneOffset.UTC),
            request.currency(),
            request.totalAmount(),
            itemsJson
        ));
        recipients.stream()
            .filter(recipient -> recipient.tenantId() != null)
            .forEach(recipient -> notificationService.create(
                recipient.tenantId(),
                recipient.userId(),
                "RECEIPT_RECEIVED",
                "New receipt received",
                "A receipt from " + integration.getRetailerName() + " is now available in your BHive receipt inbox.",
                "/customer/dashboard/receipts"
            ));
        return new SubmissionResult(
            genericAcknowledgement ? toGenericAcknowledgement(request) : toSubmissionResponse(receipt),
            true,
            genericAcknowledgement
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerReceiptResponse> getCustomerReceipts(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "An authenticated customer is required.");
        }
        return receiptRepository.findTop100ByUserIdOrderByTransactionAtDesc(userId).stream()
            .map(this::toCustomerResponse)
            .toList();
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void deleteExpiredNonces() {
        nonceRepository.deleteCreatedBefore(LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(1)));
    }

    private Instant parseAndValidateTimestamp(String timestamp) {
        try {
            Instant signedAt = Instant.parse(timestamp);
            if (Duration.between(signedAt, Instant.now()).abs().compareTo(MAX_SIGNATURE_SKEW) > 0) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner signature timestamp is outside the allowed window.");
            }
            return signedAt;
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Partner timestamp must be an ISO-8601 UTC instant.");
        }
    }

    private void verifySignature(
        PosIntegration integration,
        String partnerId,
        String timestamp,
        String nonce,
        String encodedSignature,
        String idempotencyKey,
        byte[] rawBody
    ) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(integration.getPublicKey());
            var publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            String header = "BHIVE_POS_RECEIPT_V1\nPOST\n" + RECEIPT_PATH + "\n"
                + partnerId + "\n" + timestamp + "\n" + nonce + "\n" + idempotencyKey + "\n";
            verifier.update(header.getBytes(StandardCharsets.UTF_8));
            verifier.update(rawBody);
            if (!verifier.verify(Base64.getDecoder().decode(encodedSignature))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner signature is invalid.");
            }
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Partner signature is invalid.");
        }
    }

    private PartnerReceiptSubmissionRequest parseRequest(byte[] rawBody) {
        try {
            return objectMapper.readerFor(PartnerReceiptSubmissionRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(rawBody);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt request JSON is invalid.");
        }
    }

    private Instant validateRequest(PartnerReceiptSubmissionRequest request, PosIntegration integration) {
        if (request == null
            || isBlank(request.externalReceiptId()) || request.externalReceiptId().length() > 100
            || isBlank(request.receiptNumber()) || request.receiptNumber().length() > 100
            || !hasExactlyOneRecipient(request)
            || !integration.getRetailerReference().equals(request.retailerReference())
            || !integration.getOutletReference().equals(request.outletReference())
            || request.currency() == null || !request.currency().matches("[A-Z]{3}")
            || !isValidAmount(request.totalAmount())
            || request.items() == null || request.items().isEmpty() || request.items().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt fields do not match the required contract or approved outlet.");
        }
        if (request.customerLinkToken() != null && request.customerLinkToken().length() > 128
            || request.customerEmail() != null
                && (request.customerEmail().length() > 254 || !EMAIL_PATTERN.matcher(request.customerEmail().trim()).matches())
            || request.customerPhone() != null && !isValidPhone(request.customerPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt recipient contact is invalid.");
        }
        for (ReceiptLineItem item : request.items()) {
            if (item == null || isBlank(item.productName()) || item.productName().length() > 200
                || !isValidNonNegativeAmount(item.quantity(), 3) || item.quantity().signum() == 0
                || !isValidNonNegativeAmount(item.unitPrice(), 2)
                || !isValidNonNegativeAmount(item.lineTotal(), 2)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt contains an invalid line item.");
            }
        }
        try {
            Instant transactionAt = Instant.parse(request.transactionAt());
            if (transactionAt.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
                || transactionAt.isAfter(Instant.now().plus(Duration.ofHours(24)))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionAt is outside the accepted range.");
            }
            return transactionAt;
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionAt must be an ISO-8601 UTC instant.");
        }
    }

    private boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.scale() <= 2
            && amount.compareTo(new BigDecimal("999999999999.99")) <= 0;
    }

    private boolean isValidNonNegativeAmount(BigDecimal amount, int maxScale) {
        return amount != null && amount.signum() >= 0 && amount.scale() <= maxScale
            && amount.compareTo(new BigDecimal("999999999999.99")) <= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasExactlyOneRecipient(PartnerReceiptSubmissionRequest request) {
        boolean hasLinkToken = !isBlank(request.customerLinkToken());
        boolean hasEmail = !isBlank(request.customerEmail());
        boolean hasPhone = !isBlank(request.customerPhone());
        return (hasLinkToken ? 1 : 0) + (hasEmail ? 1 : 0) + (hasPhone ? 1 : 0) == 1;
    }

    private boolean isValidPhone(String phone) {
        if (phone.length() > 30 || !PHONE_PATTERN.matcher(phone).matches()) {
            return false;
        }
        String normalized = phone.replaceAll("[^0-9]", "");
        return normalized.length() >= 8 && normalized.length() <= 15;
    }

    private void saveNonce(String partnerId, String nonce) {
        try {
            nonceRepository.saveAndFlush(new ReceiptPartnerNonce(partnerId, nonce));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Partner nonce has already been used.");
        }
    }

    private String serializeItems(List<ReceiptLineItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize validated receipt line items.", exception);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private PartnerReceiptSubmissionResponse toSubmissionResponse(ReceiptRecord receipt) {
        return new PartnerReceiptSubmissionResponse(
            receipt.getId(),
            receipt.getExternalReceiptId(),
            receipt.getReceiptNumber(),
            "RECEIVED",
            receipt.getReceivedAt() == null ? null : receipt.getReceivedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private PartnerReceiptSubmissionResponse toGenericAcknowledgement(PartnerReceiptSubmissionRequest request) {
        return new PartnerReceiptSubmissionResponse(
            null,
            request.externalReceiptId().trim(),
            request.receiptNumber().trim(),
            "ACCEPTED",
            null
        );
    }

    private CustomerReceiptResponse toCustomerResponse(ReceiptRecord receipt) {
        try {
            List<ReceiptLineItem> items = objectMapper.readValue(
                receipt.getItemsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ReceiptLineItem.class)
            );
            return new CustomerReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getRetailerName(),
                receipt.getOutletName(),
                receipt.getTransactionAt().toInstant(ZoneOffset.UTC),
                receipt.getCurrency(),
                receipt.getTotalAmount(),
                items
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored receipt line items are invalid.", exception);
        }
    }

    public record SubmissionResult(
        PartnerReceiptSubmissionResponse response,
        boolean created,
        boolean genericAcknowledgement
    ) {
    }
}
