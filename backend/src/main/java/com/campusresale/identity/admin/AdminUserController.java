// 文件功能：提供超级管理员账号管理 API，支持查询、状态调整和角色维护。
package com.campusresale.identity.admin;

import com.campusresale.identity.admin.AdminUserRequests.AssignRoleRequest;
import com.campusresale.identity.admin.AdminUserRequests.UpdateAccountStatusRequest;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 超级管理员账号管理控制器；类级权限确保只有 SUPER_ADMIN 可以操作账号和角色。
 */
@Validated
@RequireRole("SUPER_ADMIN")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 分页查询用户账号，支持关键词、状态和角色过滤。
     */
    @GetMapping
    public PageResponse<AdminUserResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String roleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return adminUserService.list(keyword, accountStatus, roleCode, page, pageSize);
    }

    /**
     * 更新账号状态：ACTIVE / LOCKED / DISABLED。
     */
    @PostMapping("/{id}/status")
    public AdminUserResponse updateStatus(
            @PathVariable long id,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return adminUserService.updateStatus(id, request, principal(servletRequest), context(servletRequest));
    }

    /**
     * 授予目标用户角色。
     */
    @PostMapping("/{id}/roles")
    public AdminUserResponse assignRole(
            @PathVariable long id,
            @Valid @RequestBody AssignRoleRequest request,
            HttpServletRequest servletRequest
    ) {
        return adminUserService.assignRole(id, request, principal(servletRequest), context(servletRequest));
    }

    /**
     * 撤销目标用户角色。
     */
    @DeleteMapping("/{id}/roles/{roleCode}")
    public AdminUserResponse removeRole(
            @PathVariable long id,
            @PathVariable String roleCode,
            @RequestParam(required = false) @Size(max = 255, message = "操作原因不能超过 255 个字符") String reason,
            HttpServletRequest servletRequest
    ) {
        return adminUserService.removeRole(id, roleCode, reason, principal(servletRequest), context(servletRequest));
    }

    private CurrentPrincipal principal(HttpServletRequest request) {
        return CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);
    }

    private AdminUserOperationContext context(HttpServletRequest request) {
        return new AdminUserOperationContext(
                clientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getRequestURI(),
                request.getMethod()
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
