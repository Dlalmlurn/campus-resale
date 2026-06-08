package com.campusresale.platform.audit;

import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.RequireRole;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * N2 维度 A + 维度 B：管理员后台审计日志查询接口。
 *
 * <p>两个接口均通过类级 {@code @RequireRole} 注解接入现有的
 * {@link com.campusresale.platform.security.AuthorizationInterceptor}，
 * 只有 CONTENT_ADMIN 或 SUPER_ADMIN 才能访问，无需在方法内手动再判断一次。
 *
 * <p>分页参数规范与 M1 契约保持一致：{@code page} 从 1 开始，{@code pageSize} 默认 20，上限 50。
 */
@RequireRole({"CONTENT_ADMIN", "SUPER_ADMIN"})
@RestController
@RequestMapping("/api/admin")
public class AdminAuditController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminAuditLogRepository repository;

    public AdminAuditController(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    // =========================================================
    // 维度 A：操作日志查询
    // =========================================================

    /**
     * GET /api/admin/operation-logs
     *
     * <p>支持按操作类型、结果状态、时间范围过滤，时间倒序分页。
     *
     * <p>底层 SQL 精准命中以下索引（由过滤条件决定走哪一个）：
     * <ul>
     *   <li>无过滤 → {@code idx_operation_logs_created}</li>
     *   <li>按 action → {@code idx_operation_logs_action_created}</li>
     *   <li>按 result → {@code idx_operation_logs_result_created}</li>
     * </ul>
     *
     * @param action    操作类型，精确匹配，例如 GOODS_APPROVE / VERIFY_APPROVE（可选）
     * @param result    结果状态：SUCCESS / FAILURE / PARTIAL（可选）
     * @param startTime ISO-8601 时间戳，时间范围起点（含，可选）
     * @param endTime   ISO-8601 时间戳，时间范围终点（含，可选）
     * @param page      页码，从 1 开始，默认 1
     * @param pageSize  每页条数，默认 20，最大 50
     */
    @GetMapping("/operation-logs")
    public PageResponse<OperationLogRecord> listOperationLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);

        long total = repository.countOperationLogs(action, result, startTime, endTime);
        var items = repository.findOperationLogs(action, result, startTime, endTime, safePage, safePageSize);
        return new PageResponse<>(items, safePage, safePageSize, total);
    }

    // =========================================================
    // 维度 B：敏感访问日志查询
    // =========================================================

    /**
     * GET /api/admin/sensitive-access-logs
     *
     * <p>用于后台查看管理员读取认证材料等敏感操作的留痕记录。
     * 支持按目标类型、管理员 id、时间范围过滤，时间倒序分页。
     *
     * <p>底层 SQL ORDER BY created_at DESC 精准命中
     * {@code idx_sensitive_access_logs_created}。
     *
     * @param targetType 目标类型，例如 CAMPUS_AUTH_MATERIAL（可选）
     * @param adminId    操作管理员 id（可选）
     * @param startTime  ISO-8601 时间戳，时间范围起点（含，可选）
     * @param endTime    ISO-8601 时间戳，时间范围终点（含，可选）
     * @param page       页码，从 1 开始，默认 1
     * @param pageSize   每页条数，默认 20，最大 50
     */
    @GetMapping("/sensitive-access-logs")
    public PageResponse<SensitiveAccessLogRecord> listSensitiveAccessLogs(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);

        long total = repository.countSensitiveAccessLogs(targetType, adminId, startTime, endTime);
        var items = repository.findSensitiveAccessLogs(
                targetType, adminId, startTime, endTime, safePage, safePageSize);
        return new PageResponse<>(items, safePage, safePageSize, total);
    }
}
