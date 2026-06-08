// 文件功能：定义后台账号管理接口返回给前端的用户摘要。
package com.campusresale.identity.admin;

import com.campusresale.identity.domain.AdminUserAccountRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 后台账号管理响应，包含账号状态、邮箱、角色和关键时间信息。
 */
public record AdminUserResponse(
        /** 用户主键。 */
        long id,
        /** 登录用户名。 */
        String username,
        /** 昵称。 */
        String nickname,
        /** 个人邮箱。 */
        String personalEmail,
        /** 账号状态。 */
        String accountStatus,
        /** 账号禁用时间。 */
        Instant disabledAt,
        /** 创建时间。 */
        Instant createdAt,
        /** 更新时间。 */
        Instant updatedAt,
        /** 角色 code 列表。 */
        List<String> roles
) {

    /**
     * 从数据库记录转换为接口响应，并稳定排序角色列表。
     */
    public static AdminUserResponse from(AdminUserAccountRecord record) {
        List<String> roles = new ArrayList<>(record.roles());
        roles.sort(Comparator.naturalOrder());
        return new AdminUserResponse(
                record.id(),
                record.username(),
                record.nickname(),
                record.personalEmail(),
                record.accountStatus(),
                record.disabledAt(),
                record.createdAt(),
                record.updatedAt(),
                roles
        );
    }
}
