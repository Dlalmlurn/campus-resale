// 文件功能：定义统一 API 错误响应 DTO，供所有业务接口返回一致错误结构。
package com.campusresale.platform.api;

import java.util.Map;

/**
 * 统一 API 错误响应结构，字段与 docs/历史资料/阶段契约/m1_contracts.md 的错误契约保持一致。
 */
public record ApiErrorResponse(
        /** 业务错误码，例如 AUTH_REQUIRED、VALIDATION_FAILED。 */
        String code,
        /** 面向前端和用户的中文错误提示。 */
        String message,
        /** 错误详情，常用于字段校验错误。 */
        Map<String, Object> details,
        /** 请求追踪 id；如果请求未带 X-Request-Id，则为 null。 */
        String traceId
) {
}
