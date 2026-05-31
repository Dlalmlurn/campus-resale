// 文件功能：生成高随机性 session token，并计算数据库保存用的 SHA-256 hash。
package com.campusresale.identity.application;

import com.campusresale.platform.security.TokenHasher;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * session token 生成器，使用 32 字节强随机数，满足不可预测登录凭据要求。
 */
@Component
public class SessionTokenGenerator {

    /**
     * token 原始随机字节数：32 字节约等于 256 bit 随机性，足够抵抗猜测。
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 强随机数生成器：用于生成不可预测的登录凭据。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * token 哈希工具：生成 token 后立即计算数据库保存的 SHA-256 hash。
     */
    private final TokenHasher tokenHasher;

    /**
     * 构造 token 生成器，Spring 自动注入 TokenHasher。
     */
    public SessionTokenGenerator(TokenHasher tokenHasher) {
        this.tokenHasher = tokenHasher;
    }

    /**
     * 生成一次新的 session token。
     *
     * @return 同时包含 rawToken 和 tokenHash；rawToken 发给浏览器，tokenHash 写入数据库。
     */
    public SessionToken generate() {
        // bytes 保存本次 token 的随机原始字节，方法返回前不再持久化。
        byte[] bytes = new byte[TOKEN_BYTES];

        // SecureRandom 填充高随机性字节，避免 token 可预测。
        secureRandom.nextBytes(bytes);

        // URL-safe Base64 避免 Cookie 中出现 +、/、= 等不便字符。
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return new SessionToken(rawToken, tokenHasher.sha256(rawToken));
    }
}
