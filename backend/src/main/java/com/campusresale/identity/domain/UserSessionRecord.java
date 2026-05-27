// 文件功能：定义服务端 session 领域对象，表达 token hash、过期和撤销状态。
package com.campusresale.identity.domain;

import java.time.Instant;

/**
 * 服务端会话记录，数据库只保存 token hash，不保存浏览器持有的真实 token。
 */
public record UserSessionRecord(
        /** session 主键，对应 user_sessions.id。 */
        long id,
        /** session 所属用户 id，对应 user_sessions.user_id。 */
        long userId,
        /** 真实 token 的 SHA-256 hash，对应 user_sessions.session_token_hash。 */
        String sessionTokenHash,
        /** 最近一次成功使用 session 的时间。 */
        Instant lastActiveAt,
        /** 闲置过期时间；每次访问会滑动续期，但不能超过 absoluteExpiresAt。 */
        Instant expiresAt,
        /** 绝对过期时间；到点后必须重新登录。 */
        Instant absoluteExpiresAt,
        /** 主动撤销时间；null 表示尚未撤销。 */
        Instant revokedAt
) {

    /**
     * 判断 session 在指定时间点是否仍可使用。
     */
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && absoluteExpiresAt.isAfter(now);
    }
}
