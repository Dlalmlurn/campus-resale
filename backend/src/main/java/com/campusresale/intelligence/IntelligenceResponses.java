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
            String auditReminder
    ) {
    }
}
