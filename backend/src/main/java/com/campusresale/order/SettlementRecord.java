package com.campusresale.order;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementRecord(
        long id,
        long orderId,
        long paymentOrderId,
        String settlementNo,
        BigDecimal settlementAmount,
        SettlementStatus status,
        Instant freezeStartedAt,
        Instant freezeEndsAt,
        Instant settledAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
