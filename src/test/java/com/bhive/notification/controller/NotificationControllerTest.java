package com.bhive.notification.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhive.notification.dto.NotificationFeed;
import com.bhive.notification.dto.NotificationSummary;
import com.bhive.notification.service.NotificationService;
import com.bhive.common.config.SecurityConfig;
import com.bhive.common.config.AccessTokenService;
import com.bhive.common.config.AccessTokenService.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.mockito.Mockito.when;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private AccessTokenService accessTokenService;

    @Test
    void returnsCurrentUsersFeedAndUnreadCount() throws Exception {
        when(accessTokenService.verify("notification-test-token"))
            .thenReturn(Optional.of(new AuthenticatedUser(8L, List.of(5L))));
        NotificationSummary notification = new NotificationSummary(
            12L, "MEMBERSHIP_REMINDER", "Membership reminder", "Renew your plan", "/dashboard",
            LocalDateTime.of(2026, 9, 30, 10, 0), null
        );
        when(notificationService.getFeed(5L, 8L)).thenReturn(new NotificationFeed(List.of(notification), 1));

        mockMvc.perform(asRecipient(get("/api/v1/notifications/my")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(1))
            .andExpect(jsonPath("$.notifications[0].id").value(12))
            .andExpect(jsonPath("$.notifications[0].title").value("Membership reminder"));
    }

    @Test
    void marksNotificationReadForCurrentUser() throws Exception {
        when(accessTokenService.verify("notification-test-token"))
            .thenReturn(Optional.of(new AuthenticatedUser(8L, List.of(5L))));
        NotificationSummary notification = new NotificationSummary(
            12L, "MEMBERSHIP_REMINDER", "Membership reminder", "Renew your plan", "/dashboard",
            LocalDateTime.of(2026, 9, 30, 10, 0), LocalDateTime.of(2026, 9, 30, 11, 0)
        );
        when(notificationService.markRead(5L, 8L, 12L)).thenReturn(Optional.of(notification));

        mockMvc.perform(asRecipient(patch("/api/v1/notifications/12/read")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    void marksAllCurrentUsersNotificationsRead() throws Exception {
        when(accessTokenService.verify("notification-test-token"))
            .thenReturn(Optional.of(new AuthenticatedUser(8L, List.of(5L))));
        mockMvc.perform(asRecipient(patch("/api/v1/notifications/my/read")))
            .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(5L, 8L);
    }

    private static MockHttpServletRequestBuilder asRecipient(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer notification-test-token").header("X-Tenant-Id", "5");
    }
}