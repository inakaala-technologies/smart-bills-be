package com.bhive.bills.repository;

import com.bhive.bills.entity.BillProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface BillProviderRepository
        extends JpaRepository<BillProvider, Long> {

    Optional<BillProvider> findByCode(String code);

    @EntityGraph(attributePaths = "category")
    Optional<BillProvider> findByCodeAndActiveTrue(String code);

    List<BillProvider> findAllByActiveTrueOrderByNameAsc();

    List<BillProvider> findAllByCategory_CodeAndActiveTrueOrderByNameAsc(
        String categoryCode
    );
}