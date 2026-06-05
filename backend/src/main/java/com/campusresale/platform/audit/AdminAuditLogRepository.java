package com.campusresale.platform.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * N2 后台审计分页查询 DAO。
 *
 * <p>操作日志查询精准命中 V14 补充的复合索引：
 * <ul>
 *   <li>{@code idx_operation_logs_created}       — 纯按时间翻页</li>
 *   <li>{@code idx_operation_logs_action_created} — 按 action 过滤</li>
 *   <li>{@code idx_operation_logs_result_created} — 按 result 过滤</li>
 * </ul>
 *
 * <p>敏感访问日志查询精准命中：
 * <ul>
 *   <li>{@code idx_sensitive_access_logs_created} — 按时间倒序翻页</li>
 * </ul>
 *
 * <p>均使用 {@link NamedParameterJdbcTemplate}，不引入 JPA/Hibernate/MyBatis。
 */
@Repository
public class AdminAuditLogRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AdminAuditLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =========================================================
    // 操作日志分页查询
    // =========================================================

    /**
     * 按条件统计操作日志总行数，用于前端分页器。
     *
     * @param action     操作类型精确匹配（null 则不过滤）
     * @param result     结果状态精确匹配：SUCCESS / FAILURE / PARTIAL（null 则不过滤）
     * @param startTime  时间范围起点（含，null 则不限）
     * @param endTime    时间范围终点（含，null 则不限）
     */
    public long countOperationLogs(
            String action,
            String result,
            Instant startTime,
            Instant endTime
    ) {
        var params = new MapSqlParameterSource();
        var where = buildOperationLogWhere(action, result, startTime, endTime, params);
        String sql = "SELECT COUNT(*) FROM operation_logs" + where;
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 按条件分页查询操作日志，按 created_at DESC 排序。
     *
     * @param action     操作类型精确匹配（null 则不过滤）
     * @param result     结果状态精确匹配（null 则不过滤）
     * @param startTime  时间范围起点（含，null 则不限）
     * @param endTime    时间范围终点（含，null 则不限）
     * @param page       从 1 开始的页码
     * @param pageSize   每页条数，上限由调用方控制（建议最大 50）
     */
    public List<OperationLogRecord> findOperationLogs(
            String action,
            String result,
            Instant startTime,
            Instant endTime,
            int page,
            int pageSize
    ) {
        var params = new MapSqlParameterSource();
        var where = buildOperationLogWhere(action, result, startTime, endTime, params);

        // OFFSET/LIMIT 分页；offset 从 0 开始，page 从 1 开始
        int offset = (page - 1) * pageSize;
        params.addValue("limit", pageSize);
        params.addValue("offset", offset);

        // ORDER BY 精准对齐 idx_operation_logs_created（created_at DESC）；
        // 当 action 不为空时，PostgreSQL 优化器同样可以利用 idx_operation_logs_action_created。
        String sql = """
                SELECT
                    id,
                    admin_id,
                    action,
                    target_type,
                    target_id,
                    CAST(ip_address AS TEXT) AS ip_address,
                    user_agent,
                    request_path,
                    http_method,
                    result,
                    operator_type,
                    created_at
                FROM operation_logs
                """ + where + """
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """;

        return jdbc.query(sql, params, AdminAuditLogRepository::mapOperationLog);
    }

    // =========================================================
    // 敏感访问日志分页查询
    // =========================================================

    /**
     * 统计敏感访问日志总行数。
     *
     * @param targetType 目标类型精确匹配（null 则不过滤）
     * @param adminId    操作管理员 id（null 则不过滤）
     * @param startTime  时间范围起点（含，null 则不限）
     * @param endTime    时间范围终点（含，null 则不限）
     */
    public long countSensitiveAccessLogs(
            String targetType,
            Long adminId,
            Instant startTime,
            Instant endTime
    ) {
        var params = new MapSqlParameterSource();
        var where = buildSensitiveAccessLogWhere(targetType, adminId, startTime, endTime, params);
        String sql = "SELECT COUNT(*) FROM sensitive_access_logs" + where;
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 按时间倒序分页查询敏感访问日志。
     *
     * <p>ORDER BY created_at DESC 精准对齐 {@code idx_sensitive_access_logs_created}。
     *
     * @param targetType 目标类型精确匹配（null 则不过滤）
     * @param adminId    操作管理员 id（null 则不过滤）
     * @param startTime  时间范围起点（含，null 则不限）
     * @param endTime    时间范围终点（含，null 则不限）
     * @param page       从 1 开始的页码
     * @param pageSize   每页条数
     */
    public List<SensitiveAccessLogRecord> findSensitiveAccessLogs(
            String targetType,
            Long adminId,
            Instant startTime,
            Instant endTime,
            int page,
            int pageSize
    ) {
        var params = new MapSqlParameterSource();
        var where = buildSensitiveAccessLogWhere(targetType, adminId, startTime, endTime, params);

        int offset = (page - 1) * pageSize;
        params.addValue("limit", pageSize);
        params.addValue("offset", offset);

        String sql = """
                SELECT
                    id,
                    admin_id,
                    target_type,
                    target_id,
                    reason,
                    result,
                    CAST(ip_address AS TEXT) AS ip_address,
                    created_at
                FROM sensitive_access_logs
                """ + where + """
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """;

        return jdbc.query(sql, params, AdminAuditLogRepository::mapSensitiveAccessLog);
    }

    // =========================================================
    // 私有工具方法：WHERE 子句动态构造
    // =========================================================

    /**
     * 动态构造 operation_logs 的 WHERE 子句。
     * 所有条件均为 AND，参数绑定到 params。
     */
    private String buildOperationLogWhere(
            String action,
            String result,
            Instant startTime,
            Instant endTime,
            MapSqlParameterSource params
    ) {
        var conditions = new ArrayList<String>();

        if (action != null && !action.isBlank()) {
            conditions.add("action = :action");
            params.addValue("action", action.trim());
        }
        if (result != null && !result.isBlank()) {
            conditions.add("result = :result");
            params.addValue("result", result.trim().toUpperCase());
        }
        if (startTime != null) {
            conditions.add("created_at >= :startTime");
            params.addValue("startTime", java.sql.Timestamp.from(startTime));
        }
        if (endTime != null) {
            conditions.add("created_at <= :endTime");
            params.addValue("endTime", java.sql.Timestamp.from(endTime));
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /**
     * 动态构造 sensitive_access_logs 的 WHERE 子句。
     */
    private String buildSensitiveAccessLogWhere(
            String targetType,
            Long adminId,
            Instant startTime,
            Instant endTime,
            MapSqlParameterSource params
    ) {
        var conditions = new ArrayList<String>();

        if (targetType != null && !targetType.isBlank()) {
            conditions.add("target_type = :targetType");
            params.addValue("targetType", targetType.trim());
        }
        if (adminId != null) {
            conditions.add("admin_id = :adminId");
            params.addValue("adminId", adminId);
        }
        if (startTime != null) {
            conditions.add("created_at >= :startTime");
            params.addValue("startTime", java.sql.Timestamp.from(startTime));
        }
        if (endTime != null) {
            conditions.add("created_at <= :endTime");
            params.addValue("endTime", java.sql.Timestamp.from(endTime));
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    // =========================================================
    // 私有工具方法：ResultSet 映射
    // =========================================================

    private static OperationLogRecord mapOperationLog(ResultSet rs, int rowNum) throws SQLException {
        return new OperationLogRecord(
                rs.getLong("id"),
                nullableLong(rs, "admin_id"),
                rs.getString("action"),
                rs.getString("target_type"),
                nullableLong(rs, "target_id"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getString("request_path"),
                rs.getString("http_method"),
                rs.getString("result"),
                rs.getString("operator_type"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static SensitiveAccessLogRecord mapSensitiveAccessLog(ResultSet rs, int rowNum) throws SQLException {
        return new SensitiveAccessLogRecord(
                rs.getLong("id"),
                nullableLong(rs, "admin_id"),
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getString("reason"),
                rs.getString("result"),
                rs.getString("ip_address"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    /**
     * 读取可为 NULL 的 BIGINT 列，wasNull() 检测防止 0 被误判为 NULL。
     */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
