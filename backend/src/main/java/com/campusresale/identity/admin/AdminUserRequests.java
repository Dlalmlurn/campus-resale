// 文件功能：定义超级管理员账号管理接口的请求体。
package com.campusresale.identity.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 后台账号管理请求体集合，集中声明状态和角色操作的校验规则。
 */
public final class AdminUserRequests {

    private AdminUserRequests() {
    }

    /**
     * 账号状态变更请求。
     *
     * @param accountStatus 目标账号状态：ACTIVE / LOCKED / DISABLED。
     * @param reason 操作原因，会进入审计日志 after_json。
     */
    public record UpdateAccountStatusRequest(
            @NotBlank(message = "账号状态不能为空")
            @Pattern(regexp = "ACTIVE|LOCKED|DISABLED", message = "账号状态只能是 ACTIVE、LOCKED 或 DISABLED")
            String accountStatus,

            @Size(max = 255, message = "操作原因不能超过 255 个字符")
            String reason
    ) {
    }

    /**
     * 角色授予请求。
     *
     * @param roleCode 角色 code，例如 CONTENT_ADMIN、VERIFIED_STUDENT。
     * @param reason 操作原因，会进入审计日志 after_json。
     */
    public record AssignRoleRequest(
            @NotBlank(message = "角色不能为空")
            @Size(max = 60, message = "角色长度不能超过 60 个字符")
            String roleCode,

            @Size(max = 255, message = "操作原因不能超过 255 个字符")
            String reason
    ) {
    }
}
