package com.campusresale.notification;

import com.campusresale.notification.NotificationService.MarkAllReadResponse;
import com.campusresale.notification.NotificationService.UnreadCountResponse;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest
    ) {
        return notificationService.list(principal(servletRequest), unreadOnly, page, pageSize);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(HttpServletRequest servletRequest) {
        return notificationService.unreadCount(principal(servletRequest));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable long id, HttpServletRequest servletRequest) {
        return notificationService.markRead(id, principal(servletRequest));
    }

    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead(HttpServletRequest servletRequest) {
        return notificationService.markAllRead(principal(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }
}
