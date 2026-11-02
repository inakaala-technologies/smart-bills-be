package com.bhive.bills.repository;

import com.bhive.bills.entity.BillCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BillCategoryRepository
        extends JpaRepository<BillCategory, Long> {

    Optional<BillCategory> findByCode(String code);

    List<BillCategory> findAllByActiveTrueOrderByNameAsc();
}