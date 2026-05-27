// 文件功能：验证 BCrypt 密码哈希和明文密码校验。
package com.campusresale.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void hashesPasswordWithBcryptAndVerifiesRawPassword() {
        String hash = passwordService.hash("520zikejiang");

        assertThat(hash).startsWith("$2");
        assertThat(passwordService.matches("520zikejiang", hash)).isTrue();
        assertThat(passwordService.matches("wrong-password", hash)).isFalse();
    }
}
