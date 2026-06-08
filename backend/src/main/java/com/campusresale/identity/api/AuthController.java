// 文件功能：实现注册、登录、退出、当前用户查询、密码找回和自助注销 Auth API。
package com.campusresale.identity.api;

import com.campusresale.identity.api.AuthRequests.ChangePasswordRequest;
import com.campusresale.identity.api.AuthRequests.DeleteAccountRequest;
import com.campusresale.identity.api.AuthRequests.LoginRequest;
import com.campusresale.identity.api.AuthRequests.PasswordResetConfirmRequest;
import com.campusresale.identity.api.AuthRequests.PasswordResetRequest;
import com.campusresale.identity.api.AuthRequests.RegisterRequest;
import com.campusresale.identity.application.AuthResult;
import com.campusresale.identity.application.AuthService;
import com.campusresale.files.FileKind;
import com.campusresale.files.FileService;
import com.campusresale.files.VisibilityScope;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.CurrentPrincipalContext;
import com.campusresale.platform.security.RequireLogin;
import com.campusresale.platform.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Auth API 控制器，提供注册、登录、退出、当前用户查询、密码找回和自助注销接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Auth 应用服务：Controller 只负责 HTTP 入参/出参，业务逻辑交给它处理。
     */
    private final AuthService authService;

    /**
     * 文件服务：当前只在头像上传时复用统一图片存储、校验和元数据记录。
     */
    private final FileService fileService;

    /**
     * 是否在登录 Cookie 上追加 Secure 属性。
     * 公网 HTTPS 部署应通过 CAMPUS_RESALE_COOKIE_SECURE=true 打开，本地 HTTP 联调保持 false。
     */
    private final boolean cookieSecure;

    /**
     * 构造 AuthController，Spring 会自动注入 AuthService 和 Cookie Secure 开关。
     */
    public AuthController(
            AuthService authService,
            FileService fileService,
            @Value("${campus-resale.security.cookie-secure:false}") boolean cookieSecure
    ) {
        this.authService = authService;
        this.fileService = fileService;
        this.cookieSecure = cookieSecure;
    }

    /**
     * 注册普通用户：只授予 REGISTERED_USER，并在成功后直接创建登录 session。
     */
    @PostMapping("/register")
    public CurrentUserResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // clientIp 和 User-Agent 会写入 user_sessions，便于后续做安全审计和设备展示。
        AuthResult result = authService.register(request, clientIp(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));
        writeSessionCookie(servletResponse, result);
        return result.currentUser();
    }

    /**
     * 用户登录：校验用户名密码，成功后通过 Set-Cookie 写入 HttpOnly session token。
     */
    @PostMapping("/login")
    public CurrentUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // 登录成功后会生成新的真实 token；普通访问接口不会轮换 token。
        AuthResult result = authService.login(request, clientIp(servletRequest), servletRequest.getHeader(HttpHeaders.USER_AGENT));
        writeSessionCookie(servletResponse, result);
        return result.currentUser();
    }

    /**
     * 退出登录：撤销当前 Cookie 对应的服务端 session，并清空浏览器 Cookie。
     */
    @PostMapping("/logout")
    public LogoutResponse logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        // 退出时只撤销当前 Cookie 对应的 session，不影响同账号其他允许存在的 session。
        authService.logout(sessionCookieValue(servletRequest));
        clearSessionCookie(servletResponse);
        return new LogoutResponse(true);
    }

    /**
     * 当前用户查询：依赖 @RequireLogin 保证只有有效 session 可以访问。
     */
    @RequireLogin
    @GetMapping("/me")
    public CurrentUserResponse me(HttpServletRequest servletRequest) {
        // CurrentPrincipal 由 SessionAuthenticationFilter 提前写入 request attribute。
        CurrentPrincipal principal = CurrentPrincipalContext.get(servletRequest)
                .orElseThrow(ApiExceptions::authRequired);
        return authService.currentUser(principal);
    }

    /**
     * 上传并绑定当前用户头像；头像以 PUBLIC AVATAR 文件保存，便于商品、消息和个人页直接展示。
     */
    @RequireLogin
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CurrentUserResponse updateAvatar(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest servletRequest
    ) {
        CurrentPrincipal principal = CurrentPrincipalContext.get(servletRequest)
                .orElseThrow(ApiExceptions::authRequired);
        var uploaded = fileService.upload(file, FileKind.AVATAR, VisibilityScope.PUBLIC, principal);
        return authService.updateAvatar(principal, uploaded.id());
    }

    /**
     * 发起邮箱找回密码：统一返回受理，避免暴露邮箱是否注册。
     */
    @PostMapping("/password-reset/request")
    public PasswordResetAcceptedResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return authService.requestPasswordReset(request);
    }

    /**
     * 使用邮件令牌重置密码。
     */
    @PostMapping("/password-reset/confirm")
    public PasswordResetConfirmResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return authService.confirmPasswordReset(request);
    }

    /**
     * 登录态直接修改密码；无需邮箱，校验当前密码后设置新密码并撤销其它端 session。
     */
    @RequireLogin
    @PostMapping("/me/password")
    public CurrentUserResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        CurrentPrincipal principal = CurrentPrincipalContext.get(servletRequest)
                .orElseThrow(ApiExceptions::authRequired);
        return authService.changePassword(principal, request.currentPassword(), request.newPassword());
    }

    /**
     * 当前用户自助注销账号；成功后软禁用账号并清空浏览器 Cookie。
     */
    @RequireLogin
    @PostMapping("/me/delete")
    public DeleteAccountResponse deleteOwnAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        CurrentPrincipal principal = CurrentPrincipalContext.get(servletRequest)
                .orElseThrow(ApiExceptions::authRequired);
        authService.deleteOwnAccount(principal, request.password());
        clearSessionCookie(servletResponse);
        return new DeleteAccountResponse(true);
    }

    /**
     * 写入登录 Cookie。这里手写 Set-Cookie 是为了显式带上 SameSite=Lax。
     */
    private void writeSessionCookie(HttpServletResponse response, AuthResult result) {
        // Servlet Cookie API 没有 SameSite 属性，使用响应头追加以满足 M1 Cookie 契约。
        response.addHeader(HttpHeaders.SET_COOKIE,
                "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax%s"
                        .formatted(SecurityProperties.SESSION_COOKIE_NAME, result.rawSessionToken(), result.idleTtlSeconds(), secureAttribute()));
    }

    /**
     * 清空登录 Cookie，浏览器收到 Max-Age=0 后会删除本地 CR_SESSION。
     */
    private void clearSessionCookie(HttpServletResponse response) {
        // Max-Age=0 是浏览器删除 Cookie 的标准方式。
        response.addHeader(HttpHeaders.SET_COOKIE,
                "%s=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax%s"
                        .formatted(SecurityProperties.SESSION_COOKIE_NAME, secureAttribute()));
    }

    /**
     * 按配置返回 Cookie 的 Secure 属性片段；HTTPS 部署下追加 "; Secure"，本地 HTTP 返回空串。
     */
    private String secureAttribute() {
        return cookieSecure ? "; Secure" : "";
    }

    /**
     * 从请求 Cookie 中取出真实 session token；数据库只会保存它的 SHA-256 hash。
     */
    private String sessionCookieValue(HttpServletRequest request) {
        // request.getCookies() 可能为 null，尤其是首次访问或无登录态请求。
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        // 只读取 CR_SESSION，忽略浏览器携带的其他 Cookie。
        for (Cookie cookie : cookies) {
            if (SecurityProperties.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 记录 session 创建 IP。生产环境反向代理下优先取 X-Forwarded-For 第一段。
     */
    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For 可能是 "客户端IP, 代理1, 代理2"，第一段才是原始客户端 IP。
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        // 本地开发或无代理部署时直接使用 Servlet 容器看到的远端地址。
        return request.getRemoteAddr();
    }
}
