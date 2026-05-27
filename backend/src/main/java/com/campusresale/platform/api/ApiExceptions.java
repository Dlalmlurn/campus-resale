// 文件功能：集中创建常用 API 异常，避免错误码和中文提示散落在业务代码中。
package com.campusresale.platform.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 常用 API 异常工厂，集中维护 M1 契约里的错误码、状态码和中文提示。
 */
public final class ApiExceptions {

    /**
     * 工具类不需要实例化。
     */
    private ApiExceptions() {
    }

    /**
     * 字段校验失败，返回 400 VALIDATION_FAILED。
     */
    public static ApiException validation(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, details);
    }

    /**
     * 未登录访问受保护接口，返回 401 AUTH_REQUIRED。
     */
    public static ApiException authRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
    }

    /**
     * 写请求来源校验失败，返回 403 CSRF_REQUIRED。
     */
    public static ApiException csrfRequired() {
        return new ApiException(HttpStatus.FORBIDDEN, "CSRF_REQUIRED", "请求来源校验失败，请从本站页面重新操作");
    }

    /**
     * 已登录但权限不足，返回 403 FORBIDDEN。
     */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /**
     * 资源冲突，例如用户名已存在，返回 409 CONFLICT。
     */
    public static ApiException conflict(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message, details);
    }

    /**
     * 未预期服务端错误，返回 500 INTERNAL_ERROR。
     */
    public static ApiException internalError() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务端处理失败");
    }
}
