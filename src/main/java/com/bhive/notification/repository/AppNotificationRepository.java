package com.bhive.notification.repository;

import com.bhive.notification.entity.AppNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findTop50ByTenantIdAndUserIdOrderByCreatedAtDesc(Long tenantId, Long userId);
    long countByTenantIdAndUserIdAndReadAtIsNull(Long tenantId, Long userId);
    Optional<AppNotification> findByIdAndTenantIdAndUserId(Long id, Long tenantId, Long userId);
    List<AppNotification> findByTenantIdAndUserIdAndReadAtIsNull(Long tenantId, Long userId);
}