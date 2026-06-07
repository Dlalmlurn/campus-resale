package com.campusresale.intelligence;

import java.util.List;

/**
 * AI 发布辅助的结构化建议草稿。
 *
 * <p>规则引擎与 LLM 客户端都产出同一种草稿，便于业务层用统一逻辑合并：
 * LLM 负责更自然的文案与分类，规则引擎负责稳健、可解释、不可被绕过的风险兜底。</p>
 */
public record GoodsAssistDraft(
        String optimizedTitle,
        String optimizedDescription,
        String suggestedCategoryCode,
        List<String> suggestedTags,
        String riskLevel,
        List<String> riskReasons,
        String recommendationReason
) {
}
