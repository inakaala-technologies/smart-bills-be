package com.bhive.notification.service;

import com.bhive.notification.dto.NotificationFeed;
import com.bhive.notification.dto.NotificationSummary;
import com.bhive.notification.entity.AppNotification;
import com.bhive.notification.repository.AppNotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final AppNotificationRepository notificationRepository;

    public NotificationService(AppNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public AppNotification create(Long tenantId, Long userId, String type, String title, String message, String targetUrl) {
        if (tenantId == null || userId == null || type == null || type.isBlank() || type.length() > 60
            || title == null || title.isBlank() || title.length() > 160
            || message == null || message.isBlank() || message.length() > 1000
            || targetUrl != null && targetUrl.length() > 500) {
            throw new IllegalArgumentException("A valid recipient, type, title, and message are required.");
        }

        AppNotification notification = new AppNotification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setType(type.trim());
        notification.setTitle(title.trim());
        notification.setMessage(message.trim());
        notification.setTargetUrl(targetUrl);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationFeed getFeed(Long tenantId, Long userId) {
        List<NotificationSummary> notifications = notificationRepository
            .findTop50ByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId)
            .stream()
            .map(NotificationSummary::from)
            .toList();
        long unreadCount = notificationRepository.countByTenantIdAndUserIdAndReadAtIsNull(tenantId, userId);
        return new NotificationFeed(notifications, unreadCount);
    }

    @Transactional
    public Optional<NotificationSummary> markRead(Long tenantId, Long userId, Long notificationId) {
        return notificationRepository.findByIdAndTenantIdAndUserId(notificationId, tenantId, userId)
            .map(notification -> {
                if (notification.getReadAt() == null) notification.setReadAt(LocalDateTime.now());
                return NotificationSummary.from(notificationRepository.save(notification));
            });
    }

    @Transactional
    public int markAllRead(Long tenantId, Long userId) {
        List<AppNotification> unread = notificationRepository.findByTenantIdAndUserIdAndReadAtIsNull(tenantId, userId);
        if (unread.isEmpty()) return 0;
        LocalDateTime readAt = LocalDateTime.now();
        unread.forEach(notification -> notification.setReadAt(readAt));
        notificationRepository.saveAll(unread);
        return unread.size();
    }
}