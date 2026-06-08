// 文件功能：验证打开 campus-resale.security.cookie-secure 后登录 Cookie 追加 Secure。
package com.campusresale.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusresale.identity.application.AuthResult;
import com.campusresale.identity.application.AuthService;
import com.campusresale.identity.application.SessionLookupService;
import com.campusresale.files.FileService;
import com.campusresale.platform.api.GlobalApiExceptionHandler;
import com.campusresale.platform.security.AuthorizationInterceptor;
import com.campusresale.platform.security.OriginCsrfInterceptor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({GlobalApiExceptionHandler.class, AuthorizationInterceptor.class})
@TestPropertySource(properties = "campus-resale.security.cookie-secure=true")
class AuthControllerCookieSecureTest {

    private static final String LOCAL_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private FileService fileService;

    @MockBean
    private OriginCsrfInterceptor originCsrfInterceptor;

    @MockBean
    private SessionLookupService sessionLookupService;

    @BeforeEach
    void allowSecurityInfrastructureByDefault() throws Exception {
        when(originCsrfInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(sessionLookupService.loadByRawToken(nullable(String.class))).thenReturn(Optional.empty());
    }

    @Test
    void loginCookieCarriesSecureWhenEnabled() throws Exception {
        when(authService.login(any(), any(), any())).thenReturn(new AuthResult(
                new CurrentUserResponse(1L, "student_demo", "认证学生演示账号", null, List.of("REGISTERED_USER", "VERIFIED_STUDENT"), "APPROVED", true),
                "raw-token",
                604800
        ));

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .contentType("application/json")
                        .content("""
                                {"username":"student_demo","password":"520zikejiang"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")));
    }
}
