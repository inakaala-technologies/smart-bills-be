package com.bhive.bills.service;

import com.bhive.bills.dto.CustomerBillConsentResponse;
import com.bhive.bills.dto.RecordCustomerBillConsentRequest;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.CustomerBillConsent;
import com.bhive.bills.entity.SavedBill;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.CustomerBillConsentRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

@Service
public class CustomerBillConsentService {

    private static final TypeReference<List<String>> FIELD_LIST_TYPE =
            new TypeReference<>() {
            };
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id", "status", "purposeCode", "statementVersion", "acceptedAt", "createdAt", "updatedAt"
    );

    private final CustomerBillConsentRepository consentRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BillProviderRepository providerRepository;
    private final ProviderInputFieldRepository inputFieldRepository;
    private final SavedBillRepository savedBillRepository;
    private final SavedBillService savedBillService;
    private final ObjectMapper objectMapper;

    public CustomerBillConsentService(
            CustomerBillConsentRepository consentRepository,
            CustomerProfileRepository customerProfileRepository,
            BillProviderRepository providerRepository,
            ProviderInputFieldRepository inputFieldRepository,
            SavedBillRepository savedBillRepository,
            SavedBillService savedBillService,
            ObjectMapper objectMapper
    ) {
        this.consentRepository = consentRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.providerRepository = providerRepository;
        this.inputFieldRepository = inputFieldRepository;
        this.savedBillRepository = savedBillRepository;
        this.savedBillService = savedBillService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustomerBillConsentResponse recordAcceptance(
            RecordCustomerBillConsentRequest request
    ) {
        if (request.accepted() == null || !request.accepted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Consent acceptance must be explicitly true."
            );
        }
        if (!ApplicationConsentStatement.VERSION.equals(request.statementVersion())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The consent statement version is not current."
            );
        }
        if (!ApplicationConsentStatement.PURPOSE_CODE.equals(request.purposeCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported consent purpose."
            );
        }

        CustomerProfile customer = currentCustomer();
        String providerCode = request.providerCode().trim().toUpperCase(Locale.ROOT);
        BillProvider provider = providerRepository.findByCodeAndActiveTrue(providerCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Provider not found or inactive."
                ));
        List<String> requestedFields = validateFields(providerCode, request.requestedDataFields());
        SavedBill savedBill = request.savedBillId() == null
                ? null
                : findSavedBill(request.savedBillId(), customer.getId(), providerCode);

        CustomerBillConsent consent = new CustomerBillConsent(
                customer,
                provider,
                serialize(requestedFields),
                ApplicationConsentStatement.PURPOSE_CODE,
                ApplicationConsentStatement.VERSION,
                statementHash(),
                LocalDateTime.now(),
                savedBill
        );
        return toResponse(consentRepository.saveAndFlush(consent));
    }

    @Transactional(readOnly = true)
    public Page<CustomerBillConsentResponse> getHistory(
            String providerCode,
            String status,
            Pageable pageable
    ) {
        Long customerId = currentCustomer().getId();
        Pageable safePageable = normalizePageable(pageable);
        String normalizedProvider = providerCode == null || providerCode.isBlank()
                ? null
                : providerCode.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = normalizeStatus(status);

        Page<CustomerBillConsent> consents;
        if (normalizedProvider != null && normalizedStatus != null) {
            consents = consentRepository.findAllByCustomer_IdAndProvider_CodeAndStatus(
                    customerId, normalizedProvider, normalizedStatus, safePageable
            );
        } else if (normalizedProvider != null) {
            consents = consentRepository.findAllByCustomer_IdAndProvider_Code(
                    customerId, normalizedProvider, safePageable
            );
        } else if (normalizedStatus != null) {
            consents = consentRepository.findAllByCustomer_IdAndStatus(
                    customerId, normalizedStatus, safePageable
            );
        } else {
            consents = consentRepository.findAllByCustomer_Id(customerId, safePageable);
        }
        return consents.map(this::toResponse);
    }

    @Transactional
    public CustomerBillConsentResponse revoke(String consentRecordId) {
        Long customerId = currentCustomer().getId();
        CustomerBillConsent consent = consentRepository
                .findByConsentRecordIdAndCustomer_Id(consentRecordId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Consent record was not found."
                ));
        consent.revoke(LocalDateTime.now());
        return toResponse(consentRepository.saveAndFlush(consent));
    }

    @Transactional
    public Long authorizeAndPrepareManualFetch(
            String consentRecordId,
            Long authenticatedCustomerId,
            String providerCode,
            List<String> requestedFields,
            Map<String, String> providerInputs
    ) {
        CustomerProfile customer = currentCustomer();
        if (!customer.getId().equals(authenticatedCustomerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent record was not found.");
        }
        String normalizedProviderCode = providerCode.trim().toUpperCase(Locale.ROOT);
        CustomerBillConsent consent = consentRepository
                .findByConsentRecordIdAndCustomer_Id(consentRecordId, authenticatedCustomerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Consent record was not found."
                ));
        if (!"ACCEPTED".equals(consent.getStatus()) || consent.getSavedBill() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Consent is revoked or has already been used."
            );
        }
        if (providerInputs == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider inputs are required.");
        }
        if (!normalizedProviderCode.equalsIgnoreCase(consent.getProvider().getCode())
                || !ApplicationConsentStatement.PURPOSE_CODE.equals(consent.getPurposeCode())
                || !ApplicationConsentStatement.VERSION.equals(consent.getStatementVersion())
                || !sameFieldSet(readRequestedFields(consent), requestedFields)
                || !sameFieldSet(requestedFields, List.copyOf(providerInputs.keySet()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Consent does not match this provider fetch."
            );
        }

        Long savedBillId = savedBillService.prepareFetchAttempt(
                normalizedProviderCode,
                providerInputs
        );
        SavedBill savedBill = savedBillRepository.findByIdAndCustomer_Id(
                        savedBillId,
                        authenticatedCustomerId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Prepared saved bill was not found for the authenticated customer."
                ));
        consent.linkToSavedBill(savedBill);
        consentRepository.saveAndFlush(consent);
        savedBill.setApplicationConsent(consent);
        savedBillRepository.saveAndFlush(savedBill);
        return savedBillId;
    }

    private CustomerProfile currentCustomer() {
        Long userId = TenantContext.getUserId();
        Long tenantId = TenantContext.getTenantId();
        if (userId == null || tenantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required to manage bill consents."
            );
        }
        return customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Authenticated customer profile was not found."
                ));
    }

    private List<String> validateFields(String providerCode, List<String> requestedFields) {
        if (requestedFields == null || requestedFields.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one requested data field is required."
            );
        }

        Set<String> allowedFields = new HashSet<>();
        inputFieldRepository
                .findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc(providerCode)
                .forEach(field -> allowedFields.add(field.getFieldKey()));

        Set<String> uniqueRequestedFields = new HashSet<>();
        for (String field : requestedFields) {
            if (field == null || field.isBlank()
                    || !uniqueRequestedFields.add(field)
                    || !allowedFields.contains(field)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Requested data fields must be unique active fields configured for the provider."
                );
            }
        }
        return List.copyOf(requestedFields);
    }

    private List<String> readRequestedFields(CustomerBillConsent consent) {
        try {
            return objectMapper.readValue(consent.getRequestedDataFields(), FIELD_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored consent field names are invalid.", exception);
        }
    }

    private boolean sameFieldSet(List<String> first, List<String> second) {
        return first != null && second != null
                && first.size() == second.size()
                && new HashSet<>(first).equals(new HashSet<>(second));
    }

    private SavedBill findSavedBill(Long savedBillId, Long customerId, String providerCode) {
        SavedBill bill = savedBillRepository.findByIdAndCustomer_Id(savedBillId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Saved bill was not found."
                ));
        if (!providerCode.equalsIgnoreCase(bill.getProvider().getCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Saved bill does not belong to the selected provider."
            );
        }
        return bill;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACCEPTED", "REVOKED").contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Consent status must be ACCEPTED or REVOKED."
            );
        }
        return normalized;
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getSort().isUnsorted()) {
            return pageable;
        }
        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();
            if (property == null || property.isBlank()
                    || !SORTABLE_PROPERTIES.contains(property)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported consent history sort property."
                );
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
    }

    private String statementHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ApplicationConsentStatement.TEXT.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String serialize(List<String> requestedFields) {
        try {
            return objectMapper.writeValueAsString(requestedFields);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize consent field names.", exception);
        }
    }

    private CustomerBillConsentResponse toResponse(CustomerBillConsent consent) {
        try {
            return new CustomerBillConsentResponse(
                    consent.getConsentRecordId(),
                    consent.getProvider().getCode(),
                    consent.getProvider().getName(),
                    objectMapper.readValue(consent.getRequestedDataFields(), FIELD_LIST_TYPE),
                    consent.getPurposeCode(),
                    consent.getStatementVersion(),
                    consent.getStatementHash(),
                    consent.getStatus(),
                    consent.getAcceptedAt(),
                    consent.getRevokedAt(),
                    consent.getSavedBill() == null ? null : consent.getSavedBill().getId(),
                    consent.getCreatedAt(),
                    consent.getUpdatedAt()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Stored consent field names are invalid.", exception);
        }
    }
}
