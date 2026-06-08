// 文件功能：封装 user_sessions 的创建、查询、续期和撤销数据库操作。
package com.campusresale.identity.infrastructure;

import com.campusresale.identity.domain.UserSessionRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 服务端 session 仓储，负责创建、查询、滑动续期和撤销 user_sessions 记录。
 */
@Repository
public class UserSessionRepository {

    /**
     * Spring JDBC 操作入口：负责执行 user_sessions 相关 SQL。
     */
    private final JdbcTemplate jdbcTemplate;

    public UserSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建 session 记录。数据库保存 token hash，真实 token 只存在浏览器 Cookie。
     */
    public long create(
            long userId,
            String sessionTokenHash,
            Instant now,
            Instant expiresAt,
            Instant absoluteExpiresAt,
            String ipAddress,
            String userAgent
    ) {
        // 返回新 session id，供 SUPER_ADMIN 单端登录策略排除当前新 session。
        return jdbcTemplate.queryForObject("""
                        INSERT INTO user_sessions (
                            user_id,
                            session_token_hash,
                            last_active_at,
                            expires_at,
                            absolute_expires_at,
                            ip_address,
                            user_agent,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, CAST(NULLIF(?, '') AS inet), ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                sessionTokenHash,
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                Timestamp.from(absoluteExpiresAt),
                // PostgreSQL inet 不能接收空字符串，SQL 中用 NULLIF 转成 null。
                ipAddress == null ? "" : ipAddress,
                userAgent,
                Timestamp.from(now)
        );
    }

    /**
     * 根据 token hash 查询仍可用的 session，过滤已撤销、闲置过期和绝对过期记录。
     */
    public Optional<UserSessionRecord> findActiveByTokenHash(String tokenHash, Instant now) {
        // 同时过滤 revoked_at、闲置过期和绝对过期，确保返回的一定是可用 session。
        List<UserSessionRecord> sessions = jdbcTemplate.query("""
                        SELECT id, user_id, session_token_hash, last_active_at, expires_at, absolute_expires_at, revoked_at
                        FROM user_sessions
                        WHERE session_token_hash = ?
                          AND revoked_at IS NULL
                          AND expires_at > ?
                          AND absolute_expires_at > ?
                        """,
                new UserSessionRowMapper(),
                tokenHash,
                Timestamp.from(now),
                Timestamp.from(now)
        );

        return sessions.stream().findFirst();
    }

    /**
     * 滑动续期：刷新最后活跃时间和闲置过期时间，但不能超过绝对过期时间。
     */
    public void touch(long sessionId, Instant now, Instant newExpiresAt) {
        // LEAST 确保滑动续期不会超过 absolute_expires_at。
        jdbcTemplate.update("""
                        UPDATE user_sessions
                        SET last_active_at = ?,
                            expires_at = LEAST(?, absolute_expires_at)
                        WHERE id = ?
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(now),
                Timestamp.from(newExpiresAt),
                sessionId
        );
    }

    /**
     * 退出登录时撤销当前 token 对应的 session。
     */
    public void revokeByTokenHash(String tokenHash, Instant now) {
        // 退出登录只撤销当前 token 对应 session；重复退出不会报错。
        jdbcTemplate.update("""
                        UPDATE user_sessions
                        SET revoked_at = ?
                        WHERE session_token_hash = ?
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(now),
                tokenHash
        );
    }

    /**
     * SUPER_ADMIN 单端登录策略：新 session 创建后撤销该用户其他活跃 session。
     */
    public void revokeOtherActiveSessions(long userId, long exceptSessionId, Instant now) {
        // 单端登录策略：保留当前新 session，撤销该用户其他仍未撤销的 session。
        jdbcTemplate.update("""
                        UPDATE user_sessions
                        SET revoked_at = ?
                        WHERE user_id = ?
                          AND id <> ?
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(now),
                userId,
                exceptSessionId
        );
    }

    /**
     * 撤销用户全部活跃 session；用于禁用账号、注销账号和密码重置后强制重新登录。
     */
    public void revokeAllActiveSessions(long userId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE user_sessions
                        SET revoked_at = ?
                        WHERE user_id = ?
                          AND revoked_at IS NULL
                        """,
                Timestamp.from(now),
                userId
        );
    }

    /**
     * 把 user_sessions 表的一行映射成 UserSessionRecord。
     */
    private static class UserSessionRowMapper implements RowMapper<UserSessionRecord> {

        @Override
        public UserSessionRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            // revoked_at 可空，表示 session 尚未被主动撤销。
            Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
            return new UserSessionRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("session_token_hash"),
                    resultSet.getTimestamp("last_active_at").toInstant(),
                    resultSet.getTimestamp("expires_at").toInstant(),
                    resultSet.getTimestamp("absolute_expires_at").toInstant(),
                    revokedAt == null ? null : revokedAt.toInstant()
            );
        }
    }
}
