package com.campusresale.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusresale.identity.application.SessionLookupService;
import com.campusresale.notification.NotificationService.MarkAllReadResponse;
import com.campusresale.notification.NotificationService.UnreadCountResponse;
import com.campusresale.platform.api.GlobalApiExceptionHandler;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.AuthorizationInterceptor;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.OriginCsrfInterceptor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({GlobalApiExceptionHandler.class, AuthorizationInterceptor.class})
class NotificationControllerTest {

    private static final String LOCAL_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private OriginCsrfInterceptor originCsrfInterceptor;

    @MockBean
    private SessionLookupService sessionLookupService;

    @BeforeEach
    void allowSecurityInfrastructureByDefault() throws Exception {
        when(originCsrfInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(sessionLookupService.loadByRawToken(nullable(String.class))).thenReturn(Optional.empty());
    }

    @Test
    void listRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void listReturnsCurrentUsersNotifications() throws Exception {
        CurrentPrincipal principal = principal(1L);
        when(notificationService.list(eq(principal), eq(true), eq(1), eq(10)))
                .thenReturn(new PageResponse<>(List.of(response()), 1, 10, 1));

        mockMvc.perform(get("/api/notifications")
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal)
                        .param("unreadOnly", "true")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void unreadCountReturnsCurrentUsersCount() throws Exception {
        CurrentPrincipal principal = principal(1L);
        when(notificationService.unreadCount(principal)).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void markReadDelegatesToService() throws Exception {
        CurrentPrincipal principal = principal(1L);
        when(notificationService.markRead(10L, principal)).thenReturn(readResponse());

        mockMvc.perform(post("/api/notifications/10/read")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markAllReadDelegatesToService() throws Exception {
        CurrentPrincipal principal = principal(1L);
        when(notificationService.markAllRead(principal)).thenReturn(new MarkAllReadResponse(2));

        mockMvc.perform(post("/api/notifications/read-all")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));
    }

    private NotificationResponse response() {
        return new NotificationResponse(
                10L,
                "ORDER_CREATED",
                "收到新的订单",
                "买家已发起下单",
                "ORDER",
                20L,
                false,
                null,
                Instant.parse("2026-06-03T07:00:00Z")
        );
    }

    private NotificationResponse readResponse() {
        return new NotificationResponse(
                10L,
                "ORDER_CREATED",
                "收到新的订单",
                "买家已发起下单",
                "ORDER",
                20L,
                true,
                Instant.parse("2026-06-03T08:00:00Z"),
                Instant.parse("2026-06-03T07:00:00Z")
        );
    }

    private CurrentPrincipal principal(long id) {
        return new CurrentPrincipal(
                id,
                "user" + id,
                "User " + id,
                "ACTIVE",
                Set.of("REGISTERED_USER"),
                10L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
