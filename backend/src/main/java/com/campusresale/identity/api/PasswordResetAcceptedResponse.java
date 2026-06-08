// 文件功能：定义密码找回发起接口的统一响应，避免暴露邮箱是否存在。
package com.campusresale.identity.api;

/**
 * 密码找回受理响应；无论邮箱是否匹配账号都返回 accepted=true，降低账号枚举风险。
 */
public record PasswordResetAcceptedResponse(
        /** 是否已受理本次找回请求。 */
        boolean accepted,
        /** 给前端展示的固定提示文案。 */
        String message
) {
}
