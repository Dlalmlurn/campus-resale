// 文件功能：实现超级管理员账号管理的状态变更、角色操作和审计留痕。
package com.campusresale.identity.admin;

import com.campusresale.identity.admin.AdminUserRequests.AssignRoleRequest;
import com.campusresale.identity.admin.AdminUserRequests.UpdateAccountStatusRequest;
import com.campusresale.identity.domain.AdminUserAccountRecord;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 超级管理员账号管理服务，集中处理账号状态、角色边界和 operation_logs 留痕。
 */
@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminUserService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 后台账号分页查询。
     */
    public PageResponse<AdminUserResponse> list(String keyword, String accountStatus, String roleCode, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long total = userAccountRepository.countAdminUsers(keyword, accountStatus, roleCode);
        var items = userAccountRepository.listAdminUsers(keyword, accountStatus, roleCode, safePage, safePageSize)
                .stream()
                .map(AdminUserResponse::from)
                .toList();
        return new PageResponse<>(items, safePage, safePageSize, total);
    }

    /**
     * 更新账号状态，必要时撤销目标用户全部 session。
     */
    @Transactional
    public AdminUserResponse updateStatus(
            long userId,
            UpdateAccountStatusRequest request,
            CurrentPrincipal admin,
            AdminUserOperationContext context
    ) {
        String targetStatus = request.accountStatus().trim().toUpperCase(Locale.ROOT);
        AdminUserAccountRecord before = load(userId);
        ensureStatusChangeAllowed(before, targetStatus, admin);

        Instant now = Instant.now();
        userAccountRepository.updateAccountStatus(userId, targetStatus, now);
        if (!"ACTIVE".equals(targetStatus)) {
            userSessionRepository.revokeAllActiveSessions(userId, now);
        }

        AdminUserAccountRecord after = load(userId);
        recordOperation(admin, "USER_STATUS_UPDATE", userId, before, auditAfter(after, null, request.reason()), context);
        return AdminUserResponse.from(after);
    }

    /**
     * 授予目标用户角色。
     */
    @Transactional
    public AdminUserResponse assignRole(
            long userId,
            AssignRoleRequest request,
            CurrentPrincipal admin,
            AdminUserOperationContext context
    ) {
        String roleCode = normalizeRole(request.roleCode());
        if (!userAccountRepository.roleExists(roleCode)) {
            throw ApiExceptions.validation("角色不存在", Map.of("field", "roleCode"));
        }

        AdminUserAccountRecord before = load(userId);
        userAccountRepository.assignRole(userId, roleCode, admin.id());
        userSessionRepository.revokeAllActiveSessions(userId, Instant.now());

        AdminUserAccountRecord after = load(userId);
        recordOperation(admin, "USER_ROLE_ASSIGN", userId, before, auditAfter(after, roleCode, request.reason()), context);
        return AdminUserResponse.from(after);
    }

    /**
     * 撤销目标用户角色。
     */
    @Transactional
    public AdminUserResponse removeRole(
            long userId,
            String rawRoleCode,
            String reason,
            CurrentPrincipal admin,
            AdminUserOperationContext context
    ) {
        String roleCode = normalizeRole(rawRoleCode);
        if (!userAccountRepository.roleExists(roleCode)) {
            throw ApiExceptions.validation("角色不存在", Map.of("field", "roleCode"));
        }

        AdminUserAccountRecord before = load(userId);
        ensureRoleRemovalAllowed(before, roleCode, admin);

        userAccountRepository.removeRole(userId, roleCode);
        userSessionRepository.revokeAllActiveSessions(userId, Instant.now());

        AdminUserAccountRecord after = load(userId);
        recordOperation(admin, "USER_ROLE_REMOVE", userId, before, auditAfter(after, roleCode, reason), context);
        return AdminUserResponse.from(after);
    }

    private AdminUserAccountRecord load(long userId) {
        return userAccountRepository.findAdminRecordById(userId)
                .orElseThrow(() -> ApiExceptions.notFound("账号不存在"));
    }

    private void ensureStatusChangeAllowed(AdminUserAccountRecord target, String targetStatus, CurrentPrincipal admin) {
        if (target.id() == admin.id() && !"ACTIVE".equals(targetStatus)) {
            throw ApiExceptions.forbidden("不能停用当前登录的超级管理员账号");
        }
        if (target.roles().contains(SecurityProperties.SUPER_ADMIN_ROLE)
                && "ACTIVE".equals(target.accountStatus())
                && !"ACTIVE".equals(targetStatus)
                && userAccountRepository.countActiveSuperAdmins() <= 1) {
            throw ApiExceptions.conflict("至少需要保留一个可用的超级管理员账号", Map.of("role", SecurityProperties.SUPER_ADMIN_ROLE));
        }
    }

    private void ensureRoleRemovalAllowed(AdminUserAccountRecord target, String roleCode, CurrentPrincipal admin) {
        if (!SecurityProperties.SUPER_ADMIN_ROLE.equals(roleCode)) {
            return;
        }
        if (target.id() == admin.id()) {
            throw ApiExceptions.forbidden("不能撤销当前登录账号的超级管理员角色");
        }
        if ("ACTIVE".equals(target.accountStatus()) && userAccountRepository.countActiveSuperAdmins() <= 1) {
            throw ApiExceptions.conflict("至少需要保留一个可用的超级管理员账号", Map.of("role", SecurityProperties.SUPER_ADMIN_ROLE));
        }
    }

    private void recordOperation(
            CurrentPrincipal admin,
            String action,
            long targetUserId,
            Object before,
            Object after,
            AdminUserOperationContext context
    ) {
        auditLogRepository.recordOperation(
                admin.id(),
                action,
                "USER",
                targetUserId,
                before,
                after,
                context.ipAddress(),
                context.userAgent(),
                context.requestPath(),
                context.httpMethod(),
                "SUCCESS",
                "ADMIN"
        );
    }

    private String normalizeRole(String roleCode) {
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> auditAfter(AdminUserAccountRecord after, String roleCode, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("after", after);
        if (roleCode != null) {
            payload.put("roleCode", roleCode);
        }
        String normalizedReason = blankToNull(reason);
        if (normalizedReason != null) {
            payload.put("reason", normalizedReason);
        }
        return payload;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
