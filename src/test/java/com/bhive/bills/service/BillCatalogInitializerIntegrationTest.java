package com.bhive.bills.service;

import com.bhive.bills.config.ApiSetuServiceCatalog;
import com.bhive.bills.repository.BillCategoryRepository;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({BillCatalogInitializer.class, BillCatalogService.class})
class BillCatalogInitializerIntegrationTest {

    @Autowired
    private BillCatalogInitializer initializer;
    @Autowired
    private BillCatalogService catalogService;
    @Autowired
    private BillCategoryRepository categoryRepository;
    @Autowired
    private BillProviderRepository providerRepository;
    @Autowired
    private ProviderInputFieldRepository inputFieldRepository;

    @Test
    void persists_all_catalog_services_and_fields_idempotently() {
        initializer.run();

        assertCatalogContents();

        initializer.run();

        assertCatalogContents();
    }

    private void assertCatalogContents() {
        assertEquals(5, categoryRepository.count());
        assertEquals(77, providerRepository.count());

        assertEquals(8, catalogService.getProviders("ELECTRICITY").size());
        assertEquals(3, catalogService.getProviders("WATER").size());
        assertEquals(61, catalogService.getProviders("INSURANCE").size());
        assertEquals(1, catalogService.getProviders("ECHALLAN").size());
        assertEquals(4, catalogService.getProviders("DOCUMENTS").size());
        assertEquals(
                Set.of("KSEB", "GUVNL", "PSPCL", "JKPDD", "NBPDCL", "BSESDELHI", "MPWZ", "UPPCL"),
                catalogService.getProviders("ELECTRICITY").stream()
                        .map(provider -> provider.code())
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertEquals(
                Set.of("ULB_UP", "GOAONLINE", "KIADB"),
                catalogService.getProviders("WATER").stream()
                        .map(provider -> provider.code())
                        .collect(java.util.stream.Collectors.toSet())
        );

        int apiSetuFieldCount = ApiSetuServiceCatalog.services().stream()
                .mapToInt(service -> service.inputFields().size())
                .sum();
        assertEquals(143, apiSetuFieldCount);
        assertEquals(161, inputFieldRepository.count());

        for (ApiSetuServiceCatalog.ServiceDefinition service
                : ApiSetuServiceCatalog.services()) {
            var provider = catalogService.getProviders(service.categoryCode()).stream()
                    .filter(item -> service.code().equals(item.code()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Missing provider " + service.code()
                    ));
            assertNotNull(provider.id());

            var storedFields = catalogService.getProviderFields(service.code());
            assertEquals(
                    service.inputFields(),
                    storedFields.stream()
                            .map(field -> field.fieldKey())
                            .toList(),
                    service.code()
            );
        }

        assertInputKeys("KSEB", "ConsumerNo");
        assertInputKeys("GUVNL", "discom", "consno");
        assertInputKeys("PSPCL", "PSPCLACCOUNT", "MOBILE");
        assertInputKeys("JKPDD", "consumerid", "installationNo", "month");
        assertInputKeys("NBPDCL", "ConsumerId");
        assertInputKeys("BSESDELHI", "ca_number");
        assertInputKeys("MPWZ", "consumer_no");
        assertInputKeys("UPPCL", "AccountNo", "Discom");
        assertInputKeys("ULB_UP", "CONNECTIONNO");
        assertInputKeys("KIADB", "RegNum");
        assertInputKeys("GOAONLINE", "UDF1", "UDF2", "UID");
    }

    private void assertInputKeys(String providerCode, String... expectedFields) {
        assertEquals(
                java.util.List.of(expectedFields),
                catalogService.getProviderFields(providerCode).stream()
                        .map(field -> field.fieldKey())
                        .toList(),
                providerCode
        );
    }
}
