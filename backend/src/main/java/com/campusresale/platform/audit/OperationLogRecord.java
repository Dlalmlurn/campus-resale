package com.campusresale.platform.audit;

import java.time.Instant;

/**
 * 操作日志查询结果，对应 operation_logs 表，包含 V14 新增的可追溯字段。
 */
public record OperationLogRecord(
        long id,
        Long adminId,
        String action,
        String targetType,
        Long targetId,
        String ipAddress,
        String userAgent,
        String requestPath,
        String httpMethod,
        /** SUCCESS / FAILURE / PARTIAL */
        String result,
        /** ADMIN / SYSTEM / SCHEDULED */
        String operatorType,
        Instant createdAt
) {
}
