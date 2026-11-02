
package com.bhive.bills.service;

import com.bhive.bills.entity.BillCategory;
import com.bhive.bills.entity.BillProvider;
import com.bhive.bills.entity.ProviderInputField;
import com.bhive.bills.repository.BillCategoryRepository;
import com.bhive.bills.repository.BillProviderRepository;
import com.bhive.bills.repository.ProviderInputFieldRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BillCatalogInitializer implements CommandLineRunner {

    private final BillCategoryRepository categoryRepository;
    private final BillProviderRepository providerRepository;
    private final ProviderInputFieldRepository inputFieldRepository;

    public BillCatalogInitializer(
            BillCategoryRepository categoryRepository,
            BillProviderRepository providerRepository,
            ProviderInputFieldRepository inputFieldRepository) {

        this.categoryRepository = categoryRepository;
        this.providerRepository = providerRepository;
        this.inputFieldRepository = inputFieldRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {

        // Categories
        BillCategory electricity = category(
                "ELECTRICITY", "Electricity");

        BillCategory water = category(
                "WATER", "Water");

        category("INSURANCE", "Insurance");
        category("ECHALLAN", "E-challan");
        category("DOCUMENTS", "Documents");

        // Electricity providers
        provider("KSEB",
                "Kerala State Electricity Board Limited",
                electricity);

        provider("GUVNL",
                "Gujarat Urja Vikas Nigam Limited",
                electricity);

        provider("PSPCL",
                "Punjab State Power Corporation Ltd.",
                electricity);

        provider("JKPDD",
                "Jammu and Kashmir Power Development Department",
                electricity);

        provider("NBPDCL",
                "Bihar Power Distribution Company Limited",
                electricity);

        provider("BSESDELHI",
                "BSES Yamuna/Rajdhani Power Ltd.",
                electricity);

        provider("MPWZ",
                "Madhya Pradesh Paschim Kshetra Vidyut Vitaran Company Limited",
                electricity);

        provider("UPPCL",
                "Uttar Pradesh Power Corporation Limited",
                electricity);

        // Water providers
        provider("ULB_UP",
                "Urban Local Bodies – Uttar Pradesh",
                water);

        provider("GOAONLINE",
                "Goa Online",
                water);

        provider("KIADB",
                "Karnataka Industrial Areas Development Board",
                water);

        // Provider input fields
        seedProviderInputFields();
    }

    // Create or update category
    private BillCategory category(String code, String name) {

        BillCategory category = categoryRepository.findByCode(code)
                .orElseGet(() -> new BillCategory(code, name));

        category.setName(name);
        category.setActive(true);

        return categoryRepository.save(category);
    }

    // Create or update provider
    private void provider(
            String code,
            String name,
            BillCategory category) {

        BillProvider provider = providerRepository.findByCode(code)
                .orElseGet(() -> new BillProvider(
                        code,
                        name,
                        category
                ));

        provider.setName(name);
        provider.setCategory(category);
        provider.setActive(true);

        providerRepository.save(provider);
    }

    // Seed provider-specific input fields
    private void seedProviderInputFields() {

        // Electricity providers

        seedField(
                "KSEB", "ConsumerNo",
                "Consumer Number", "text", true,
                "Enter consumer number",
                "Consumer number registered with KSEB", 1
        );

        seedField(
                "GUVNL", "discom",
                "DISCOM", "text", true,
                "Enter DISCOM",
                "Distribution company code", 1
        );

        seedField(
                "GUVNL", "consno",
                "Consumer Number", "text", true,
                "Enter consumer number",
                "Consumer number assigned by the DISCOM", 2
        );

        seedField(
                "PSPCL", "PSPCLACCOUNT",
                "PSPCL Account Number", "text", true,
                "Enter account number",
                "Account number registered with PSPCL", 1
        );

        seedField(
                "PSPCL", "MOBILE",
                "Registered Mobile Number", "tel", true,
                "Enter mobile number",
                "Mobile number registered with the provider", 2
        );

        seedField(
                "JKPDD", "consumerid",
                "Consumer ID", "text", true,
                "Enter consumer ID", null, 1
        );

        seedField(
                "JKPDD", "installationNo",
                "Installation Number", "text", true,
                "Enter installation number", null, 2
        );

        seedField(
                "JKPDD", "month",
                "Billing Month", "text", true,
                "Enter billing month", null, 3
        );

        seedField(
                "NBPDCL", "ConsumerId",
                "Consumer ID", "text", true,
                "Enter consumer ID", null, 1
        );

        seedField(
                "BSESDELHI", "ca_number",
                "CA Number", "text", true,
                "Enter CA number",
                "Customer account number provided by BSES", 1
        );

        seedField(
                "MPWZ", "consumer_no",
                "Consumer Number", "text", true,
                "Enter consumer number", null, 1
        );

        seedField(
                "UPPCL", "AccountNo",
                "Account Number", "text", true,
                "Enter account number", null, 1
        );

        seedField(
                "UPPCL", "Discom",
                "DISCOM", "text", true,
                "Enter DISCOM", null, 2
        );

        // Water providers

        seedField(
                "ULB_UP", "CONNECTIONNO",
                "Connection Number", "text", true,
                "Enter connection number", null, 1
        );

        seedField(
                "KIADB", "RegNum",
                "Registration Number", "text", true,
                "Enter registration number", null, 1
        );

        seedField(
                "GOAONLINE", "UDF1",
                "Additional Detail 1", "text", true,
                "Enter required detail", null, 1
        );

        seedField(
                "GOAONLINE", "UDF2",
                "Additional Detail 2", "text", false,
                "Enter additional detail", null, 2
        );

        seedField(
                "GOAONLINE", "UID",
                "User ID", "text", true,
                "Enter user ID", null, 3
        );
    }

    // Create or update provider input field
    private void seedField(
            String providerCode,
            String fieldKey,
            String label,
            String inputType,
            boolean required,
            String placeholder,
            String helpText,
            int displayOrder) {

        BillProvider provider = providerRepository
                .findByCode(providerCode)
                .orElse(null);

        if (provider == null) {
            return;
        }

        ProviderInputField field = inputFieldRepository
                .findByProvider_CodeAndFieldKey(
                        providerCode,
                        fieldKey
                )
                .orElseGet(() -> new ProviderInputField(
                        provider,
                        fieldKey,
                        label,
                        inputType,
                        required,
                        placeholder,
                        helpText,
                        displayOrder
                ));

        field.setLabel(label);
        field.setInputType(inputType);
        field.setRequired(required);
        field.setPlaceholder(placeholder);
        field.setHelpText(helpText);
        field.setDisplayOrder(displayOrder);
        field.setActive(true);

        inputFieldRepository.save(field);
    }
}
