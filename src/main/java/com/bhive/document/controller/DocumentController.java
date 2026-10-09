package com.bhive.document.controller;

import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.document.entity.Document;
import com.bhive.document.repository.DocumentRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final BusinessMembershipService businessMembershipService;
    private final CustomerProfileRepository customerProfileRepository;
    private final NotificationService notificationService;

    public DocumentController(DocumentRepository documentRepository,
                             BusinessMembershipService businessMembershipService,
                             CustomerProfileRepository customerProfileRepository,
                             NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.businessMembershipService = businessMembershipService;
        this.customerProfileRepository = customerProfileRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/documents")
    public List<Document> getAllDocuments() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        return documentRepository.findAll().stream()
            .filter(document -> tenantId == null || tenantId.equals(document.getTenantId()))
            .filter(document -> userId == null || document.getBusinessId() == null || businessMembershipService.userHasAccessToBusiness(userId, document.getBusinessId(), tenantId))
            .toList();
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Document> getDocumentById(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        return documentRepository.findById(id)
            .filter(document -> tenantId == null || tenantId.equals(document.getTenantId()))
            .filter(document -> userId == null || document.getBusinessId() == null || businessMembershipService.userHasAccessToBusiness(userId, document.getBusinessId(), tenantId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/documents")
    public ResponseEntity<Document> createDocument(@RequestBody Document document) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        if (document == null) {
            return ResponseEntity.badRequest().build();
        }

        if (document.getTenantId() == null) {
            return ResponseEntity.badRequest().build();
        }

        if (tenantId != null && !tenantId.equals(document.getTenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (document.getFileName() == null || document.getFileName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (document.getFileUrl() == null || document.getFileUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (document.getBusinessId() != null && (userId == null || tenantId == null || !businessMembershipService.userHasAccessToBusiness(userId, document.getBusinessId(), tenantId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (document.getDocumentType() == null) {
            document.setDocumentType(com.bhive.document.entity.DocumentType.OTHER);
        }

        if (document.getStatus() == null) {
            document.setStatus(com.bhive.document.entity.DocumentStatus.ACTIVE);
        }

        Document saved = documentRepository.save(document);
        if (saved.getBusinessId() != null && saved.getCustomerId() != null) {
            customerProfileRepository.findById(saved.getCustomerId())
                .filter(customer -> tenantId.equals(customer.getTenantId()))
                .map(CustomerProfile::getUserId)
                .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(userId))
                .ifPresent(recipientUserId -> notificationService.create(
                    tenantId,
                    recipientUserId,
                    "DOCUMENT_ADDED",
                    "New document",
                    saved.getFileName() + " is available in your documents.",
                    "/dashboard"
                ));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        return documentRepository.findById(id)
            .filter(document -> tenantId == null || tenantId.equals(document.getTenantId()))
            .filter(document -> userId == null || document.getBusinessId() == null || businessMembershipService.userHasAccessToBusiness(userId, document.getBusinessId(), tenantId))
            .map(document -> {
                document.setStatus(com.bhive.document.entity.DocumentStatus.DELETED);
                documentRepository.save(document);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }
}
