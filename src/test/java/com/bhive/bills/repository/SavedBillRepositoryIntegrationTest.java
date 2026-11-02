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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @BeforeEach
    void setUp() {
        electricity = billCategoryRepository.saveAndFlush(
                new BillCategory("ELECTRICITY", "Electricity")
        );
        BillCategory water = billCategoryRepository.saveAndFlush(
                new BillCategory("WATER", "Water")
        );

        customerOne = customerProfileRepository.saveAndFlush(customer(101L));
        customerTwo = customerProfileRepository.saveAndFlush(customer(102L));
        kseb = billProviderRepository.saveAndFlush(
                new BillProvider("KSEB", "Kerala State Electricity Board", electricity)
        );
        uppcl = billProviderRepository.saveAndFlush(
                new BillProvider("UPPCL", "Uttar Pradesh Power Corporation", electricity)
        );
        billProviderRepository.saveAndFlush(
                new BillProvider("KIADB", "Karnataka Industrial Areas Development Board", water)
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

        assertEquals("{\"providerField\":7}", storedJson.getResponseJson());
        assertEquals("<Bill><Amount>7</Amount></Bill>", storedXml.getResponseXml());
        assertArrayEquals(pdfBytes, storedPdf.getPdfData());
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
