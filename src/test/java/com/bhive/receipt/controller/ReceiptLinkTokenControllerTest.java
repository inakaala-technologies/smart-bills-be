package com.bhive.receipt.controller;

import com.bhive.common.util.TenantContext;
import com.bhive.receipt.dto.ReceiptLinkTokenResponse;
import com.bhive.receipt.service.ReceiptLinkTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptLinkTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceiptLinkTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptLinkTokenService receiptLinkTokenService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createTokenUsesAuthenticatedUserAndDisablesCaching() throws Exception {
        TenantContext.setUserId(42L);
        TenantContext.setTenantId(7L);
        when(receiptLinkTokenService.createToken(42L, 7L))
            .thenReturn(new ReceiptLinkTokenResponse("opaque-token", "2026-11-09T06:00:00Z"));

        mockMvc.perform(post("/api/v1/customer/receipt-link-tokens"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.token").value("opaque-token"))
            .andExpect(jsonPath("$.expiresAt").value("2026-11-09T06:00:00Z"));

        verify(receiptLinkTokenService).createToken(42L, 7L);
    }

    @Test
    void createTokenRequiresAuthenticatedUserContext() throws Exception {
        TenantContext.setTenantId(7L);
        mockMvc.perform(post("/api/v1/customer/receipt-link-tokens"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(receiptLinkTokenService);
    }

    @Test
    void createTokenRequiresTenantContext() throws Exception {
        TenantContext.setUserId(42L);

        mockMvc.perform(post("/api/v1/customer/receipt-link-tokens"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(receiptLinkTokenService);
    }
}
