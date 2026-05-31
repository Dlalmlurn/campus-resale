// 文件功能：全局捕获 Controller 异常，并转换为 docs/阶段契约/m1_contracts.md 约定的错误 JSON。
package com.campusresale.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器，把参数错误、业务错误和兜底异常统一转换为 M1 错误 JSON。
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    /**
     * 处理业务主动抛出的 ApiException。
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(toResponse(exception.code(), exception.getMessage(), exception.details(), request));
    }

    /**
     * 处理 @Valid 请求体字段校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        // fieldErrors 按字段名保存错误提示，前端可以直接定位到表单项。
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return validationResponse("请求字段校验失败", fieldErrors, request);
    }

    /**
     * 处理参数类型、缺少参数、JSON 格式错误等通用校验异常。
     */
    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidationException(Exception exception, HttpServletRequest request) {
        return validationResponse("请求字段格式不正确", Map.of("error", exception.getMessage()), request);
    }

    /**
     * 兜底处理未预期异常，避免把 Java 堆栈直接暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(toResponse("INTERNAL_ERROR", "服务端处理失败", Map.of(), request));
    }

    /**
     * 构造统一的字段校验失败响应。
     */
    private ResponseEntity<ApiErrorResponse> validationResponse(
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(toResponse("VALIDATION_FAILED", message, details, request));
    }

    /**
     * 构造统一错误响应对象。
     */
    private ApiErrorResponse toResponse(
            String code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(code, message, details == null ? Map.of() : details, traceId(request));
    }

    /**
     * 从请求头中读取追踪 id，方便联调时串联前后端日志。
     */
    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Request-Id");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }
}
