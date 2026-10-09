package com.bhive.notification.controller;

import com.bhive.common.util.TenantContext;
import com.bhive.notification.dto.NotificationFeed;
import com.bhive.notification.dto.NotificationSummary;
import com.bhive.notification.service.NotificationService;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my")
    public ResponseEntity<NotificationFeed> getMyNotifications() {
        Optional<Recipient> recipient = currentRecipient();
        if (recipient.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Recipient current = recipient.get();
        return ResponseEntity.ok(notificationService.getFeed(current.tenantId(), current.userId()));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationSummary> markRead(@PathVariable Long notificationId) {
        Optional<Recipient> recipient = currentRecipient();
        if (recipient.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Recipient current = recipient.get();
        return notificationService.markRead(current.tenantId(), current.userId(), notificationId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/my/read")
    public ResponseEntity<Void> markAllRead() {
        Optional<Recipient> recipient = currentRecipient();
        if (recipient.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Recipient current = recipient.get();
        notificationService.markAllRead(current.tenantId(), current.userId());
        return ResponseEntity.noContent().build();
    }

    private Optional<Recipient> currentRecipient() {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null || tenantId <= 0 || userId <= 0) return Optional.empty();
        return Optional.of(new Recipient(tenantId, userId));
    }

    private record Recipient(Long tenantId, Long userId) { }
}