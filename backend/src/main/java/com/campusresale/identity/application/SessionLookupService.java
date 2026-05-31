// 文件功能：根据 Cookie token 加载有效 session，并执行滑动续期。
package com.campusresale.identity.application;

import com.campusresale.identity.domain.UserAccount;
import com.campusresale.identity.domain.UserSessionRecord;
import com.campusresale.identity.infrastructure.UserAccountRepository;
import com.campusresale.identity.infrastructure.UserSessionRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import com.campusresale.platform.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 请求级 session 加载服务，Filter 调用它把 Cookie token 转成当前登录主体。
 */
@Service
public class SessionLookupService {

    /**
     * session 闲置有效期：每次有效访问后最多再延长 7 天。
     */
    private static final Duration IDLE_TTL = Duration.ofDays(7);

    /** session 仓储：根据 token hash 查询和续期 user_sessions。 */
    private final UserSessionRepository userSessionRepository;

    /** 用户仓储：根据 session.user_id 加载账号和角色。 */
    private final UserAccountRepository userAccountRepository;

    /** token 哈希工具：把浏览器真实 token 转成数据库保存的 hash。 */
    private final TokenHasher tokenHasher;

    public SessionLookupService(
            UserSessionRepository userSessionRepository,
            UserAccountRepository userAccountRepository,
            TokenHasher tokenHasher
    ) {
        this.userSessionRepository = userSessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.tokenHasher = tokenHasher;
    }

    /**
     * 根据浏览器传来的真实 token 查找有效 session；无效、过期或账号停用都返回空。
     */
    public Optional<CurrentPrincipal> loadByRawToken(String rawToken) {
        // 没有 Cookie 或 Cookie 为空时，不抛错，按匿名请求继续。
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        // now 是本次请求的统一判断时间，用于过期判断和续期。
        Instant now = Instant.now();

        // 数据库只按 token hash 查询；找到 session 后还要确认用户仍是 ACTIVE。
        return userSessionRepository.findActiveByTokenHash(tokenHasher.sha256(rawToken), now)
                .flatMap(session -> userAccountRepository.findById(session.userId())
                        .filter(UserAccount::isActive)
                        .map(user -> toPrincipal(user, session, now)));
    }

    /**
     * 把用户和 session 合并为当前主体，并刷新 session 闲置过期时间。
     */
    private CurrentPrincipal toPrincipal(UserAccount userAccount, UserSessionRecord session, Instant now) {
        // 目标闲置过期时间：从本次请求起再延长 7 天。
        Instant requestedExpiresAt = now.plus(IDLE_TTL);

        userSessionRepository.touch(session.id(), now, requestedExpiresAt);

        // 对外暴露的有效过期时间不能超过绝对过期时间。
        Instant effectiveExpiresAt = requestedExpiresAt.isBefore(session.absoluteExpiresAt())
                ? requestedExpiresAt
                : session.absoluteExpiresAt();

        // CurrentPrincipal 是请求内共享的登录主体，供权限拦截器和 Controller 使用。
        return new CurrentPrincipal(
                userAccount.id(),
                userAccount.username(),
                userAccount.nickname(),
                userAccount.accountStatus(),
                userAccount.roles(),
                session.id(),
                effectiveExpiresAt,
                session.absoluteExpiresAt()
        );
    }
}
