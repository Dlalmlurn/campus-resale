package com.campusresale.platform.audit;

import java.time.Instant;

/**
 * 敏感访问日志查询结果，对应 sensitive_access_logs 表。
 */
public record SensitiveAccessLogRecord(
        long id,
        Long adminId,
        String targetType,
        long targetId,
        String reason,
        /** ALLOWED / DENIED / FAILED */
        String result,
        String ipAddress,
        Instant createdAt
) {
}
