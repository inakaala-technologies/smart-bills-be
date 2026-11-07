package com.bhive.billing.repository;

import com.bhive.billing.entity.Product;
import com.bhive.billing.entity.ProductStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByBusinessIdAndStatusOrderByNameAsc(Long businessId, ProductStatus status);
}
