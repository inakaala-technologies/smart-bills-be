package com.bhive.bills.repository;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.SavedBill;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SavedBillRepositoryIntegrationTest {

    @Autowired
    private SavedBillRepository savedBillRepository;
    @Autowired
    private CustomerProfileRepository customerProfileRepository;
    @Autowired
    private BillProviderRepository billProviderRepository;
    @Autowired
    private BillCategoryRepository billCategoryRepository;
    @Autowired
    private EntityManager entityManager;

    private CustomerProfile customerOne;
    private CustomerProfile customerTwo;
    private BillProvider kseb;
    private BillProvider uppcl;
    private BillCategory electricity;
    private BillCategory water;
    private BillCategory echallan;
    private BillCategory documents;
    private BillProvider kiadb;
    private BillProvider insuranceProvider;
    private BillProvider challanProvider;
    private BillProvider documentsProvider;

    @BeforeEach
    void setUp() {
        electricity = billCategoryRepository.saveAndFlush(
                new BillCategory("ELECTRICITY", "Electricity")
        );
        water = billCategoryRepository.saveAndFlush(
                new BillCategory("WATER", "Water")
        );
        BillCategory insurance = billCategoryRepository.saveAndFlush(
                new BillCategory("INSURANCE", "Insurance")
        );
        echallan = billCategoryRepository.saveAndFlush(
                new BillCategory("ECHALLAN", "E-challan")
        );
        documents = billCategoryRepository.saveAndFlush(
                new BillCategory("DOCUMENTS", "Documents")
        );

        customerOne = customerProfileRepository.saveAndFlush(customer(101L));
        customerTwo = customerProfileRepository.saveAndFlush(customer(102L));
        kseb = billProviderRepository.saveAndFlush(
                new BillProvider("KSEB", "Kerala State Electricity Board", electricity)
        );
        uppcl = billProviderRepository.saveAndFlush(
                new BillProvider("UPPCL", "Uttar Pradesh Power Corporation", electricity)
        );
        kiadb = billProviderRepository.saveAndFlush(
                new BillProvider("KIADB", "Karnataka Industrial Areas Development Board", water)
        );
        insuranceProvider = billProviderRepository.saveAndFlush(
                new BillProvider("INS-1", "Insurance Provider", insurance)
        );
        challanProvider = billProviderRepository.saveAndFlush(
                new BillProvider("CHALLAN-1", "Challan Provider", echallan)
        );
        documentsProvider = billProviderRepository.saveAndFlush(
                new BillProvider("DOC-1", "Document Provider", documents)
        );
    }

    @Test
    void database_unique_constraint_rejects_duplicate_customer_provider_identity() {
        savedBillRepository.saveAndFlush(savedBill(customerOne, kseb, "K-1", "same-hash"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> savedBillRepository.saveAndFlush(
                        savedBill(customerOne, kseb, "K-1", "same-hash")
                )
        );
    }

    @Test
    void database_unique_constraint_allows_other_customers_providers_and_consumers() {
        savedBillRepository.saveAndFlush(savedBill(customerOne, kseb, "K-1", "identity-1"));
        savedBillRepository.saveAndFlush(savedBill(customerOne, kseb, "K-2", "identity-2"));
        savedBillRepository.saveAndFlush(savedBill(customerOne, uppcl, "K-1", "identity-1"));
        savedBillRepository.saveAndFlush(savedBill(customerTwo, kseb, "K-1", "identity-1"));

        assertEquals(4, savedBillRepository.count());
    }

    @Test
    void persists_json_xml_and_pdf_data_in_their_native_representations() {
        SavedBill jsonBill = savedBill(customerOne, kseb, "K-1", "json-identity");
        jsonBill.updateResponse("{}", "JSON", "application/json", "{\"providerField\":7}", null, null);
        SavedBill xmlBill = savedBill(customerOne, kseb, "K-2", "xml-identity");
        xmlBill.updateResponse("{}", "XML", "application/xml", null, "<Bill><Amount>7</Amount></Bill>", null);
        byte[] pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x00, (byte) 0xff};
        SavedBill pdfBill = savedBill(customerOne, kseb, "K-3", "pdf-identity");
        pdfBill.updateResponse("{}", "PDF", "application/pdf", null, null, pdfBytes);

        Long jsonId = savedBillRepository.saveAndFlush(jsonBill).getId();
        Long xmlId = savedBillRepository.saveAndFlush(xmlBill).getId();
        Long pdfId = savedBillRepository.saveAndFlush(pdfBill).getId();
        entityManager.clear();

        SavedBill storedJson = savedBillRepository.findById(jsonId).orElseThrow();
        SavedBill storedXml = savedBillRepository.findById(xmlId).orElseThrow();
        SavedBill storedPdf = savedBillRepository.findById(pdfId).orElseThrow();

        assertEquals("SUCCESS", storedJson.getFetchStatus());
        assertEquals("{\"providerField\":7}", storedJson.getResponseJson());
        assertNotNull(storedJson.getFetchedAt());
        assertEquals("<Bill><Amount>7</Amount></Bill>", storedXml.getResponseXml());
        assertArrayEquals(pdfBytes, storedPdf.getPdfData());
    }

    @Test
    void persists_fetch_attempt_before_any_provider_response_exists() {
        SavedBill pendingBill = new SavedBill(
                customerOne,
                kseb,
                electricity,
                "K-1",
                "pending-identity"
        );
        pendingBill.prepareFetch("{\"ConsumerNo\":\"K-1\"}");

        Long id = savedBillRepository.saveAndFlush(pendingBill).getId();
        entityManager.clear();

        SavedBill storedBill = savedBillRepository.findById(id).orElseThrow();
        assertEquals("IN_PROGRESS", storedBill.getFetchStatus());
        assertEquals("{\"ConsumerNo\":\"K-1\"}", storedBill.getProviderInputData());
        assertNull(storedBill.getResponseFormat());
        assertNull(storedBill.getFetchedAt());
        assertNotNull(storedBill.getLastFetchAttemptAt());
    }

    @Test
    void persists_failure_status_without_replacing_the_previous_successful_response() {
        SavedBill existingBill = savedBill(
                customerOne,
                kseb,
                "K-1",
                "failed-retry-identity"
        );
        existingBill.updateResponse(
                "{\"ConsumerNo\":\"K-1\"}",
                "JSON",
                "application/json",
                "{\"bill\":\"previous-success\"}",
                null,
                null
        );
        LocalDateTime successfulFetchAt = existingBill.getFetchedAt();
        existingBill.prepareFetch("{\"ConsumerNo\":\"K-1\"}");
        existingBill.markFetchFailed("PROVIDER_HTTP_ERROR");

        Long id = savedBillRepository.saveAndFlush(existingBill).getId();
        entityManager.clear();

        SavedBill storedBill = savedBillRepository.findById(id).orElseThrow();
        assertEquals("FAILED", storedBill.getFetchStatus());
        assertEquals("PROVIDER_HTTP_ERROR", storedBill.getLastFetchError());
        assertEquals("{\"ConsumerNo\":\"K-1\"}", storedBill.getProviderInputData());
        assertEquals("{\"bill\":\"previous-success\"}", storedBill.getResponseJson());
        assertTrue(Math.abs(Duration.between(
                successfulFetchAt,
                storedBill.getFetchedAt()
        ).toNanos()) <= 1_000);
    }

    @Test
    void atomically_claims_due_and_stale_records_only_once() {
        LocalDateTime now = LocalDateTime.now();
        SavedBill dueBill = savedBill(
                customerOne,
                kseb,
                "K-1",
                "due-identity"
        );
        dueBill.updateResponse("{}", "JSON", "application/json", "{}", null, null);
        dueBill.scheduleNextFetch(now.minusMinutes(1));
        SavedBill staleBill = new SavedBill(
                customerOne,
                kseb,
                electricity,
                "K-2",
                "stale-identity"
        );
        staleBill.prepareFetch("{\"ConsumerNo\":\"K-2\"}");
        staleBill.claimFetch(UUID.randomUUID(), now.minusHours(1));
        Long dueId = savedBillRepository.saveAndFlush(dueBill).getId();
        Long staleId = savedBillRepository.saveAndFlush(staleBill).getId();

        var candidates = savedBillRepository.findEligibleFetchIds(
                now,
                now.minusMinutes(15),
                PageRequest.of(0, 10)
        );

        assertEquals(2, candidates.size());
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                dueId,
                "claim-due",
                now,
                now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                dueId,
                "duplicate-claim",
                now,
                now.minusMinutes(15)
        ));
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                staleId,
                "recover-stale",
                now,
                now.minusMinutes(15)
        ));
        entityManager.clear();
        assertEquals(
                "recover-stale",
                savedBillRepository.findById(staleId).orElseThrow().getFetchClaimToken()
        );
    }

    @Test
    void selects_due_electricity_water_and_challan_but_excludes_documents_and_insurance() {
        LocalDateTime now = LocalDateTime.now();
        SavedBill dueWater = savedBill(customerOne, kiadb, "W-1", "due-water");
        dueWater.scheduleNextFetch(now.minusMinutes(1));
        SavedBill activeManual = new SavedBill(
                customerOne, kseb, electricity, "K-1", "active-manual"
        );
        activeManual.prepareFetch("{\"ConsumerNo\":\"K-1\"}");
        SavedBill dueInsurance = savedBill(
                customerOne, insuranceProvider, "I-1", "due-insurance"
        );
        dueInsurance.scheduleNextFetch(now.minusMinutes(1));
        SavedBill dueChallan = savedBill(
                customerOne, challanProvider, "C-1", "due-challan"
        );
        dueChallan.scheduleNextFetch(now.minusMinutes(1));
        SavedBill dueDocument = savedBill(
                customerOne, documentsProvider, "D-1", "due-document"
        );
        dueDocument.scheduleNextFetch(now.minusMinutes(1));
        Long dueWaterId = savedBillRepository.saveAndFlush(dueWater).getId();
        Long manualId = savedBillRepository.saveAndFlush(activeManual).getId();
        Long insuranceId = savedBillRepository.saveAndFlush(dueInsurance).getId();
        Long challanId = savedBillRepository.saveAndFlush(dueChallan).getId();
        Long documentId = savedBillRepository.saveAndFlush(dueDocument).getId();

        var candidates = savedBillRepository.findEligibleFetchIds(
                now,
                now.minusMinutes(15),
                PageRequest.of(0, 10)
        );

        assertTrue(candidates.contains(dueWaterId));
        assertTrue(candidates.contains(challanId));
        assertFalse(candidates.contains(manualId));
        assertFalse(candidates.contains(insuranceId));
        assertFalse(candidates.contains(documentId));
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                dueWaterId,
                "claim-water",
                now,
                now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                manualId,
                "claim-manual",
                now,
                now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                insuranceId,
                "claim-insurance",
                now,
                now.minusMinutes(15)
        ));
        assertEquals(1, savedBillRepository.claimEligibleFetch(
                challanId,
                "claim-challan",
                now,
                now.minusMinutes(15)
        ));
        assertEquals(0, savedBillRepository.claimEligibleFetch(
                documentId,
                "claim-document",
                now,
                now.minusMinutes(15)
        ));
    }

    @Test
    void sorts_customer_bills_by_a_valid_saved_bill_property() {
        savedBillRepository.saveAndFlush(savedBill(customerOne, kseb, "K-2", "identity-2"));
        savedBillRepository.saveAndFlush(savedBill(customerOne, kseb, "K-1", "identity-1"));

        var page = savedBillRepository.findAllByCustomer_Id(
                customerOne.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("consumerNumber")))
        );

        assertEquals("K-1", page.getContent().get(0).getConsumerNumber());
        assertEquals("K-2", page.getContent().get(1).getConsumerNumber());
    }

    private CustomerProfile customer(Long userId) {
        CustomerProfile customer = new CustomerProfile();
        customer.setTenantId(1L);
        customer.setUserId(userId);
        customer.setName("Customer " + userId);
        return customer;
    }

    private SavedBill savedBill(
            CustomerProfile customer,
            BillProvider provider,
            String consumerNumber,
            String identityHash
    ) {
        SavedBill bill = new SavedBill(
                customer,
                provider,
                provider.getCategory(),
                consumerNumber,
                identityHash
        );
        bill.updateResponse("{}", "JSON", "application/json", "{}", null, null);
        return bill;
    }
}
