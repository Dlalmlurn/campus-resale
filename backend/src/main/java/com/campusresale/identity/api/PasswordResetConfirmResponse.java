// 文件功能：定义密码重置确认接口的成功响应。
package com.campusresale.identity.api;

/**
 * 密码重置确认响应，表示令牌已消费且旧 session 已全部撤销。
 */
public record PasswordResetConfirmResponse(
        /** 是否完成密码重置。 */
        boolean reset
) {
}
