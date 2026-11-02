package com.bhive.bills.service;

import com.bhive.bills.dto.BillCategoryResponse;
import com.bhive.bills.dto.BillProviderResponse;
import com.bhive.bills.repository.BillCategoryRepository;
import com.bhive.bills.repository.BillProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bhive.bills.dto.ProviderInputFieldResponse;
import com.bhive.bills.repository.ProviderInputFieldRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BillCatalogService {

    private final BillCategoryRepository categoryRepository;
    private final BillProviderRepository providerRepository;
    private final ProviderInputFieldRepository inputFieldRepository;

   public BillCatalogService(
        BillCategoryRepository categoryRepository,
        BillProviderRepository providerRepository,
        ProviderInputFieldRepository inputFieldRepository) {

    this.categoryRepository = categoryRepository;
    this.providerRepository = providerRepository;
    this.inputFieldRepository = inputFieldRepository;
}

    public List<BillCategoryResponse> getCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(category -> new BillCategoryResponse(
                        category.getId(),
                        category.getCode(),
                        category.getName()))
                .toList();
    }

    public List<BillProviderResponse> getProviders(String categoryCode) {
        var providers = (categoryCode == null || categoryCode.isBlank())
                ? providerRepository.findAllByActiveTrueOrderByNameAsc()
                : providerRepository
                    .findAllByCategory_CodeAndActiveTrueOrderByNameAsc(
                        categoryCode.trim().toUpperCase());

        return providers.stream()
                .map(provider -> new BillProviderResponse(
                        provider.getId(),
                        provider.getCode(),
                        provider.getName(),
                        provider.getCategory().getCode(),
                        provider.getCategory().getName()))
                .toList();
    }
    public List<ProviderInputFieldResponse> getProviderFields(
        String providerCode) {

    return inputFieldRepository
            .findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc(
                    providerCode.trim().toUpperCase()
            )
            .stream()
            .map(field -> new ProviderInputFieldResponse(
                    field.getFieldKey(),
                    field.getLabel(),
                    field.getInputType(),
                    field.isRequired(),
                    field.getPlaceholder(),
                    field.getHelpText(),
                    field.getDisplayOrder()
            ))
            .toList();
    }
}