// 文件功能：定义用户账号领域对象，承载登录和角色判断所需字段。
package com.campusresale.identity.domain;

import java.util.Set;

/**
 * 用户账号领域对象，承载登录校验、角色判断和 CurrentUser 响应所需的最小字段。
 */
public record UserAccount(
        /** 用户主键，对应 users.id。 */
        long id,
        /** 规范化用户名，当前规则为 3 到 20 位小写字母、数字、下划线。 */
        String username,
        /** BCrypt 密码哈希，对应 users.password_hash，不保存明文密码。 */
        String passwordHash,
        /** 用户展示昵称，对应 users.nickname。 */
        String nickname,
        /** 当前头像文件 id，对应 users.avatar_file_id；为空时前端展示默认头像。 */
        Long avatarFileId,
        /** 账号状态，对应 users.account_status；只有 ACTIVE 允许登录和使用 session。 */
        String accountStatus,
        /** 用户拥有的角色 code 集合，例如 REGISTERED_USER、VERIFIED_STUDENT。 */
        Set<String> roles
) {

    /**
     * 判断账号是否可用。
     */
    public boolean isActive() {
        return "ACTIVE".equals(accountStatus);
    }

    /**
     * 判断用户是否拥有指定角色。
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
