// 文件功能：定义密码找回令牌数据库记录的领域对象。
package com.campusresale.identity.domain;

import java.time.Instant;

/**
 * 密码找回令牌记录；用于校验令牌归属、过期时间和消费状态。
 */
public record PasswordResetTokenRecord(
        /** 令牌记录主键。 */
        long id,
        /** 令牌归属用户 id。 */
        long userId,
        /** 令牌 SHA-256 hash。 */
        String tokenHash,
        /** 请求找回时提交的邮箱。 */
        String requestedEmail,
        /** 令牌过期时间。 */
        Instant expiresAt,
        /** 令牌消费时间；为 null 表示尚未使用。 */
        Instant consumedAt
) {
}
