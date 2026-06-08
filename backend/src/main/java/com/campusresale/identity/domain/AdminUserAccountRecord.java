// 文件功能：定义后台账号管理列表和详情使用的用户记录。
package com.campusresale.identity.domain;

import java.time.Instant;
import java.util.Set;

/**
 * 后台账号管理记录，包含用户基础信息、状态、邮箱、时间和角色集合。
 */
public record AdminUserAccountRecord(
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
        /** 禁用时间；仅 DISABLED 时通常有值。 */
        Instant disabledAt,
        /** 创建时间。 */
        Instant createdAt,
        /** 更新时间。 */
        Instant updatedAt,
        /** 角色 code 集合。 */
        Set<String> roles
) {
}
