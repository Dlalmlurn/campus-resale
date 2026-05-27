// 文件功能：验证 Origin/Referer CSRF 拦截器的放行和拒绝规则。
package com.campusresale.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.config.CampusResaleProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class OriginCsrfInterceptorTest {

    private final OriginCsrfInterceptor interceptor = new OriginCsrfInterceptor(
            new CampusResaleProperties(
                    new CampusResaleProperties.Cors(List.of("http://localhost:5173")),
                    new CampusResaleProperties.Storage("http://localhost:9000", "bucket", "access", "secret")
            )
    );

    @Test
    void allowsSafeGetRequestWithoutOrigin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        assertThat(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void allowsWriteRequestFromConfiguredOrigin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Origin")).thenReturn("http://localhost:5173");

        assertThat(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void rejectsWriteRequestWithoutAllowedOriginOrReferer() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Origin")).thenReturn("http://evil.example");

        assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("CSRF_REQUIRED"));
    }
}
