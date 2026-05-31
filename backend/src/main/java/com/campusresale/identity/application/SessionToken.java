// 文件功能：保存一次新建 session 的真实 token 和数据库 token hash。
package com.campusresale.identity.application;

/**
 * 登录成功后的真实 session token 与数据库 hash，真实 token 只写入浏览器 Cookie。
 */
public record SessionToken(
        /** 发给浏览器的真实 token。 */
        String rawToken,
        /** rawToken 的 SHA-256 hash，写入 user_sessions.session_token_hash。 */
        String tokenHash
) {
}
