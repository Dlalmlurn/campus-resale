// 文件功能：实现注册、登录、退出、session 创建和超级管理员单端登录策略。
package com.campusresale.identity.application;

import com.campusresale.identity.api.AuthRequests.LoginRequest;
import com.campusresale.identity.api.AuthRequests.PasswordResetConfirmRequest;
import com.campusresale.identity.api.AuthRequests.PasswordResetRequest;
import com.campusresale.identity.api.AuthRequests.RegisterRequest;
import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.api.PasswordResetAcceptedResponse;
import com.campusresale.identity.api.PasswordResetConfirmResponse;
import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.infrastructure.PasswordResetTokenRepository;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
import com.campusresale.files.FileService;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.SecurityProperties;
import com.campusresale.platform.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务，负责注册、登录、退出、session 创建和超级管理员单端登录策略。
 */
@Service
public class AuthService {

    /**
     * 用户名允许的字符范围：字母、数字、下划线；不允许空格和其他符号。
     */
    private static final java.util.regex.Pattern USERNAME_PATTERN = java.util.regex.Pattern.compile("^[a-z0-9_]{3,20}$");

    /**
     * session 闲置有效期：用户 7 天内没有任何有效请求就需要重新登录。
     */
    private static final Duration IDLE_TTL = Duration.ofDays(7);

    /**
     * session 绝对有效期：即使用户持续访问，30 天后也必须重新登录。
     */
    private static final Duration ABSOLUTE_TTL = Duration.ofDays(30);

    /** 用户账号仓储：负责 users / roles / user_roles 的数据库读写。 */
    private final UserAccountRepository userAccountRepository;

    /** session 仓储：负责 user_sessions 的创建、查询、续期和撤销。 */
    private final UserSessionRepository userSessionRepository;

    /** 密码找回令牌仓储：保存一次性 token hash 和消费状态。 */
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /** 密码服务：负责 BCrypt 哈希和明文密码校验。 */
    private final PasswordService passwordService;

    /** token 生成器：负责生成真实 token 和数据库 token hash。 */
    private final SessionTokenGenerator sessionTokenGenerator;

    /** 密码找回 token 生成器：负责一次性邮箱令牌。 */
    private final PasswordResetTokenGenerator passwordResetTokenGenerator;

    /** 密码找回投递边界：当前用于演示日志，后续可接 SMTP。 */
    private final PasswordResetDeliveryService passwordResetDeliveryService;

    /** 文件服务：用于校验头像文件归属和公开可见性。 */
    private final FileService fileService;

    /** token 哈希工具：退出登录时把浏览器 token 转成数据库 hash。 */
    private final TokenHasher tokenHasher;

    /** 当前用户响应组装器：集中处理角色和 canTrade 过渡逻辑。 */
    private final CurrentUserMapper currentUserMapper;

