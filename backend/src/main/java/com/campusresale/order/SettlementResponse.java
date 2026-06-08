package com.campusresale.order;

import java.math.RoundingMode;
import java.time.Instant;

public record SettlementResponse(
        long id,
        long orderId,
        long paymentOrderId,
        String settlementNo,
        String settlementAmount,
        String status,
        Instant freezeStartedAt,
        Instant freezeEndsAt,
        Instant settledAt,
        String failureReason
) {

    public static SettlementResponse from(SettlementRecord record) {
        return new SettlementResponse(
                record.id(),
                record.orderId(),
                record.paymentOrderId(),
                record.settlementNo(),
                record.settlementAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                record.status().name(),
                record.freezeStartedAt(),
                record.freezeEndsAt(),
                record.settledAt(),
                record.failureReason()
        );
    }
}
