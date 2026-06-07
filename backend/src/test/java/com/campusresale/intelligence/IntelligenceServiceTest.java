package com.campusresale.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.notification.NotificationService;
import com.campusresale.notification.NotificationType;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntelligenceServiceTest {

    private final IntelligenceRepository intelligenceRepository = org.mockito.Mockito.mock(IntelligenceRepository.class);
    private final SystemConfigRepository systemConfigRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
    private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
    private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
    private final LlmGoodsAssistClient llmGoodsAssistClient = org.mockito.Mockito.mock(LlmGoodsAssistClient.class);
    private final IntelligenceService service = new IntelligenceService(
            intelligenceRepository, systemConfigRepository, auditLogRepository, notificationService, llmGoodsAssistClient);

    @Test
    void goodsAssistReturnsExplainableAdviceWithoutAutomaticActions() {
        CurrentPrincipal principal = principal();
        when(systemConfigRepository.findValue("ai.goods_assist.daily_limit")).thenReturn(Optional.of("5"));
        when(intelligenceRepository.countTodayRequests(31L, "GOODS_ASSIST")).thenReturn(0);
        when(llmGoodsAssistClient.generate(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(intelligenceRepository.createRecord(31L, "GOODS_ASSIST", "旧书", "LOW", "根据标题和描述判断更适合教材资料分类")).thenReturn(501L);

        IntelligenceResponses.GoodsAssistResponse response = service.assistGoods(
                new IntelligenceRequests.GoodsAssistRequest("旧书", "数据库课程复习资料，包含笔记。", "24.00"),
                principal,
                "127.0.0.1"
        );

        assertThat(response.optimizedTitle()).contains("数据库");
        assertThat(response.auditReminder()).contains("不会自动审核、下架或处罚");
        assertThat(response.riskLevel()).isEqualTo("LOW");
        assertThat(response.assistSource()).isEqualTo("RULES");
        verify(auditLogRepository).recordOperation(31L, "AI_GOODS_ASSIST", "INTELLIGENCE_RECORD", 501L, null, response, "127.0.0.1");
        verify(intelligenceRepository).updateRecordDetails(501L, "旧书", "数据库课程复习资料，包含笔记。", "24.00", response);
    }

    @Test
    void highRiskGoodsAssistCreatesGovernanceReminderWithoutAutomaticAction() {
        CurrentPrincipal principal = principal();
        when(systemConfigRepository.findValue("ai.goods_assist.daily_limit")).thenReturn(Optional.of("5"));
        when(intelligenceRepository.countTodayRequests(31L, "GOODS_ASSIST")).thenReturn(0);
        when(llmGoodsAssistClient.generate(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(intelligenceRepository.createRecord(31L, "GOODS_ASSIST", "代写资料", "HIGH", "根据标题和描述识别到治理风险，建议提交前人工复核")).thenReturn(502L);

        IntelligenceResponses.GoodsAssistResponse response = service.assistGoods(
                new IntelligenceRequests.GoodsAssistRequest("代写资料", "可提供代写服务。", "99.00"),
                principal,
                "127.0.0.1"
        );

        assertThat(response.riskLevel()).isEqualTo("HIGH");
        verify(notificationService).create(31L, NotificationType.AI_REVIEW_REMINDER, "AI 发布风险提醒", "AI 发现该商品文案存在高风险词，请人工修改后再提交审核。", "INTELLIGENCE_RECORD", 502L, "ai:goods-assist:502:user:31");
    }

    @Test
    void goodsAssistPrefersLlmDraftWhenAvailable() {
        CurrentPrincipal principal = principal();
        when(systemConfigRepository.findValue("ai.goods_assist.daily_limit")).thenReturn(Optional.of("5"));
        when(intelligenceRepository.countTodayRequests(31L, "GOODS_ASSIST")).thenReturn(0);
        when(llmGoodsAssistClient.generate(anyString(), anyString(), any())).thenReturn(Optional.of(new GoodsAssistDraft(
                "九成新机械键盘",
                "自用机械键盘，红轴手感顺滑，配件齐全，可校内面交。",
                "DIGITAL",
                List.of("数码电子", "配件齐全"),
                "LOW",
                List.of("未发现明显风险关键词"),
                "LLM 判断更适合数码电子分类"
        )));
        when(intelligenceRepository.createRecord(eq(31L), eq("GOODS_ASSIST"), eq("键盘"), eq("LOW"), anyString())).thenReturn(601L);

        IntelligenceResponses.GoodsAssistResponse response = service.assistGoods(
                new IntelligenceRequests.GoodsAssistRequest("键盘", "机械键盘出售。", "120.00"),
                principal,
                "127.0.0.1"
        );

        assertThat(response.assistSource()).isEqualTo("LLM");
        assertThat(response.optimizedTitle()).isEqualTo("九成新机械键盘");
        assertThat(response.suggestedCategoryCode()).isEqualTo("DIGITAL");
        assertThat(response.riskLevel()).isEqualTo("LOW");
        verify(notificationService, never()).create(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ruleEngineEnforcesRiskFloorEvenWhenLlmReportsLow() {
        CurrentPrincipal principal = principal();
        when(systemConfigRepository.findValue("ai.goods_assist.daily_limit")).thenReturn(Optional.of("5"));
        when(intelligenceRepository.countTodayRequests(31L, "GOODS_ASSIST")).thenReturn(0);
        // LLM 误判为低风险，但描述含"代写"高风险词，规则引擎必须把最终风险兜底为 HIGH。
        when(llmGoodsAssistClient.generate(anyString(), anyString(), any())).thenReturn(Optional.of(new GoodsAssistDraft(
                "学习资料整理",
                "整理好的学习资料，欢迎咨询。",
                "BOOKS",
                List.of("教材资料"),
                "LOW",
                List.of("未发现明显风险关键词"),
                "LLM 判断为普通教材资料"
        )));
        when(intelligenceRepository.createRecord(eq(31L), eq("GOODS_ASSIST"), eq("学习资料"), eq("HIGH"), anyString())).thenReturn(701L);

        IntelligenceResponses.GoodsAssistResponse response = service.assistGoods(
                new IntelligenceRequests.GoodsAssistRequest("学习资料", "可提供代写和答案。", "50.00"),
                principal,
                "127.0.0.1"
        );

        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.assistSource()).isEqualTo("LLM");
        assertThat(response.riskReasons()).anySatisfy(reason -> assertThat(reason).contains("代写"));
        assertThat(response.recommendationReason()).contains("规则引擎额外识别到更高风险");
        verify(notificationService).create(eq(31L), eq(NotificationType.AI_REVIEW_REMINDER), anyString(), anyString(), eq("INTELLIGENCE_RECORD"), eq(701L), anyString());
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
