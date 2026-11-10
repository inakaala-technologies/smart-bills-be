package com.bhive.receipt.controller;

import com.bhive.auth.entity.UserRole;
import com.bhive.auth.repository.UserAccountRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.receipt.dto.AdminPosPartnerApplicationResponse;
import com.bhive.receipt.service.PosPartnerApplicationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/pos-partner-applications")
public class AdminPosPartnerApplicationController {

    private final PosPartnerApplicationService service;
    private final UserAccountRepository userAccountRepository;

    public AdminPosPartnerApplicationController(
        PosPartnerApplicationService service,
        UserAccountRepository userAccountRepository
    ) {
        this.service = service;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public ResponseEntity<List<AdminPosPartnerApplicationResponse>> list() {
        requireActiveAdmin();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.listApplications());
    }

    @PostMapping("/{applicationId}/approve")
    public AdminPosPartnerApplicationResponse approve(@PathVariable String applicationId) {
        return service.approve(applicationId, requireActiveAdmin());
    }

    @PostMapping("/{applicationId}/reject")
    public AdminPosPartnerApplicationResponse reject(
        @PathVariable String applicationId,
        @jakarta.validation.Valid @RequestBody RejectApplicationRequest request
    ) {
        return service.reject(applicationId, requireActiveAdmin(), request.reason());
    }

    private Long requireActiveAdmin() {
        Long userId = TenantContext.getUserId();
        boolean isActiveAdmin = userId != null && userAccountRepository
            .findByUserIdAndRole(userId, UserRole.ADMIN)
            .stream()
            .anyMatch(account -> "ACTIVE".equals(account.getStatus()));
        if (!isActiveAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active administrator account is required.");
        }
        return userId;
    }

    public record RejectApplicationRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {
    }
}
