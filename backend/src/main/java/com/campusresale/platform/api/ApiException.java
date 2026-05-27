// 文件功能：定义携带 HTTP 状态、业务错误码和错误详情的 API 异常。
package com.campusresale.platform.api;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 业务异常基类，Controller 和拦截器通过它表达契约化错误码和 HTTP 状态。
 */
public class ApiException extends RuntimeException {

    /**
     * HTTP 状态码，例如 400、401、403。
     */
    private final HttpStatus status;

    /**
     * 业务错误码，对齐 docs/阶段契约/m1_contracts.md。
     */
    private final String code;

    /**
     * 结构化错误详情，例如字段名、限制条件等。
     */
    private final Map<String, Object> details;

    /**
     * 创建无 details 的 API 异常。
     */
    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Collections.emptyMap());
    }

    /**
     * 创建带 details 的 API 异常。
     */
    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Collections.emptyMap() : Map.copyOf(details);
    }

    /**
     * 返回 HTTP 状态码。
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 返回业务错误码。
     */
    public String code() {
        return code;
    }

    /**
     * 返回结构化错误详情。
     */
    public Map<String, Object> details() {
        return details;
    }
}
