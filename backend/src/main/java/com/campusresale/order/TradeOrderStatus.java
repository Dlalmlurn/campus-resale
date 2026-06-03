package com.campusresale.order;

import com.campusresale.platform.api.ApiExceptions;
import java.util.Arrays;
import java.util.Map;

public enum TradeOrderStatus {
    PENDING_SELLER_CONFIRM,
    PENDING_PAYMENT,
    PAID_PENDING_MEETUP,
    COMPLETED_PENDING_SETTLEMENT,
    COMPLETED,
    CANCELLED,
    CLOSED,
    DISPUTE_PROCESSING,
    REFUND_PROCESSING;

    public static TradeOrderStatus parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiExceptions.validation("订单状态不支持", Map.of("field", "status")));
    }
}
