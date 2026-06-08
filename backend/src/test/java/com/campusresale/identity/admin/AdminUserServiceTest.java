// 文件功能：验证超级管理员账号管理服务的状态变更、角色操作和保护规则。
package com.campusresale.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.identity.admin.AdminUserRequests.AssignRoleRequest;
import com.campusresale.identity.admin.AdminUserRequests.UpdateAccountStatusRequest;
import com.campusresale.identity.domain.AdminUserAccountRecord;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminUserServiceTest {

    private final UserAccountRepository userAccountRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final UserSessionRepository userSessionRepository = org.mockito.Mockito.mock(UserSessionRepository.class);
    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final AdminUserService adminUserService = new AdminUserService(
            userAccountRepository,
            userSessionRepository,
            auditLogRepository
    );

    @Test
    void updateStatusDisablesAccountRevokesSessionsAndWritesAuditLog() {
        AdminUserAccountRecord before = user(2L, "student", "ACTIVE", Set.of("REGISTERED_USER"));
        AdminUserAccountRecord after = user(2L, "student", "DISABLED", Set.of("REGISTERED_USER"));
        when(userAccountRepository.findAdminRecordById(2L)).thenReturn(Optional.of(before), Optional.of(after));

        AdminUserResponse response = adminUserService.updateStatus(
                2L,
                new UpdateAccountStatusRequest("DISABLED", "违规处理"),
                superAdmin(),
                context()
        );

        assertThat(response.accountStatus()).isEqualTo("DISABLED");
        verify(userAccountRepository).updateAccountStatus(eq(2L), eq("DISABLED"), any());
        verify(userSessionRepository).revokeAllActiveSessions(eq(2L), any());
        verify(auditLogRepository).recordOperation(
                eq(1L),
                eq("USER_STATUS_UPDATE"),
                eq("USER"),
                eq(2L),
                eq(before),
                any(),
                eq("127.0.0.1"),
                eq("test-agent"),
                eq("/api/admin/users/2/status"),
                eq("POST"),
                eq("SUCCESS"),
                eq("ADMIN")
        );
    }

    @Test
    void updateStatusProtectsLastActiveSuperAdmin() {
        AdminUserAccountRecord target = user(2L, "super2", "ACTIVE", Set.of("SUPER_ADMIN"));
        when(userAccountRepository.findAdminRecordById(2L)).thenReturn(Optional.of(target));
        when(userAccountRepository.countActiveSuperAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.updateStatus(
                2L,
                new UpdateAccountStatusRequest("DISABLED", null),
                superAdmin(),
                context()
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo("CONFLICT"));

        verify(userAccountRepository, never()).updateAccountStatus(eq(2L), eq("DISABLED"), any());
    }

    @Test
    void assignRoleValidatesRoleAndWritesAuditLog() {
        AdminUserAccountRecord before = user(2L, "student", "ACTIVE", Set.of("REGISTERED_USER"));
        AdminUserAccountRecord after = user(2L, "student", "ACTIVE", Set.of("REGISTERED_USER", "VERIFIED_STUDENT"));
        when(userAccountRepository.roleExists("VERIFIED_STUDENT")).thenReturn(true);
        when(userAccountRepository.findAdminRecordById(2L)).thenReturn(Optional.of(before), Optional.of(after));

        AdminUserResponse response = adminUserService.assignRole(
                2L,
                new AssignRoleRequest("verified_student", "补认证角色"),
                superAdmin(),
                context()
        );

        assertThat(response.roles()).contains("VERIFIED_STUDENT");
        verify(userAccountRepository).assignRole(2L, "VERIFIED_STUDENT", 1L);
        verify(userSessionRepository).revokeAllActiveSessions(eq(2L), any());
        verify(auditLogRepository).recordOperation(eq(1L), eq("USER_ROLE_ASSIGN"), eq("USER"), eq(2L),
                eq(before), any(), any(), any(), any(), any(), eq("SUCCESS"), eq("ADMIN"));
    }

    private AdminUserAccountRecord user(long id, String username, String status, Set<String> roles) {
        Instant now = Instant.parse("2026-06-08T10:00:00Z");
        return new AdminUserAccountRecord(
                id,
                username,
                "Test",
                username + "@example.edu",
                status,
                "DISABLED".equals(status) ? now : null,
                now,
                now,
                roles
        );
    }

    private CurrentPrincipal superAdmin() {
        return new CurrentPrincipal(
                1L,
                "super_admin",
                "Super",
                "ACTIVE",
                Set.of("SUPER_ADMIN"),
                1L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }

    private AdminUserOperationContext context() {
        return new AdminUserOperationContext("127.0.0.1", "test-agent", "/api/admin/users/2/status", "POST");
    }
}
