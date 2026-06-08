// 文件功能：实现注册、登录、退出、session 创建和超级管理员单端登录策略。
package com.campusresale.identity.application;

import com.campusresale.identity.api.AuthRequests.LoginRequest;
import com.campusresale.identity.api.AuthRequests.RegisterRequest;
import com.campusresale.identity.api.CurrentUserResponse;
import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
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

    /** 密码服务：负责 BCrypt 哈希和明文密码校验。 */
    private final PasswordService passwordService;

    /** token 生成器：负责生成真实 token 和数据库 token hash。 */
    private final SessionTokenGenerator sessionTokenGenerator;

    /** token 哈希工具：退出登录时把浏览器 token 转成数据库 hash。 */
    private final TokenHasher tokenHasher;

    /** 当前用户响应组装器：集中处理角色和 canTrade 过渡逻辑。 */
    private final CurrentUserMapper currentUserMapper;

    public AuthService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            PasswordService passwordService,
            SessionTokenGenerator sessionTokenGenerator,
            TokenHasher tokenHasher,
            CurrentUserMapper currentUserMapper
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordService = passwordService;
        this.sessionTokenGenerator = sessionTokenGenerator;
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
        return currentUserMapper.fromPrincipal(principal);
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
