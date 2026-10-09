package com.bhive.document.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.document.entity.Document;
import com.bhive.document.repository.DocumentRepository;
import com.bhive.notification.service.NotificationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private BusinessMembershipService businessMembershipService;

    @MockBean
    private CustomerProfileRepository customerProfileRepository;

    @MockBean
    private NotificationService notificationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getAllDocumentsShouldReturnList() throws Exception {
        Document document = new Document();
        document.setBusinessId(10L);
        document.setCustomerId(11L);
        document.setTenantId(5L);
        document.setDocumentType(com.bhive.document.entity.DocumentType.INVOICE);
        document.setFileName("receipt.pdf");
        document.setFileUrl("/files/receipt.pdf");
        document.setStatus(com.bhive.document.entity.DocumentStatus.ACTIVE);

        when(documentRepository.findAll()).thenReturn(List.of(document));

        mockMvc.perform(get("/api/v1/documents")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].fileName").value("receipt.pdf"));
    }

    @Test
    void businessDocumentCreationNotifiesLinkedCustomer() throws Exception {
        TenantContext.setTenantId(5L);
        TenantContext.setUserId(9L);
        when(businessMembershipService.userHasAccessToBusiness(9L, 10L, 5L)).thenReturn(true);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerProfile customer = new CustomerProfile();
        customer.setId(11L);
        customer.setTenantId(5L);
        customer.setUserId(18L);
        when(customerProfileRepository.findById(11L)).thenReturn(Optional.of(customer));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":5,\"businessId\":10,\"customerId\":11,\"documentType\":\"INVOICE\",\"fileName\":\"invoice.pdf\",\"fileUrl\":\"/files/invoice.pdf\"}"))
            .andExpect(status().isCreated());

        verify(notificationService).create(
            5L, 18L, "DOCUMENT_ADDED", "New document", "invoice.pdf is available in your documents.", "/dashboard"
        );
    }
}
