// 文件功能：承载密码找回令牌的明文值、哈希值和过期时间。
package com.campusresale.identity.application;

import java.time.Instant;

/**
 * 密码找回令牌值对象；明文只用于邮件投递，数据库仅保存 tokenHash。
 */
public record PasswordResetToken(
        /** 邮件中使用的一次性明文令牌。 */
        String rawToken,
        /** 明文令牌的 SHA-256 hash，落库和校验使用。 */
        String tokenHash,
        /** 令牌过期时间。 */
        Instant expiresAt
) {
}
