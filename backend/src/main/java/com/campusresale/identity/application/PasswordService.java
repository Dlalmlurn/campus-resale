// 文件功能：封装 BCrypt 密码哈希和密码校验能力。
package com.campusresale.identity.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码哈希服务，统一使用 BCrypt；不要把用户明文密码写入日志或数据库。
 */
@Component
public class PasswordService {

    /**
     * Spring Security 提供的 BCrypt 实现；默认强度适合课程项目本地和演示环境。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 把明文密码转为 BCrypt hash 后再入库。
     *
     * @param rawPassword 用户提交的明文密码，只能短暂存在于内存中。
     * @return 可安全保存到 users.password_hash 的 BCrypt 字符串。
     */
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 校验登录密码是否匹配数据库中的 BCrypt hash。
     *
     * @param rawPassword 用户本次登录提交的明文密码。
     * @param passwordHash 数据库保存的 BCrypt hash。
     * @return true 表示密码正确。
     */
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
