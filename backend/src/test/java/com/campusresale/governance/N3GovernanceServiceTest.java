package com.campusresale.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.governance.N3GovernanceRepository.OrderSnapshot;
import com.campusresale.governance.N3Requests.CreateRefundRequest;
import com.campusresale.governance.N3Requests.SubmitReportRequest;
import com.campusresale.governance.N3Responses.ReportResponse;
import com.campusresale.governance.N3Responses.UserSummary;
import com.campusresale.notification.NotificationService;
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

    private CurrentPrincipal principal(long id, Set<String> roles) {
        Instant now = Instant.parse("2026-06-05T10:00:00Z");
        return new CurrentPrincipal(id, "user" + id, "用户" + id, "ACTIVE", roles, 1L, now.plusSeconds(3600), now.plusSeconds(7200));
    }
}
