// 文件功能：读取权限注解并统一执行登录和角色校验。
package com.campusresale.platform.security;

import com.campusresale.platform.api.ApiExceptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 注解式权限拦截器，统一执行 @RequireLogin 和 @RequireRole，避免 Controller 手写重复鉴权。
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    /**
     * Controller 方法执行前做权限判断。
     *
     * @param request 当前 HTTP 请求，Filter 已可能写入 CurrentPrincipal。
     * @param response 当前 HTTP 响应，本方法通常不直接写响应。
     * @param handler Spring 匹配到的处理器，Controller 方法会表现为 HandlerMethod。
     * @return true 表示继续进入 Controller；抛异常表示拒绝访问。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 静态资源等非 Controller 方法不参与注解式权限判断。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // requireRole 表示“需要任意一个指定角色”，requireLogin 表示“只需要登录”。
        RequireRole requireRole = findAnnotation(handlerMethod, RequireRole.class);
        RequireLogin requireLogin = findAnnotation(handlerMethod, RequireLogin.class);

        // 方法和类上都没有权限注解时，保持公开访问。
        if (requireRole == null && requireLogin == null) {
            return true;
        }

        // 需要权限的接口必须先有当前登录主体，否则返回 401 AUTH_REQUIRED。
        CurrentPrincipal principal = CurrentPrincipalContext.get(request)
                .orElseThrow(ApiExceptions::authRequired);

        // 有角色要求时，只要命中任意一个角色就放行；一个都没有则返回 403 FORBIDDEN。
        if (requireRole != null && !principal.hasAnyRole(requireRole.value())) {
            throw ApiExceptions.forbidden("当前账号无权执行该操作");
        }

        return true;
    }

    /**
     * 查找方法或类上的权限注解；方法注解优先于类注解。
     *
     * @param handlerMethod 当前 Controller 方法。
     * @param type 要查找的注解类型。
     * @return 找到的注解实例；没有则返回 null。
     */
    private <A extends java.lang.annotation.Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> type) {
        // 先看具体方法，支持同一个 Controller 里不同方法配置不同权限。
        A methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), type);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        // 方法没有时再看类注解，适合整个 Controller 都要求登录或管理员权限。
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), type);
    }
}
