package com.campusresale.intelligence;

public final class IntelligenceRequests {

    private IntelligenceRequests() {
    }

    public record GoodsAssistRequest(
            String title,
            String description,
            String price
    ) {
    }
}
