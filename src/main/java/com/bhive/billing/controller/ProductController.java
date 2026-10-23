package com.bhive.billing.controller;

import com.bhive.billing.entity.Product;
import com.bhive.billing.repository.ProductRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bhive.billing.entity.ProductStatus;
import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/billing")
public class ProductController {

    private final ProductRepository productRepository;
    private final BusinessMembershipService businessMembershipService;

    public ProductController(ProductRepository productRepository, BusinessMembershipService businessMembershipService) {
        this.productRepository = productRepository;
        this.businessMembershipService = businessMembershipService;
    }

    private boolean canAccessBusiness(Long businessId) {
    Long tenantId = TenantContext.getTenantId();
    Long userId = TenantContext.getUserId();

    return businessId != null
            && tenantId != null
            && userId != null
            && businessMembershipService.userHasAccessToBusiness(
                    userId, businessId, tenantId);
}

private boolean isValidProduct(Product product) {
    if (product == null
            || product.getName() == null
            || product.getName().isBlank()
            || product.getPrice() == null
            || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
        return false;
    }

    BigDecimal gstRate = product.getGstRate();

    return gstRate == null
            || (gstRate.compareTo(BigDecimal.ZERO) >= 0
            && gstRate.compareTo(new BigDecimal("100")) <= 0);
}
  
@GetMapping("/products")
public ResponseEntity<List<Product>> getAllProducts(
        @RequestParam(required = false) Long businessId) {

    if (TenantContext.getTenantId() == null
            || TenantContext.getUserId() == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    List<Product> products = productRepository.findAll().stream()
            .filter(product -> businessId == null
                    || businessId.equals(product.getBusinessId()))
            .filter(product -> canAccessBusiness(product.getBusinessId()))
            .toList();

    return ResponseEntity.ok(products);
}

   @GetMapping("/products/{id}")
public ResponseEntity<Product> getProductById(@PathVariable Long id) {

    if (TenantContext.getTenantId() == null
            || TenantContext.getUserId() == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Optional<Product> result = productRepository.findById(id)
            .filter(product -> canAccessBusiness(product.getBusinessId()));

    return result.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}

    @PostMapping("/products")
public ResponseEntity<Product> createProduct(
        @RequestBody Product product) {

    if (product == null) {
        return ResponseEntity.badRequest().build();
    }

    if (!canAccessBusiness(product.getBusinessId())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    if (!isValidProduct(product)) {
        return ResponseEntity.badRequest().build();
    }

    product.setId(null);
    product.setCreatedAt(null);
    product.setUpdatedAt(null);
    product.setStatus(ProductStatus.ACTIVE);

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(productRepository.save(product));
}
@PutMapping("/products/{id}")
public ResponseEntity<Product> updateProduct(
        @PathVariable Long id,
        @RequestBody Product request) {

    if (request == null || !isValidProduct(request)) {
        return ResponseEntity.badRequest().build();
    }

    Optional<Product> result = productRepository.findById(id)
            .filter(product -> canAccessBusiness(product.getBusinessId()));

    if (result.isEmpty()) {
        return ResponseEntity.notFound().build();
    }

    Product existing = result.get();

    if (request.getBusinessId() != null
            && !request.getBusinessId().equals(existing.getBusinessId())) {
        return ResponseEntity.badRequest().build();
    }

    existing.setName(request.getName().trim());
    existing.setSku(request.getSku());
    existing.setDescription(request.getDescription());
    existing.setHsnCode(request.getHsnCode());
    existing.setPrice(request.getPrice());
    existing.setGstRate(request.getGstRate());
    existing.setUnit(request.getUnit());

    return ResponseEntity.ok(productRepository.save(existing));
}
@PatchMapping("/products/{id}/deactivate")
public ResponseEntity<Product> deactivateProduct(
        @PathVariable Long id) {

    Optional<Product> result = productRepository.findById(id)
            .filter(product -> canAccessBusiness(product.getBusinessId()));

    if (result.isEmpty()) {
        return ResponseEntity.notFound().build();
    }

    Product product = result.get();
    product.setStatus(ProductStatus.INACTIVE);

    return ResponseEntity.ok(productRepository.save(product));
}
}
