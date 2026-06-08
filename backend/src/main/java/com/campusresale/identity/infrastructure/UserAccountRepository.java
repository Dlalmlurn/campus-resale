// 文件功能：封装 users、roles、user_roles 的用户账号数据库读写。
package com.campusresale.identity.infrastructure;

import com.campusresale.identity.domain.AdminUserAccountRecord;
import com.campusresale.identity.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Locale;
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
                        SELECT id, username, password_hash, nickname, avatar_file_id, account_status
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
                        SELECT id, username, password_hash, nickname, avatar_file_id, account_status
                        FROM users
                        WHERE id = ?
                        """,
                new UserAccountRowMapper(),
                userId
        );

        return users.stream().findFirst().map(this::withRoles);
    }

    /**
     * 按个人邮箱查找可用账号，用于密码找回发起流程。
     */
    public Optional<UserAccount> findActiveByPersonalEmail(String email) {
        // 邮箱匹配大小写不敏感；只允许 ACTIVE 账号进入找回流程。
        List<UserAccount> users = jdbcTemplate.query("""
                        SELECT id, username, password_hash, nickname, avatar_file_id, account_status
                        FROM users
                        WHERE lower(personal_email) = ?
                          AND account_status = 'ACTIVE'
                        """,
                new UserAccountRowMapper(),
                email.toLowerCase(Locale.ROOT)
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
     * 移除指定角色；后台账号管理撤销权限时使用。
     */
    public void removeRole(long userId, String roleCode) {
        jdbcTemplate.update("""
                        DELETE FROM user_roles ur
                        USING roles r
                        WHERE ur.role_id = r.id
                          AND ur.user_id = ?
                          AND r.code = ?
                        """,
                userId,
                roleCode
        );
    }

    /**
     * 判断角色 code 是否存在，避免写入拼写错误的角色。
     */
    public boolean roleExists(String roleCode) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM roles WHERE code = ?)",
                Boolean.class,
                roleCode
        );
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 修改用户密码哈希，找回密码成功后调用。
     */
    public void updatePasswordHash(long userId, String passwordHash, Instant now) {
        jdbcTemplate.update("""
                        UPDATE users
                        SET password_hash = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                passwordHash,
                Timestamp.from(now),
                userId
        );
    }

    /**
     * 更新用户头像文件引用；头像文件本身由 files 模块负责存储和访问控制。
     */
    public void updateAvatarFileId(long userId, long avatarFileId, Instant now) {
        jdbcTemplate.update("""
                        UPDATE users
                        SET avatar_file_id = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                avatarFileId,
                Timestamp.from(now),
                userId
        );
    }

    /**
     * 更新账号状态；DISABLED 会同时写 disabled_at，ACTIVE/LOCKED 会清空 disabled_at。
     */
    public void updateAccountStatus(long userId, String accountStatus, Instant now) {
        jdbcTemplate.update("""
                        UPDATE users
                        SET account_status = ?,
                            disabled_at = CASE WHEN ? = 'DISABLED' THEN ? ELSE NULL END,
                            updated_at = ?
                        WHERE id = ?
                        """,
                accountStatus,
                accountStatus,
                Timestamp.from(now),
                Timestamp.from(now),
                userId
        );
    }

    /**
     * 后台账号管理按 id 查询完整记录。
     */
    public Optional<AdminUserAccountRecord> findAdminRecordById(long userId) {
        List<AdminUserAccountRecord> users = jdbcTemplate.query("""
                        SELECT id, username, nickname, personal_email, account_status, disabled_at, created_at, updated_at
                        FROM users
                        WHERE id = ?
                        """,
                new AdminUserAccountRowMapper(),
                userId
        );
        return users.stream().findFirst().map(this::withAdminRoles);
    }

    /**
     * 统计后台账号列表行数。
     */
    public long countAdminUsers(String keyword, String accountStatus, String roleCode) {
        List<Object> params = new ArrayList<>();
        String where = buildAdminUserWhere(keyword, accountStatus, roleCode, params);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users u" + where, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    /**
     * 分页查询后台账号列表。
     */
    public List<AdminUserAccountRecord> listAdminUsers(
            String keyword,
            String accountStatus,
            String roleCode,
            int page,
            int pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildAdminUserWhere(keyword, accountStatus, roleCode, params);
        params.add(pageSize);
        params.add((page - 1) * pageSize);

        List<AdminUserAccountRecord> users = jdbcTemplate.query("""
                        SELECT id, username, nickname, personal_email, account_status, disabled_at, created_at, updated_at
                        FROM users u
                        """ + where + """
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                new AdminUserAccountRowMapper(),
                params.toArray()
        );
        return users.stream().map(this::withAdminRoles).toList();
    }

    /**
     * 统计仍可登录的超级管理员数量，用于保护最后一个超管。
     */
    public long countActiveSuperAdmins() {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM users u
                        JOIN user_roles ur ON ur.user_id = u.id
                        JOIN roles r ON r.id = ur.role_id
                        WHERE r.code = 'SUPER_ADMIN'
                          AND u.account_status = 'ACTIVE'
                        """,
                Long.class
        );
        return count == null ? 0L : count;
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
                userAccount.avatarFileId(),
                userAccount.accountStatus(),
                roles
        );
    }

    private AdminUserAccountRecord withAdminRoles(AdminUserAccountRecord userAccount) {
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
        return new AdminUserAccountRecord(
                userAccount.id(),
                userAccount.username(),
                userAccount.nickname(),
                userAccount.personalEmail(),
                userAccount.accountStatus(),
                userAccount.disabledAt(),
                userAccount.createdAt(),
                userAccount.updatedAt(),
                roles
        );
    }

    private String buildAdminUserWhere(String keyword, String accountStatus, String roleCode, List<Object> params) {
        List<String> conditions = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            conditions.add("""
                    (
                        lower(u.username) LIKE ?
                        OR lower(u.nickname) LIKE ?
                        OR lower(COALESCE(u.personal_email, '')) LIKE ?
                    )
                    """);
            params.add(normalizedKeyword);
            params.add(normalizedKeyword);
            params.add(normalizedKeyword);
        }
        if (accountStatus != null && !accountStatus.isBlank()) {
            conditions.add("u.account_status = ?");
            params.add(accountStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (roleCode != null && !roleCode.isBlank()) {
            conditions.add("""
                    EXISTS (
                        SELECT 1
                        FROM user_roles ur
                        JOIN roles r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id
                          AND r.code = ?
                    )
                    """);
            params.add(roleCode.trim().toUpperCase(Locale.ROOT));
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
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
                    nullableLong(resultSet, "avatar_file_id"),
                    resultSet.getString("account_status"),
                    Set.of()
            );
        }

        private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
            long value = resultSet.getLong(column);
            return resultSet.wasNull() ? null : value;
        }
    }

    /**
     * 把 users 表的一行后台账号字段映射为 AdminUserAccountRecord；角色稍后补齐。
     */
    private static class AdminUserAccountRowMapper implements RowMapper<AdminUserAccountRecord> {

        @Override
        public AdminUserAccountRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            Timestamp disabledAt = resultSet.getTimestamp("disabled_at");
            return new AdminUserAccountRecord(
                    resultSet.getLong("id"),
                    resultSet.getString("username"),
                    resultSet.getString("nickname"),
                    resultSet.getString("personal_email"),
                    resultSet.getString("account_status"),
                    disabledAt == null ? null : disabledAt.toInstant(),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    Set.of()
            );
        }
    }
}
