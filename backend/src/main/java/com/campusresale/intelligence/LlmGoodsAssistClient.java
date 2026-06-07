package com.campusresale.intelligence;

import com.campusresale.platform.config.CampusResaleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek（OpenAI 兼容）LLM 客户端，用于生成商品发布文案与风险初判。
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>仅当 {@code campus-resale.ai} 开启且配置了 baseUrl/apiKey 时才会真正联网调用。</li>
 *   <li>任何失败（未启用、网络异常、超时、解析失败、字段缺失）都返回 {@link Optional#empty()}，
 *       由 {@link IntelligenceService} 退回稳健规则引擎，保证离线与答辩演示始终可用。</li>
 *   <li>LLM 只产出建议草稿，风险判定最终仍由规则引擎兜底，不可被 LLM 降级。</li>
 * </ul>
 */
@Component
public class LlmGoodsAssistClient {

    private static final Logger log = LoggerFactory.getLogger(LlmGoodsAssistClient.class);

    private static final String SYSTEM_PROMPT = """
            你是校园二手交易平台的发布助手。请阅读卖家填写的商品标题、描述和价格，给出发布优化建议。
            只能输出一个 JSON 对象，不要包含多余文字或 Markdown 代码块，字段如下：
            {
              "optimizedTitle": "更清晰的标题，<=28 字",
              "optimizedDescription": "更完整的描述，补充成色、配件、面交方式等",
              "suggestedCategoryCode": "DIGITAL|BOOKS|DAILY|SPORTS|CLOTHING 之一",
              "suggestedTags": ["最多4个标签"],
              "riskLevel": "LOW|MEDIUM|HIGH",
              "riskReasons": ["命中的风险原因，没有则给出'未发现明显风险'"],
              "recommendationReason": "一句话说明分类与优化依据"
            }
            你只提供建议与风险提醒，不做自动审核、下架或处罚。""";

    private final CampusResaleProperties.Ai aiConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmGoodsAssistClient(CampusResaleProperties properties, ObjectMapper objectMapper) {
        this.aiConfig = properties == null ? null : properties.ai();
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient(this.aiConfig);
    }

    /**
     * 调用 LLM 生成商品发布建议草稿。未启用或任何异常时返回空，交由规则引擎兜底。
     */
    public Optional<GoodsAssistDraft> generate(String title, String description, String price) {
        if (restClient == null || aiConfig == null || !aiConfig.usable()) {
            return Optional.empty();
        }
        try {
            String userPrompt = "标题：" + title + "\n描述：" + description
                    + "\n价格：" + (price == null || price.isBlank() ? "未填写" : price);
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", aiConfig.model(),
                    "temperature", 0.3,
                    "stream", false,
                    "response_format", java.util.Map.of("type", "json_object"),
                    "messages", List.of(
                            java.util.Map.of("role", "system", "content", SYSTEM_PROMPT),
                            java.util.Map.of("role", "user", "content", userPrompt)
                    )
            ));
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + aiConfig.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(raw);
        } catch (Exception exception) {
            // 联网失败或返回异常不应阻断发布流程，记录后退回规则引擎。
            log.warn("LLM goods-assist call failed, falling back to rule engine: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GoodsAssistDraft> parse(String rawResponse) {
        try {
            if (rawResponse == null || rawResponse.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return Optional.empty();
            }
            JsonNode advice = objectMapper.readTree(content.asText());
            String optimizedTitle = text(advice, "optimizedTitle");
            String optimizedDescription = text(advice, "optimizedDescription");
            if (optimizedTitle == null || optimizedDescription == null) {
                return Optional.empty();
            }
            return Optional.of(new GoodsAssistDraft(
                    optimizedTitle,
                    optimizedDescription,
                    normalizeCategory(text(advice, "suggestedCategoryCode")),
                    stringList(advice.path("suggestedTags"), 4),
                    normalizeRisk(text(advice, "riskLevel")),
                    stringList(advice.path("riskReasons"), 6),
                    orDefault(text(advice, "recommendationReason"), "LLM 根据标题和描述生成的发布优化建议")
            ));
        } catch (Exception exception) {
            log.warn("LLM goods-assist response parse failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static RestClient buildRestClient(CampusResaleProperties.Ai config) {
        if (config == null || !config.usable()) {
            return null;
        }
        int timeout = config.timeoutMs() > 0 ? config.timeoutMs() : 6000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeout));
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static List<String> stringList(JsonNode arrayNode, int max) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                String text = element.asText("").trim();
                if (!text.isEmpty() && !values.contains(text)) {
                    values.add(text);
                }
                if (values.size() >= max) {
                    break;
                }
            }
        }
        return values;
    }

    private static String normalizeCategory(String raw) {
        if (raw == null) {
            return "DAILY";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "DIGITAL", "BOOKS", "DAILY", "SPORTS", "CLOTHING" -> upper;
            default -> "DAILY";
        };
    }

    private static String normalizeRisk(String raw) {
        if (raw == null) {
            return "LOW";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "LOW", "MEDIUM", "HIGH" -> upper;
            default -> "LOW";
        };
    }
}
