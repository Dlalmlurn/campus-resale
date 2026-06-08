// 文件功能：编排 N3 平台治理业务流程，集中处理举报、申诉、退款、处罚与信用摘要。
package com.campusresale.governance;

import com.campusresale.governance.N3GovernanceRepository.OrderSnapshot;
import com.campusresale.governance.N3Requests.ApplyPenaltyRequest;
import com.campusresale.governance.N3Requests.CreateRefundRequest;
import com.campusresale.governance.N3Requests.DecideRefundRequest;
import com.campusresale.governance.N3Requests.HandleReportRequest;
import com.campusresale.governance.N3Requests.LiftPenaltyRequest;
import com.campusresale.governance.N3Requests.ReviewAppealRequest;
import com.campusresale.governance.N3Requests.SubmitAppealRequest;
import com.campusresale.governance.N3Requests.SubmitReportRequest;
import com.campusresale.governance.N3Responses.AdminQueueResponse;
import com.campusresale.governance.N3Responses.AppealResponse;
import com.campusresale.governance.N3Responses.CreditSummaryResponse;
import com.campusresale.governance.N3Responses.FavoriteResponse;
import com.campusresale.governance.N3Responses.FollowResponse;
import com.campusresale.governance.N3Responses.GovernanceOverviewResponse;
import com.campusresale.governance.N3Responses.PenaltyResponse;
import com.campusresale.governance.N3Responses.RefundResponse;
import com.campusresale.governance.N3Responses.ReportResponse;
import com.campusresale.governance.N3Responses.ToggleResponse;
import com.campusresale.notification.NotificationService;
import com.campusresale.notification.NotificationType;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class N3GovernanceService {

    private static final Set<String> REPORT_TARGETS = Set.of("GOODS", "ORDER", "USER");
    private static final Set<String> REPORT_STATUSES = Set.of("UPHELD", "REJECTED", "CLOSED");
    private static final Set<String> OPEN_REPORT_STATUSES = Set.of("PENDING", "PROCESSING");
    private static final Set<String> APPEAL_STATUSES = Set.of("APPROVED", "REJECTED", "CLOSED");
    private static final Set<String> REFUND_TYPES = Set.of("FULL", "PARTIAL");
    private static final Set<String> REFUND_STATUSES = Set.of("PROCESSING", "REFUNDED", "FAILED", "CLOSED");
    private static final Set<String> PENALTY_TYPES = Set.of("WARNING", "TRADE_RESTRICT", "ACCOUNT_LOCK");

    private final N3GovernanceRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;

    public N3GovernanceService(
            N3GovernanceRepository repository,
            AuditLogRepository auditLogRepository,
            NotificationService notificationService
    ) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
    }

    public GovernanceOverviewResponse overview(CurrentPrincipal principal) {
        return new GovernanceOverviewResponse(
                repository.listReportsByUser(principal.id()),
                repository.listAppealsByUser(principal.id()),
                repository.listRefundsByUser(principal.id()),
                repository.listFavorites(principal.id()),
                repository.listFollows(principal.id()),
                repository.creditSummary(principal.id()),
                isAdmin(principal) ? adminQueue() : null
        );
    }

    @Transactional
    public ReportResponse submitReport(SubmitReportRequest request, CurrentPrincipal principal) {
        String targetType = oneOf(required(request.targetType(), "targetType", "举报对象类型不能为空").toUpperCase(), REPORT_TARGETS, "targetType");
        long targetId = positive(request.targetId(), "targetId");
        if (!repository.targetExists(targetType, targetId)) {
            throw ApiExceptions.notFound("举报对象不存在");
        }
        String reasonType = required(request.reasonType(), "reasonType", "举报原因不能为空").toUpperCase();
        String description = max(required(request.description(), "description", "举报说明不能为空"), 1500, "description");
        long reportId = repository.createReport(principal.id(), targetType, targetId, reasonType, description, safeIds(request.evidenceFileIds()));
        ReportResponse response = repository.findReport(reportId).orElseThrow(() -> ApiExceptions.notFound("举报记录不存在"));
        notifyAdmins(NotificationType.REPORT_SUBMITTED, "收到新的举报", "有新的平台治理举报需要处理。", "REPORT", reportId);
        return response;
    }

    public List<ReportResponse> myReports(CurrentPrincipal principal) {
        return repository.listReportsByUser(principal.id());
    }

    @Transactional
    public AppealResponse submitAppeal(SubmitAppealRequest request, CurrentPrincipal principal) {
        long reportId = positive(request.reportId(), "reportId");
        repository.findReport(reportId).orElseThrow(() -> ApiExceptions.notFound("举报记录不存在"));
        String description = max(required(request.description(), "description", "申诉说明不能为空"), 1500, "description");
        long appealId = repository.createAppeal(reportId, principal.id(), description, safeIds(request.evidenceFileIds()));
        return repository.findAppeal(appealId).orElseThrow(() -> ApiExceptions.notFound("申诉记录不存在"));
    }

    public List<AppealResponse> myAppeals(CurrentPrincipal principal) {
        return repository.listAppealsByUser(principal.id());
    }

    @Transactional
    public RefundResponse createRefund(CreateRefundRequest request, CurrentPrincipal principal) {
        long orderId = positive(request.orderId(), "orderId");
        OrderSnapshot order = repository.orderSnapshot(orderId)
                .orElseThrow(() -> ApiExceptions.notFound("订单不存在"));
        if (order.buyerId() != principal.id() && order.sellerId() != principal.id()) {
            throw ApiExceptions.forbidden("只能为自己的交易订单发起退款");
        }
        String refundType = oneOf(required(request.refundType(), "refundType", "退款类型不能为空").toUpperCase(), REFUND_TYPES, "refundType");
        BigDecimal amount = parseMoney(request.amount(), "amount");
        if (amount.compareTo(order.frozenAmount()) > 0) {
            throw ApiExceptions.validation("退款金额不能超过订单金额", Map.of("field", "amount"));
        }
        String reason = max(required(request.reason(), "reason", "退款原因不能为空"), 1000, "reason");
        long refundId = repository.createRefund("R" + Instant.now().toEpochMilli(), order.id(), order.paymentOrderId(), principal.id(), amount, refundType, reason);
        return repository.findRefund(refundId).orElseThrow(() -> ApiExceptions.notFound("退款记录不存在"));
    }

    public List<RefundResponse> myRefunds(CurrentPrincipal principal) {
        return repository.listRefundsByUser(principal.id());
    }

    public CreditSummaryResponse myCredit(CurrentPrincipal principal) {
        return repository.creditSummary(principal.id());
    }

    public List<FavoriteResponse> myFavorites(CurrentPrincipal principal) {
        return repository.listFavorites(principal.id());
    }

    public List<FollowResponse> myFollows(CurrentPrincipal principal) {
        return repository.listFollows(principal.id());
    }

    @Transactional
    public ToggleResponse favorite(long goodsId, CurrentPrincipal principal) {
        if (!repository.goodsExists(goodsId)) {
            throw ApiExceptions.notFound("商品不存在");
        }
        repository.upsertFavorite(principal.id(), goodsId);
        return new ToggleResponse(true);
    }

    @Transactional
    public ToggleResponse unfavorite(long goodsId, CurrentPrincipal principal) {
        repository.removeFavorite(principal.id(), goodsId);
        return new ToggleResponse(false);
    }

    @Transactional
    public ToggleResponse follow(long userId, CurrentPrincipal principal) {
        if (userId == principal.id()) {
            throw ApiExceptions.validation("不能关注自己", Map.of("field", "userId"));
        }
        if (!repository.userExists(userId)) {
            throw ApiExceptions.notFound("用户不存在");
        }
        repository.upsertFollow(principal.id(), userId);
        return new ToggleResponse(true);
    }

    @Transactional
    public ToggleResponse unfollow(long userId, CurrentPrincipal principal) {
        repository.removeFollow(principal.id(), userId);
        return new ToggleResponse(false);
    }

    public AdminQueueResponse adminQueue() {
        return new AdminQueueResponse(
                repository.listPendingReportsForAdmin(),
                repository.listPendingAppealsForAdmin(),
                repository.listPendingRefundsForAdmin(),
                repository.listActivePenaltiesForAdmin()
        );
    }

    public List<ReportResponse> adminReports(CurrentPrincipal admin, String ipAddress) {
        auditLogRepository.recordSensitiveAccess(admin.id(), "REPORT_QUEUE", 0, "查看举报队列", "SUCCESS", ipAddress);
        return repository.listReportsForAdmin();
    }

    public List<AppealResponse> adminAppeals(CurrentPrincipal admin, String ipAddress) {
        auditLogRepository.recordSensitiveAccess(admin.id(), "APPEAL_QUEUE", 0, "查看申诉队列", "SUCCESS", ipAddress);
        return repository.listAppealsForAdmin();
    }

    public List<RefundResponse> adminRefunds(CurrentPrincipal admin, String ipAddress) {
        auditLogRepository.recordSensitiveAccess(admin.id(), "REFUND_QUEUE", 0, "查看退款队列", "SUCCESS", ipAddress);
        return repository.listRefundsForAdmin();
    }

    @Transactional
    public ReportResponse handleReport(long reportId, HandleReportRequest request, CurrentPrincipal admin, String ipAddress) {
        ReportResponse before = repository.findReport(reportId).orElseThrow(() -> ApiExceptions.notFound("举报记录不存在"));
        if (!OPEN_REPORT_STATUSES.contains(before.status())) {
            throw ApiExceptions.conflict("举报已处理，不能重复操作", Map.of("reportId", reportId, "status", before.status()));
        }
        String status = oneOf(required(request.status(), "status", "处理状态不能为空").toUpperCase(), REPORT_STATUSES, "status");
        String note = max(required(request.handlingNote(), "handlingNote", "处理说明不能为空"), 1000, "handlingNote");
        String penaltyType = null;
        if ("UPHELD".equals(status) && request.penaltyUserId() != null) {
            if (!repository.userExists(request.penaltyUserId())) {
                throw ApiExceptions.notFound("处罚用户不存在");
            }
            penaltyType = oneOf(defaultText(request.penaltyType(), "WARNING").toUpperCase(), PENALTY_TYPES, "penaltyType");
        }
        ReportResponse after = repository.updateReport(reportId, admin.id(), status, note, Instant.now())
                .orElseThrow(() -> ApiExceptions.notFound("举报记录不存在"));
        auditLogRepository.recordOperation(admin.id(), "N3_REPORT_HANDLE", "REPORT", reportId, before, after, ipAddress);
        auditLogRepository.recordSensitiveAccess(admin.id(), "REPORT_EVIDENCE", reportId, "处理举报证据", "SUCCESS", ipAddress);
        if ("UPHELD".equals(status)) {
            repository.applyUpheldReportEffects(reportId, before.targetType(), before.targetId(), request.penaltyUserId(), admin.id(), note);
        }
        if ("UPHELD".equals(status) && request.penaltyUserId() != null) {
            createPenaltyInternal(request.penaltyUserId(), reportId, null, penaltyType, note, admin, ipAddress);
        }
        notificationService.create(before.reporter().id(), NotificationType.REPORT_RESOLVED, "举报处理完成", "你的举报已完成处理，处理结果为 " + status + "。", "REPORT", reportId, null);
        return after;
    }

    @Transactional
    public AppealResponse reviewAppeal(long appealId, ReviewAppealRequest request, CurrentPrincipal admin, String ipAddress) {
        AppealResponse before = repository.findAppeal(appealId).orElseThrow(() -> ApiExceptions.notFound("申诉记录不存在"));
        String status = oneOf(required(request.status(), "status", "审核状态不能为空").toUpperCase(), APPEAL_STATUSES, "status");
        String note = max(required(request.reviewNote(), "reviewNote", "审核说明不能为空"), 1000, "reviewNote");
        AppealResponse after = repository.updateAppeal(appealId, admin.id(), status, note, Instant.now())
                .orElseThrow(() -> ApiExceptions.notFound("申诉记录不存在"));
        auditLogRepository.recordOperation(admin.id(), "N3_APPEAL_REVIEW", "APPEAL", appealId, before, after, ipAddress);
        auditLogRepository.recordSensitiveAccess(admin.id(), "APPEAL_EVIDENCE", appealId, "审核申诉证据", "SUCCESS", ipAddress);
        if ("APPROVED".equals(status)) {
            repository.liftActivePenaltiesForReport(before.reportId(), admin.id(), appealId, Instant.now());
            repository.insertCreditRecord(before.appellant().id(), "APPEAL", appealId, "申诉通过，信用影响已修正", 5, "申诉通过", admin.id());
        }
        notificationService.create(before.appellant().id(), NotificationType.APPEAL_REVIEWED, "申诉审核完成", "你的申诉审核结果为 " + status + "。", "APPEAL", appealId, null);
        return after;
    }

    @Transactional
    public RefundResponse decideRefund(long refundId, DecideRefundRequest request, CurrentPrincipal admin, String ipAddress) {
        RefundResponse before = repository.findRefund(refundId).orElseThrow(() -> ApiExceptions.notFound("退款记录不存在"));
        String status = oneOf(required(request.status(), "status", "退款状态不能为空").toUpperCase(), REFUND_STATUSES, "status");
        String note = max(required(request.decisionNote(), "decisionNote", "退款处理说明不能为空"), 1000, "decisionNote");
        RefundResponse after = repository.updateRefund(refundId, admin.id(), status, note, Instant.now())
                .orElseThrow(() -> ApiExceptions.notFound("退款记录不存在"));
        auditLogRepository.recordOperation(admin.id(), "N3_REFUND_DECIDE", "REFUND", refundId, before, after, ipAddress);
        repository.insertCreditRecord(before.requester().id(), "REFUND", refundId, "退款工单处理结果：" + status, "REFUNDED".equals(status) ? 2 : 0, "退款已处理", admin.id());
        notificationService.create(before.requester().id(), NotificationType.REFUND_STATUS_CHANGED, "退款状态已更新", "退款申请状态已更新为 " + status + "。", "REFUND", refundId, null);
        return after;
    }

    @Transactional
    public PenaltyResponse applyPenalty(ApplyPenaltyRequest request, CurrentPrincipal admin, String ipAddress) {
        long userId = positive(request.userId(), "userId");
        if (!repository.userExists(userId)) {
            throw ApiExceptions.notFound("用户不存在");
        }
        String penaltyType = oneOf(required(request.penaltyType(), "penaltyType", "处罚类型不能为空").toUpperCase(), PENALTY_TYPES, "penaltyType");
        String reason = max(required(request.reason(), "reason", "处罚原因不能为空"), 1000, "reason");
        return createPenaltyInternal(userId, request.reportId(), null, penaltyType, reason, admin, ipAddress);
    }

    @Transactional
    public PenaltyResponse liftPenalty(long penaltyId, LiftPenaltyRequest request, CurrentPrincipal admin, String ipAddress) {
        PenaltyResponse before = repository.findPenalty(penaltyId).orElseThrow(() -> ApiExceptions.notFound("处罚记录不存在"));
        PenaltyResponse after = repository.liftPenalty(penaltyId, admin.id(), Instant.now())
                .orElseThrow(() -> ApiExceptions.notFound("处罚记录不存在"));
        String reason = defaultText(request == null ? null : request.reason(), "管理员解除处罚");
        auditLogRepository.recordOperation(admin.id(), "N3_PENALTY_LIFT", "PENALTY", penaltyId, before, Map.of("after", after, "reason", reason), ipAddress);
        repository.insertCreditRecord(after.user().id(), "MANUAL", penaltyId, reason, 5, "处罚解除", admin.id());
        return after;
    }

    private PenaltyResponse createPenaltyInternal(Long userId, Long reportId, Long appealId, String penaltyType, String reason, CurrentPrincipal admin, String ipAddress) {
        long penaltyId = repository.createPenalty(userId, reportId, appealId, penaltyType, reason, admin.id());
        PenaltyResponse penalty = repository.findPenalty(penaltyId).orElseThrow(() -> ApiExceptions.notFound("处罚记录不存在"));
        auditLogRepository.recordOperation(admin.id(), "N3_PENALTY_APPLY", "PENALTY", penaltyId, null, penalty, ipAddress);
        repository.insertCreditRecord(userId, "REPORT", reportId, reason, penaltyDelta(penaltyType), "平台治理记录", admin.id());
        notificationService.create(userId, NotificationType.PENALTY_APPLIED, "平台治理处理", "你的账号产生一条治理处理记录：" + penaltyType + "。", "PENALTY", penaltyId, null);
        return penalty;
    }

    private void notifyAdmins(NotificationType type, String title, String content, String relatedType, long relatedId) {
        // 当前项目尚无管理员广播表，N3 先把管理员待办展示在后台队列里，通知入口留给后续 N4/N5 扩展。
    }

    private static boolean isAdmin(CurrentPrincipal principal) {
        return principal.hasRole("CONTENT_ADMIN") || principal.hasRole("SUPER_ADMIN");
    }

    private static List<Long> safeIds(List<Long> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && value > 0).distinct().toList();
    }

    private static long positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw ApiExceptions.validation("参数必须为正数", Map.of("field", field));
        }
        return value;
    }

    private static String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String max(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw ApiExceptions.validation("文本长度不能超过 " + maxLength + " 字", Map.of("field", field));
        }
        return value;
    }

    private static String oneOf(String value, Set<String> allowed, String field) {
        if (!allowed.contains(value)) {
            throw ApiExceptions.validation("参数取值不在允许范围内", Map.of("field", field, "allowed", allowed));
        }
        return value;
    }

    private static BigDecimal parseMoney(String value, String field) {
        try {
            BigDecimal amount = new BigDecimal(required(value, field, "金额不能为空"));
            if (amount.compareTo(new BigDecimal("0.01")) < 0) {
                throw ApiExceptions.validation("金额必须大于等于 0.01", Map.of("field", field));
            }
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw ApiExceptions.validation("金额格式不正确", Map.of("field", field));
        }
    }

    private static int penaltyDelta(String penaltyType) {
        return switch (penaltyType) {
            case "ACCOUNT_LOCK" -> -30;
            case "TRADE_RESTRICT" -> -20;
            default -> -8;
        };
    }
}
