// 文件功能：在一次 HTTP 请求内保存和读取当前登录用户，供 Controller 与拦截器共享。
package com.campusresale.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 当前用户请求上下文工具，避免业务代码直接解析 Cookie 或重复查询 session。
 */
public final class CurrentPrincipalContext {

    /**
     * request attribute 名称，避免和其他模块使用的属性名冲突。
     */
    public static final String REQUEST_ATTRIBUTE = CurrentPrincipalContext.class.getName() + ".principal";

    /**
     * 工具类不需要实例化。
     */
    private CurrentPrincipalContext() {
    }

    /**
     * 从当前请求中读取登录主体。
     */
    public static Optional<CurrentPrincipal> get(HttpServletRequest request) {
        // request attribute 是 Object，需要先做类型判断再转换。
        Object principal = request.getAttribute(REQUEST_ATTRIBUTE);
        if (principal instanceof CurrentPrincipal currentPrincipal) {
            return Optional.of(currentPrincipal);
        }
        return Optional.empty();
    }

    /**
     * 把登录主体写入当前请求，供后续拦截器和 Controller 读取。
     */
    public static void set(HttpServletRequest request, CurrentPrincipal principal) {
        request.setAttribute(REQUEST_ATTRIBUTE, principal);
    }
}
