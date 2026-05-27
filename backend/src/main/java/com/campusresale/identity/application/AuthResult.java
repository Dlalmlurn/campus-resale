// 文件功能：组合认证成功后的当前用户响应和需要写入 Cookie 的真实 token。
package com.campusresale.identity.application;

import com.campusresale.identity.api.CurrentUserResponse;

/**
 * 注册或登录成功后的组合结果，包含前端用户信息和需要写入 Cookie 的真实 session token。
 */
public record AuthResult(
        /** 返回给前端的当前用户信息。 */
        CurrentUserResponse currentUser,
        /** 真实 session token，只写入 HttpOnly Cookie，不入库。 */
        String rawSessionToken,
        /** Cookie Max-Age 秒数，对应 session 闲置有效期。 */
        long idleTtlSeconds
) {
}
