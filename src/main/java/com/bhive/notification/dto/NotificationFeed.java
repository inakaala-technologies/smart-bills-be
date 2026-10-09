package com.bhive.notification.dto;

import java.util.List;

public record NotificationFeed(List<NotificationSummary> notifications, long unreadCount) {
}