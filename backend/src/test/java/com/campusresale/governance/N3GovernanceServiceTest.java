package com.campusresale.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.governance.N3GovernanceRepository.OrderSnapshot;
import com.campusresale.governance.N3Requests.CreateRefundRequest;
import com.campusresale.governance.N3Requests.DecideRefundRequest;
import com.campusresale.governance.N3Requests.HandleReportRequest;
import com.campusresale.governance.N3Requests.ReviewAppealRequest;
import com.campusresale.governance.N3Requests.SubmitReportRequest;
import com.campusresale.governance.N3Responses.AppealResponse;
import com.campusresale.governance.N3Responses.RefundResponse;
import com.campusresale.governance.N3Responses.ReportResponse;
import com.campusresale.governance.N3Responses.UserSummary;
import com.campusresale.notification.NotificationService;
import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class N3GovernanceServiceTest {

    private final N3GovernanceRepository repository = org.mockito.Mockito.mock(N3GovernanceRepository.class);
    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final N3GovernanceService service = new N3GovernanceService(repository, auditLogRepository, notificationService);

    @Test
    void submitReportPersistsTargetAndEvidence() {
        CurrentPrincipal principal = principal(31L, Set.of("REGISTERED_USER", "VERIFIED_STUDENT"));
        SubmitReportRequest request = new SubmitReportRequest("goods", 10L, "fake_goods", "商品描述不一致", List.of(900L, 900L, 901L));
        ReportResponse response = new ReportResponse(
                88L,
                new UserSummary(31L, "买家同学"),
                "GOODS",
                10L,
                "FAKE_GOODS",
                "商品描述不一致",
                "PENDING",
                "NORMAL",
                null,
                null,
                null,
                List.of(900L, 901L),
                Instant.parse("2026-06-05T10:00:00Z")
        );
        when(repository.targetExists("GOODS", 10L)).thenReturn(true);
        when(repository.createReport(31L, "GOODS", 10L, "FAKE_GOODS", "商品描述不一致", List.of(900L, 901L))).thenReturn(88L);
        when(repository.findReport(88L)).thenReturn(Optional.of(response));

        ReportResponse created = service.submitReport(request, principal);

        assertThat(created.id()).isEqualTo(88L);
        verify(repository).createReport(31L, "GOODS", 10L, "FAKE_GOODS", "商品描述不一致", List.of(900L, 901L));
    }

    @Test
    void createRefundRejectsAmountAboveOrderAmount() {
        CurrentPrincipal principal = principal(31L, Set.of("REGISTERED_USER", "VERIFIED_STUDENT"));
        when(repository.orderSnapshot(77L)).thenReturn(Optional.of(new OrderSnapshot(77L, 31L, 11L, new BigDecimal("129.00"), 501L)));

        assertThatThrownBy(() -> service.createRefund(
                new CreateRefundRequest(77L, "FULL", "130.00", "金额超过订单"),
                principal
        ))
                .satisfies(exception -> assertThat(((com.campusresale.platform.api.ApiException) exception).code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void upheldGoodsReportLinksGoodsOrderPenaltyAndCreditEffects() {
        CurrentPrincipal admin = principal(1L, Set.of("CONTENT_ADMIN"));
        ReportResponse before = report("PENDING");
        ReportResponse after = report("UPHELD");
        when(repository.findReport(88L)).thenReturn(Optional.of(before));
        when(repository.userExists(11L)).thenReturn(true);
        when(repository.updateReport(eq(88L), eq(1L), eq("UPHELD"), eq("举报成立，下架商品并限制卖家交易"), any(Instant.class)))
                .thenReturn(Optional.of(after));
        when(repository.createPenalty(11L, 88L, null, "TRADE_RESTRICT", "举报成立，下架商品并限制卖家交易", 1L)).thenReturn(6L);
        when(repository.findPenalty(6L)).thenReturn(Optional.of(new N3Responses.PenaltyResponse(
                6L,
                new UserSummary(11L, "小林同学"),
                88L,
                null,
                "TRADE_RESTRICT",
                "举报成立，下架商品并限制卖家交易",
                "ACTIVE",
                1L,
                null,
                null,
                Instant.parse("2026-06-05T10:00:00Z")
        )));

        service.handleReport(88L, new HandleReportRequest("UPHELD", "举报成立，下架商品并限制卖家交易", 11L, "TRADE_RESTRICT"), admin, "127.0.0.1");

        verify(repository).applyUpheldReportEffects(88L, "GOODS", 10L, 11L, 1L, "举报成立，下架商品并限制卖家交易");
        verify(repository).insertCreditRecord(11L, "REPORT", 88L, "举报成立，下架商品并限制卖家交易", -20, "平台治理记录", 1L);
        verify(auditLogRepository).recordOperation(eq(1L), eq("N3_REPORT_HANDLE"), eq("REPORT"), eq(88L), eq(before), eq(after), eq("127.0.0.1"));
    }

    @Test
    void handleReportRejectsMissingReport() {
        CurrentPrincipal admin = principal(1L, Set.of("CONTENT_ADMIN"));
        when(repository.findReport(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleReport(404L, new HandleReportRequest("UPHELD", "找不到举报", null, null), admin, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void handleReportRejectsAlreadyHandledReport() {
        CurrentPrincipal admin = principal(1L, Set.of("CONTENT_ADMIN"));
        when(repository.findReport(88L)).thenReturn(Optional.of(report("UPHELD")));

        assertThatThrownBy(() -> service.handleReport(88L, new HandleReportRequest("REJECTED", "重复处理", null, null), admin, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("CONFLICT"));
    }

    @Test
    void handleReportRejectsMissingPenaltyUserBeforeUpdatingReport() {
        CurrentPrincipal admin = principal(1L, Set.of("CONTENT_ADMIN"));
        when(repository.findReport(88L)).thenReturn(Optional.of(report("PENDING")));
        when(repository.userExists(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.handleReport(88L, new HandleReportRequest("UPHELD", "处罚用户不存在", 999L, "WARNING"), admin, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void reviewAppealApprovesAndRepairsCreditEffects() {
        CurrentPrincipal admin = principal(1L, Set.of("CONTENT_ADMIN"));
        AppealResponse before = appeal("PENDING_REVIEW");
        AppealResponse after = appeal("APPROVED");
        when(repository.findAppeal(41L)).thenReturn(Optional.of(before));
        when(repository.updateAppeal(eq(41L), eq(1L), eq("APPROVED"), eq("申诉材料有效，予以通过"), any(Instant.class)))
                .thenReturn(Optional.of(after));

        AppealResponse response = service.reviewAppeal(41L, new ReviewAppealRequest("APPROVED", "申诉材料有效，予以通过"), admin, "127.0.0.1");

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(repository).liftActivePenaltiesForReport(eq(88L), eq(1L), eq(41L), any(Instant.class));
        verify(repository).insertCreditRecord(11L, "APPEAL", 41L, "申诉通过，信用影响已修正", 5, "申诉通过", 1L);
        verify(auditLogRepository).recordOperation(eq(1L), eq("N3_APPEAL_REVIEW"), eq("APPEAL"), eq(41L), eq(before), eq(after), eq("127.0.0.1"));
    }

    @Test
    void decideRefundMarksRefundedAndWritesCreditRecord() {
        CurrentPrincipal admin = principal(1L, Set.of("CONTENT_ADMIN"));
        RefundResponse before = refund("PENDING");
        RefundResponse after = refund("REFUNDED");
        when(repository.findRefund(51L)).thenReturn(Optional.of(before));
        when(repository.updateRefund(eq(51L), eq(1L), eq("REFUNDED"), eq("已完成模拟退款处理"), any(Instant.class)))
                .thenReturn(Optional.of(after));

        RefundResponse response = service.decideRefund(51L, new DecideRefundRequest("REFUNDED", "已完成模拟退款处理"), admin, "127.0.0.1");

        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(repository).insertCreditRecord(31L, "REFUND", 51L, "退款工单处理结果：REFUNDED", 2, "退款已处理", 1L);
        verify(auditLogRepository).recordOperation(eq(1L), eq("N3_REFUND_DECIDE"), eq("REFUND"), eq(51L), eq(before), eq(after), eq("127.0.0.1"));
    }

    private ReportResponse report(String status) {
        return new ReportResponse(
                88L,
                new UserSummary(31L, "买家同学"),
                "GOODS",
                10L,
                "FAKE_GOODS",
                "商品描述不一致",
                status,
                "NORMAL",
                null,
                null,
                null,
                List.of(),
                Instant.parse("2026-06-05T10:00:00Z")
        );
    }

    private AppealResponse appeal(String status) {
        return new AppealResponse(
                41L,
                88L,
                new UserSummary(11L, "小林同学"),
                "商品成色说明已补充，申请复核。",
                status,
                null,
                null,
                null,
                List.of(),
                Instant.parse("2026-06-05T12:00:00Z")
        );
    }

    private RefundResponse refund(String status) {
        return new RefundResponse(
                51L,
                "R202606050001",
                77L,
                501L,
                new UserSummary(31L, "买家同学"),
                "29.00",
                "PARTIAL",
                "商品配件缺失，申请部分退款。",
                status,
                null,
                null,
                null,
                Instant.parse("2026-06-05T12:10:00Z")
        );
    }

    private CurrentPrincipal principal(long id, Set<String> roles) {
        Instant now = Instant.parse("2026-06-05T10:00:00Z");
        return new CurrentPrincipal(id, "user" + id, "用户" + id, "ACTIVE", roles, 1L, now.plusSeconds(3600), now.plusSeconds(7200));
    }
}
