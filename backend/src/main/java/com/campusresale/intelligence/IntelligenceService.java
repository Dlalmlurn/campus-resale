package com.campusresale.intelligence;

import com.campusresale.intelligence.IntelligenceRequests.GoodsAssistRequest;
import com.campusresale.intelligence.IntelligenceResponses.GoodsAssistResponse;
import com.campusresale.notification.NotificationService;
import com.campusresale.notification.NotificationType;
import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.audit.AuditLogRepository;
import com.campusresale.platform.config.SystemConfigRepository;
import com.campusresale.platform.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * AI 发布辅助服务。
 *
 * <p>融合两条能力线：</p>
 * <ul>
 *   <li>稳健规则引擎：本地、可解释、离线可用，负责分类初判与禁售/高风险关键词识别。</li>
 *   <li>DeepSeek LLM：在配置开启时生成更自然的标题/描述与分类建议。</li>
 * </ul>
 *
 * <p>关键安全约束：风险判定以规则引擎为<strong>下限</strong>。即使 LLM 判为低风险，
 * 只要规则命中高风险词，最终仍按更高风险提示并发治理提醒，LLM 不能把风险降级。</p>
 */
@Service
public class IntelligenceService {

    private static final String GOODS_ASSIST = "GOODS_ASSIST";
    private static final String AUDIT_REMINDER = "AI 仅提供辅助建议，不会自动审核、下架或处罚。";
    private static final String HIGH_RISK_RECOMMENDATION = "根据标题和描述识别到治理风险，建议提交前人工复核";

    // 高风险/禁售关键词；命中即判 HIGH，规则引擎据此对 LLM 结果做风险兜底。
    private static final List<String> HIGH_RISK_TERMS = List.of(
            "代写", "替考", "答案", "考试答案", "套现", "校园卡套现", "黄牛", "刷单",
            "代开发票", "发票", "违禁", "违禁药品", "处方药", "管制刀具", "仿真枪",
            "现金贷", "裸条", "外挂", "烟", "酒", "毒", "身份证");
    // 需要人工确认的中等风险表述；命中判 MEDIUM。
    private static final List<String> MEDIUM_RISK_TERMS = List.of(
            "账号", "代购", "门票", "转让", "兼职", "二维码", "约");
    // 分类关键词，按优先级匹配。
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = categoryKeywords();
    // 教材类标题中可识别的学科关键词，用于优化标题。
    private static final List<String> BOOK_SUBJECTS = List.of(
            "数据库", "操作系统", "计算机网络", "高等数学", "线性代数", "概率论",
            "英语", "考研", "政治", "数据结构", "计算机");

    private final IntelligenceRepository intelligenceRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final LlmGoodsAssistClient llmGoodsAssistClient;

    public IntelligenceService(
            IntelligenceRepository intelligenceRepository,
            SystemConfigRepository systemConfigRepository,
            AuditLogRepository auditLogRepository,
            NotificationService notificationService,
            LlmGoodsAssistClient llmGoodsAssistClient
    ) {
        this.intelligenceRepository = intelligenceRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.llmGoodsAssistClient = llmGoodsAssistClient;
    }

