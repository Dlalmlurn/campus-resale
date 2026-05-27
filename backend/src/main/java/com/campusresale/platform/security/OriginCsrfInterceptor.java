// 文件功能：用 Origin/Referer 校验实现 M1 阶段最低 CSRF 防护。
package com.campusresale.platform.security;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.config.CampusResaleProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * M1 最低 CSRF 防护：写请求必须来自配置允许的前端 Origin 或 Referer。
 *
 * <p>它可以防普通跨站请求伪造，但不能防终端被控制或本站 XSS；这些风险需要由终端安全、
 * 前端转义和后续更完整的 CSRF token 方案共同处理。</p>
 */
@Component
public class OriginCsrfInterceptor implements HandlerInterceptor {

    /**
     * 安全 HTTP 方法：这些请求理论上不改变服务端状态，M1 阶段不做 CSRF 来源校验。
     */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * 允许发起写请求的前端来源，例如 http://localhost:5173。
     */
    private final Set<String> allowedOrigins;

    /**
     * 从项目配置读取 CORS 白名单，并复用为 CSRF 来源白名单。
     */
    public OriginCsrfInterceptor(CampusResaleProperties properties) {
        this.allowedOrigins = new HashSet<>(properties.cors().allowedOrigins());
    }

    /**
     * Controller 执行前校验请求来源。
     *
     * @param request 当前 HTTP 请求。
     * @param response 当前 HTTP 响应，本拦截器不直接写响应。
     * @param handler 当前处理器，CSRF 规则对 /api/** 统一生效。
     * @return true 表示来源可信；抛异常表示拒绝写请求。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // GET/HEAD/OPTIONS 不改变数据，直接放行。
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }

        // Origin 是浏览器跨站/同站写请求常带的来源头，优先检查它。
        String origin = request.getHeader("Origin");
        if (isAllowedOrigin(origin)) {
            return true;
        }

        // 部分场景没有 Origin 时退回 Referer，从完整 URL 中提取 scheme://host:port 比对。
        String referer = request.getHeader("Referer");
        if (isAllowedReferer(referer)) {
            return true;
        }

        throw ApiExceptions.csrfRequired();
    }

    /**
     * 判断 Origin 是否在允许列表中。
     */
    private boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    /**
     * 从 Referer URL 中提取来源并判断是否允许。
     */
    private boolean isAllowedReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return false;
        }
        try {
            // Referer 可能包含路径和查询参数，这里只取 scheme://authority。
            URI uri = URI.create(referer);
            String origin = uri.getScheme() + "://" + uri.getAuthority();
            return allowedOrigins.contains(origin);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
