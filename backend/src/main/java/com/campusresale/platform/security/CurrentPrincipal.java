// 文件功能：描述当前请求识别出的登录用户、角色和 session 信息。
package com.campusresale.platform.security;

import java.time.Instant;
import java.util.Set;

/**
 * 当前请求已识别的登录主体，Filter 从 Cookie session 中加载并放入请求上下文。
 */
public record CurrentPrincipal(
        /** 当前登录用户 id。 */
        long id,
        /** 当前登录用户名。 */
        String username,
        /** 当前登录用户昵称。 */
        String nickname,
        /** 当前账号状态，正常使用时应为 ACTIVE。 */
        String accountStatus,
        /** 当前用户角色 code 集合。 */
        Set<String> roles,
        /** 当前请求命中的 session id。 */
        long sessionId,
        /** 当前 session 闲置过期时间。 */
        Instant sessionExpiresAt,
        /** 当前 session 绝对过期时间。 */
        Instant sessionAbsoluteExpiresAt
) {

    /**
     * 判断当前用户是否拥有指定角色。
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * 判断当前用户是否拥有候选角色中的任意一个。
     */
    public boolean hasAnyRole(String[] requiredRoles) {
        // 没有声明具体角色时，只要登录即可通过。
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }

        for (String role : requiredRoles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
}
