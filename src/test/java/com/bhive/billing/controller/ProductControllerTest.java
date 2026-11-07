package com.bhive.billing.controller;

import com.bhive.billing.entity.Product;
import com.bhive.billing.entity.ProductStatus;
import com.bhive.billing.repository.ProductRepository;
import com.bhive.business.entity.Business;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private BusinessRepository businessRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void productsForCustomerShouldBeForbiddenWithoutTenantContext() throws Exception {
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/billing/products/business/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void productsForCustomerShouldReturnActiveProductsOnlyForBusinessesInTheirTenant() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(9L);
        Business business = new Business();
        business.setTenantId(5L);
        Product product = new Product();
        product.setBusinessId(1L);
        product.setName("Active product");
        product.setStatus(ProductStatus.ACTIVE);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));
        Mockito.when(productRepository.findByBusinessIdAndStatusOrderByNameAsc(1L, ProductStatus.ACTIVE))
            .thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/billing/products/business/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value("Active product"));

        Mockito.verify(productRepository)
            .findByBusinessIdAndStatusOrderByNameAsc(1L, ProductStatus.ACTIVE);
    }

    @Test
    void productsForCustomerShouldNotReturnProductsForAnotherTenantBusiness() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(9L);
        Business business = new Business();
        business.setTenantId(99L);

        Mockito.when(businessRepository.findById(1L)).thenReturn(Optional.of(business));

        mockMvc.perform(get("/api/v1/billing/products/business/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        Mockito.verify(productRepository, Mockito.never())
            .findByBusinessIdAndStatusOrderByNameAsc(Mockito.anyLong(), Mockito.any(ProductStatus.class));
    }
}
