// 文件功能：生成密码找回一次性令牌并计算其数据库 hash。
package com.campusresale.identity.application;

import com.campusresale.platform.security.TokenHasher;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 密码找回令牌生成器，负责生成高熵明文 token，并返回对应 SHA-256 hash。
 */
@Component
public class PasswordResetTokenGenerator {

    /** 密码重置令牌默认有效期。 */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /** token 随机字节长度；32 字节经 URL-safe Base64 后适合放入邮件链接。 */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final TokenHasher tokenHasher;

    public PasswordResetTokenGenerator(TokenHasher tokenHasher) {
        this.tokenHasher = tokenHasher;
    }

    /**
     * 生成新的密码找回令牌。
     */
    public PasswordResetToken generate(Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new PasswordResetToken(rawToken, tokenHasher.sha256(rawToken), now.plus(TOKEN_TTL));
    }
}
