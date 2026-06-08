// 文件功能：验证注解式权限拦截器对登录、角色和交易资格注解的处理。
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;

class AuthorizationInterceptorTest {

    private final TradeEligibilityChecker tradeEligibilityChecker = mock(TradeEligibilityChecker.class);
    private final AuthorizationInterceptor interceptor = new AuthorizationInterceptor(provider(tradeEligibilityChecker));

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

    @Test
    void rejectsAnonymousRequestWhenTradeEligibilityRequired() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "tradeEligibleOnly");

        assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), handlerMethod))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("AUTH_REQUIRED"));
    }

    @Test
    void rejectsUserWithoutTradeEligibility() throws Exception {
        CurrentPrincipal principal = principalWithRoles("REGISTERED_USER", "VERIFIED_STUDENT");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(CurrentPrincipalContext.REQUEST_ATTRIBUTE)).thenReturn(principal);
        when(tradeEligibilityChecker.canTrade(principal)).thenReturn(false);
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "tradeEligibleOnly");

        assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), handlerMethod))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void allowsUserWithTradeEligibility() throws Exception {
        CurrentPrincipal principal = principalWithRoles("REGISTERED_USER", "VERIFIED_STUDENT");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(CurrentPrincipalContext.REQUEST_ATTRIBUTE)).thenReturn(principal);
        when(tradeEligibilityChecker.canTrade(principal)).thenReturn(true);
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "tradeEligibleOnly");

        org.assertj.core.api.Assertions.assertThat(interceptor.preHandle(request, mock(HttpServletResponse.class), handlerMethod))
                .isTrue();
    }

    @Test
    void rejectsTradeEligibilityAnnotationWhenCheckerMissing() throws Exception {
        AuthorizationInterceptor interceptorWithoutChecker = new AuthorizationInterceptor(provider(null));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(CurrentPrincipalContext.REQUEST_ATTRIBUTE))
                .thenReturn(principalWithRoles("REGISTERED_USER", "VERIFIED_STUDENT"));
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "tradeEligibleOnly");

        assertThatThrownBy(() -> interceptorWithoutChecker.preHandle(request, mock(HttpServletResponse.class), handlerMethod))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("FORBIDDEN"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TradeEligibilityChecker> provider(TradeEligibilityChecker checker) {
        ObjectProvider<TradeEligibilityChecker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(checker);
        return provider;
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

        @RequireTradeEligible
        public void tradeEligibleOnly() {
        }
    }
}