    public GoodsAssistResponse assistGoods(GoodsAssistRequest request, CurrentPrincipal principal, String ipAddress) {
        String title = required(request.title(), "title", "商品标题不能为空");
        String description = required(request.description(), "description", "商品描述不能为空");
        int dailyLimit = dailyLimit();
        int used = intelligenceRepository.countTodayRequests(principal.id(), GOODS_ASSIST);
        if (used >= dailyLimit) {
            throw ApiExceptions.rateLimited("今日 AI 发布辅助次数已用完", Map.of("limit", dailyLimit, "used", used));
        }

        GoodsAssistDraft ruleDraft = ruleDraft(title, description);
        Optional<GoodsAssistDraft> llmDraft = llmGoodsAssistClient.generate(title, description, request.price());
        Merged merged = merge(ruleDraft, llmDraft);
        GoodsAssistDraft draft = merged.draft();

        long requestId = intelligenceRepository.createRecord(
                principal.id(),
                GOODS_ASSIST,
                title,
                draft.riskLevel(),
                draft.recommendationReason()
        );
        GoodsAssistResponse response = new GoodsAssistResponse(
                requestId,
                draft.optimizedTitle(),
                draft.optimizedDescription(),
                draft.suggestedCategoryCode(),
                draft.suggestedTags(),
                draft.riskLevel(),
                draft.riskReasons(),
                draft.recommendationReason(),
                AUDIT_REMINDER,
                merged.source()
        );
        intelligenceRepository.updateRecordDetails(requestId, title, description, normalizedPrice(request.price()), response);
        if ("HIGH".equals(draft.riskLevel())) {
            notificationService.create(
                    principal.id(),
                    NotificationType.AI_REVIEW_REMINDER,
                    "AI 发布风险提醒",
                    "AI 发现该商品文案存在高风险词，请人工修改后再提交审核。",
                    "INTELLIGENCE_RECORD",
                    requestId,
                    "ai:goods-assist:" + requestId + ":user:" + principal.id()
            );
        }
        auditLogRepository.recordOperation(principal.id(), "AI_GOODS_ASSIST", "INTELLIGENCE_RECORD", requestId, null, response, ipAddress);
        return response;
    }

    /**
     * 合并规则草稿与 LLM 草稿：文案/分类优先采用 LLM，风险等级取两者更高者，规则风险不可被降级。
     */
    private Merged merge(GoodsAssistDraft rule, Optional<GoodsAssistDraft> llmOptional) {
        if (llmOptional.isEmpty()) {
            return new Merged(rule, "RULES");
        }
        GoodsAssistDraft llm = llmOptional.get();
        int ruleSeverity = severity(rule.riskLevel());
        int llmSeverity = severity(llm.riskLevel());
        String finalRisk = ruleSeverity >= llmSeverity ? rule.riskLevel() : llm.riskLevel();

        List<String> reasons = new ArrayList<>();
        if (ruleSeverity > 0) {
            reasons.addAll(rule.riskReasons());
        }
        if (llmSeverity > 0) {
            reasons.addAll(llm.riskReasons());
        }
        List<String> riskReasons = reasons.isEmpty()
                ? List.of("未发现明显风险关键词")
                : new ArrayList<>(new LinkedHashSet<>(reasons));

        String recommendation = blankToNull(llm.recommendationReason()) == null
                ? rule.recommendationReason()
                : llm.recommendationReason();
        if (ruleSeverity > llmSeverity) {
            // 规则引擎识别到比 LLM 更高的风险，明确告知最终按更高风险提示。
            recommendation = recommendation + "；规则引擎额外识别到更高风险，已按更高风险提示";
        }

        GoodsAssistDraft draft = new GoodsAssistDraft(
                firstNonBlank(llm.optimizedTitle(), rule.optimizedTitle()),
                firstNonBlank(llm.optimizedDescription(), rule.optimizedDescription()),
                firstNonBlank(llm.suggestedCategoryCode(), rule.suggestedCategoryCode()),
                llm.suggestedTags() == null || llm.suggestedTags().isEmpty() ? rule.suggestedTags() : llm.suggestedTags(),
                finalRisk,
                riskReasons,
                recommendation
        );
        return new Merged(draft, "LLM");
    }

