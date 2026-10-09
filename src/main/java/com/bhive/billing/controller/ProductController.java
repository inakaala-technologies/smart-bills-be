package com.bhive.billing.controller;

import com.bhive.billing.entity.Product;
import com.bhive.billing.repository.ProductRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
public class ProductController {

    private final ProductRepository productRepository;
    private final BusinessMembershipService businessMembershipService;

    public ProductController(ProductRepository productRepository, BusinessMembershipService businessMembershipService) {
        this.productRepository = productRepository;
        this.businessMembershipService = businessMembershipService;
    }

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            return productRepository.findAll();
        }
        return productRepository.findAll().stream()
            .filter(product -> businessMembershipService.userHasAccessToBusiness(userId, product.getBusinessId(), tenantId))
            .toList();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        return productRepository.findById(id)
            .filter(product -> tenantId == null || userId == null || businessMembershipService.userHasAccessToBusiness(userId, product.getBusinessId(), tenantId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (product.getBusinessId() == null || tenantId == null || userId == null || !businessMembershipService.userHasAccessToBusiness(userId, product.getBusinessId(), tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(productRepository.save(product));
    }
}
