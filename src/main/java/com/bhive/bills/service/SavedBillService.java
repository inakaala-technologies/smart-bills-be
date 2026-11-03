package com.bhive.bills.service;

import com.bhive.bills.dto.SavedBillResponse;
import com.bhive.bills.dto.SavedBillPdfResponse;
import com.bhive.bills.entity.SavedBill;
import com.bhive.bills.provider.ProviderFetchResponse;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.common.util.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SavedBillService {

    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id",
            "consumerNumber",
            "consumerIdentityHash",
            "providerInputData",
            "responseFormat",
            "contentType",
            "responseJson",
            "responseXml",
            "fetchedAt",
            "createdAt",
            "updatedAt"
    );
    private static final Sort DEFAULT_SAVED_BILLS_SORT =
            Sort.by(Sort.Order.desc("fetchedAt"));

    private static final TypeReference<Map<String, String>> INPUT_MAP_TYPE =
            new TypeReference<>() {
            };

    private final SavedBillRepository savedBillRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final SavedBillPersistenceService persistenceService;
    private final ConsumerIdentityResolver identityResolver;
    private final ObjectMapper objectMapper;

    public SavedBillService(
            SavedBillRepository savedBillRepository,
            CustomerProfileRepository customerProfileRepository,
            SavedBillPersistenceService persistenceService,
            ConsumerIdentityResolver identityResolver,
            ObjectMapper objectMapper
    ) {
        this.savedBillRepository = savedBillRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.persistenceService = persistenceService;
        this.identityResolver = identityResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Long requireAuthenticatedCustomerId() {
        return currentCustomer().getId();
    }

    public void saveFetchedBill(
            String providerCode,
            Map<String, String> providerInputs,
            ProviderFetchResponse response
    ) {
        Long customerId = requireAuthenticatedCustomerId();
        if (providerInputs == null) {
            throw new IllegalArgumentException("Provider inputs are required.");
        }
        validateResponse(response);

        String normalizedProviderCode = providerCode.trim().toUpperCase(Locale.ROOT);
        ConsumerIdentity identity = identityResolver.resolve(normalizedProviderCode, providerInputs);
        String inputData = serialize(providerInputs);
        String responseJson = response.jsonData() == null
                ? null
                : serialize(response.jsonData());
        String responseFormat = response.format().trim().toUpperCase(Locale.ROOT);

        persistenceService.completeManualFetch(
                customerId,
                normalizedProviderCode,
                identity.hash(),
                inputData,
                responseFormat,
                response.contentType(),
                responseJson,
                response.xmlData(),
                response.pdfData()
        );
    }

    public void prepareFetchAttempt(
            String providerCode,
            Map<String, String> providerInputs
    ) {
        if (providerInputs == null) {
            throw new IllegalArgumentException("Provider inputs are required.");
        }
        Long customerId = requireAuthenticatedCustomerId();
        String normalizedProviderCode = providerCode.trim().toUpperCase(Locale.ROOT);
        ConsumerIdentity identity = identityResolver.resolve(normalizedProviderCode, providerInputs);
        String inputData = serialize(providerInputs);

        try {
            persistenceService.prepareFetchAttempt(
                    customerId,
                    normalizedProviderCode,
                    identity,
                    inputData
            );
        } catch (DataIntegrityViolationException exception) {
            if (!persistenceService.identityExists(
                    customerId,
                    normalizedProviderCode,
                    identity.hash()
            )) {
                throw exception;
            }
            persistenceService.prepareFetchAttempt(
                    customerId,
                    normalizedProviderCode,
                    identity,
                    inputData
            );
        }
    }

    public void recordFetchFailure(
            String providerCode,
            Map<String, String> providerInputs,
            String errorCode
    ) {
        if (providerInputs == null) {
            throw new IllegalArgumentException("Provider inputs are required.");
        }
        Long customerId = requireAuthenticatedCustomerId();
        String normalizedProviderCode = providerCode.trim().toUpperCase(Locale.ROOT);
        ConsumerIdentity identity = identityResolver.resolve(normalizedProviderCode, providerInputs);
        persistenceService.markFetchFailed(
                customerId,
                normalizedProviderCode,
                identity.hash(),
                errorCode
        );
    }

    public void completeScheduledFetch(
            ScheduledBillFetch claim,
            ProviderFetchResponse response
    ) {
        validateResponse(response);
        persistenceService.completeScheduledFetch(
                claim,
                response.format().trim().toUpperCase(Locale.ROOT),
                response.contentType(),
                response.jsonData() == null ? null : serialize(response.jsonData()),
                response.xmlData(),
                response.pdfData()
        );
    }

    public void failScheduledFetch(
            ScheduledBillFetch claim,
            String errorCode,
            boolean temporary,
            long baseDelaySeconds,
            long maxDelaySeconds,
            int maxRetries
    ) {
        persistenceService.failScheduledFetch(
                claim,
                errorCode,
                temporary,
                baseDelaySeconds,
                maxDelaySeconds,
                maxRetries
        );
    }

    public void deferScheduledFetch(ScheduledBillFetch claim, long deferHours) {
        persistenceService.deferScheduledFetch(claim, deferHours);
    }

    @Transactional(readOnly = true)
    public Page<SavedBillResponse> getSavedBills(String category, Pageable pageable) {
        Long customerId = requireAuthenticatedCustomerId();
        Pageable safePageable = normalizePageable(pageable);
        Page<SavedBill> bills;
        if (category == null || category.isBlank()) {
            bills = savedBillRepository.findAllByCustomer_Id(customerId, safePageable);
        } else {
            String categoryCode = category.trim().toUpperCase(Locale.ROOT);
            if (!Set.of(
                    "ELECTRICITY", "WATER", "ECHALLAN", "INSURANCE", "DOCUMENTS"
            ).contains(categoryCode)) {
                throw new IllegalArgumentException(
                        "Category must be ELECTRICITY, WATER, ECHALLAN, INSURANCE, or DOCUMENTS."
                );
            }
            bills = savedBillRepository.findAllByCustomer_IdAndCategory_Code(
                    customerId,
                    categoryCode,
                    safePageable
            );
        }
        return bills.map(this::toResponse);
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return pageable;
        }

        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> validOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();
            if (property == null || property.isBlank() || "[]".equals(property.trim())) {
                continue;
            }
            if (!SORTABLE_PROPERTIES.contains(property)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported saved-bill sort property."
                );
            }
            validOrders.add(order);
        }

        Sort sort = validOrders.isEmpty() ? DEFAULT_SAVED_BILLS_SORT : Sort.by(validOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    @Transactional(readOnly = true)
    public SavedBillResponse getSavedBill(Long id) {
        Long customerId = requireAuthenticatedCustomerId();
        SavedBill bill = savedBillRepository.findByIdAndCustomer_Id(id, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Saved bill was not found."
                ));
        return toResponse(bill);
    }

    @Transactional(readOnly = true)
    public SavedBillPdfResponse getSavedBillForPdf(Long id) {
        Long customerId = requireAuthenticatedCustomerId();
        SavedBill bill = savedBillRepository.findByIdAndCustomer_Id(id, customerId)
                .filter(savedBill -> savedBill.getPdfData() != null && savedBill.getPdfData().length > 0)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Saved bill PDF was not found."
                ));
        return new SavedBillPdfResponse(bill.getContentType(), bill.getPdfData());
    }

    private CustomerProfile currentCustomer() {
        Long userId = TenantContext.getUserId();
        Long tenantId = TenantContext.getTenantId();
        if (userId == null || tenantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required to access saved bills."
            );
        }
        return customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Authenticated customer profile was not found."
                ));
    }

    private void validateResponse(ProviderFetchResponse response) {
        if (response == null || response.format() == null || response.format().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The bill provider returned an invalid response."
            );
        }
        String format = response.format().trim().toUpperCase(Locale.ROOT);
        boolean valid = switch (format) {
            case "JSON" -> response.jsonData() != null && !response.jsonData().isNull();
            case "XML" -> response.xmlData() != null && !response.xmlData().isBlank();
            case "PDF" -> response.pdfData() != null && response.pdfData().length > 0;
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The bill provider returned an invalid response."
            );
        }
    }

    private SavedBillResponse toResponse(SavedBill bill) {
        JsonNode jsonData = bill.getResponseJson() == null
                ? null
                : readJson(bill.getResponseJson());
        Map<String, String> providerInputs = readInputs(bill.getProviderInputData());
        return new SavedBillResponse(
                bill.getId(),
                bill.getProvider().getCode(),
                bill.getProvider().getName(),
                bill.getCategory().getCode(),
                bill.getConsumerNumber(),
                providerInputs,
                bill.getResponseFormat(),
                bill.getContentType(),
                jsonData,
                bill.getResponseXml(),
                bill.getPdfData() != null && bill.getPdfData().length > 0,
                bill.getFetchedAt(),
                bill.getCreatedAt(),
                bill.getUpdatedAt()
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize saved bill data.", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored bill JSON is invalid.", exception);
        }
    }

    private Map<String, String> readInputs(String value) {
        try {
            return objectMapper.readValue(value, INPUT_MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored bill input data is invalid.", exception);
        }
    }
}
