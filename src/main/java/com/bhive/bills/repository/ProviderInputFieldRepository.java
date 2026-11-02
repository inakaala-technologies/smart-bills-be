
package com.bhive.bills.repository;

import com.bhive.bills.entity.ProviderInputField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderInputFieldRepository
        extends JpaRepository<ProviderInputField, Long> {

    List<ProviderInputField>
    findByProvider_CodeAndActiveTrueOrderByDisplayOrderAsc(
            String providerCode);

    boolean existsByProvider_CodeAndFieldKey(
            String providerCode,
            String fieldKey);

    Optional<ProviderInputField> findByProvider_CodeAndFieldKey(
            String providerCode,
            String fieldKey);
}
