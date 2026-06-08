package com.campusresale.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 原有签名保持不变，内部委托给带完整字段的重载方法，兼容所有已有调用方。
     */
    public void recordOperation(
            Long adminId,
            String action,
            String targetType,
            Long targetId,
            Object before,
            Object after,
            String ipAddress
    ) {
        recordOperation(adminId, action, targetType, targetId, before, after,
                ipAddress, null, null, null, "SUCCESS", "ADMIN");
    }

    /**
     * V14 增强版：额外持久化 user_agent、request_path、http_method、result、operator_type。
     * 所有调用方应优先使用此重载，以便后台审计查询能看到完整追溯信息。
     */
    public void recordOperation(
            Long adminId,
            String action,
            String targetType,
            Long targetId,
            Object before,
            Object after,
            String ipAddress,
            String userAgent,
            String requestPath,
            String httpMethod,
            String result,
            String operatorType
    ) {
        jdbcTemplate.update("""
                        INSERT INTO operation_logs (
                            admin_id,
                            action,
                            target_type,
                            target_id,
                            before_json,
                            after_json,
                            ip_address,
                            user_agent,
                            request_path,
                            http_method,
                            result,
                            operator_type,
                            created_at
                        )
                        VALUES (
                            ?, ?, ?, ?,
                            CAST(? AS jsonb), CAST(? AS jsonb),
                            CAST(NULLIF(?, '') AS inet),
                            ?, ?, ?, ?, ?,
                            ?
                        )
                        """,
                adminId,
                action,
                targetType,
                targetId,
                jsonb(before),
                jsonb(after),
                ipAddress == null ? "" : ipAddress,
                userAgent,
                requestPath,
                httpMethod,
                result == null ? "SUCCESS" : result,
                operatorType == null ? "ADMIN" : operatorType,
                Timestamp.from(Instant.now())
        );
    }

    /** sensitive_access_logs.result 的合法取值，与 ck_sensitive_access_logs_result 约束一致。 */
    private static final java.util.Set<String> SENSITIVE_ACCESS_RESULTS = java.util.Set.of("ALLOWED", "DENIED", "FAILED");

    public void recordSensitiveAccess(
            long adminId,
            String targetType,
            long targetId,
            String reason,
            String result,
            String ipAddress
    ) {
        // 提前拦住非法 result（例如误传 "SUCCESS"），避免直到 INSERT 才被 DB 约束以 500 形式打断业务流程。
        if (!SENSITIVE_ACCESS_RESULTS.contains(result)) {
            throw new IllegalArgumentException(
                    "sensitive access result 必须是 ALLOWED/DENIED/FAILED，实际为：" + result);
        }
        jdbcTemplate.update("""
                        INSERT INTO sensitive_access_logs (
                            admin_id,
                            target_type,
                            target_id,
                            reason,
                            result,
                            ip_address,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, CAST(NULLIF(?, '') AS inet), ?)
                        """,
                adminId,
                targetType,
                targetId,
                reason,
                result,
                ipAddress == null ? "" : ipAddress,
                Timestamp.from(Instant.now())
        );
    }

    private String jsonb(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot serialize audit payload", exception);
        }
    }
}
