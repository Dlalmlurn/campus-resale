// 文件功能：封装 users、roles、user_roles 的用户账号数据库读写。
package com.campusresale.identity.infrastructure;

import com.campusresale.identity.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户账号仓储，封装 users、roles、user_roles 三张表的常用读写。
 */
@Repository
public class UserAccountRepository {

    /**
     * Spring JDBC 操作入口：负责执行 SQL 并把结果映射成 Java 对象。
     */
    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按规范化用户名查用户，并附带加载角色。
     */
    public Optional<UserAccount> findByUsername(String normalizedUsername) {
        // normalizedUsername 已在 AuthService 中转小写；这里仍用 lower(username) 兼容历史数据。
        List<UserAccount> users = jdbcTemplate.query("""
                        SELECT id, username, password_hash, nickname, account_status
                        FROM users
                        WHERE lower(username) = ?
                        """,
                new UserAccountRowMapper(),
                normalizedUsername
        );

        // 查询到基础用户后，再补齐角色集合，保持对外返回对象完整。
        return users.stream().findFirst().map(this::withRoles);
    }

    /**
     * 按用户 id 查用户，并附带加载角色；Filter 加载 session 时会用到。
     */
    public Optional<UserAccount> findById(long userId) {
        // Filter 根据 session.user_id 反查用户时走这里。
        List<UserAccount> users = jdbcTemplate.query("""
                        SELECT id, username, password_hash, nickname, account_status
                        FROM users
                        WHERE id = ?
                        """,
                new UserAccountRowMapper(),
                userId
        );

        return users.stream().findFirst().map(this::withRoles);
    }

    /**
     * 创建普通注册用户，并固定只授予 REGISTERED_USER。
     */
    @Transactional
    public UserAccount createRegisteredUser(
            String normalizedUsername,
            String passwordHash,
            String nickname,
            String personalEmail
    ) {
        // userId 是新用户主键，PostgreSQL RETURNING 语法可以避免再按用户名查一次。
        Long userId = jdbcTemplate.queryForObject("""
                        INSERT INTO users (username, password_hash, nickname, personal_email, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                normalizedUsername,
                passwordHash,
                nickname,
                blankToNull(personalEmail),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );

        // 注册入口只授予 REGISTERED_USER，不能通过前端传参获得管理员或认证学生角色。
        assignRole(userId, "REGISTERED_USER", null);

        return findById(userId).orElseThrow(() -> new IllegalStateException("Created user cannot be loaded"));
    }

    /**
     * 检查规范化用户名是否已存在，用于在写库前给出更友好的冲突错误。
     */
    public boolean usernameExists(String normalizedUsername) {
        // SELECT EXISTS 只返回布尔值，比加载整行用户更轻。
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE lower(username) = ?)",
                Boolean.class,
                normalizedUsername
        );
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 授予角色；注册时只授予 REGISTERED_USER，管理员/认证学生由种子或后续审核流程授予。
     */
    public void assignRole(long userId, String roleCode, Long assignedByAdminId) {
        // 通过角色 code 查 role_id，避免业务层直接依赖数据库自增 id。
        jdbcTemplate.update("""
                        INSERT INTO user_roles (user_id, role_id, assigned_by_admin_id)
                        SELECT ?, id, ?
                        FROM roles
                        WHERE code = ?
                        ON CONFLICT (user_id, role_id) DO NOTHING
                        """,
                userId,
                assignedByAdminId,
                roleCode
        );
    }

    /**
     * 补齐用户角色集合，保持 Repository 对外返回完整 UserAccount。
     */
    private UserAccount withRoles(UserAccount userAccount) {
        // LinkedHashSet 保留 SQL ORDER BY 后的稳定顺序，方便测试和响应观察。
        Set<String> roles = new LinkedHashSet<>(jdbcTemplate.queryForList("""
                        SELECT r.code
                        FROM roles r
                        JOIN user_roles ur ON ur.role_id = r.id
                        WHERE ur.user_id = ?
                        ORDER BY r.code
                        """,
                String.class,
                userAccount.id()
        ));
        return new UserAccount(
                userAccount.id(),
                userAccount.username(),
                userAccount.passwordHash(),
                userAccount.nickname(),
                userAccount.accountStatus(),
                roles
        );
    }

    private String blankToNull(String value) {
        // 邮箱等可选字符串字段：空白输入按 null 保存，避免数据库里存无意义空串。
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 把 users 表的一行基础字段映射为 UserAccount；角色稍后由 withRoles 补齐。
     */
    private static class UserAccountRowMapper implements RowMapper<UserAccount> {

        @Override
        public UserAccount mapRow(ResultSet resultSet, int rowNum) throws SQLException, DataAccessException {
            return new UserAccount(
                    resultSet.getLong("id"),
                    resultSet.getString("username"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("nickname"),
                    resultSet.getString("account_status"),
                    Set.of()
            );
        }
    }
}
