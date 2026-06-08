// 文件功能：验证 AuthController 的 Cookie、当前用户、退出和统一错误响应行为。
package com.campusresale.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusresale.identity.application.AuthResult;
import com.campusresale.identity.application.AuthService;
import com.campusresale.identity.application.SessionLookupService;
import com.campusresale.files.FileKind;
import com.campusresale.files.FileService;
import com.campusresale.files.StoredFileSummary;
import com.campusresale.files.VisibilityScope;
import com.campusresale.platform.api.GlobalApiExceptionHandler;
import com.campusresale.platform.security.AuthorizationInterceptor;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.OriginCsrfInterceptor;
import com.campusresale.platform.security.SecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({GlobalApiExceptionHandler.class, AuthorizationInterceptor.class})
class AuthControllerTest {

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
    void loginWritesHttpOnlySessionCookieAndReturnsCurrentUser() throws Exception {
        when(authService.login(any(), any(), any())).thenReturn(new AuthResult(
                currentUser(),
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
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("CR_SESSION=raw-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                // 默认（本地 HTTP）不带 Secure，避免本地登录 Cookie 因 Secure 而无法回传。
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Secure"))))
                .andExpect(jsonPath("$.username").value("student_demo"))
                .andExpect(jsonPath("$.canTrade").value(true));
    }

    @Test
    void meRequiresCurrentPrincipal() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                1L,
                "student_demo",
                "认证学生演示账号",
                "ACTIVE",
                Set.of("REGISTERED_USER", "VERIFIED_STUDENT"),
                10L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
        when(authService.currentUser(principal)).thenReturn(currentUser());

        mockMvc.perform(get("/api/auth/me")
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));
    }

    @Test
    void meReturnsContractErrorWhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void logoutRevokesSessionAndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .cookie(new jakarta.servlet.http.Cookie(SecurityProperties.SESSION_COOKIE_NAME, "raw-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("CR_SESSION=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.ok").value(true));

        verify(authService).logout(eq("raw-token"));
    }

    @Test
    void passwordResetRequestReturnsAcceptedResponse() throws Exception {
        when(authService.requestPasswordReset(any())).thenReturn(new PasswordResetAcceptedResponse(
                true,
                "如果邮箱已绑定账号，系统会发送密码重置指引"
        ));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .contentType("application/json")
                        .content("""
                                {"email":"student@example.edu"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void passwordResetConfirmReturnsResetResponse() throws Exception {
        when(authService.confirmPasswordReset(any())).thenReturn(new PasswordResetConfirmResponse(true));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .contentType("application/json")
                        .content("""
                                {"token":"raw-token","newPassword":"new-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset").value(true));
    }

    @Test
    void deleteOwnAccountRequiresLoginAndClearsCookie() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                1L,
                "student_demo",
                "认证学生演示账号",
                "ACTIVE",
                Set.of("REGISTERED_USER"),
                10L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );

        mockMvc.perform(post("/api/auth/me/delete")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal)
                        .contentType("application/json")
                        .content("""
                                {"password":"520zikejiang"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.deleted").value(true));

        verify(authService).deleteOwnAccount(principal, "520zikejiang");
    }

    @Test
    void updateAvatarUploadsPublicAvatarAndReturnsCurrentUser() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                1L,
                "student_demo",
                "认证学生演示账号",
                "ACTIVE",
                Set.of("REGISTERED_USER"),
                10L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        );
        when(fileService.upload(any(), eq(FileKind.AVATAR), eq(VisibilityScope.PUBLIC), eq(principal)))
                .thenReturn(new StoredFileSummary(44L, "avatar.png", "image/png", 8L, "AVATAR", "PUBLIC", "PENDING", Instant.now()));
        when(authService.updateAvatar(principal, 44L)).thenReturn(new CurrentUserResponse(
                1L,
                "student_demo",
                "认证学生演示账号",
                "/api/files/44/content",
                List.of("REGISTERED_USER"),
                "APPROVED",
                true
        ));

        mockMvc.perform(multipart("/api/auth/me/avatar")
                        .file(file)
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .requestAttr(CurrentPrincipalContext.REQUEST_ATTRIBUTE, principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/api/files/44/content"));
    }

    private CurrentUserResponse currentUser() {
        return new CurrentUserResponse(
                1L,
                "student_demo",
                "认证学生演示账号",
                null,
                List.of("REGISTERED_USER", "VERIFIED_STUDENT"),
                "APPROVED",
                true
        );
    }
}
