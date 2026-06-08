// 文件功能：提供 session token 的 SHA-256 哈希能力，保证数据库不保存真实 token。
package com.campusresale.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * session token 哈希工具：浏览器持有真实 token，数据库只保存 SHA-256 指纹。
 */
@Component
public class TokenHasher {

    /**
     * 计算 token 的 SHA-256 十六进制字符串。
     *
     * @param token 浏览器持有的真实 session token。
     * @return 数据库 user_sessions.session_token_hash 保存和查询使用的 hash。
     */
    public String sha256(String token) {
        try {
            // MessageDigest 是 JDK 标准哈希 API；SHA-256 在所有常规 JDK 中都可用。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 统一使用 UTF-8，保证不同系统上同一个 token 得到相同 hash。
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            // 十六进制便于数据库保存、日志排查和唯一索引比较。
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            // 如果 JDK 缺少 SHA-256，说明运行环境异常，应快速失败。
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
