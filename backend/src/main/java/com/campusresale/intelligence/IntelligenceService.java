package com.campusresale.intelligence;

import com.campusresale.intelligence.IntelligenceRequests.GoodsAssistRequest;
import com.campusresale.intelligence.IntelligenceResponses.GoodsAssistResponse;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceService {

    private static final String GOODS_ASSIST = "GOODS_ASSIST";
    private static final String AUDIT_REMINDER = "AI 仅提供辅助建议，不会自动审核、下架或处罚。";

    private final IntelligenceRepository intelligenceRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogRepository auditLogRepository;

    public IntelligenceService(
            IntelligenceRepository intelligenceRepository,
            SystemConfigRepository systemConfigRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.intelligenceRepository = intelligenceRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public GoodsAssistResponse assistGoods(GoodsAssistRequest request, CurrentPrincipal principal, String ipAddress) {
        String title = required(request.title(), "title", "商品标题不能为空");
        String description = required(request.description(), "description", "商品描述不能为空");
        int dailyLimit = dailyLimit();
        int used = intelligenceRepository.countTodayRequests(principal.id(), GOODS_ASSIST);
        if (used >= dailyLimit) {
            throw ApiExceptions.rateLimited("今日 AI 发布辅助次数已用完", Map.of("limit", dailyLimit, "used", used));
        }

        Advice advice = buildAdvice(title, description);
        long requestId = intelligenceRepository.createRecord(
                principal.id(),
                GOODS_ASSIST,
                title,
                advice.riskLevel(),
                advice.recommendationReason()
        );
        GoodsAssistResponse response = new GoodsAssistResponse(
                requestId,
                advice.optimizedTitle(),
                advice.optimizedDescription(),
                advice.suggestedCategoryCode(),
                advice.suggestedTags(),
                advice.riskLevel(),
                advice.riskReasons(),
                advice.recommendationReason(),
                AUDIT_REMINDER
        );
        auditLogRepository.recordOperation(principal.id(), "AI_GOODS_ASSIST", "INTELLIGENCE_RECORD", requestId, null, response, ipAddress);
        return response;
    }

    private Advice buildAdvice(String title, String description) {
        String text = (title + " " + description).toLowerCase(Locale.ROOT);
        boolean book = text.contains("教材") || text.contains("数据库") || text.contains("课程") || text.contains("复习") || text.contains("书");
        boolean prohibited = text.contains("烟") || text.contains("酒") || text.contains("药") || text.contains("代写") || text.contains("禁售");
        if (prohibited) {
            return new Advice(
                    compactTitle(title),
                    description + " 建议删除风险表述，并补充来源、成色和可面交地点。",
                    book ? "BOOKS" : "OTHER",
                    book ? List.of("教材资料", "风险待核") : List.of("风险待核"),
                    "HIGH",
                    List.of("命中禁售或高风险关键词，需要人工审核"),
                    "根据标题和描述识别到治理风险，建议提交前人工复核"
            );
        }
        if (book) {
            return new Advice(
                    "数据库课程复习资料",
                    "适合数据库原理期末复习，包含重点笔记，建议补充版本、新旧程度和是否可现场翻看。",
                    "BOOKS",
                    List.of("教材资料", "期末复习"),
                    "LOW",
                    List.of("未发现明显禁售词"),
                    "根据标题和描述判断更适合教材资料分类"
            );
        }
        return new Advice(
                compactTitle(title),
                description + " 建议补充品牌型号、使用年限、瑕疵说明和面交时间。",
                "OTHER",
                List.of("校园闲置"),
                "LOW",
                List.of("未发现明显禁售词"),
                "根据标题和描述提供通用发布优化建议"
        );
    }

    private int dailyLimit() {
        return systemConfigRepository.findValue("ai.goods_assist.daily_limit")
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        return 5;
                    }
                })
                .orElse(5);
    }

    private static String compactTitle(String title) {
        String value = title.trim();
        return value.length() > 28 ? value.substring(0, 28) : value;
    }

    private static String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value.trim();
    }

    private record Advice(
            String optimizedTitle,
            String optimizedDescription,
            String suggestedCategoryCode,
            List<String> suggestedTags,
            String riskLevel,
            List<String> riskReasons,
            String recommendationReason
    ) {
    }
}
