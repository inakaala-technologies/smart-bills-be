package com.bhive.bills.repository;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.SavedBill;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=${BILL_TEST_MYSQL_URL}",
        "spring.datasource.username=${BILL_TEST_MYSQL_USERNAME:root}",
        "spring.datasource.password=${BILL_TEST_MYSQL_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "BILL_TEST_MYSQL_URL", matches = ".+")
class SavedBillRepositoryMySqlIntegrationTest {

    @Autowired
    private SavedBillRepository savedBillRepository;
    @Autowired
    private CustomerProfileRepository customerProfileRepository;
    @Autowired
    private BillProviderRepository providerRepository;
    @Autowired
    private BillCategoryRepository categoryRepository;

    @Test
    void mysql_selects_bill_categories_and_claims_due_and_stale_records_conditionally() {
        LocalDateTime now = LocalDateTime.now();
        BillCategory electricity = categoryRepository.findByCode("ELECTRICITY")
                .orElseGet(() -> categoryRepository.saveAndFlush(
                        new BillCategory("ELECTRICITY", "Electricity")
                ));
        BillCategory insurance = categoryRepository.findByCode("INSURANCE")
                .orElseGet(() -> categoryRepository.saveAndFlush(
                        new BillCategory("INSURANCE", "Insurance")
                ));
        BillCategory echallan = categoryRepository.findByCode("ECHALLAN")
                .orElseGet(() -> categoryRepository.saveAndFlush(
                        new BillCategory("ECHALLAN", "E-challan")
                ));
        BillCategory documents = categoryRepository.findByCode("DOCUMENTS")
                .orElseGet(() -> categoryRepository.saveAndFlush(
                        new BillCategory("DOCUMENTS", "Documents")
                ));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        BillProvider provider = providerRepository.saveAndFlush(
                new BillProvider("TST" + suffix, "Test provider", electricity)
        );
        BillProvider nonBillProvider = providerRepository.saveAndFlush(
                new BillProvider("INS" + suffix, "Test insurance", insurance)
        );
        BillProvider challanProvider = providerRepository.saveAndFlush(
                new BillProvider("CHL" + suffix, "Test challan", echallan)
        );
        BillProvider documentProvider = providerRepository.saveAndFlush(
                new BillProvider("DOC" + suffix, "Test document", documents)
        );
        CustomerProfile customer = new CustomerProfile();
        customer.setTenantId(1L);
        customer.setUserId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        customer.setName("Scheduler MySQL integration test");
        customer = customerProfileRepository.saveAndFlush(customer);

        SavedBill dueBill = bill(customer, provider, electricity, "due-" + suffix);
        dueBill.scheduleNextFetch(now.minusMinutes(1));
        SavedBill manualBill = bill(customer, provider, electricity, "manual-" + suffix);
        manualBill.prepareFetch("{}");
        SavedBill staleScheduledBill = bill(customer, provider, electricity, "stale-" + suffix);
        staleScheduledBill.prepareFetch("{}");
        staleScheduledBill.claimFetch(UUID.randomUUID(), now.minusHours(1));
        SavedBill insuranceBill = bill(
                customer, nonBillProvider, insurance, "insurance-" + suffix
        );
        insuranceBill.scheduleNextFetch(now.minusMinutes(1));
        SavedBill challanBill = bill(
                customer, challanProvider, echallan, "challan-" + suffix
        );
        challanBill.scheduleNextFetch(now.minusMinutes(1));
        SavedBill documentBill = bill(
                customer, documentProvider, documents, "document-" + suffix
        );
        documentBill.scheduleNextFetch(now.minusMinutes(1));

        Long dueId = savedBillRepository.saveAndFlush(dueBill).getId();
        Long manualId = savedBillRepository.saveAndFlush(manualBill).getId();
        Long staleId = savedBillRepository.saveAndFlush(staleScheduledBill).getId();
        Long insuranceId = savedBillRepository.saveAndFlush(insuranceBill).getId();
        Long challanId = savedBillRepository.saveAndFlush(challanBill).getId();
        Long documentId = savedBillRepository.saveAndFlush(documentBill).getId();

        java.util.List<Long> eligibleIds = savedBillRepository.findEligibleFetchIds(
                now,
                now.minusMinutes(15),
                PageRequest.of(0, 10)
        );
        assertTrue(eligibleIds.contains(dueId));
        assertTrue(eligibleIds.contains(staleId));
        assertTrue(eligibleIds.contains(challanId));
        assertFalse(eligibleIds.contains(manualId));
        assertFalse(eligibleIds.contains(insuranceId));
        assertFalse(eligibleIds.contains(documentId));
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                dueId, "due-" + suffix, now, now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                dueId, "duplicate-" + suffix, now, now.minusMinutes(15)
        ));
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                staleId, "stale-" + suffix, now, now.minusMinutes(15)
        ));
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                challanId, "challan-" + suffix, now, now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                manualId, "manual-" + suffix, now, now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                insuranceId, "insurance-" + suffix, now, now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                documentId, "document-" + suffix, now, now.minusMinutes(15)
        ));
    }

    private SavedBill bill(
            CustomerProfile customer,
            BillProvider provider,
            BillCategory category,
            String identityHash
    ) {
        SavedBill bill = new SavedBill(
                customer,
                provider,
                category,
                identityHash,
                identityHash
        );
        bill.updateResponse("{}", "JSON", "application/json", "{}", null, null);
        return bill;
    }
}