    /**
     * 稳健规则引擎：基于关键词给出分类、标签、风险与可解释理由，离线可用。
     */
    private GoodsAssistDraft ruleDraft(String title, String description) {
        String text = (title + " " + description).toLowerCase(Locale.ROOT);

        List<String> highHits = matches(text, HIGH_RISK_TERMS);
        List<String> mediumHits = matches(text, MEDIUM_RISK_TERMS);
        String category = detectCategory(text);
        boolean book = "BOOKS".equals(category);

        if (!highHits.isEmpty()) {
            return new GoodsAssistDraft(
                    compactTitle(title),
                    description + " 建议删除风险表述，并补充来源、成色和可面交地点。",
                    category,
                    book ? List.of("教材资料", "风险待核") : List.of("风险待核"),
                    "HIGH",
                    List.of("命中禁售或高风险关键词：" + String.join("、", highHits)),
                    HIGH_RISK_RECOMMENDATION
            );
        }

        if (book) {
            String subject = firstSubject(text);
            String optimizedTitle = subject == null ? compactTitle(title) : subject + "课程复习资料";
            String risk = mediumHits.isEmpty() ? "LOW" : "MEDIUM";
            List<String> riskReasons = mediumHits.isEmpty()
                    ? List.of("未发现明显风险关键词")
                    : List.of("包含需要人工确认的敏感表述：" + String.join("、", mediumHits));
            return new GoodsAssistDraft(
                    optimizedTitle,
                    "适合课程复习使用，建议补充版本、新旧程度和是否可现场翻看。",
                    "BOOKS",
                    List.of("教材资料", "期末复习"),
                    risk,
                    riskReasons,
                    "根据标题和描述判断更适合教材资料分类"
            );
        }

        String risk = mediumHits.isEmpty() ? "LOW" : "MEDIUM";
        List<String> riskReasons = mediumHits.isEmpty()
                ? List.of("未发现明显风险关键词")
                : List.of("包含需要人工确认的敏感表述：" + String.join("、", mediumHits));
        return new GoodsAssistDraft(
                compactTitle(title),
                description + " 建议补充品牌型号、使用年限、瑕疵说明和面交时间。",
                category,
                categoryTags(category),
                risk,
                riskReasons,
                "根据标题和描述给出通用发布优化建议，分类初判为 " + category
        );
    }

    private static String detectCategory(String text) {
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "DAILY";
    }

    private static List<String> categoryTags(String category) {
        return switch (category) {
            case "DIGITAL" -> List.of("数码电子", "配件齐全");
            case "SPORTS" -> List.of("运动户外");
            case "CLOTHING" -> List.of("服饰鞋包");
            default -> List.of("校园闲置");
        };
    }

    private static String firstSubject(String text) {
        for (String subject : BOOK_SUBJECTS) {
            if (text.contains(subject)) {
                return subject;
            }
        }
        return null;
    }

    private static List<String> matches(String text, List<String> terms) {
        List<String> hits = new ArrayList<>();
        for (String term : terms) {
            if (text.contains(term) && !hits.contains(term)) {
                hits.add(term);
            }
        }
        return hits;
    }

    private static int severity(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
    }

    private static Map<String, List<String>> categoryKeywords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("BOOKS", List.of("教材", "书", "复习", "笔记", "真题", "考研", "课程", "资料", "词汇", "习题", "数据库"));
        map.put("DIGITAL", List.of("电脑", "笔记本", "手机", "平板", "耳机", "数码", "显示器", "键盘", "鼠标", "相机", "充电", "计算器", "ipad", "switch"));
        map.put("SPORTS", List.of("球", "运动", "健身", "羽毛球", "篮球", "跑鞋", "瑜伽", "滑板"));
        map.put("CLOTHING", List.of("衣", "鞋", "包", "外套", "卫衣", "裤", "帽"));
        map.put("DAILY", List.of("台灯", "宿舍", "收纳", "水杯", "被", "生活", "椅", "桌"));
        return map;
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

    private static String firstNonBlank(String primary, String fallback) {
        return blankToNull(primary) == null ? fallback : primary;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizedPrice(String price) {
        if (price == null || price.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(price.trim()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String required(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw ApiExceptions.validation(message, Map.of("field", field));
        }
        return value.trim();
    }

    private record Merged(GoodsAssistDraft draft, String source) {
    }
}
