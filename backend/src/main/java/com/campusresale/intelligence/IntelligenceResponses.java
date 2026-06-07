package com.campusresale.intelligence;

import java.util.List;

public final class IntelligenceResponses {

    private IntelligenceResponses() {
    }

    public record GoodsAssistResponse(
            long requestId,
            String optimizedTitle,
            String optimizedDescription,
            String suggestedCategoryCode,
            List<String> suggestedTags,
            String riskLevel,
            List<String> riskReasons,
            String recommendationReason,
            String auditReminder,
            // 建议来源：LLM 表示由大模型生成，RULES 表示退回规则引擎；便于前端与答辩区分。
            String assistSource
    ) {
    }
}
