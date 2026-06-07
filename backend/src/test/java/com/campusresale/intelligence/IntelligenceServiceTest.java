package com.campusresale.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntelligenceServiceTest {

    private final IntelligenceRepository intelligenceRepository = org.mockito.Mockito.mock(IntelligenceRepository.class);
    private final SystemConfigRepository systemConfigRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final IntelligenceService service = new IntelligenceService(intelligenceRepository, systemConfigRepository, auditLogRepository);

    @Test
    void goodsAssistReturnsExplainableAdviceWithoutAutomaticActions() {
        CurrentPrincipal principal = principal();
        when(systemConfigRepository.findValue("ai.goods_assist.daily_limit")).thenReturn(Optional.of("5"));
        when(intelligenceRepository.countTodayRequests(31L, "GOODS_ASSIST")).thenReturn(0);
        when(intelligenceRepository.createRecord(31L, "GOODS_ASSIST", "旧书", "LOW", "根据标题和描述判断更适合教材资料分类")).thenReturn(501L);

        IntelligenceResponses.GoodsAssistResponse response = service.assistGoods(
                new IntelligenceRequests.GoodsAssistRequest("旧书", "数据库课程复习资料，包含笔记。", "24.00"),
                principal,
                "127.0.0.1"
        );

        assertThat(response.optimizedTitle()).contains("数据库");
        assertThat(response.auditReminder()).contains("不会自动审核、下架或处罚");
        assertThat(response.riskLevel()).isEqualTo("LOW");
        verify(auditLogRepository).recordOperation(31L, "AI_GOODS_ASSIST", "INTELLIGENCE_RECORD", 501L, null, response, "127.0.0.1");
    }

    @Test
    void goodsAssistRejectsWhenDailyLimitExceeded() {
        when(systemConfigRepository.findValue("ai.goods_assist.daily_limit")).thenReturn(Optional.of("1"));
        when(intelligenceRepository.countTodayRequests(31L, "GOODS_ASSIST")).thenReturn(1);

        assertThatThrownBy(() -> service.assistGoods(
                new IntelligenceRequests.GoodsAssistRequest("旧书", "数据库课程复习资料，包含笔记。", "24.00"),
                principal(),
                "127.0.0.1"
        ))
                .satisfies(exception -> assertThat(((com.campusresale.platform.api.ApiException) exception).code()).isEqualTo("RATE_LIMITED"));
    }

    private CurrentPrincipal principal() {
        Instant now = Instant.parse("2026-06-07T10:00:00Z");
        return new CurrentPrincipal(31L, "buyer_demo", "买家同学", "ACTIVE", Set.of("REGISTERED_USER", "VERIFIED_STUDENT"), 1L, now.plusSeconds(3600), now.plusSeconds(7200));
    }
}
