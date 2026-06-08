// 文件功能：封装 password_reset_tokens 的创建、查询和消费数据库操作。
package com.campusresale.identity.infrastructure;

import com.campusresale.identity.domain.PasswordResetTokenRecord;
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
 * 密码找回令牌仓储，负责一次性令牌的持久化、有效性查询和消费标记。
 */
@Repository
public class PasswordResetTokenRepository {

    /** Spring JDBC 操作入口。 */
    private final JdbcTemplate jdbcTemplate;

    public PasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建新的密码找回令牌记录。
     */
    public void create(long userId, String tokenHash, String requestedEmail, Instant expiresAt, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO password_reset_tokens (
                            user_id,
                            token_hash,
                            requested_email,
                            expires_at,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                userId,
                tokenHash,
                requestedEmail,
                Timestamp.from(expiresAt),
                Timestamp.from(now)
        );
    }

    /**
     * 按 token hash 查找尚未消费且未过期的令牌。
     */
    public Optional<PasswordResetTokenRecord> findActiveByTokenHash(String tokenHash, Instant now) {
        List<PasswordResetTokenRecord> records = jdbcTemplate.query("""
                        SELECT id, user_id, token_hash, requested_email, expires_at, consumed_at
                        FROM password_reset_tokens
                        WHERE token_hash = ?
                          AND consumed_at IS NULL
                          AND expires_at > ?
                        """,
                new PasswordResetTokenRowMapper(),
                tokenHash,
                Timestamp.from(now)
        );
        return records.stream().findFirst();
    }

    /**
     * 标记令牌已消费；重复消费不会影响已用令牌。
     */
    public void markConsumed(long tokenId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE password_reset_tokens
                        SET consumed_at = ?
                        WHERE id = ?
                          AND consumed_at IS NULL
                        """,
                Timestamp.from(now),
                tokenId
        );
    }

    /**
     * 把 password_reset_tokens 的一行映射为领域对象。
     */
    private static class PasswordResetTokenRowMapper implements RowMapper<PasswordResetTokenRecord> {

        @Override
        public PasswordResetTokenRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            Timestamp consumedAt = resultSet.getTimestamp("consumed_at");
            return new PasswordResetTokenRecord(
                    resultSet.getLong("id"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("token_hash"),
                    resultSet.getString("requested_email"),
                    resultSet.getTimestamp("expires_at").toInstant(),
                    consumedAt == null ? null : consumedAt.toInstant()
            );
        }
    }
}
