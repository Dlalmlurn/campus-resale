package com.campusresale.order;

import java.time.Instant;

public final class OrderRequests {

    private OrderRequests() {
    }

    public record CreateOrderRequest(
            Long goodsId,
            Long tradePlaceId,
            String tradePlaceDetail,
            Instant meetupTime,
            String note
    ) {
    }

    public record ReasonRequest(String reason) {
    }

    public record ReviewRequest(Integer rating, String content) {
    }
}
