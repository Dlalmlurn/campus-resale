package com.campusresale.conversation;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversations")
public class AdminConversationController {

    private final ConversationService conversationService;

    public AdminConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
    @GetMapping("/{id}")
    public ConversationDetailResponse detail(
            @PathVariable long id,
            @RequestParam(required = false) String reason,
            HttpServletRequest servletRequest
    ) {
        return conversationService.adminDetail(id, principal(servletRequest), reason, clientIp(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
