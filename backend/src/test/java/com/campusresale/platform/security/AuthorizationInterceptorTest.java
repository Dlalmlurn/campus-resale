// 文件功能：验证注解式权限拦截器对未登录和角色不足请求的拒绝行为。
package com.campusresale.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campusresale.platform.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class AuthorizationInterceptorTest {

    private final AuthorizationInterceptor interceptor = new AuthorizationInterceptor();

    @Test
    void rejectsAnonymousRequestWhenLoginRequired() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "loginRequired");

        assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), handlerMethod))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("AUTH_REQUIRED"));
    }

    @Test
    void rejectsUserWithoutRequiredRole() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(CurrentPrincipalContext.REQUEST_ATTRIBUTE)).thenReturn(principalWithRoles("REGISTERED_USER"));
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "adminOnly");

        assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), handlerMethod))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("FORBIDDEN"));
    }

    private CurrentPrincipal principalWithRoles(String... roles) {
        return new CurrentPrincipal(
                1L,
                "alice",
                "Alice",
                "ACTIVE",
                Set.of(roles),
                1L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }

    private static class TestController {

        @RequireLogin
        public void loginRequired() {
        }

        @RequireRole("SUPER_ADMIN")
        public void adminOnly() {
        }
    }
}
