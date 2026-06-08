package com.campusresale.order;

import java.math.RoundingMode;
import java.time.Instant;

public record PaymentResponse(
        long id,
        String paymentNo,
        long orderId,
        String amount,
        String status,
        String provider,
        Instant createdAt,
        Instant paidAt,
        Instant closedAt
) {

    public static PaymentResponse from(PaymentOrderRecord record) {
        return new PaymentResponse(
                record.id(),
                record.paymentNo(),
                record.orderId(),
                record.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                record.status().name(),
                record.provider(),
                record.createdAt(),
                record.paidAt(),
                record.closedAt()
        );
    }
}
