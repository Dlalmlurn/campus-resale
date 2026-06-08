package com.campusresale.order;

import java.time.Instant;
import java.util.List;

public final class OrderRequests {

    private OrderRequests() {
    }

    public record CreateOrderRequest(
            Long goodsId,
            Long acceptedBargainCardId,
            Long tradePlaceId,
            String tradePlaceDetail,
            Instant meetupTime,
            String note
    ) {
    }

    public record ReasonRequest(String reason) {
    }

    public record RefundRequest(
            String refundType,
            String amount,
            String reason,
            List<Long> evidenceFileIds
    ) {
    }

    public record DecideRefundRequest(
            String decision,
            String decisionNote
    ) {
    }

    public record ReviewRequest(Integer rating, String content) {
    }
}
