package com.bhive.bills.service;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.SavedBill;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SavedBillPersistenceService {

    private final SavedBillRepository savedBillRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BillProviderRepository providerRepository;
    @Value("${app.bills.fetch.interval-hours:24}")
    private long fetchIntervalHours = 24;
    @Value("${app.bills.fetch.stale-claim-minutes:15}")
    private long staleClaimMinutes = 15;

    public SavedBillPersistenceService(
            SavedBillRepository savedBillRepository,
            CustomerProfileRepository customerProfileRepository,
            BillProviderRepository providerRepository
    ) {
        this.savedBillRepository = savedBillRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional
    public SavedBill upsert(
            Long customerId,
            String providerCode,
            ConsumerIdentity identity,
            String providerInputData,
            String responseFormat,
            String contentType,
            String responseJson,
            String responseXml,
            byte[] pdfData
    ) {
        SavedBill bill = findOrCreate(customerId, providerCode, identity);

        bill.updateResponse(
                providerInputData,
                responseFormat,
                contentType,
                responseJson,
                responseXml,
                pdfData
        );
        bill.scheduleNextFetch(LocalDateTime.now().plusHours(fetchIntervalHours));
        return savedBillRepository.saveAndFlush(bill);
    }

    @Transactional
    public SavedBill prepareFetchAttempt(
            Long customerId,
            String providerCode,
            ConsumerIdentity identity,
            String providerInputData
    ) {
        SavedBill bill = savedBillRepository
                .findForUpdateByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        customerId,
                        providerCode,
                        identity.hash()
                )
                .orElse(null);
        if (bill == null) {
            bill = findOrCreate(customerId, providerCode, identity);
        } else if ("IN_PROGRESS".equals(bill.getFetchStatus())) {
            LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(staleClaimMinutes);
            boolean staleManualAttempt = bill.getFetchClaimToken() == null
                    && bill.getLastFetchAttemptAt() != null
                    && !bill.getLastFetchAttemptAt().isAfter(staleBefore);
            if (!staleManualAttempt) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A fetch for this saved bill is already in progress."
                );
            }
        }
        bill.prepareFetch(providerInputData);
        return savedBillRepository.saveAndFlush(bill);
    }

    @Transactional
    public SavedBill completeManualFetch(
            Long customerId,
            String providerCode,
            String identityHash,
            String providerInputData,
            String responseFormat,
            String contentType,
            String responseJson,
            String responseXml,
            byte[] pdfData
    ) {
        SavedBill bill = requireManualFetch(customerId, providerCode, identityHash);
        bill.updateResponse(
                providerInputData,
                responseFormat,
                contentType,
                responseJson,
                responseXml,
                pdfData
        );
        bill.scheduleNextFetch(LocalDateTime.now().plusHours(fetchIntervalHours));
        return savedBillRepository.saveAndFlush(bill);
    }

    @Transactional
    public SavedBill markFetchFailed(
            Long customerId,
            String providerCode,
            String identityHash,
            String errorCode
    ) {
        SavedBill bill = requireManualFetch(customerId, providerCode, identityHash);
        bill.markFetchFailed(errorCode);
        return savedBillRepository.saveAndFlush(bill);
    }

    @Transactional
    public void deferScheduledFetch(ScheduledBillFetch claim, long deferHours) {
        SavedBill bill = requireClaim(claim);
        bill.deferForConsent(LocalDateTime.now().plusHours(deferHours));
        savedBillRepository.saveAndFlush(bill);
    }

    @Transactional(readOnly = true)
    public boolean identityExists(Long customerId, String providerCode, String identityHash) {
        return savedBillRepository.existsByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                customerId,
                providerCode,
                identityHash
        );
    }

    @Transactional
    public List<ScheduledBillFetch> claimEligibleFetches(
            LocalDateTime now,
            int batchSize,
            long staleMinutes
    ) {
        LocalDateTime staleBefore = now.minusMinutes(staleMinutes);
        List<Long> candidates = savedBillRepository.findEligibleFetchIds(
                now,
                staleBefore,
                org.springframework.data.domain.PageRequest.of(0, batchSize)
        );
        List<ScheduledBillFetch> claimed = new ArrayList<>();
        for (Long id : candidates) {
            String token = UUID.randomUUID().toString();
            int updated = savedBillRepository.claimEligibleFetch(
                    id,
                    token,
                    now,
                    staleBefore
            );
            if (updated != 1) {
                continue;
            }
            savedBillRepository.findByIdAndFetchClaimToken(id, token)
                    .ifPresent(bill -> claimed.add(new ScheduledBillFetch(
                            bill.getId(),
                            bill.getCustomer().getId(),
                            bill.getCustomer().getTenantId(),
                            bill.getProvider().getCode(),
                            bill.getCategory().getCode(),
                            bill.getProviderInputData(),
                            token
                    )));
        }
        return claimed;
    }

    @Transactional
    public void completeScheduledFetch(
            ScheduledBillFetch claim,
            String responseFormat,
            String contentType,
            String responseJson,
            String responseXml,
            byte[] pdfData
    ) {
        SavedBill bill = requireClaim(claim);
        bill.updateResponse(
                bill.getProviderInputData(),
                responseFormat,
                contentType,
                responseJson,
                responseXml,
                pdfData
        );
        bill.scheduleNextFetch(LocalDateTime.now().plusHours(fetchIntervalHours));
        savedBillRepository.saveAndFlush(bill);
    }

    @Transactional
    public void failScheduledFetch(
            ScheduledBillFetch claim,
            String errorCode,
            boolean temporary,
            long baseDelaySeconds,
            long maxDelaySeconds,
            int maxRetries
    ) {
        SavedBill bill = requireClaim(claim);
        int retryCount = bill.incrementFetchRetryCount();
        if (!temporary || retryCount > maxRetries) {
            bill.stopRetrying(errorCode);
        } else {
            long multiplier = 1L << Math.min(retryCount - 1, 30);
            long safeBaseDelay = Math.max(1, baseDelaySeconds);
            long safeMaxDelay = Math.max(safeBaseDelay, maxDelaySeconds);
            long delay = safeBaseDelay > safeMaxDelay / multiplier
                    ? safeMaxDelay
                    : safeBaseDelay * multiplier;
            bill.scheduleRetry(LocalDateTime.now().plusSeconds(delay), errorCode);
        }
        savedBillRepository.saveAndFlush(bill);
    }

    private SavedBill requireClaim(ScheduledBillFetch claim) {
        SavedBill bill = savedBillRepository
                .findByIdAndFetchClaimToken(claim.savedBillId(), claim.claimToken())
                .orElseThrow(() -> new IllegalStateException(
                        "Scheduled bill fetch claim is no longer valid."
                ));
        if (!claim.customerId().equals(bill.getCustomer().getId())
                || !claim.tenantId().equals(bill.getCustomer().getTenantId())) {
            throw new IllegalStateException("Scheduled bill fetch ownership changed.");
        }
        return bill;
    }

    private SavedBill requireManualFetch(
            Long customerId,
            String providerCode,
            String identityHash
    ) {
        SavedBill bill = savedBillRepository
                .findForUpdateByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        customerId,
                        providerCode,
                        identityHash
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Manual saved bill fetch attempt was not found."
                ));
        if (!"IN_PROGRESS".equals(bill.getFetchStatus())
                || bill.getFetchClaimToken() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The manual fetch no longer owns this saved bill."
            );
        }
        return bill;
    }

    private SavedBill findOrCreate(
            Long customerId,
            String providerCode,
            ConsumerIdentity identity
    ) {
        SavedBill bill = savedBillRepository
                .findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        customerId,
                        providerCode,
                        identity.hash()
                )
                .orElse(null);
        if (bill != null) {
            return bill;
        }

        CustomerProfile customer = customerProfileRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Authenticated customer profile was not found."
                ));
        BillProvider provider = providerRepository.findByCodeAndActiveTrue(providerCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Bill provider was not found or is inactive."
                ));
        BillCategory category = provider.getCategory();
        if (category == null || !List.of(
                "ELECTRICITY", "WATER", "ECHALLAN", "INSURANCE", "DOCUMENTS"
        ).contains(category.getCode())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "This bill category cannot be saved."
            );
        }
        return new SavedBill(
                customer,
                provider,
                category,
                identity.primaryConsumerNumber(),
                identity.hash()
        );
    }
}
