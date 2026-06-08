// 文件功能：定义退出登录接口的简单成功响应。
package com.campusresale.identity.api;

/**
 * 退出登录响应 DTO，保持简单明确的成功标记。
 */
public record LogoutResponse(
        /** 是否已完成退出处理；没有登录态时也返回 true，方便前端统一清理状态。 */
        boolean ok
) {
}
