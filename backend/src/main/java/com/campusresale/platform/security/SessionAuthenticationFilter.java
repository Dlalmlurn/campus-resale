// 文件功能：请求进入后端时读取 CR_SESSION Cookie 并加载当前登录用户。
package com.campusresale.platform.security;

import com.campusresale.identity.application.SessionLookupService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求入口处的登录态识别 Filter：读取 CR_SESSION Cookie，加载当前用户并放入请求上下文。
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    /**
     * session 查询服务：把浏览器 Cookie 中的真实 token 转换为当前登录主体。
     */
    private final SessionLookupService sessionLookupService;

    /**
     * 构造 Filter，Spring 会自动注入 SessionLookupService。
     */
    public SessionAuthenticationFilter(SessionLookupService sessionLookupService) {
        this.sessionLookupService = sessionLookupService;
    }

    /**
     * 每个 HTTP 请求只执行一次：读取 CR_SESSION Cookie，识别用户后继续放行请求链。
     *
     * @param request 当前 HTTP 请求，可能携带 CR_SESSION Cookie。
     * @param response 当前 HTTP 响应，本 Filter 不直接写响应。
     * @param filterChain 后续 Filter / DispatcherServlet 调用链。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // rawSessionToken 是浏览器保存的真实 token；数据库中不会直接保存它。
        String rawSessionToken = findSessionCookie(request);

        // 如果 token 有效，把当前用户放到 request attribute，后续 Interceptor/Controller 共享使用。
        sessionLookupService.loadByRawToken(rawSessionToken)
                .ifPresent(principal -> CurrentPrincipalContext.set(request, principal));

        // 无论是否登录，都继续进入后续链路；需要登录的接口由 AuthorizationInterceptor 拦截。
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求 Cookie 数组中查找 CR_SESSION 的值。
     *
     * @param request 当前 HTTP 请求。
     * @return 找到时返回真实 session token；没有 Cookie 或没有 CR_SESSION 时返回 null。
     */
    private String findSessionCookie(HttpServletRequest request) {
        // request.getCookies() 可能为 null，因此先做空判断。
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        // 遍历所有 Cookie，找到约定名称 CR_SESSION。
        for (Cookie cookie : cookies) {
            if (SecurityProperties.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
