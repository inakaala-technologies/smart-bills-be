package com.bhive.bills.service;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.SavedBill;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.SavedBillRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class SavedBillPersistenceService {

    private final SavedBillRepository savedBillRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BillProviderRepository providerRepository;

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
        SavedBill bill = savedBillRepository
                .findByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                        customerId,
                        providerCode,
                        identity.hash()
                )
                .orElse(null);

        if (bill == null) {
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
            if (category == null
                    || !("ELECTRICITY".equals(category.getCode())
                    || "WATER".equals(category.getCode()))) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Only electricity and water bills can be saved."
                );
            }
            bill = new SavedBill(
                    customer,
                    provider,
                    category,
                    identity.primaryConsumerNumber(),
                    identity.hash()
            );
        }

        bill.updateResponse(
                providerInputData,
                responseFormat,
                contentType,
                responseJson,
                responseXml,
                pdfData
        );
        return savedBillRepository.saveAndFlush(bill);
    }

    @Transactional(readOnly = true)
    public boolean identityExists(Long customerId, String providerCode, String identityHash) {
        return savedBillRepository.existsByCustomer_IdAndProvider_CodeAndConsumerIdentityHash(
                customerId,
                providerCode,
                identityHash
        );
    }
}
