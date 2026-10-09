package com.bhive.notification.dto;

import com.bhive.notification.entity.AppNotification;
import java.time.LocalDateTime;

public record NotificationSummary(
    Long id,
    String type,
    String title,
    String message,
    String targetUrl,
    LocalDateTime createdAt,
    LocalDateTime readAt
) {
    public static NotificationSummary from(AppNotification notification) {
        return new NotificationSummary(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getTargetUrl(),
            notification.getCreatedAt(),
            notification.getReadAt()
        );
    }
}