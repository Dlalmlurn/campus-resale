// 文件功能：承载后台账号管理审计日志需要的 HTTP 上下文。
package com.campusresale.identity.admin;

/**
 * 后台账号管理操作上下文，用于把 IP、UA、路径和方法写入 operation_logs。
 */
public record AdminUserOperationContext(
        /** 客户端 IP。 */
        String ipAddress,
        /** 浏览器或调用方 User-Agent。 */
        String userAgent,
        /** 请求路径。 */
        String requestPath,
        /** HTTP 方法。 */
        String httpMethod
) {
}