    public AuthService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordService passwordService,
            SessionTokenGenerator sessionTokenGenerator,
            PasswordResetTokenGenerator passwordResetTokenGenerator,
            PasswordResetDeliveryService passwordResetDeliveryService,
            FileService fileService,
            TokenHasher tokenHasher,
            CurrentUserMapper currentUserMapper
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordService = passwordService;
        this.sessionTokenGenerator = sessionTokenGenerator;
        this.passwordResetTokenGenerator = passwordResetTokenGenerator;
        this.passwordResetDeliveryService = passwordResetDeliveryService;
        this.fileService = fileService;
        this.tokenHasher = tokenHasher;
        this.currentUserMapper = currentUserMapper;
    }

    /**
     * 注册入口：规范化用户名、校验唯一性、保存 BCrypt 密码哈希并创建登录 session。
     */
    @Transactional
    public AuthResult register(RegisterRequest request, String ipAddress, String userAgent) {
        // 先规范化再校验，确保 "Alice" 和 "alice" 最终只会落到同一个账号；空格不会被吞掉。
        String username = normalizeUsername(request.username());
        validateUsername(username);

        // 提前查询能返回更友好的冲突提示；真正防重复仍依赖数据库唯一约束。
        if (userAccountRepository.usernameExists(username)) {
            throw ApiExceptions.conflict("用户名已存在", Map.of("field", "username"));
        }

        try {
            // 注册接口永远只创建普通用户，角色授予在 Repository 内固定为 REGISTERED_USER。
            UserAccount userAccount = userAccountRepository.createRegisteredUser(
                    username,
                    passwordService.hash(request.password()),
                    request.nickname().trim(),
                    request.personalEmail()
            );
            return createSessionFor(userAccount, ipAddress, userAgent);
        } catch (DuplicateKeyException exception) {
            throw ApiExceptions.conflict("用户名已存在", Map.of("field", "username"));
        } catch (DataIntegrityViolationException exception) {
            throw ApiExceptions.validation("注册信息不符合系统约束", Map.of("field", "username"));
        }
    }

    /**
     * 登录入口：先按用户名加载用户，再用 BCrypt 校验密码，成功后创建新 session。
     */
    @Transactional
    public AuthResult login(LoginRequest request, String ipAddress, String userAgent) {
        // 登录同样规范化用户名，让用户输入 Student_Demo / student_demo 都能命中同一账号；含空格输入会被拒绝。
        String username = normalizeUsername(request.username());
        validateUsername(username);

        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(this::invalidCredentials);

        if (!userAccount.isActive()) {
            throw ApiExceptions.forbidden("账号当前不可用");
        }
        if (!passwordService.matches(request.password(), userAccount.passwordHash())) {
            throw invalidCredentials();
        }

        return createSessionFor(userAccount, ipAddress, userAgent);
    }

    /**
     * 组装当前用户响应；这里不重新查库，直接使用 Filter 已识别出的当前主体。
     */
    public CurrentUserResponse currentUser(CurrentPrincipal principal) {
        return currentUserMapper.fromUser(userAccountRepository.findById(principal.id())
                .orElseThrow(() -> ApiExceptions.notFound("账号不存在")));
    }

    /**
     * 绑定当前用户头像：只允许使用本人上传的 PUBLIC AVATAR 文件。
     */
    @Transactional
    public CurrentUserResponse updateAvatar(CurrentPrincipal principal, long avatarFileId) {
        fileService.requireOwnedPublicAvatar(avatarFileId, principal.id());
        userAccountRepository.updateAvatarFileId(principal.id(), avatarFileId, Instant.now());
        return currentUser(principal);
    }

    /**
     * 发起邮箱找回密码。响应不暴露邮箱是否存在，匹配账号时生成一次性令牌并进入投递边界。
     */
    @Transactional
    public PasswordResetAcceptedResponse requestPasswordReset(PasswordResetRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        userAccountRepository.findActiveByPersonalEmail(email).ifPresent(userAccount -> {
            PasswordResetToken token = passwordResetTokenGenerator.generate(now);
            passwordResetTokenRepository.create(userAccount.id(), token.tokenHash(), email, token.expiresAt(), now);
            passwordResetDeliveryService.deliver(userAccount, email, token.rawToken());
        });

        return new PasswordResetAcceptedResponse(true, "如果邮箱已绑定账号，系统会发送密码重置指引");
    }

    /**
     * 使用邮箱令牌重置密码。成功后消费令牌并撤销该账号全部 session。
     */
    @Transactional
    public PasswordResetConfirmResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        Instant now = Instant.now();
        String tokenHash = tokenHasher.sha256(request.token().trim());
        var tokenRecord = passwordResetTokenRepository.findActiveByTokenHash(tokenHash, now)
                .orElseThrow(() -> ApiExceptions.validation("重置令牌无效或已过期", Map.of("field", "token")));

        UserAccount userAccount = userAccountRepository.findById(tokenRecord.userId())
                .orElseThrow(() -> ApiExceptions.notFound("账号不存在"));
        if (!userAccount.isActive()) {
            throw ApiExceptions.forbidden("账号当前不可用");
        }

        userAccountRepository.updatePasswordHash(userAccount.id(), passwordService.hash(request.newPassword()), now);
        passwordResetTokenRepository.markConsumed(tokenRecord.id(), now);
        userSessionRepository.revokeAllActiveSessions(userAccount.id(), now);
        return new PasswordResetConfirmResponse(true);
    }

    /**
     * 登录态直接修改密码：校验当前密码后设置新密码，并撤销其它端 session（保留当前会话）。
     */
    @Transactional
    public CurrentUserResponse changePassword(CurrentPrincipal principal, String currentPassword, String newPassword) {
        UserAccount userAccount = userAccountRepository.findById(principal.id())
                .orElseThrow(() -> ApiExceptions.notFound("账号不存在"));
        if (!userAccount.isActive()) {
            throw ApiExceptions.forbidden("账号当前不可用");
        }
        if (!passwordService.matches(currentPassword, userAccount.passwordHash())) {
            throw ApiExceptions.validation("当前密码不正确", Map.of("field", "currentPassword"));
        }
        if (passwordService.matches(newPassword, userAccount.passwordHash())) {
            throw ApiExceptions.validation("新密码不能与当前密码相同", Map.of("field", "newPassword"));
        }

        Instant now = Instant.now();
        userAccountRepository.updatePasswordHash(userAccount.id(), passwordService.hash(newPassword), now);
        // 只撤销其它端 session，保留当前会话，避免用户在个人中心改密后立刻被登出。
        userSessionRepository.revokeOtherActiveSessions(userAccount.id(), principal.sessionId(), now);
        return currentUser(principal);
    }

    /**
     * 当前用户自助注销账号：校验密码后软禁用账号，并撤销全部 session。
     */
    @Transactional
    public void deleteOwnAccount(CurrentPrincipal principal, String password) {
        UserAccount userAccount = userAccountRepository.findById(principal.id())
                .orElseThrow(() -> ApiExceptions.notFound("账号不存在"));

        if (userAccount.hasRole(SecurityProperties.SUPER_ADMIN_ROLE)) {
            throw ApiExceptions.forbidden("超级管理员账号不能自助注销，请先移交权限");
        }
        if (!userAccount.isActive()) {
            throw ApiExceptions.forbidden("账号当前不可用");
        }
        if (!passwordService.matches(password, userAccount.passwordHash())) {
            throw ApiExceptions.validation("密码不正确", Map.of("field", "password"));
        }

        Instant now = Instant.now();
        userAccountRepository.updateAccountStatus(userAccount.id(), "DISABLED", now);
        userSessionRepository.revokeAllActiveSessions(userAccount.id(), now);
    }

    /**
     * 退出登录：把 token 转成 hash 后撤销数据库 session 记录。
     */
    public void logout(String rawSessionToken) {
        // 没有 Cookie 时退出接口仍返回成功，保持接口幂等，方便前端无条件调用。
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            return;
        }
        userSessionRepository.revokeByTokenHash(tokenHasher.sha256(rawSessionToken), Instant.now());
    }

    /**
     * 用户名规范化：只转小写，不 trim；这样任何首尾空格都会被后续正则拒绝。
     */
    public String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    /**
     * 用户名业务校验：3 到 20 位，只允许小写字母、数字、下划线。
     */
    private void validateUsername(String normalizedUsername) {
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw ApiExceptions.validation(
                    "用户名必须为 3 到 20 位，只能包含字母、数字和下划线",
                    Map.of("field", "username")
            );
        }
    }

    /**
     * 创建服务端 session。SUPER_ADMIN 登录后撤销旧 session，其他角色允许多端。
     */
    private AuthResult createSessionFor(UserAccount userAccount, String ipAddress, String userAgent) {
        // now 是本次登录的统一时间戳，避免同一 session 内多个时间字段微小漂移。
        Instant now = Instant.now();

        // 真实 token 只返回给浏览器；数据库只保存 sessionToken.tokenHash()。
        SessionToken sessionToken = sessionTokenGenerator.generate();

        long sessionId = userSessionRepository.create(
                userAccount.id(),
                sessionToken.tokenHash(),
                now,
                now.plus(IDLE_TTL),
                now.plus(ABSOLUTE_TTL),
                ipAddress,
                userAgent
        );

        // 按用户要求：只有 SUPER_ADMIN 单端登录；CONTENT_ADMIN 为了联调和课程演示允许多端。
        if (userAccount.hasRole(SecurityProperties.SUPER_ADMIN_ROLE)) {
            userSessionRepository.revokeOtherActiveSessions(userAccount.id(), sessionId, now);
        }

        return new AuthResult(
                currentUserMapper.fromUser(userAccount),
                sessionToken.rawToken(),
                IDLE_TTL.toSeconds()
        );
    }

    /**
     * 用户名或密码错误时返回统一校验错误，不暴露到底是账号不存在还是密码错误。
     */
    private ApiException invalidCredentials() {
        return ApiExceptions.validation("用户名或密码不正确", Map.of("field", "password"));
    }
}
