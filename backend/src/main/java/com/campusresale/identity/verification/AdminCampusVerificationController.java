package com.campusresale.identity.verification;

import com.campusresale.identity.verification.CampusVerificationRequests.ReviewRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RestController
@RequestMapping("/api/admin/verifications")
public class AdminCampusVerificationController {

    private final CampusVerificationService campusVerificationService;

    public AdminCampusVerificationController(CampusVerificationService campusVerificationService) {
        this.campusVerificationService = campusVerificationService;
    }

    @GetMapping
    public PageResponse<CampusVerificationResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return campusVerificationService.adminList(status, page, pageSize);
    }

    @PostMapping("/{id}/approve")
    public CampusVerificationResponse approve(
            @PathVariable long id,
            @Valid @RequestBody(required = false) ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return campusVerificationService.approve(id, request, principal(servletRequest), clientIp(servletRequest));
    }

    @PostMapping("/{id}/reject")
    public CampusVerificationResponse reject(
            @PathVariable long id,
            @Valid @RequestBody(required = false) ReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return campusVerificationService.reject(id, request, principal(servletRequest), clientIp(servletRequest));
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
